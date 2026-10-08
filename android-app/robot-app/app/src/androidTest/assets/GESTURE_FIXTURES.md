# MediaPipe gesture test photos

These photos are used only by Android instrumentation tests, not packaged into the application APK.

- `gesture-thumb-up.jpg`: https://storage.googleapis.com/mediapipe-assets/thumb_up.jpg
- `gesture-two-hands.jpg`: https://storage.googleapis.com/mediapipe-assets/right_hands.jpg

Upstream project: https://github.com/google-ai-edge/mediapipe
The upstream test asset definitions are in `third_party/external_files.bzl`.

These samples validate the bundled stock model and hand landmark adapter. Finger-heart and two-hand-heart geometry is tested separately with synthetic landmarks; these photos do not establish real-world accuracy for custom heart poses.
