#!/usr/bin/env python3
"""Train Ken's small softmax head from explicit labels. Python standard library only.
Never splits adjacent frames: all examples from a session stay in the same partition.
"""
import argparse
import hashlib
import json
import math
import random
from pathlib import Path

FEATURE_VERSION = "ken-hand69-v1"
SIZE = 69


def features(frame):
    points = frame["points"]
    aspect, side = float(frame["aspect"]), frame["side"]
    if len(points) != 21 or side not in ("Left", "Right") or not .1 <= aspect <= 10:
        raise ValueError("Invalid hand frame")
    if any(len(p) != 3 or not all(math.isfinite(v) for v in p) or
           not -.05 <= p[0] <= 1.05 or not -.05 <= p[1] <= 1.05 for p in points):
        raise ValueError("Invalid/clipped landmarks")
    if any(not (0 <= points[i][0] <= 1 and 0 <= points[i][1] <= 1) for i in (0, 5, 9, 13, 17)):
        raise ValueError("Clipped palm")
    if sum(0 <= p[0] <= 1 and 0 <= p[1] <= 1 for p in points) < 17:
        raise ValueError("Clipped hand")
    origin = points[0]
    scale = math.hypot(points[9][0]-origin[0], (points[9][1]-origin[1])*aspect)
    if scale <= .025:
        raise ValueError("Hand too small")
    flip = -1 if side == "Left" else 1
    result = [v for p in points for v in ((p[0]-origin[0])/scale*flip,
              (p[1]-origin[1])*aspect/scale, (p[2]-origin[2])/scale)]
    def length(a, b):
        return math.sqrt((a[0]-b[0])**2 + ((a[1]-b[1])*aspect)**2 + (a[2]-b[2])**2)
    for finger in range(5):
        indices = (1, 3, 4) if finger == 0 else (0, 2+finger*4, 4+finger*4)
        a, b, c = (points[i] for i in indices)
        ab, bc, ac = length(a, b), length(b, c), length(a, c)
        if ab < .0001 or bc < .0001:
            raise ValueError("Degenerate finger")
        result.append(math.acos(max(-1, min(1, (ab*ab+bc*bc-ac*ac)/(2*ab*bc)))) / math.pi)
    result.append(math.hypot(points[4][0]-points[8][0], (points[4][1]-points[8][1])*aspect)/scale)
    if any(not math.isfinite(v) or abs(v) > 8 for v in result):
        raise ValueError("Feature out of bounds")
    return result


def distance(a, b):
    return math.sqrt(sum((x-y)**2 for x, y in zip(a, b))/SIZE)


def load_dataset(root, person):
    if root["version"] != 1 or root["featureVersion"] != FEATURE_VERSION:
        raise ValueError("Incompatible feature version")
    definitions = {d["id"]: d for d in root["definitions"] if d["personId"] == person}
    rows = []
    ids = set()
    for e in root["examples"]:
        if e["id"] in ids:
            raise ValueError("Duplicate example ID")
        ids.add(e["id"])
        if not e["accepted"] or e["gestureId"] not in definitions:
            continue
        frames = e["frames"]
        if not 3 <= len(frames) <= 12 or not e["sessionId"]:
            raise ValueError("Invalid capture")
        if any(b["elapsedMs"] <= a["elapsedMs"] for a, b in zip(frames, frames[1:])):
            raise ValueError("Non-monotonic capture")
        values = [features(f) for f in frames]
        rows.append({"id": e["id"], "label": e["gestureId"], "group": e["sessionId"],
                     "x": [sum(v[i] for v in values)/len(values) for i in range(SIZE)]})
    labels = sorted(set(r["label"] for r in rows))
    if not 2 <= len(labels) <= 40 or not any(definitions[l]["action"] == "none" for l in labels):
        raise ValueError("Need at least one gesture and Tay bình thường (none)")
    return definitions, labels, rows


def split_sessions(rows, labels, seed=42):
    rng = random.Random(seed)
    groups = {label: sorted(set(r["group"] for r in rows if r["label"] == label)) for label in labels}
    if any(len(g) < 4 for g in groups.values()):
        raise ValueError("Collect at least 4 independent sessions per label, including none; use 5+ captures/session")
    # Shared feedback sessions may contain several labels. Assign the session once.
    for attempt in range(300):
        assigned = {}
        ordering = sorted(labels, key=lambda l: (len(groups[l]), rng.random()))
        failed = False
        for label in ordering:
            gs = groups[label][:]
            rng.shuffle(gs)
            missing = [part for part in ("train", "validation", "test")
                       if not any(assigned.get(g) == part for g in gs)]
            free = [g for g in gs if g not in assigned]
            if len(free) < len(missing):
                failed = True
                break
            for part, group in zip(missing, free):
                assigned[group] = part
        if failed:
            continue
        for row in rows:
            assigned.setdefault(row["group"], "train")
        parts = {part: [r for r in rows if assigned[r["group"]] == part]
                 for part in ("train", "validation", "test")}
        if all(sum(r["label"] == label for r in parts[part]) >= minimum
               for label in labels for part, minimum in (("train", 10), ("validation", 5), ("test", 5))):
            return parts
    raise ValueError("Cannot build disjoint balanced partitions; collect more independent sessions for each label")


def scores(model, x):
    logits = [b + sum(w*v for w, v in zip(weight, x)) for weight, b in zip(model["weights"], model["bias"])]
    peak = max(logits)
    values = [math.exp(v-peak) for v in logits]
    return [v/sum(values) for v in values]


