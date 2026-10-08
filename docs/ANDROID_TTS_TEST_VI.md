# Android Voice Test: PhoWhisper + Hải Đăng

Đã tích hợp VieNeu-TTS-v3-Turbo FP32 ONNX vào app Android hiện tại, dùng preset
Hải Đăng, bộ phát âm sea-g2p native và audio PCM 48 kHz. Không cần backend hay
Internet cho STT/TTS. Bản test chạy trên ARM64, Android 8 trở lên.

Mở project `android-app/whisper-test` bằng Android Studio, chọn JDK 17 rồi build
APK debug. Các model, AAR và thư viện native đã chuẩn bị trong workspace hiện tại.
APK nằm tại `app/build/outputs/apk/debug/app-debug.apk` trong project đó.

## Cách thử

1. Cài APK cập nhật app RobotAI Voice Test.
2. Nhập câu tiếng Việt ở phần **TTS · Giọng Hải Đăng**, tối đa 200 ký tự.
3. Nhấn **Tạo và phát**. Lần đầu app sao chép model vào bộ nhớ riêng rồi nạp model.
4. Dùng **Dừng**, **Phát lại**, **Lưu WAV** để so sánh kết quả.
5. Gửi WAV cùng số đo nạp model, tạo audio, RTF và RAM nếu có vấn đề.

Giới hạn hiện tại: tạo hết audio rồi mới phát, chưa streaming; preset cố định;
chưa hỗ trợ điều khiển cảm xúc/clone giọng; chưa nối hội thoại backend.
RAM hiển thị là PSS của cả tiến trình, không phải RAM riêng TTS hay bộ nhớ tối đa.
Nạp đồng thời PhoWhisper và TTS cần nhiều RAM hơn chạy riêng từng model.

## STT

Chỉ đóng gói PhoWhisper-small đã chọn. Các model Whisper cũ được giữ lại trong
`android-app/.work/legacy-whisper-assets`, không xóa. Whisper chạy tiếng Việt,
không sinh timestamp. Bộ xác minh người nói quyết định có nhận lượt nói hay không;
STT nhận audio gốc thay vì ghép các đoạn VAD, theo kết quả đối chiếu WAV trước đó.

## Kiểm tra hiện tại

- Kotlin và APK debug build thành công.
- 7 unit test đạt.
- Đã xác minh native ARM64 có trong APK. Bản 1.2 tách runtime TTS 1.24.3
  (`libort_tts.so`) khỏi runtime Sherpa 1.28.2 (`libonnxruntime.so`).
- Đã thêm instrumented test đối chiếu phoneme/BPE với Python, hủy xử lý,
  tạo audio và chạy model lần hai.
- Bản 1.2 đã cài lên CPH2651, versionCode 3.
- Hai instrumented test đạt trên điện thoại (tổng 7.987 giây): đăng ký ba mẫu,
  xác minh giọng; nạp cả hai runtime và tạo TTS trong cùng tiến trình; đối chiếu
  phoneme/BPE với Python, kiểm tra hủy và tạo audio liên tiếp.
- Đây là kiểm tra luồng xử lý và liên kết thư viện, không phải đánh giá độ chính xác
  của bộ xác minh người nói. Ba mẫu trong test hồi quy dùng lại một WAV cố định.

## Lỗi crash đăng ký giọng ở bản 1.1

Log: `UnsatisfiedLinkError: cannot locate symbol OrtGetApiBase`, tại `Vad.<clinit>`
khi xử lý mẫu đăng ký. Sherpa JNI yêu cầu symbol version `VERS_1.28.2`, nhưng bản
1.1 dùng `pickFirst` đóng gói runtime TTS có `VERS_1.24.3`. Build/unit test không
phát hiện được lỗi liên kết native này.

Bản 1.2 đổi SONAME/DT_NEEDED và tên provider trong GNU version metadata của runtime
TTS, giữ riêng hai thư viện; không dùng `pickFirst` cho ONNX. Thêm xử lý `LinkageError`
ở lượt ghi âm để báo lỗi thay vì thoát app nếu việc nạp thư viện thất bại trong tương lai.

Xem `android-app/tts-test/README.md` và `sea-g2p-android.lock` để tái tạo native build.
