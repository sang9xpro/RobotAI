# Build và kiểm thử hiển thị tên người quen — 07/10/2026

Build APK ứng dụng và APK instrumentation thành công bằng JDK 17. JDK 21 mặc định gặp lỗi Android JdkImageTransform; chạy lại với JDK 17 theo README của dự án đã thành công.

## Kết quả

- App unit test: 2/2 qua.
- Domain test: 21/21 qua, đã chạy lại với `--rerun-tasks`.
- Instrumentation trên `emulator-5556`: 6/6 qua trong 20,009 giây.

Các bài instrumentation đã chạy:

1. `RecognizedPeopleStatusTest#namesFollowRecognitionRenameAndRemoval`: không hiện tên khi chưa nhận diện hoặc gặp người lạ; hiện tên tiếng Việt của nhiều người, loại bỏ tên trùng, cập nhật khi sửa/xóa hồ sơ, ẩn khi không còn khuôn mặt.
2. `IdentityPlatformTest#registerEditReloadAndDeleteVietnameseNameThroughUi`: đăng ký, sửa, nạp lại và xóa hồ sơ qua UI.
3. `IdentityPlatformTest#encryptedProfilesAreAccountScopedAndSurviveReload`: hồ sơ mã hóa, tách theo tài khoản, tồn tại sau nạp lại.
4. `IdentityPlatformTest#enrolledVoiceReturnsSavedNameAndClearsIdentityOnSilentTurn`: nhận diện giọng từ mẫu audio kiểm thử, trả tên đã lưu và xóa kết quả khi lượt nói im lặng.
5. `IdentityPlatformTest#nativeFaceAndVoiceRuntimesCoexist`: runtime mặt và giọng chạy cùng nhau.
6. `IdentityPlatformTest#cameraRunsWithoutPreviewAndStopsOnSleep`: camera chạy khi không có preview, dừng khi ngủ, khởi động lại khi thức.

Ảnh dưới là màn hình thành phần hiển thị tên trong test Compose với dữ liệu nhận diện giả lập. Chưa chạy bài đăng ký và nhận diện khuôn mặt người thật qua camera điện thoại trong lần kiểm thử này.

![Tên người quen trong test giao diện](recognized-people-test-2026-10-07.png)

APK: `android-app/robot-app/app/build/outputs/apk/debug/app-debug.apk`.
