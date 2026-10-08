# PhoWhisper backend STT

Java gọi whisper.cpp worker giữ model nạp sẵn. Mặc định PhoWhisper-small, Metal trên macOS. Python chỉ quản lý tiến trình; không cần PyTorch để inference.

## Chạy từ root RobotAI

```bash
python3 services/phowhisper/local.py build
python3 services/phowhisper/local.py start
python3 services/phowhisper/local.py status
python3 services/phowhisper/local.py configure-backend
```

Cần CMake/C++ compiler và model GGML. Source vendored ở `android-app/vendor/whisper.cpp`; binary build trong `.local/phowhisper-build`. Worker bind `127.0.0.1:8788`, không có auth; chỉ dùng private local. Không expose trực tiếp ra Internet. `status` kiểm tiến trình thuộc runner và `/health` sau khi model nạp xong.

Cấu hình `.local/phowhisper.json` gồm `model` (đường dẫn GGML), `port`, `threads`. Đổi model cần stop/start. Small hiện tham chiếu file đã chuẩn bị `android-app/whisper-test/app/src/main/assets/models/ggml-phowhisper-small.bin`, SHA-256 `db8de368e822fce5dcdce7c0c4c80c4b554c27655516dea5ec8c093e22f58785`. Large Q5 có ở `android-app/.work/phowhisper-large/ggml-phowhisper-large-q5_0.bin`, cần benchmark riêng. Model không được tự tải.

`configure-backend` ghi vào `.local/backend.env` khi worker ready:

```text
XIAOZHI_STT_DEFAULT_PROVIDER=phowhisper
XIAOZHI_STT_PHOWHISPER_API_URL=http://127.0.0.1:8788/inference
```

Build Java rồi stop/start hai module bằng `server-java/scripts/robotai-local.py`, management trước rồi dialogue. Role không chỉ định STT dùng default này; role chọn STT riêng vẫn giữ lựa chọn đó. Giao diện quản trị có provider PhoWhisper và trường Inference URL, không cần API key. API trạng thái STT mặc định kiểm health worker.

Dừng worker: `python3 services/phowhisper/local.py stop`.

## Luồng và giới hạn

Android Opus → Java decode/VAD/segment → PhoWhisperSttService → WAV PCM mono 16 kHz S16LE → multipart POST /inference → final text → LLM hiện có. Ép tiếng Việt, không translate, greedy; worker không giữ context request trước. Adapter nhận PCM liên tục nhưng chỉ inference sau endpoint; chưa có partial hoặc contract worker WebSocket/P2.

Giới hạn 30 giây PCM mỗi đoạn, 45 giây chờ endpoint, 90 giây chờ HTTP, một lượt trong mỗi JVM. Request đồng thời nhận `busy`. Hủy/timeout ngừng chờ phía Java; whisper.cpp có thể tiếp tục inference request đã gửi. Semaphore nhả khi Java ngừng chờ; mutex worker vẫn tuần tự xử lý. Nhiều JVM và cancellation/preemption thật cần admission/protocol worker riêng. Dialogue không tự replay audio khi PhoWhisper lỗi. Lỗi worker trả STT failure, không coi là im lặng. TTS chưa thay đổi.

## Kiểm chứng

Mẫu `samples/tts/vieneu-v3-turbo/hai-dang-technical.wav` chuẩn hóa 16 kHz mono, dài 4,8 giây. Small/Metal trên Mac M4 xử lý khoảng 0,82 giây trong một lần đo warm; Java adapter gọi model thật cũng trả text. Transcript có lỗi cụm “bộ nhớ đệm”. Đây là smoke test, không phải đánh giá WER hay latency toàn hội thoại.

```bash
ffmpeg -y -i samples/tts/vieneu-v3-turbo/hai-dang-technical.wav -ar 16000 -ac 1 -c:a pcm_s16le -fflags +bitexact -flags:a +bitexact .local/phowhisper-smoke.wav
python3 services/phowhisper/local.py smoke --wav .local/phowhisper-smoke.wav
```

Test Java kiểm WAV/language/final, frame lẻ/quá dung lượng, lỗi kết nối/response, admission và nhả capacity, readiness và chọn default. Live test bật bằng `-Dphowhisper.test.wav=/absolute/path/to/canonical-pcm.wav`, worker port 8788. Chưa nghiệm thu microphone Android → STT → LLM → TTS trên thiết bị thật.

Model: https://huggingface.co/vinai/PhoWhisper-small . Runtime: https://github.com/ggml-org/whisper.cpp .
