# Contract hội thoại P2 — bản thiết kế

Ngày: 03/10/2026. Chưa triển khai. Đi cùng [kiến trúc dự án](PROJECT_ARCHITECTURE_VI.md). Các trường `robotai`, generation, turn_id, revision và header RAI1 là extension đề xuất của RobotAI, không phải giao thức Xiaozhi chuẩn.

## 1. Transport và thương lượng

Endpoint giữ `/ws/xiaozhi/v1/`. Dùng WSS ngoài môi trường dev. Xác thực handshake bằng cơ chế device hiện có; Device-Id/Client-Id không tự thay thế token xác thực. Android dùng ID do backend cấp/đăng ký, không phụ thuộc quyền đọc MAC điện thoại.

Client gửi hello nền Xiaozhi với version 1 và extension:

```json
{
  "type": "hello",
  "version": 1,
  "transport": "websocket",
  "audio_params": {
    "format": "opus", "sample_rate": 16000,
    "channels": 1, "frame_duration": 60
  },
  "robotai": {
    "contract_version": 1,
    "audio_envelopes": ["rai1"],
    "stt_partial": true,
    "playback_progress": true
  }
}
```

Server chỉ chọn `rai1` sau khi đã xác minh hỗ trợ cả hai phía:

```json
{
  "type": "hello", "transport": "websocket", "session_id": "s1",
  "audio_params": {
    "format": "opus", "sample_rate": 24000,
    "channels": 1, "frame_duration": 60
  },
  "robotai": {
    "contract_version": 1, "audio_envelope": "rai1", "generation": 1,
    "stt_mode": "incremental", "tts_mode": "pcm_stream",
    "voice_id": "hai-dang", "max_utterance_ms": 30000
  }
}
```

Downlink 24 kHz ở ví dụ là cấu hình thương lượng, không khẳng định sample rate gốc model. Adapter resample/encode theo audio_params đã chọn. `stt_mode` cho phép `online`, `incremental` hoặc `final_only`; final_only không đạt phụ đề live F04. `tts_mode` mô tả worker pipeline; payload public vẫn là Opus.

Nếu thiếu `robotai`, chỉ dùng raw Opus v1 và JSON hiện có. Client mới cần báo chế độ tương thích và hạn chế, hoặc từ chối khi bật yêu cầu strict P2; không tự gửi header tùy biến cho server cũ.

## 2. Identity và event chung

- session_id: backend cấp, scope của phiên kết nối.
- generation: số nguyên uint32 do server cấp trong hello; thay đổi khi tạo kết nối mới. Session mới cũng phải có identity mới để tránh xung đột.
- turn_id: Android cấp, duy nhất và tăng trong session; biểu diễn JSON dạng chuỗi số thập phân uint64 để tránh mất precision ở JavaScript. Gắn cùng lượt user và response của lượt đó.
- seq: số thứ tự riêng mỗi chiều audio trong lượt, bắt đầu 0; không dùng seq làm thời điểm âm thanh.
- request_id: cho thao tác idempotent như abort, tool, xóa dữ liệu; deadline và timeout cụ thể đặt trong cấu hình.

JSON của extension sau hello luôn có session_id, generation và turn_id nếu thuộc một lượt. Actor không có quyền không được chọn session/turn của thiết bị khác. Một final/response chỉ được commit một lần cho một lượt.

## 3. Bắt đầu, endpoint và phụ đề

```json
{"type":"listen","state":"start","mode":"auto","session_id":"s1","generation":1,"turn_id":"1"}
```

Client bắt đầu gửi frame sau listen.start theo đúng thứ tự cùng socket. Khi auto endpoint, server ngừng nhận thêm PCM cho lượt đã đóng và gửi event `utterance_end`; Android dừng/đổi turn theo state. Nút finish gửi:

```json
{"type":"listen","state":"stop","session_id":"s1","generation":1,"turn_id":"1"}
```

Endpoint trùng do VAD và nút finish là idempotent. Gửi flush phần PCM cuối trước listen.stop; backend chỉ final sau khi input đã kết thúc. Lượt rỗng trả no_speech, không gọi LLM.

```json
{"type":"stt","state":"partial","session_id":"s1","generation":1,"turn_id":"1","revision":2,"text":"Đây là dung lượng lưu trữ"}
```

```json
{"type":"stt","state":"final","session_id":"s1","generation":1,"turn_id":"1","revision":3,"text":"Đây là dung lượng lưu trữ, không phải RAM."}
```

Partial thay thế toàn bộ phụ đề của cùng lượt, không nối chuỗi. Revision cũ bị bỏ; final khóa transcript, partial đến sau bị bỏ. Final đúng turn hiện hành mới được đưa vào LLM.

## 4. TTS và progress

Server gửi `tts.start`, rồi `tts.sentence_start` có sentence_id và text, sau đó audio. Các câu được gửi/phát tuần tự; câu sau không vượt câu trước.

```json
{"type":"tts","state":"start","session_id":"s1","generation":1,"turn_id":"1"}
```

