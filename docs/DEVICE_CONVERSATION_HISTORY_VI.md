# Lịch sử trò chuyện theo thiết bị

Mở mục **Lịch sử thiết bị** tại `/device-history`. Trang quản lý thiết bị cũng có nút mở lịch sử của từng thiết bị.

Chọn thiết bị hoặc để tất cả, lọc theo ngày, nội dung, người gửi, nguồn hội thoại hay ID phiên, rồi bấm **Tìm kiếm**. **Đặt lại** xóa các bộ lọc; **Tải lại** giữ nguyên bộ lọc hiện tại. Danh sách phân trang tối đa 100 tin nhắn mỗi trang.

**Xem phiên** chọn đúng thiết bị và phiên của tin nhắn, xóa các bộ lọc có thể che mất tin nhắn rồi sắp xếp từ cũ đến mới. **Chi tiết** hiển thị đầy đủ nội dung, nhân vật, nguồn, thời gian, ID phiên, lời gọi công cụ và bản ghi âm nếu đã được lưu. Không tự phát âm thanh.

Người dùng thường chỉ truy vấn được tin nhắn mang `sys_message.userId` của mình trên thiết bị hiện đang thuộc mình. Khi robot đổi chủ, người mới không xem được tin nhắn của chủ cũ, người cũ không tiếp tục truy vấn lịch sử robot đó. Thiết bị hội thoại app/web của mỗi tài khoản cũng xuất hiện trong bộ chọn.

Quản trị viên được xem tin nhắn của tất cả chủ sở hữu và tất cả thiết bị, gồm lịch sử trước khi chuyển chủ. Trang ghi rõ người dùng của từng hội thoại, thay vì suy luận chủ sở hữu cũ từ chủ hiện tại. Tin nhắn còn lưu của thiết bị đã xóa vẫn có thể tìm trong chế độ tất cả hoặc qua ID thiết bị. Tin nhắn đã xóa mềm không xuất hiện.

Hai API đọc `/api/device-history` và `/api/device-history/devices` lấy phạm vi truy cập từ danh tính Sa-Token. Tham số `userId`, `scopeUserId` hay thay đổi URL không mở rộng quyền. Migration V94 thêm menu và quyền đọc cho các vai trò hiện có, cùng chỉ mục phân trang theo thiết bị/người dùng và thời gian. Các vai trò mới do quản trị viên tạo có thể được cấp quyền `system:device-history` và `system:device-history:api:list` qua trang phân quyền.

Màn hình phục vụ xem và kiểm tra lịch sử, không thay đổi dữ liệu hội thoại.
