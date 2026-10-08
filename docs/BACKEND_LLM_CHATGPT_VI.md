# Service LLM dùng tài khoản ChatGPT cho Ken

Đã triển khai gateway riêng tại `services/llm-gateway`, provider Java `codex-chatgpt` và lựa chọn provider trong giao diện quản trị.

Luồng: **backend Java → HTTP SSE localhost → codex app-server → model**. Đăng nhập bằng tài khoản ChatGPT hiện có, không cần OpenAI API key. Token nội bộ tự sinh chỉ bảo vệ kết nối giữa Java và gateway. Hạn mức sử dụng thuộc Codex của tài khoản; không phải một API cho mọi model trên ChatGPT web.

Backend giữ các provider sẵn có như OpenAI-compatible, Ollama. Chọn provider bằng cấu hình LLM, chọn cấu hình cho robot bằng `role.modelId`. Gateway không tự chuyển sang provider có tính phí khi lỗi hoặc hết hạn mức.

## Cấu hình đã áp dụng trên máy này

- Gateway: `127.0.0.1:8787`, chỉ nhận kết nối local có token.
- MySQL: schema riêng `robotai_ken`, config ID **2**, provider `codex-chatgpt`, model `codex-default`, trạng thái bật, mặc định cho LLM chat.
- `codex-default` hiện chọn **gpt-6.1-sol** theo catalog của tài khoản. Có thể đổi thành tên model do gateway trả về.
- `enableThinking=false` → reasoning effort `low`; bật → `medium`.
- Backend quản trị `8091`, hội thoại `8092` đã được build lại và khởi động lại.
- Không lưu token đăng nhập ChatGPT vào MySQL, không sao chép cookie hay đọc auth cache. CLI chính thức quản lý đăng nhập/refresh.

## Kiểm tra

- 10 test gateway/JSON-RPC qua: SSE, xác thực, payload/model, hủy khi disconnect, timeout không báo thành công, 429 khi bận, từ chối công cụ, isolation khi MCP còn bật.
- 3 test Java qua, gồm adapter Spring AI gọi gateway thật và nhận streaming tiếng Việt.
- Đóng gói toàn bộ backend Maven thành công; đây không phải kết quả chạy toàn bộ test suite cũ.
- Build frontend quản trị và kiểm tra TypeScript thành công.
- Lượt thử sau khi hoàn thiện isolation: token đầu **6,27 giây**, hoàn tất **6,61 giây**.
- Lượt thử đầu tiên: token đầu **5,72 giây**, hoàn tất **6,18 giây**. Đây là một mẫu trên máy hiện tại, chưa phải p50/p95 hay thời gian toàn pipeline STT/LLM/TTS/Android.
- Service là prototype chạy local, mỗi lượt dùng process/thread ephemeral riêng. Thiết kế này có thêm chi phí khởi động. Cần đo và tối ưu thêm để đạt tốc độ hội thoại mong muốn.

## Sử dụng

Chạy từ thư mục gốc dự án:

```sh
python services/llm-gateway/local.py status
python services/llm-gateway/local.py models
python services/llm-gateway/local.py smoke
```

Nếu máy khởi động lại:

```sh
python services/llm-gateway/local.py start
python server-java/scripts/robotai-local.py start
```

Tạo lại cấu hình gateway trong database: `python services/llm-gateway/local.py configure-backend`, sau đó restart Java để xóa cache cấu hình. Lệnh này chỉ sửa config được gắn nhãn của gateway, không thay các provider khác hoặc default đã tồn tại.

Gateway chỉ hỗ trợ **text chat**. Provider Java bỏ tool callbacks và tool context trước khi gửi. Tính năng điều khiển robot qua function calling, ảnh, embeddings và JSON schema cần provider khác hoặc triển khai adapter sau. Backend vẫn giữ lịch sử hội thoại; gateway không tạo lịch sử Codex bền vững.

Chi tiết contract, lỗi, giới hạn, lệnh test, cấu hình CLI và isolation: [README gateway](../services/llm-gateway/README.md).

Nguồn: [OpenAI app-server](https://learn.chatgpt.com/docs/app-server), [OpenAI authentication](https://learn.chatgpt.com/docs/auth). Repository người dùng cung cấp [codex-with-chatgpt](https://github.com/XiaoDuoYa/codex-with-chatgpt) là cầu nối workspace MCP cho quy trình lập trình, nên không dùng làm runtime inference.
