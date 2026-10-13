# Facial expression recognition

Model: OpenCV Zoo Progressive Teacher / MobileFaceNet, FP32 ONNX.
Source revision: `47534e27c9851bb1128ccc0102f1145e27f23f98`.
Upstream file: `models/facial_expression_recognition/facial_expression_recognition_mobilefacenet_2022july.onnx`.
SHA-256: `4f61307602fc089ce20488a31d4e4614e3c9753a7d6c41578c854858b183e1a9` (4,791,892 bytes).

Download with `bash android-app/scripts/setup-expressions.sh` from repository root.
Model files are excluded by the repository's publishing policy. The script is pinned and verifies the asset.

Input `data`: float `[1,3,112,112]`, five-point aligned face, RGB, `(pixel/255 - .5)/.5`.
Output `label`: float `[1,7]`, **logits** (the final node is Gemm; apply stable softmax once).
Class order: angry, disgust, fearful, happy, neutral, sad, surprised.

Source: https://github.com/opencv/opencv_zoo/tree/47534e27c9851bb1128ccc0102f1145e27f23f98/models/facial_expression_recognition
Reference preprocessing: `facial_fer_model.py` at the same revision.
License: Apache 2.0, included as `FACIAL_EXPRESSION_LICENSE` from the repository root LICENSE.
Upstream directory README declares Apache 2.0 for its files. Its relative LICENSE link is absent at the pinned revision, so the root LICENSE is retained here.
Upstream model copyright notice in the adapter: Copyright (C) 2022, Shenzhen Institute of Artificial Intelligence and Robotics for Society, all rights reserved; third-party copyrights belong to their respective owners.

This asset predicts visible facial expression categories. Softmax scores are not calibrated probabilities of internal emotion or correctness. RobotAI confirms identity independently with SFace, uses temporal filtering, and enables screen reactions only for happy/neutral/sad/surprised. It does not store these observations in personal memory.
