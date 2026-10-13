"""Recreate the ignored Android model asset using the pinned upstream source and checksum."""
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys


def main():
    root = pathlib.Path(__file__).resolve().parents[2]
    manifest = json.loads((root / "services/active-speaker/model-manifest.json").read_text())
    cache = root / ".local/active-speaker"
    cache.mkdir(parents=True, exist_ok=True)
    source = cache / "TalkNet-ASD"
    if not source.exists():
        subprocess.run(["git", "clone", manifest["source"], str(source)], check=True)
    commit = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if commit != manifest["commit"]:
        subprocess.run(["git", "-C", str(source), "checkout", "--detach", manifest["commit"]], check=True)
    checkpoint = cache / "pretrain_TalkSet.model"
    if not checkpoint.exists():
        import gdown
        gdown.download(id=manifest["checkpointGoogleDriveId"], output=str(checkpoint), quiet=False)
    if hashlib.sha256(checkpoint.read_bytes()).hexdigest() != manifest["checkpointSha256"]:
        raise ValueError("Downloaded checkpoint does not match pinned checksum")
    assets = root / "android-app/robot-app/app/src/main/assets/speaker"
    subprocess.run([sys.executable, str(root / "services/active-speaker/export_talknet.py"),
                    "--source", str(source), "--checkpoint", str(checkpoint),
                    "--sha256", manifest["checkpointSha256"], "--output", str(assets / "talknet.onnx")], check=True)
    shutil.copyfile(source / "LICENSE.md", assets / "TALKNET_LICENSE.txt")


if __name__ == "__main__":
    main()
