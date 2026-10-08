# Chốt kiến trúc giọng nói: STT và TTS trên backend server

Ghi chú cập nhật 03/10/2026: người dùng đã xác nhận P0 hoàn thành và phê duyệt tính năng P2. Bản thiết kế triển khai hiện hành là [kiến trúc toàn dự án](PROJECT_ARCHITECTURE_VI.md) và [contract P2](P2_PROTOCOL_CONTRACT_VI.md). Các nhận xét dưới đây giữ bối cảnh lần chốt hướng ban đầu; không coi các mục chưa nghiệm thu lúc đó là trạng thái P0 hiện tại.

Lập ngày 02/10/2026, xác nhận vào plan P0 ngày 03/10/2026. Quyết định của người dùng: chuyển xử lý STT/TTS từ điện thoại Android lên backend. Phương án offline Android + PhoWhisper/Vulkan trước đây giữ làm bản thử nghiệm và số liệu đối chứng; không phải kiến trúc chính của robot nữa. Tài liệu này chốt hướng và kế hoạch, chưa chuyển app/backend sang kiến trúc mới.

## Phân công

- Android: thu microphone, xử lý âm thanh đầu vào/AEC khi có khả năng phù hợp, kích hoạt lượt nói, mã hóa Opus, gửi audio liên tục; nhận phụ đề, giải mã/phát TTS theo phần, hiển thị mặt robot và gửi lệnh đến ESP32.
- Backend Java hiện có: xác thực thiết bị, WebSocket và trạng thái hội thoại, giải mã/chuẩn hóa audio, VAD/endpoint, gọi STT, LLM, TTS, hủy lượt cũ, điều phối phụ đề/cảm xúc/lệnh robot. Xác minh giọng có thể chuyển lên đây; không coi xác minh người nói là tách giọng chồng nhau hay phát hiện người đang hướng lời nói tới bot.
- STT/TTS worker: inference trên server; nếu dùng PhoWhisper/VieNeu và stack Python/CUDA phù hợp, chạy dịch vụ worker riêng do Java gọi. Không bắt buộc chạy inference trực tiếp trong JVM. Giữ model nạp sẵn, giới hạn số phiên và hàng đợi.
- ESP32: động cơ N20, cảm biến, BLE/Wi-Fi với Android, watchdog và dừng an toàn khi mất kết nối. Vòng điều khiển motor không phụ thuộc phản hồi từ server.

## Giao thức và bài học từ Xiaozhi

Xiaozhi có WebSocket truyền JSON điều khiển và binary Opus hai chiều. Ví dụ uplink: mono 16 kHz, frame 60 ms. Đây là frame truyền tải, không phải yêu cầu model phải trả một từ mỗi 60 ms. WebSocket chạy trên TCP; khi triển khai qua Internet dùng WSS/TLS. Nhánh MQTT+UDP dùng MQTT cho điều khiển và UDP cho audio, không phải mọi bản Xiaozhi đều gửi audio bằng TCP.

Nguồn chính thức:
- https://github.com/78/xiaozhi-esp32/blob/main/docs/websocket.md
- https://github.com/78/xiaozhi-esp32/blob/main/docs/mqtt-udp.md
- https://github.com/joey-zhou/xiaozhi-esp32-server-java

Chọn WebSocket/WSS theo giao thức Xiaozhi cho phiên bản đầu. Tái sử dụng endpoint `/ws/xiaozhi/v1/` hiện có; uplink Opus mono 16 kHz, bắt đầu với frame 60 ms để giảm khác biệt giao thức. Downlink lấy sample rate/format qua hello, không mặc định bằng uplink; nếu VieNeu tạo 48 kHz cần thống nhất đầu ra hoặc resample trước khi encode Opus. Không gửi WAV đầy đủ sau khi người dùng dừng; không đóng gói audio dạng JSON/base64.

Luồng:
1. Kết nối/xác thực, hello và thống nhất audio_params.
2. listen start, Android gửi binary audio ngay khi có frame.
3. Server giải mã, VAD và đẩy PCM vào phiên STT trong lúc audio tiếp tục đến.
4. Provider streaming thực sự có thể phát partial text; endpoint tạo final text. UI cho phép partial thay đổi. Partial/final cần được quy định rõ, ưu tiên final mới kích hoạt phản hồi LLM.
5. LLM trả token dần; gom thành câu/cụm đủ nghĩa, gửi TTS trước khi toàn bộ câu trả lời hoàn tất.
6. TTS trả audio theo phần, server encode/gửi xuống; Android phát ngay với jitter buffer nhỏ có giới hạn.
7. Khi người dùng ngắt lời: AEC/VAD/STT hỗ trợ phân biệt giọng người với tiếng robot; abort phiên trả lời cũ, hủy LLM/TTS khi có thể và xóa buffer phát trên Android. Dùng turn_id/generation để bỏ dữ liệu đến muộn. Không chỉ dừng gửi audio ở server vì điện thoại vẫn có thể còn buffer.

