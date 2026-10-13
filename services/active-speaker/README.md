# TalkNet for individual conversation

Run `prepare.py` with `requirements.txt` in an isolated Python environment to recreate the ignored Android ONNX asset. The official upstream commit and checkpoint SHA256 are pinned in `model-manifest.json`. The exporter uses CPU, loads tensors with `weights_only=True`, verifies every model weight, and compares ONNX logits against PyTorch before reporting success.

Input is one second: audio MFCC `[1,100,13]`, grayscale face `[1,25,112,112]`; output is `[25,2]` logits. Android resamples timestamped face frames to 25 FPS, rejects stale frames and tracking discontinuities, and requires both verified voice and facing evidence. This does not add group conversation.

The observed ONNX checksum is for the pinned export environment. Model acceptance on Android remains gated by handset measurements; CPU export parity does not measure robot recognition accuracy. See [the implementation and device verification notes](../../docs/INDIVIDUAL_CONVERSATION_VI.md).
