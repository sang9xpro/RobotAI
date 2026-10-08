# B1 — chuẩn bị backend local cho Ken

Ngày 03/10/2026. Phạm vi B1: tài nguyên máy, dịch vụ MySQL/Redis, cấu hình Java và kiểm tra khởi động. Chưa tích hợp STT/TTS worker hoặc chứng minh độ trễ hội thoại.

Trạng thái: **B1 hoàn thành về hạ tầng và khởi động Java**. Hai tiến trình đang chạy local sau kiểm tra. Maven package thành công với `-DskipTests`; lần này không chạy toàn bộ unit test backend.

Kết quả runtime:

- Management 8091 và dialogue 8092: process sống, TCP đáp ứng, log xác nhận Tomcat/application khởi động thành công.
- Management áp dụng 34 migration thành công; database có 16 bảng, phiên bản Flyway hiện tại 88.
- Connection pool datasource của dialogue khởi tạo thành công. Redis đã kiểm PONG; ứng dụng dùng DB 15.
- Không có bản ghi cấu hình LLM trong database mới. Chưa có provider/API key LLM sẵn dùng cho Ken.
- Probe OpenAPI nhận HTTP error, chưa đọc được schema qua request không xác thực; không coi kiểm cổng là nghiệm thu API quản trị hoặc đăng nhập. Xác thực/chức năng API kiểm riêng ở B2.

## Môi trường đã xác minh

| Thành phần | Cấu hình |
|---|---|
| Máy | Mac mini Apple M4, 10 lõi CPU, 16 GB RAM |
| GPU | Apple M4, 10 lõi GPU, Metal; không có GPU NVIDIA/CUDA |
| MySQL | Container `dev-mysql`, MySQL 8.0.45, host `127.0.0.1:3306` |
| Xác thực MySQL | Đã kiểm chứng user root bằng mật khẩu người dùng cung cấp; không ghi mật khẩu trong tài liệu |
| Database Ken | `robotai_ken`, tạo riêng; migration Java quản lý schema |
| Redis | Container `dev-redis`, Redis 7.0.15, host `127.0.0.1:6379`; PING → PONG |
| Redis auth | Không cần username/password với kết nối default hiện tại |
| Redis DB Ken | 15, đã xác minh trống trước khi dùng; không dùng chung DB 0 mặc định |
| Java | Corretto 21.0.6; source yêu cầu Java 21, Spring Boot 3.5.8 |
| Inference | Chưa có worker. Python base conda hiện không có PyTorch; chưa kiểm chứng MPS cho model |

Cấu hình bí mật nằm tại `.local/backend.env` ở root dự án, quyền 0600, Git ignore. Hai tiến trình Java dùng cùng datasource/Redis, JWT/device-auth secret và thư mục runtime. Không đưa mật khẩu vào command-line Java. Không dùng `source` file này vì đường dẫn chứa khoảng trắng; runner đọc trực tiếp key=value.

## Lệnh vận hành

Chạy từ root RobotAI:

```bash
python3 server-java/scripts/robotai-preflight.py
python3 server-java/scripts/robotai-local.py init-db
python3 server-java/scripts/robotai-local.py start --module xiaozhi-server
python3 server-java/scripts/robotai-local.py status
# Sau khi management đã khởi động và hoàn thành Flyway:
python3 server-java/scripts/robotai-local.py start --module xiaozhi-dialogue
python3 server-java/scripts/robotai-local.py database-status
python3 server-java/scripts/robotai-local.py stop
```

`init-db` chỉ tạo database riêng, không xóa database hoặc flush Redis. Nếu database có bảng nhưng không có dấu sở hữu của setup này, runner từ chối chạy migration khởi tạo. Start là bất đồng bộ: phải kiểm status/log readiness trước khi chạy dialogue. Log/PID/runtime nằm trong `.local`, log không được coi là tài liệu công khai.

Build từ thư mục `server-java`, với JAVA_HOME trỏ JDK 21:

```bash
mvn -s scripts/robotai-maven-settings.xml -DskipTests package -B -ntp
```

Settings riêng dùng Maven Central cho dependency tiêu chuẩn vì mirror Aliyun trong POM chậm khi thử; vẫn giữ repository file của project và repository DSP riêng. Không sửa settings Maven toàn máy.

Management dự kiến `http://127.0.0.1:8091`, dialogue `ws://127.0.0.1:8092/ws/xiaozhi/v1/`. Runner bind Java localhost cho môi trường phát triển. Android thật chưa truy cập trực tiếp được bằng IP LAN khi bind localhost; ở B2 cần tunnel/ADB reverse hoặc cấu hình bind/proxy thích hợp và auth.

## Giới hạn B1

- Kiểm DB/Redis và khởi động Java không tương đương STT → LLM → TTS đã chạy được.
- PhoWhisper/VieNeu cần worker runtime riêng và benchmark trên M4 hoặc host GPU sẽ chọn. Chưa đặt mức VRAM hoặc số phiên đảm bảo.
- Danh mục provider trong database không chứng minh có API key hợp lệ hay model đang chạy; cần kiểm tra cấu hình thực tế trước B6.
- Docker MySQL/Redis đang publish port ra host; bước này giữ cấu hình container hiện có, không đổi network hoặc xóa dữ liệu dịch vụ khác.


Cập nhật sau B1: provider ChatGPT qua Codex đã được triển khai và cấu hình local. Xem [service LLM](BACKEND_LLM_CHATGPT_VI.md).

Cập nhật 04/10/2026: đã tích hợp STT PhoWhisper-small theo đoạn, worker Metal local ready và default STT Java chuyển sang PhoWhisper. Chi tiết/giới hạn ở [Backend PhoWhisper](BACKEND_PHOWHISPER_VI.md). Các mục thiếu worker phía trên mô tả thời điểm B1 ban đầu.
