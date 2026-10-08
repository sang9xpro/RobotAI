# Đăng nhập Android RobotAI

Ngày 04/10/2026. App `android-app/robot-app` có màn hình đăng nhập lần đầu, khôi phục phiên và đăng xuất. Dùng tài khoản backend hiện có; không tạo tài khoản hoặc reset mật khẩu của user trong quá trình triển khai.

## Cách dùng

Khi test qua ADB reverse, API là `http://127.0.0.1:8091`, WebSocket là `ws://127.0.0.1:8092/ws/xiaozhi/v1/`. Trên Emulator đã tạo các reverse này bằng lệnh `install` của [runner Emulator](../tools/android-emulator/README.md). Nhập username/email/tel và mật khẩu backend.

App gọi `/api/user/login`, dùng `Authorization: Bearer <token>` cho API và WebSocket, với `device-id: user_chat_<userId>` lấy từ response đăng nhập. Không truyền token trong URL, không lưu mật khẩu. Token/session được AES-GCM mã hóa bằng Android Keystore, `allowBackup=false`. HTTP/WS chỉ được cho phép trên bản debug; bản release yêu cầu HTTPS/WSS. API và WebSocket phải cùng host và mức bảo mật; HTTP client không theo redirect có thể chuyển credential sang host khác.

Mở lại app: `/api/user/check-token` xác thực trước khi mở session. Token hết hạn trả về login và xóa token local. Mất mạng giữ ciphertext để user thử khôi phục; không coi mất mạng là đăng nhập thành công. Logout dừng mic/player/socket, xóa session local rồi gọi `/api/user/logout`; nếu không thu hồi được ở server, UI thông báo rõ, phiên local vẫn bị xóa.

## User, cấu hình và context

- Backend identity ổn định `user_chat_<userId>`; conversation của Java lưu theo ownerId/userId/roleId, vì vậy logout không xóa lịch sử và đăng nhập lại cùng user/role dùng cùng history. Không lưu transcript/context trên Android.
- GET `/api/user/robot-profile`: trả role thuộc user đã đăng nhập và role đang chọn, không trả credential provider.
- PUT `/api/user/robot-profile/role` với `roleId`: kiểm role bật và cùng owner, tạo/bind virtual device ổn định, lưu lựa chọn trên server. Không nhận userId từ client. Đổi role ngắt socket để phiên tiếp theo nạp đúng cấu hình/context.
- Tên đánh thức và URL client nằm trong preferences tách theo API origin + userId. Đổi user xóa UI hội thoại/session hiện tại, không dùng preferences của user trước.
- Nếu user chưa có role, app báo tạo role trong trang quản trị rồi Làm mới; không tự sao chép cấu hình/API key của user khác. Database local ban đầu chưa có role; chưa tự tạo role cho tài khoản thật.

## Backend Java hiện tại

Client xác thực dùng hello và raw Opus của Java v1, 16 kHz mono/60 ms. Backend giả lập RAI1 24 kHz chỉ có đường mở debug test bằng Intent `robotai.mockTest=true`. Bản release không có bypass này.

Legacy v1 chưa có generation/turn marker: khi ngắt lời, app đóng socket và yêu cầu kết nối lại trước lượt mới để loại audio cũ. Không coi đây là triển khai đầy đủ P2 hoặc partial STT. Timeout phản hồi legacy tăng lên 120 giây để không bị cắt bởi LLM prototype mất khoảng 45 giây ở lần đo trước. TTS backend vẫn dùng provider theo role; VieNeu backend chưa có.

## Kiểm chứng

- Build app/backend thành công.
- 25 test Java qua: 10 handshake auth, 11 UserController, 4 ownership/profile (không cho user dùng role của user khác).
- Android Emulator ARM64/API 36.1 boot thành công. Test UI dùng fixture tài khoản riêng: login sai, login đúng, token Keystore, WebSocket có header, đóng/mở Activity với ViewModel mới để restore, logout/đổi user, token hết hạn và mất mạng đều qua (4 tests, 12,032 giây).
- Fixture không phải backend tài khoản thật; chưa xác nhận đăng nhập app bằng mật khẩu tài khoản thật hoặc context conversation thật giữa hai phiên. Cần user đăng nhập bằng tài khoản hiện có và role đã cấu hình để nghiệm thu phần đó.

Test runner: `python3 tools/auth-fixture/server.py` và `python3 tools/android-emulator/local.py test-login`. AndroidX Test cập nhật Runner 1.7.0/JUnit 1.3.0/Espresso 3.7.0 để tương thích InputManager Android 16.