```json
{"type":"tts","state":"sentence_start","session_id":"s1","generation":1,"turn_id":"1","sentence_id":0,"text":"Đúng rồi, đó là dung lượng lưu trữ."}
```

`tts.stop` nghĩa server đã gửi hết audio, không nghĩa loa đã phát hết. Client gửi playback started/progress/completed theo turn, audio_seq đã thực sự phát và played_ms. Progress dùng cho flow control, biểu cảm và lưu trạng thái message; không dựa vào số byte đã download để nói đã phát xong.

`llm` emotion/text tương thích nền Xiaozhi; metadata cảm xúc extension chỉ dùng enum allowlist. UI state nói lấy từ playback thực tế.

## 5. Binary envelope RAI1

Chỉ dùng sau hello chọn rai1. Một WebSocket binary message = header 24 byte + một Opus packet; không WAV, base64 hoặc nhiều header trong một packet. Hai chiều dùng chung cấu trúc, WebSocket connection/session và hướng truyền phân biệt upload/download.

| Offset | Byte | Nội dung |
|---:|---:|---|
| 0 | 4 | ASCII `RAI1` |
| 4 | 4 | generation, uint32 big-endian |
| 8 | 8 | turn_id, uint64 big-endian |
| 16 | 4 | audio_seq, uint32 big-endian |
| 20 | 4 | payload_length, uint32 big-endian |
| 24 | payload_length | Opus packet |

Kiểm magic/length/generation/turn/seq trước decode. Max payload và max WebSocket message được giới hạn riêng trong cấu hình; không tin payload_length của peer. packet với turn đã abort, session cũ hoặc generation sai bị loại ở cả Java và Android. Seq trùng bị loại; seq thiếu trên đường WSS cho thấy bug/drop application, phải báo discontinuity thay vì coi là network UDP mất gói.

Đây là format ứng dụng RobotAI được thương lượng, không đổi ý nghĩa `Protocol-Version: 1` cho client Xiaozhi legacy. JSON không bật extension thì codec chỉ parse raw Opus. Có test byte-order và contract fixture hai phía trước bật trên thiết bị thật.

## 6. Interrupt, lỗi và reconnect

```json
{"type":"abort","session_id":"s1","generation":1,"turn_id":"1","request_id":"a1","reason":"user_interrupt"}
```

Android đánh dấu lượt canceled, stop/flush AudioTrack và xóa decoder queue ngay. Backend khóa response cũ, hủy inference/subscription và loại output đến muộn trước serializer, rồi gửi `abort_ack` mang request_id. ACK muộn không được làm lượt mới chuyển về idle. Khi model không hỗ trợ cancel thật, kết quả vẫn bị loại theo request/turn guard.

Full duplex: Android có thể mở lượt mới với turn_id mới khi lượt response cũ bị abort; mỗi mic packet vẫn gắn đúng lượt upload. Nút stop TTS và nút finish mic là hai thao tác khác nhau.

Mã lỗi ban đầu: unauthorized, unsupported_contract, unsupported_audio, no_speech, server_busy, stt_failed, tts_failed, llm_failed, timeout, audio_backlog, device_unavailable. Error có retryable và phase; không log token/audio mặc định. Không retry tool chuyển động hoặc audio cũ tự động.

Reconnect tạo session/generation mới, reset codec/seq và queue. conversation_id lưu bền có thể được yêu cầu tiếp tục sau kiểm tra quyền; stream dở không được resume/replay. Chế độ raw v1 chỉ mở lượt mới sau barrier hủy và bảo đảm sender queue đã drain; nếu không chứng minh được thì đóng/mở socket để loại audio cũ.

## 7. Worker contract và control API

Voice worker là private service. WebSocket nội bộ theo một request stream: JSON open (task STT/TTS, request_id, format, model/voice), binary PCM của request, endpoint/cancel, partial/final hoặc PCM output và done/error. Request không multiplex trong cùng worker socket ở bản đầu; request_id guard vẫn bắt buộc. Audio PCM là S16LE, chunk phải chia hết kích thước sample, sample rate/channels khai báo ở open; deadline và capacity có từ đầu. Không tự dùng RAI1 Opus trên đường PCM nội bộ.

HTTPS control API cần các use case: đọc/sửa persona, tạo/reset conversation, đọc/xóa lịch sử, cập nhật consent, xem/xóa memory và reminder. Ánh xạ vào endpoint hiện có sau khi kiểm quyền/schema; không coi tên use case này là API đã có sẵn.

Tool robot dùng capability discovery và tool request/result qua bridge dựa trên kênh MCP hiện có khi phù hợp. Payload gồm action_id, tên allowlist, args hữu hạn và deadline. Result có accepted/completed/rejected/unavailable, không gán completed ngay khi gửi BLE. ESP32 không nhận JSON từ LLM hoặc token backend.

Nguồn nền giao thức: [Xiaozhi WebSocket](https://github.com/78/xiaozhi-esp32/blob/main/docs/websocket.md). Contract extension này cần được kiểm thử và triển khai đồng bộ hai phía trước khi bật.
