# RobotAI

## Khởi chạy toàn bộ backend local

Chạy từ thư mục gốc dự án:

```bash
python3 backend.py
```

Lệnh bật các container MySQL/Redis đã cấu hình, LLM gateway, PhoWhisper,
xiaozhi-server (8091) và xiaozhi-dialogue (8092), theo thứ tự phụ thuộc.
Dịch vụ chạy nền, có thể đóng terminal và chạy lại lệnh mà không tạo bản sao.
Script chờ database, gateway và model STT sẵn sàng; với API quản trị kiểm tra
tiến trình và phản hồi HTTP (200 hoặc 401 do yêu cầu đăng nhập), với dialogue
kiểm tra tiến trình và cổng TCP. Đây là kiểm tra khởi chạy, chưa kiểm chứng
đăng nhập hay một phiên hội thoại thật.

Yêu cầu bộ local đã chuẩn bị: Docker đang chạy; container được chỉ định trong
`.local/backend.env` đã tồn tại; database có `.local/schema-owned`; hai JAR Java
đã build; PhoWhisper đã build và có model; Python của gateway có `aiohttp`
(runtime lưu trong `.local/llm-gateway-python`); Codex đã đăng nhập cho LLM.
Lệnh sử dụng cấu hình hiện có, không tự tải model, build hay ghi lại cấu hình provider.
Nếu một bước lỗi, script dừng với mã khác 0; các dịch vụ đã bật vẫn chạy.

```bash
python3 backend.py status
python3 backend.py stop
```

`stop` gửi yêu cầu dừng hai server Java và hai worker; giữ MySQL/Redis chạy vì
container có thể được dùng chung. Log của từng dịch vụ nằm trong `.local/`.

## Nghe nhạc cho robot

Công cụ `get_playlist` và `play_music` tìm tên bài và link audio trên backend,
rồi gọi MCP trên Android để phát nhạc trên nền mặt robot, kèm hiệu ứng và điều khiển.
Xem [cấu hình và sử dụng](docs/MUSIC_TOOLS_VI.md).
