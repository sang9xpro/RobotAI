# Quản lý người dùng và robot

Mở trang quản trị `/user` bằng tài khoản có vai trò quản trị viên.

- **Thêm người dùng**: nhập tên đăng nhập, mật khẩu ban đầu, thông tin liên hệ và vai trò phân quyền. Có thể chọn robot ngay khi tạo tài khoản.
- **Sửa**: cập nhật tên hiển thị, email, số điện thoại và vai trò. Không đổi vai trò của tài khoản đang đăng nhập hoặc quản trị viên hệ thống.
- **Thiết bị**: xem các thiết bị thuộc người dùng, tìm và chọn nhiều robot đã đăng ký, rồi bấm **Gán robot**. Mỗi lần gán tối đa 20 robot; có thể thực hiện nhiều lần.
- **Tắt/Bật**: vô hiệu hóa hoặc kích hoạt tài khoản. Tắt tài khoản thu hồi token đăng nhập, giữ lại dữ liệu.

Một người dùng có thể sở hữu nhiều robot. Mỗi robot chỉ có một chủ sở hữu tại một thời điểm. Bộ chọn dùng robot có ID MAC đã tồn tại trong `sys_device`; thiết bị chat ảo `user_chat_*` thuộc riêng từng tài khoản và không được chuyển.

Nếu robot đang thuộc người khác, giao diện yêu cầu xác nhận việc chuyển. Backend khóa các robot theo thứ tự ID và kiểm tra chủ sở hữu tại thời điểm lựa chọn. Nếu một robot đã đổi chủ hoặc không còn tồn tại, toàn bộ lô bị từ chối. Tạo tài khoản kèm gán robot cũng là một giao dịch: gán thất bại thì tài khoản và tài nguyên mới không được lưu.

Khi chuyển robot, backend gán nhân vật của người dùng mới và gửi yêu cầu đóng phiên cũ sau khi giao dịch đã commit. Backend hội thoại giữ danh tính của phiên cũ trong quá trình dọn dẹp, kể cả khi thông báo cập nhật và đóng phiên được xử lý đồng thời. Kết nối tiếp theo dùng nhân vật của người dùng mới. Tin nhắn và tóm tắt cũ được giữ riêng; trang tóm tắt lọc cả chủ sở hữu của nhân vật để người dùng mới không đọc ngữ cảnh của chủ cũ.

Mỗi tài khoản mới được khởi tạo nhân vật và thiết bị chat ảo. Nếu quản trị viên chưa tạo nhân vật mẫu, hệ thống tạo nhân vật RobotAI cơ bản, giọng Việt, thay vì lấy cấu hình của người dùng khác.

Migration `V93__admin_user_device_management.sql` thêm quyền tạo/sửa người dùng và xem/gán thiết bị, cấp cho vai trò `admin`. API quản trị ở `/api/admin/users` kiểm tra cả vai trò quản trị và quyền từng thao tác; các API tự phục vụ vẫn giữ kiểm tra chủ sở hữu.
