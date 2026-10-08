#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
lib="$root/whisper-test/app/libs/sherpa-onnx-1.13.8.aar"
model="$root/whisper-test/app/src/main/assets/speaker/campplus.onnx"
mkdir -p "$(dirname "$lib")" "$(dirname "$model")"
fetch() {
  local url="$1" dest="$2" expected="$3"
  if [[ -f "$dest" && "$(shasum -a 256 "$dest" | cut -d ' ' -f 1)" == "$expected" ]]; then
    echo "Already verified: $dest"
    return
  fi
  curl --fail --location --retry 3 "$url" --output "$dest.part"
  local actual
  actual="$(shasum -a 256 "$dest.part" | cut -d ' ' -f 1)"
  [[ "$actual" == "$expected" ]] || { echo "SHA-256 mismatch: $dest" >&2; exit 1; }
  mv "$dest.part" "$dest"
}
fetch 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar' "$lib" '633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
fetch 'https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx' "$model" '357a834f702b80161e5b981182c038e18553c1f2ca752ed6cec2052365d4129b'
fetch 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx' "$(dirname "$model")/silero_vad.onnx" '9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6'
echo 'Offline speaker model and Android runtime ready.'
