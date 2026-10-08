#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
revision=6e4ab854f67f743900934a703d5603419384c961
repo="$root/vendor/whisper.cpp"
if [[ ! -d "$repo/.git" ]]; then
  git clone https://github.com/ggml-org/whisper.cpp.git "$repo"
fi
if [[ -n "$(git -C "$repo" status --porcelain)" ]]; then
  echo 'Vendor checkout has local changes; leave it untouched.' >&2
  exit 1
fi
git -C "$repo" fetch origin "$revision"
git -C "$repo" checkout --detach "$revision"
echo "Whisper source ready: $revision"