Partial/final/turn_id là thiết kế cần thống nhất thêm cho app/backend này, không mặc định mọi trường đều có sẵn trong giao thức Xiaozhi chuẩn.

## Những gì backend hiện có đã làm được

Đã đọc source local, chưa chạy/benchmark server trong lần chốt này:
- `xiaozhi-dialogue/.../server/websocket/WebSocketConfig.java`: đăng ký WebSocket với xác thực.
- `xiaozhi-ai/.../stt/SttService.java`: Flux<byte[]> và callback partial tùy provider.
- `xiaozhi-dialogue/.../dialogue/DialogueService.java`: VAD/segment, đưa audio vào STT và xử lý final; callback partial hiện phục vụ ngắt lời, chưa tự động gửi mọi partial thành phụ đề Android.
- `SenseVoiceSttService`: gom frames đến khi stream kết thúc rồi gọi OfflineRecognizer; không có partial. Không dùng tên stream() để khẳng định đây là streaming ASR.
- `VoskSttService`: acceptWaveForm/getPartialResult theo audio đến; vẫn phải đánh giá model tiếng Việt và độ chính xác.
- `FunASRSttService`: có đường xử lý kết quả trung gian, nhưng khả năng tiếng Việt/online thực tế phụ thuộc model server bên ngoài.
- `TtsService.textToSpeech`: API trả Path; có khung phân câu trong SentenceHelper. Muốn VieNeu phát ngay từ PCM chunk cần adapter/đường audio stream riêng; không chỉ chuyển model lên server rồi tiếp tục đợi WAV xong.

## Chọn model và kế hoạch thực hiện

Không chốt rằng PhoWhisper-large là model streaming: đó vẫn là Whisper theo đoạn, kể cả chuyển lên GPU server. Giữ làm baseline chất lượng tiếng Việt. Không chọn SenseVoice/FunASR chỉ vì tên provider; phải xác minh model cụ thể hỗ trợ tiếng Việt và partial thực sự.

Giữ VieNeu-TTS-v3-Turbo giọng Hải Đăng làm ứng viên TTS, chuyển engine streaming lên worker server và đo lại trên phần cứng server. Mục tiêu giọng tự nhiên đã chọn không đổi.

Thực hiện theo thứ tự:
1. Bản Android client nhẹ: Opus/WebSocket, phụ đề, phát audio stream, reconnect và thống kê; tạm giữ bản offline để đối chiếu, bỏ yêu cầu nạp model STT/TTS local ở chế độ server.
2. Kết nối với Java hiện có, kiểm format/sample rate, listen/hello/abort, audio lên/xuống liên tục và hủy dữ liệu cũ.
3. Benchmark cùng WAV và nói trực tiếp: PhoWhisper GPU server so với provider streaming hỗ trợ tiếng Việt; đánh giá độ chính xác và độ trễ, rồi chốt STT. Phần cứng server/GPU hiện chưa được xác minh.
4. Thêm adapter VieNeu streaming và partial/final rõ ràng; kiểm LLM streaming → phân câu → TTS chunks → AudioTrack.
5. Kiểm AEC/ngắt lời, nhiều người nói, mạng yếu, mất mạng và tải đồng thời.

Số đo bắt buộc: thời gian ra partial đầu tiên, từ hết nói đến final STT (bao gồm thời gian chờ im lặng), từ hết nói đến audio phản hồi đầu đến điện thoại, RTF inference, độ dài hàng đợi, RAM/VRAM và lỗi nhận dạng tiếng Việt. Mục tiêu ban đầu để kiểm chứng: final sau hết nói khoảng 0,5–1 giây, audio phản hồi đầu khoảng 1–2 giây trong mạng tốt; đây là mục tiêu, chưa phải kết quả hay cam kết.

Mất mạng: robot vẫn điều khiển động cơ/cảm biến an toàn; hội thoại dùng server không hoạt động. Android báo trạng thái kết nối và reconnect; chỉ bật phương án STT/TTS local dự phòng nếu sau này người dùng yêu cầu.
