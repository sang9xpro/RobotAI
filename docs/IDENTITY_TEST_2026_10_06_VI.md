# Kiểm thử đăng ký tên và nhận diện — 06/10/2026

## Kết quả

Build APK debug và APK instrumentation thành công với JDK 17. Chạy lại toàn bộ unit test bằng `--rerun-tasks`: **22/22 đạt**, không lỗi, không bỏ qua; trong đó 4 bài `IdentityTest` kiểm tra người lạ, điểm tương đồng mơ hồ, vector sai kích thước và quy tắc xác định người nói.

Trên điện thoại OPPO CPH2651, serial `WCFE6XCI9PYLDAGM`: **7/7 instrumentation test đạt**, thời gian chạy test **29,532 giây** (không gồm build/cài APK).

| Bài kiểm tra | Kết quả đã xác minh |
| --- | --- |
| Đăng ký tên trên UI | Tên trắng bị vô hiệu hóa; thêm tên tiếng Việt có dấu và cách xưng hô; bỏ khoảng trắng đầu/cuối; sửa, tải lại hồ sơ giữ nguyên ID và cách xưng hô; bắt đầu/hủy thu mặt; xác nhận xóa người |
| Đăng ký mặt thật | Camera trước thu đủ 8 vector cho một khuôn mặt; nhận lại ID và tên `Người thử camera`; tạo controller mới đọc hồ sơ và vẫn nhận đúng tên qua camera |
| Đăng ký giọng và nhận tên | Với audio fixture, 2 mẫu chưa đủ nên trả `unknown`; 3 mẫu được lưu và trả `recognized`, đúng ID, tên `Nguyễn Văn Sang`, cách xưng hô `anh Sang` và nguồn `voice` |
| Không giữ tên ở lượt sau | Lượt im lặng trả `unknown`, tên rỗng và giao diện `Chưa xác định`; xóa mẫu giọng khiến cùng audio không còn nhận ra người |
| Kho hồ sơ | Đọc lại được hồ sơ đã mã hóa; phạm vi tài khoản khác không đọc thấy người; tên không xuất hiện dạng rõ trong tệp mã hóa |
| Model native | SFace và CAM++/Silero chạy cùng trong tiến trình Android, đầu ra hợp lệ |
| Camera/micro | Camera chạy không cần preview, tắt và xóa ảnh khi ngủ, mở lại khi thức; micro thu PCM thật trong lúc camera phân tích |

Các bài bổ sung dùng scope hồ sơ test riêng và ghi kho rỗng khi kết thúc; không ghi đè hồ sơ người quen của tài khoản đang dùng. Tên trong bài camera là nhãn thử, không xác minh danh tính thực của người xuất hiện.

## Phạm vi và giới hạn

Bài mặt thật xác minh đăng ký và nhận lại một khuôn mặt trong điều kiện camera hiện tại. Chưa đo tỷ lệ nhận nhầm với nhiều người, người lạ, thay đổi ánh sáng/góc mặt hoặc chống giả mạo. Bài giọng sử dụng lại cùng audio fixture để đăng ký và nhận diện; chỉ chứng minh luồng model → lưu mẫu → đối chiếu → metadata tên, chưa chứng minh độ chính xác với câu nói mới hoặc giọng live. Microphone đồng thời camera là thu thật nhưng không dùng để đánh giá danh tính người nói. Chưa kiểm tra robot gọi tên qua câu trả lời LLM/backend trong lượt hội thoại thật.

## Chạy lại

Từ `android-app/robot-app`, đặt `JAVA_HOME` tới JDK 17 rồi chạy:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline
./gradlew :app:testDebugUnitTest :domain:test --offline --rerun-tasks
```

Từ root dự án:

```sh
python3 tools/acceptance/run_identity_phone.py --serial WCFE6XCI9PYLDAGM
```

Bộ test hiện bao gồm camera thật: cần điện thoại mở khóa, cấp quyền camera/micro và một người nhìn camera trước, đủ gần, đủ sáng trong lúc thu mặt. Thiếu điều kiện này có thể làm bài mặt hết thời gian chờ.

Log lần chạy: `.local/identity-test/oppo-instrumentation.log`; số đo micro: `.local/identity-test/oppo-microphone-result.json`. Mã kiểm thử: `android-app/robot-app/app/src/androidTest/java/com/robotai/robot/IdentityPlatformTest.kt`.
