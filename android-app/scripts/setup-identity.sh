#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
fetch() {
  local url="$1" dest="$2" expected="$3"
  mkdir -p "$(dirname "$dest")"
  if [[ -f "$dest" && "$(shasum -a 256 "$dest" | cut -d ' ' -f 1)" == "$expected" ]]; then return; fi
  curl --fail --location --retry 3 "$url" --output "$dest.part"
  [[ "$(shasum -a 256 "$dest.part" | cut -d ' ' -f 1)" == "$expected" ]] || { echo "Checksum mismatch: $dest" >&2; exit 1; }
  mv "$dest.part" "$dest"
}
fetch 'https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx' "$root/robot-app/app/src/main/assets/identity/sface.onnx" '0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79'
bash "$root/scripts/setup-speaker.sh"
fetch 'https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.24.3/onnxruntime-android-1.24.3.aar' "$root/whisper-test/app/libs/onnxruntime-android-1.24.3.aar" '67397e4a970e75617f765d2015ceaf911917e1d822276cfb5792744e8085cbce'
python3 "$root/tts-test/prepare_ort_runtime.py"
mkdir -p "$root/robot-app/app/src/main/assets/speaker"
cp "$root/whisper-test/app/src/main/assets/speaker/campplus.onnx" "$root/robot-app/app/src/main/assets/speaker/"
cp "$root/whisper-test/app/src/main/assets/speaker/silero_vad.onnx" "$root/robot-app/app/src/main/assets/speaker/"
echo 'Identity models verified. Runtime currently supports ARM64 only.'
