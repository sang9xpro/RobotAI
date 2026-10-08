#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TTS_NDK="${ANDROID_NDK_HOME:-/Volumes/SSD_Sang/Library/Android/sdk/ndk/25.2.9519653}"
TTS_TOOLS="$TTS_NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin"
export CARGO_HOME="$TASK_ROOT/android-app/.work/cargo"
export RUSTC="$TASK_ROOT/android-app/.work/rust-native/bin/rustc"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TTS_TOOLS/aarch64-linux-android26-clang"
export RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"
cp "$TASK_ROOT/android-app/tts-test/sea-g2p-android.lock" "$TASK_ROOT/android-app/.work/sea-g2p/Cargo.lock"
"$TASK_ROOT/android-app/.work/rust-native/bin/cargo" build --manifest-path "$TASK_ROOT/android-app/.work/sea-g2p/Cargo.toml" --release --target aarch64-linux-android --no-default-features --features capi --locked
TTS_OUT="$TASK_ROOT/android-app/whisper-test/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$TTS_OUT"
cp "$TASK_ROOT/android-app/.work/sea-g2p/target/aarch64-linux-android/release/libsea_g2p_rs.so" "$TTS_OUT/"
"$TTS_TOOLS/aarch64-linux-android26-clang++" -shared -O3 -std=c++17 -static-libstdc++ -Wl,-z,max-page-size=16384 -Wl,-soname,librobotai_tts.so "$TASK_ROOT/android-app/tts-test/native_tts.cpp" -L"$TTS_OUT" -lsea_g2p_rs -o "$TTS_OUT/librobotai_tts.so"
