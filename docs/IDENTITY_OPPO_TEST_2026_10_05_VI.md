# Kiểm thử camera và nhận diện trên OPPO — 05/10/2026

Thiết bị: OPPO CPH2651, Android 16/API 36, ARM64, serial WCFE6XCI9PYLDAGM. APK debug mới đã cài bằng cập nhật, giữ dữ liệu app hiện có. Camera/micro dùng hộp thoại quyền Android vì OPPO chặn pm grant qua ADB. RobotAI đã được mở lại sau kiểm thử.

## Kết quả

| Bài kiểm thử trên điện thoại thật | Kết quả |
|---|---|
| SFace ONNX và CAM++/Silero VAD cùng nạp, suy luận không xung đột native | Qua |
| Microphone AudioRecord thu PCM thật trong khi CameraX đang phân tích | Qua |
| Camera chạy khi không hiển thị preview, thêm/bỏ preview, ngủ tắt và thức mở lại | Qua |
| Hồ sơ AES-GCM theo tài khoản, đọc lại sau tạo kho mới và xóa dữ liệu thử | Qua |

Instrumentation: **OK (4 tests)**, 8,829 giây. Log: `.local/identity-test/oppo-instrumentation.log`.

Microphone: 16.000 Hz mono, nhận 44.160 mẫu PCM trong khoảng thu 3 giây; peak PCM16 = 1.223. Camera vẫn báo `Camera bật · chưa thấy mặt` ở cuối bài đồng thời. Số liệu xác nhận thu thật và hai cảm biến cùng hoạt động, không xác nhận lời nói hoặc danh tính người nói.

Build Android thành công; 16 domain tests và 1 app unit test qua. Backend: 36 tests được chọn về speaker metadata, prompt, lịch sử và listen protocol qua, không dùng inference LLM thật.

## Lỗi đã tìm và sửa nhờ máy thật

Lần chạy đầu, native face/voice qua nhưng camera crash với `invalid/free'd bitmap` tại FaceEmbedding.extract. MPImage.close() giải phóng bitmap chung trong lúc SFace còn dùng. Bản sửa cấp bitmap riêng cho MediaPipe; bài camera và bộ 4 tests sau đó qua trên OPPO.

Ngoài ra đã chặn khung hình của phiên camera cũ cập nhật sau ngủ/đổi camera, chặn kết quả giọng cũ cập nhật sau hủy lượt, và giữ trạng thái ngủ khi ứng dụng vào nền.

## Phần chưa được chứng minh

Chưa đăng ký và kiểm tra độ chính xác danh tính của bạn hoặc nhiều người/người lạ, chưa đo chất lượng nhận diện giọng thật tiếng Việt, nói chồng nhau, pin/nhiệt 60 phút, hoặc chạy hội thoại thật với metadata người nói đến LLM. Audio mẫu đóng gói dùng trong bài native chỉ xác nhận model hoạt động trên thiết bị. Backend runtime đang chạy cần được triển khai bản build mới để sử dụng metadata; lần này chỉ xác minh mã backend bằng tests.

Hướng dẫn đăng ký và giới hạn: [IDENTITY_CAMERA_VI.md](IDENTITY_CAMERA_VI.md).

## Khởi động cho thử thủ công sau nghiệm thu

Theo yêu cầu của người dùng, backend mới đã được package và khởi động cùng PhoWhisper và LLM gateway. OPPO dùng ADB reverse 8091/8092, đăng nhập tài khoản thử local do người dùng chọn; phiên lưu qua Android Keystore. Màn hình RobotAI xác nhận `Đã kết nối backend`, `Camera bật · 1 mặt · Người nói: Chưa xác định`, có nút `Nói với Ken`. Đây là trạng thái sẵn sàng để người dùng đăng ký mẫu và thử trực tiếp, chưa phải kết quả xác minh độ chính xác người nói hoặc hội thoại metadata đến LLM.
