# Kiểm thử app và backend STT/LLM/TTS

Ngày 04/10/2026. Kết luận: **chưa đạt toàn luồng Android → backend Java → STT → LLM → TTS → loa Android**. Các provider backend đã chạy liên tiếp với dữ liệu thật, nhưng client hiện tại chỉ hỗ trợ mock và chưa được nối vào backend Java thật.

## Provider thật ở backend

Test `LiveVoicePipelineTest` gọi các adapter Java sản xuất, không mock STT/LLM/TTS. Đầu vào WAV `hai-dang-technical.wav` chuẩn hóa mono 16 kHz, 4,8 giây. PhoWhisper-small/Metal → ChatGPT qua Codex gateway → Edge TTS mặc định `zh-CN-XiaoyiNeural`.

| Bước | Kết quả lần đo |
|---|---|
| STT | 671 ms, có transcript; sai cụm “bộ nhớ đệm” |
| LLM token đầu | 44.539 ms |
| LLM hoàn tất | 45.283 ms, có câu trả lời tiếng Việt |
| TTS | 5.207 ms, có MP3 40.896 byte |
| Tổng test | 51.338 ms |
| Audio đầu ra | MP3 mono 24 kHz, dài 6,816 giây; decode thành công, RMS −19,93 dB |

Đây là test nối provider trực tiếp bằng WAV/file. Không đi qua WebSocket, VAD/coordinator, phân câu TTS của dialogue hay AudioTrack của app. Số đo không phải latency microphone → loa và không phải p50/p95. TTS dùng Edge giọng mặc định tiếng Trung, chưa đánh giá phát âm tiếng Việt; VieNeu giọng Hải Đăng chưa tích hợp vào backend.

Câu LLM trả về: “Đúng rồi, dung lượng lưu trữ chứa dữ liệu, còn RAM giữ dữ liệu tạm thời khi máy xử lý.”

Audio và JSON kết quả: [reply-edge.mp3](../samples/backend-voice-test/reply-edge.mp3), [result.json](../samples/backend-voice-test/result.json).

## Các điểm chặn Android → Java

- `SocketBackend` chỉ tạo request WebSocket từ URL, chưa có luồng device credential/OTA. Request không có device-id/token bị Java trả **HTTP 401**, đã tái hiện cả từ host và instrumentation trên OPPO CPH2651 (Android 16/API 36).
- `RobotViewModel.receive()` bắt buộc hello có `robotai.audio_envelope=rai1`, `contract_version=1`, **mock=true**; app chủ động từ chối backend thật.
- Java chưa triển khai envelope RAI1 và các extension P2 (generation/turn_id/revision/abort_ack) mà app đang yêu cầu. Không thể chỉ bỏ kiểm tra mock để nối thành công.
- Database `robotai_ken` hiện có 1 user, 1 device, **0 role**; có LLM config ID 2 `codex-chatgpt`. Chưa có role để gắn các cấu hình STT/LLM/TTS cho robot.
- Backend STT hiện final theo đoạn, chưa partial thật. VieNeu streaming chưa có adapter backend.

`RealBackendCompatibilityTest` PASS nghĩa đã tái hiện lỗi 401 dự kiến, không phải E2E hội thoại thành công. Không tắt xác thực backend để ép test qua.

## Build và test client

APK build thành công. Các unit test domain/Opus được Gradle xác nhận UP-TO-DATE với kết quả trước đó (8 domain + 1 Opus), không ghi đây là lượt chạy unit test mới.

Instrumentation mock chạy trên điện thoại thật qua ADB reverse 8766. Backend mock phát WAV Hải Đăng cố định, STT/LLM là dữ liệu script; không coi đây là inference thật. Test đầu bị mất Compose hierarchy sau bước cấp quyền microphone; cấp quyền trước rồi chạy lại. Lần sau chạy tới ngắt lời nhưng assertion phụ đề dùng equality thay vì substring; đã sửa test sang `substring=true`.

## Chạy lại

Từ root, cần worker PhoWhisper và gateway LLM đang ready:

```bash
python3 services/phowhisper/local.py status
python3 services/llm-gateway/local.py start
```

Từ `server-java`, JDK 21:

```bash
KEN_VOICE_LIVE=1 mvn -o -s scripts/robotai-maven-settings.xml -pl xiaozhi-ai -am -Dtest=LiveVoicePipelineTest -Dsurefire.failIfNoSpecifiedTests=false -Drobotai.root="/absolute/path/RobotAI" test
```

Test dùng `.local/phowhisper-smoke.wav` canonical PCM WAV; cách tạo tại [PhoWhisper worker](../services/phowhisper/README.md). `voice.test.voice` có thể đổi giọng TTS cho test, không đổi cấu hình production. Test opt-in và dùng hạn mức Codex hiện có. Kết quả mỗi lần ghi `.local/full-voice-test/result.json`, audio `.local/full-voice-test/reply.mp3`.

## Kết quả instrumentation cuối

- `RobotSmokeTest`: **PASS**, 1 test, 11,803 giây trên OPPO CPH2651. Qua thu mic, gửi Opus, nhận/phát mẫu Hải Đăng, ngắt lời, ngủ/thức/reconnect và lượt tiếp theo phát hoàn tất. Chỉ xác nhận backend giả lập.
- `RealBackendCompatibilityTest`: **PASS**, 1 test, 2,059 giây; nội dung app ghi nhận: `Expected HTTP 101 response but was '401 '`. Đây là test tái hiện điểm chặn auth.
- Log local: `.local/full-voice-device-mock-final.log`, `.local/full-voice-device-real.log`, `.local/full-voice-java.log`; các log không commit.

Việc còn lại để nghiệm thu toàn luồng thật: tích hợp credential/OTA của app, adapter/giao thức Java tương thích app, tạo/gắn role thiết bị, tích hợp VieNeu nếu tiếp tục dùng giọng Hải Đăng, rồi chạy lại test thu mic và phát phản hồi inference thật. LLM latency lần đo này cần điều tra thêm.

Cập nhật sau lần test: đã triển khai login/logout/restore, credential WebSocket theo user, profile/role ownership và chế độ Java v1 raw Opus 16 kHz của app. Các điểm “app chưa có auth/chỉ mock” phía trên mô tả phiên bản trước khi sửa. Test login fixture trên Emulator đã qua; chưa nghiệm thu toàn hội thoại thật với tài khoản/role của user. Xem [Android login](ANDROID_LOGIN_VI.md).
