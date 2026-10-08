#!/usr/bin/env bash
set -euo pipefail
model="${1:-tiny}"
case "$model" in tiny|base|small) ;; *) echo 'Use tiny, base or small (multilingual).' >&2; exit 1 ;; esac
root="$(cd "$(dirname "$0")/.." && pwd)"
output="$root/whisper-test/app/src/main/assets/models"
mkdir -p "$output"
curl --fail --location --retry 3 "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$model.bin" --output "$output/ggml-$model.bin.part"
mv "$output/ggml-$model.bin.part" "$output/ggml-$model.bin"
shasum -a 256 "$output/ggml-$model.bin"
