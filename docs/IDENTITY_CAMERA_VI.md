# Camera khi thức và nhận diện người đối thoại

CameraX ImageAnalysis nằm trên các tab. Camera mở khi robot thức và app foreground, có quyền camera; ngủ hoặc vào nền thì giải phóng camera và ảnh trong RAM. Chuyển tab chỉ thêm/bớt preview. Khi mất mạng, camera local tiếp tục hoạt động. Khi đăng xuất/đổi tài khoản, bộ nhận diện cũ được đóng và hồ sơ được tải theo tài khoản mới.

## Đăng ký

1. Đăng nhập, đánh thức robot, cấp quyền camera khi app yêu cầu.
2. Mở **Người quen**, nhập tên và cách xưng hô, nhấn **Thêm người**.
3. Nhấn **Đăng ký mặt**. Chỉ một người trong khung hình, đủ sáng, mặt đủ lớn; nhìn thẳng và nghiêng nhẹ. App thu 8 mẫu đạt kiểm tra góc đầu/độ sáng/kích thước, đối chiếu tính nhất quán giữa các mẫu.
4. Nhấn **Thêm mẫu giọng**, cấp quyền microphone. Trong mỗi lượt 8 giây, đọc một câu liền mạch có ít nhất 3 giây lời nói. Đăng ký ít nhất 3 lượt, chỉ một người nói. Có nút hủy, xóa mẫu giọng, sửa và xóa người.
5. Nói với robot như trước. Danh tính được chốt khi kết thúc thu, gửi kèm `listen.stop` cho từng lượt; backend đưa tên/cách xưng hô vào metadata trước khi gọi LLM.

Khuôn mặt được nhận diện bằng SFace; giọng bằng CAM++ và Silero VAD. Mẫu mặt/giọng mã hóa AES-GCM bằng Android Keystore trong noBackupFilesDir; ảnh camera và PCM phục vụ nhận diện không lưu vào kho hồ sơ. Không gửi vector sinh trắc hoặc ảnh theo mỗi lượt lên LLM. Tính năng hỏi về cảnh vẫn cần chụp và bật cho phép gửi riêng.

## Quy tắc danh tính

- Khuôn mặt cần đạt ngưỡng, cách biệt với người gần thứ hai và ổn định qua 3 khung hình.
- Người nói cần có ít nhất 3 mẫu giọng. Tất cả cửa sổ giọng đủ dài trong lượt phải nhận ra cùng người; cửa sổ không chắc chắn hoặc khác người khiến toàn lượt chưa xác định.
- Nếu người nhận ra qua giọng cũng xuất hiện trong camera ở lượt đó, nguồn là `face_voice`; nếu không, là `voice`. Khuôn mặt xuất hiện riêng chỉ xác định người đang hiện diện, không tự gán tiếng ngoài khung hình cho người đó.
- Người lạ, câu ngắn hoặc không đủ bằng chứng được gọi trung tính. Điểm cosine không phải xác suất. Ngưỡng mặc định mặt 0.50, giọng 0.65 và khoảng cách với người thứ hai chưa được hiệu chỉnh trên nhiều người thật.
- Backend nhận đây là tín hiệu do client báo, không dùng để xác thực hoặc cấp quyền. `turn_id` phải khớp lượt bắt đầu, dữ liệu được làm sạch và không thừa hưởng danh tính của lượt trước.

## Giới hạn cần nghiệm thu

Chưa có model active-speaker đọc khẩu hình, tách nguồn khi nói chồng nhau, hoặc xác thực liveness. CAM++ trong cửa sổ có thể bị trộn hai người; không bảo đảm phát hiện mọi trường hợp chồng giọng. App vẫn dùng nút/tên gọi robot để mở lượt; camera không tự mở microphone liên tục. Backend đang lưu lịch sử/ký ức theo tài khoản/thiết bị/role, chưa có kho ký ức riêng cho từng personId. Metadata giúp tránh gán nhầm phát ngôn trong hội thoại chung, không tạo quyền riêng tư mới.

Model/runtime hiện hỗ trợ ARM64, Android 9+. Camera khi khóa màn hình hoặc app nền chưa thuộc chế độ robot foreground này. Cần thử nhiều người/người lạ, ánh sáng và khoảng cách, đo pin/nhiệt ít nhất 60 phút trước khi kết luận hiệu năng sử dụng lâu dài.

## Build và kiểm thử

Chuẩn bị model: `bash android-app/scripts/setup-identity.sh`. Script kiểm SHA-256 và tái tạo ONNX Runtime Java đã đổi SONAME để dùng cùng Sherpa; không dùng pickFirst cho hai libonnxruntime khác phiên bản. Giấy phép nằm trong assets/identity và assets/speaker.

Build với JDK 17, từ android-app/robot-app:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :domain:test
```

Kiểm thử OPPO được chọn rõ theo serial:

```sh
python3 tools/acceptance/run_identity_phone.py --serial WCFE6XCI9PYLDAGM
```

Bài thử dùng hồ sơ tạm riêng, không đăng ký ghi đè người quen hiện có. Kiểm tra native face/voice, mã hóa/phạm vi tài khoản, camera không cần preview và ngủ/thức, cùng microphone hoạt động khi camera phân tích. Audio fixture chỉ chứng minh model chạy trên máy; bài PCM microphone chỉ chứng minh thu thật đồng thời, chưa chứng minh độ chính xác nhận diện người thật.

Bộ test được bổ sung luồng đăng ký/sửa/xóa tên tiếng Việt trên UI, đăng ký giọng bằng fixture và đăng ký 8 mẫu mặt thật rồi nhận lại tên sau khi tải lại hồ sơ. Khi chạy cần một người nhìn camera trước, đủ sáng và đủ gần. Kết quả 06/10/2026: **7/7 bài trên OPPO, 22/22 unit test đạt**; xem [báo cáo và giới hạn kiểm thử](IDENTITY_TEST_2026_10_06_VI.md).
