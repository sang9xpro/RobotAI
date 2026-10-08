import json
import math
import tempfile
import unittest
from pathlib import Path

import train


def hand(straight):
    points = [[.5, .6, 0.] for _ in range(21)]
    points[0] = [.5, .8, 0.]
    points[1], points[2], points[3] = [.35, .7, 0.], [.35, .6, 0.], [.35, .5, 0.]
    points[4] = [.35, .3 if 0 in straight else .65, 0.]
    for finger in range(1, 5):
        x = .4+finger*.04
        points[finger*4+1] = [x, .65, 0.]
        points[finger*4+2] = [x, .5, 0.]
        points[finger*4+3] = [x, .45, 0.]
        points[finger*4+4] = [2*x-.5, .2, 0.] if finger in straight else [.5, .72, 0.]
    return {"points": points, "side": "Right", "aspect": 1., "elapsedMs": 0}


def dataset():
    definitions = [{"id": "like", "personId": "fixture", "name": "Like của tôi", "action": "THUMBS_UP"},
                   {"id": "rock", "personId": "fixture", "name": "Rock", "action": "ROCK_ON"},
                   {"id": "neutral", "personId": "fixture", "name": "Tay bình thường", "action": "none"}]
    examples = []
    for label, straight in (("like", {0}), ("rock", {1, 4}), ("neutral", {0, 1, 2, 3, 4})):
        for session in range(4):
            for index in range(5):
                frames = []
                for f in range(3):
                    frame = hand(straight)
                    # Bounded independent perturbation/translation, not exact duplicate frames.
                    for p in frame["points"]:
                        p[0] += (session-1.5)*.005+index*.001
                        p[1] += f*.0005
                    frame["elapsedMs"] = f*200
                    frames.append(frame)
                examples.append({"id": f"{label}-{session}-{index}", "gestureId": label,
                                 "sessionId": f"session-{label}-{session}", "frames": frames,
                                 "createdAt": 1000, "accepted": True, "feedback": "taught"})
    return {"version": 1, "featureVersion": train.FEATURE_VERSION, "revision": 1,
            "definitions": definitions, "examples": examples}


class TrainingTest(unittest.TestCase):
    def test_feature_invariance_and_invalid_frames(self):
        frame = hand({0})
        mirrored = hand({0})
        mirrored["side"] = "Left"
        for p in mirrored["points"]:
            p[0] = 1-p[0]
        self.assertLess(train.distance(train.features(frame), train.features(mirrored)), 1e-6)
        frame["points"][4][2] = math.nan
        with self.assertRaises(ValueError):
            train.features(frame)

    def test_split_has_no_session_leakage_and_requires_independent_sessions(self):
        root = dataset()
        _, labels, rows = train.load_dataset(root, "fixture")
        split = train.split_sessions(rows, labels)
        groups = {name: {r["group"] for r in part} for name, part in split.items()}
        self.assertFalse(groups["train"] & groups["test"])
        self.assertFalse(groups["validation"] & groups["test"])
        self.assertFalse(groups["train"] & groups["validation"])
        for row in rows:
            row["group"] = "same-video"
        with self.assertRaises(ValueError):
            train.split_sessions(rows, labels)

    def test_training_reproduces_labels_and_exports_a_real_model(self):
        root = dataset()
        artifact = train.train(root, "fixture", epochs=80)
        self.assertTrue(artifact["report"]["eligible"])
        self.assertEqual(artifact["report"]["falsePositiveRate"], 0)
        self.assertEqual(artifact["report"]["testExamples"], 15)
        defs = {d["id"]: d for d in root["definitions"]}
        for label, pose in (("like", {0}), ("rock", {1, 4}), ("none", {0, 1, 2, 3, 4})):
            self.assertEqual(label, train.predict(artifact["model"], train.features(hand(pose)), defs))

    def test_tflite_graph_and_weights_match_training_artifact(self):
        import tflite
        from export_tflite import export
        artifact = train.train(dataset(), "fixture", epochs=80)
        data = export(artifact["model"])
        self.assertEqual(data[4:8], b"TFL3")
        model = tflite.Model.GetRootAsModel(data, 0)
        graph = model.Subgraphs(0)
        self.assertEqual(graph.Tensors(0).Shape(1), 69)
        self.assertEqual(graph.Tensors(4).Shape(1), 3)
        self.assertEqual(graph.OperatorsLength(), 2)
        import struct
        raw = bytes(model.Buffers(1).DataAsNumpy())
        weights = struct.unpack("<"+"f"*(len(raw)//4), raw)
        expected = [v for row in artifact["model"]["weights"] for v in row]
        for a, b in zip(weights, expected):
            self.assertAlmostEqual(a, b, places=5)


if __name__ == "__main__":
    unittest.main()