def predict(model, x, definitions):
    probabilities = scores(model, x)
    indices = sorted(range(len(probabilities)), key=lambda i: probabilities[i], reverse=True)
    winner = indices[0]
    label = model["labels"][winner]
    if definitions[label]["action"] == "none":
        return "none"
    if probabilities[winner] < model["threshold"] or probabilities[winner]-probabilities[indices[1]] < model["margin"]:
        return "none"
    if min(distance(x, anchor) for anchor in model["anchors"][winner]) > model["radius"]:
        return "none"
    return label


def evaluate(model, rows, definitions):
    confusion, support, correct, false_positives, negatives = {}, {}, 0, 0, 0
    for row in rows:
        actual = "none" if definitions[row["label"]]["action"] == "none" else row["label"]
        prediction = predict(model, row["x"], definitions)
        confusion.setdefault(actual, {})[prediction] = confusion.get(actual, {}).get(prediction, 0)+1
        support[actual] = support.get(actual, 0)+1
        correct += actual == prediction
        if actual == "none":
            negatives += 1
            false_positives += prediction != "none"
    recall = {label: confusion[label].get(label, 0)/n for label, n in support.items()}
    return {"accuracy": correct/len(rows), "falsePositiveRate": false_positives/negatives if negatives else 1,
            "examples": len(rows), "confusion": confusion, "support": support, "recall": recall}


def fit(labels, parts, epochs, seed):
    rng = random.Random(seed)
    weights = [[rng.uniform(-.001, .001) for _ in range(SIZE)] for _ in labels]
    bias = [0.0]*len(labels)
    model = {"labels": labels, "weights": weights, "bias": bias}
    best_loss, best, stale = float("inf"), None, 0
    for epoch in range(epochs):
        rows = parts["train"][:]
        rng.shuffle(rows)
        lr = .05/(1+epoch*.01)
        for row in rows:
            probabilities = scores(model, row["x"])
            target = labels.index(row["label"])
            for j in range(len(labels)):
                error = probabilities[j]-(j == target)
                bias[j] -= lr*error
                for i in range(SIZE):
                    weights[j][i] -= lr*(error*row["x"][i]+.0001*weights[j][i])
        loss = sum(-math.log(max(1e-12, scores(model, r["x"])[labels.index(r["label"])])) for r in parts["validation"])/len(parts["validation"])
        if loss < best_loss-.00001:
            best_loss, best, stale = loss, ([w[:] for w in weights], bias[:]), 0
        else:
            stale += 1
        if stale >= 25:
            break
    return best, epoch+1


def train(root, person, epochs=200, seed=42):
    definitions, labels, rows = load_dataset(root, person)
    parts = split_sessions(rows, labels, seed)
    (weights, bias), epochs_run = fit(labels, parts, epochs, seed)
    fingerprint = hashlib.sha256(json.dumps(root, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    model_id = hashlib.sha256(f"{fingerprint}:{person}:{epochs}:{seed}".encode()).hexdigest()[:12]
    model = {"id": "ken-"+model_id, "personId": person, "featureVersion": FEATURE_VERSION,
             "labels": labels, "weights": weights, "bias": bias, "threshold": .75, "margin": .2, "radius": .2,
             "anchors": [[r["x"] for r in parts["train"] if r["label"] == label][:100] for label in labels]}
    # Select thresholds with validation only. Test set is never consulted during calibration.
    best_score, choice = -1, None
    for threshold in (.75, .8, .85, .9, .95):
        for margin in (.1, .15, .2, .3):
            model.update(threshold=threshold, margin=margin)
            result = evaluate(model, parts["validation"], definitions)
            value = result["accuracy"]-2*result["falsePositiveRate"]
            if value > best_score:
                best_score, choice = value, (threshold, margin)
    model.update(threshold=choice[0], margin=choice[1])
    test = evaluate(model, parts["test"], definitions)
    report = {**test, "testExamples": len(parts["test"]), "validation": evaluate(model, parts["validation"], definitions),
              "eligible": test["accuracy"] >= .85 and test["falsePositiveRate"] <= .05 and min(test["recall"].values()) >= .8,
              "datasetSha256": fingerprint, "datasetRevision": root["revision"], "seed": seed, "epochs": epochs_run,
              "splitSessions": {name: sorted(set(r["group"] for r in values)) for name, values in parts.items()},
              "splitExamples": {name: len(values) for name, values in parts.items()},
              "limits": "Held-out capture evaluation only; test false triggers and latency on the actual phone before acceptance."}
    return {"version": 1, "model": model, "report": report}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--person", help="Person ID; inferred only if dataset contains one person")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=200)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    if args.dataset.stat().st_size > 16_000_000:
        parser.error("Dataset exceeds 16 MB")
    root = json.loads(args.dataset.read_text())
    people = {d["personId"] for d in root["definitions"]}
    person = args.person or (next(iter(people)) if len(people) == 1 else None)
    if not person or not 1 <= args.epochs <= 2000:
        parser.error("Select --person and epochs 1..2000")
    try:
        artifact = train(root, person, args.epochs, args.seed)
    except (ValueError, KeyError, TypeError) as error:
        parser.error(str(error))
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output/"model.json").write_text(json.dumps(artifact, indent=2, allow_nan=False))
    (args.output/"report.json").write_text(json.dumps(artifact["report"], indent=2, allow_nan=False))
    print(json.dumps({key: artifact["report"][key] for key in ("eligible", "accuracy", "falsePositiveRate", "testExamples")}))


if __name__ == "__main__":
    main()
