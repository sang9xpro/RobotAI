# VieNeu TTS prototype

Android test integration lives in `android-app/whisper-test`: VieNeu v3 Turbo FP32,
fixed Hải Đăng preset, CPU ONNX Runtime 1.24.3, sea-g2p C ABI through JNI.
Run `python3 android-app/tts-test/prepare_ort_runtime.py` before building from
original AARs. This creates the TTS AAR with SONAME `libort_tts.so` and matching
JNI dependency/version-provider filename. Sherpa retains its own ORT 1.28.2.
Do not resolve the duplicate original runtime with Gradle `pickFirst`: their
versioned C API imports require different symbol versions and cause a crash.
ARM64 / Android 8+ only. No server or network is used during synthesis.

The test creates the complete audio before playback (not streaming), accepts up to
200 text characters, offers cancellation, replay and WAV export. Models copy to
app-private storage on first use. Keep sufficient free storage for APK + model copy.
STT uses PhoWhisper-small with timestamps disabled and the original recorded audio.

Prepare assets with `prepare_android.py` in the VieNeu virtual environment.
`build_native.sh` builds JNI from sea-g2p commit
`e825173f235d08ea19315b2b279fb11153b44cea`, using Rust 1.85.1 + Android ARM64 std
and NDK 25.2.9519653. The local Cargo lock pins unicode-normalization to 0.1.24;
the 0.1.25 archive was unavailable from the accessible registry mirrors.
Native `.so` files and downloaded model assets are ignored by Git but present locally.
Android Studio can build the prepared project normally; no Rust step is needed
unless rebuilding the native phonemizer.

Build: `./gradlew :app:assembleDebug` from `android-app/whisper-test` with JDK 17.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

Desktop sample generation:

Pinned upstream SDK commit: `85344322b7258b4e25479b692e8e3396baf9db34` (SDK 3.8.3).

From workspace root, after cloning that revision to `android-app/.work/vieneu-tts` and installing its frozen `uv.lock`:

```sh
python3 android-app/tts-test/download_vieneu.py
HF_HOME="$PWD/android-app/.work/hf-cache" HF_HUB_OFFLINE=1 NUMBA_CACHE_DIR="$PWD/android-app/.work/numba-cache" android-app/.work/vieneu-tts/.venv/bin/python android-app/tts-test/generate_samples.py
python3 -m http.server 8765 --bind 127.0.0.1 --directory samples/tts/vieneu-v3-turbo
```

Open http://127.0.0.1:8765. Download script pins model/codec revisions, verifies range lengths and writes per-file SHA256 provenance (not independent expected checksum verification). Generation is offline and uses built-in preset speaker embeddings/reference codes; no user voice cloning. Peak gain is reduced if needed before PCM16 export. Measurements are for the desktop CPU only.

See `docs/VIENEU_TTS_TRIAL_VI.md` for Android prerequisites and limits.
