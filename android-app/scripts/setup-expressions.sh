#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
revision='47534e27c9851bb1128ccc0102f1145e27f23f98'
dest="$root/robot-app/app/src/main/assets/identity/facial_expression_mobilefacenet.onnx"
expected='4f61307602fc089ce20488a31d4e4614e3c9753a7d6c41578c854858b183e1a9'
mkdir -p "$(dirname "$dest")"
if [[ ! -f "$dest" ]] || [[ "$(shasum -a 256 "$dest" | cut -d ' ' -f 1)" != "$expected" ]]; then
  curl --fail --location --retry 3 "https://media.githubusercontent.com/media/opencv/opencv_zoo/$revision/models/facial_expression_recognition/facial_expression_recognition_mobilefacenet_2022july.onnx" --output "$dest.part"
  [[ "$(shasum -a 256 "$dest.part" | cut -d ' ' -f 1)" == "$expected" ]] || { echo 'Expression model checksum mismatch' >&2; exit 1; }
  mv "$dest.part" "$dest"
fi
echo 'Facial expression model verified (RGB [-1,1], NCHW 112x112, seven logits).'
