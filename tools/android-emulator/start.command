#!/bin/zsh
cd "$(dirname "$0")/../.." || exit 1
exec python3 tools/android-emulator/local.py start
