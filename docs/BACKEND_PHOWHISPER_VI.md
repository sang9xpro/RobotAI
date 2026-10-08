# Backend PhoWhisper

Ngày 04/10/2026: thêm provider `phowhisper` vào STT factory Java, worker whisper.cpp giữ model nạp sẵn, runner local, provider trong giao diện quản trị và kiểm readiness.

Hướng dẫn chạy, cấu hình, smoke test và giới hạn: [PhoWhisper worker](../services/phowhisper/README.md).

Bản này tích hợp theo đoạn, trả final text qua luồng STT hiện có. Chưa có partial/revision/window overlap hoặc WebSocket worker của P2, chưa nghiệm thu toàn bộ P2. VieNeu TTS và kiểm thử hội thoại Android là công việc riêng.

Xác minh local: worker model_ready=True; Java management 8091 và dialogue 8092 đều process/TCP hoạt động, log hai module xác nhận default STT PhoWhisper. Build toàn backend thành công; 15 test Java và 10 test role manager qua, frontend type-check qua. OpenAPI không xác thực trả 401; chưa coi là nghiệm thu API quản trị.
