# RobotAI Whisper Test

App thử STT tiếng Việt offline, dựa trên mẫu MIT chính thức của whisper.cpp.
Android tối thiểu 8.0 (API 26). Chưa tích hợp TTS, backend Java hoặc ESP32.

## Chuẩn bị

```bash
bash scripts/setup-whisper.sh
bash scripts/setup-speaker.sh
bash scripts/download-model.sh tiny
# Tùy chọn để so sánh:
bash scripts/download-model.sh base
# Model lớn hơn để thử tiếng Việt:
bash scripts/download-model.sh small
```

Chạy các lệnh từ thư mục `android-app`. Script tải model multilingual, không dùng `.en`.
Model không commit vào Git. Source vendor pin tại commit
`6e4ab854f67f743900934a703d5603419384c961` và được khôi phục bằng setup script.
Giữ license upstream; bản sao license đi kèm tại `whisper-test/UPSTREAM_LICENSE`.

Mở **android-app/whisper-test** trong Android Studio.
Cài SDK 34, NDK 25.2.9519653 và CMake khi IDE yêu cầu; dùng JDK 17 tương thích AGP 8.1.1.
Đặt đường SDK của máy trong `local.properties` (không commit).

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK sau khi build thành công: `whisper-test/app/build/outputs/apk/debug/app-debug.apk`.
Native code luôn tối ưu Release, kể cả APK debug.

## Dùng trên điện thoại

1. Cài APK, mở RobotAI Whisper Test.
2. Chọn tiny, base hoặc small để nạp model. Danh sách chỉ hiện model đã đóng gói khi build.
3. Ghi đủ 3 mẫu đăng ký giọng trong phòng yên tĩnh (mỗi mẫu 5–10 giây, dùng câu khác nhau).
4. Cấp quyền microphone, nhấn Ghi âm kiểm tra, nói câu tiếng Việt rồi Dừng và xử lý.
5. Mỗi lần tối đa 30 giây audio; nhấn Dừng để xử lý sau khi đạt giới hạn.
6. Tắt mạng và lặp lại. App không khai báo quyền INTERNET.
7. So sánh cùng nội dung với các model; sao chép kết quả và metrics trên màn hình.

Chỉ ghi âm khi app ở foreground; vào nền thì dừng và bỏ lượt nhận dạng đó.
File WAV gần nhất nằm trong cache riêng của app và bị ghi đè ở lần kế tiếp.
RTF = thời gian inference / độ dài audio. Nạp model đo riêng.
Chưa có đo peak RAM, nhiệt, pin hoặc tính WER tự động. Chưa có nhận dạng streaming.

## Đăng ký và kiểm tra người nói

- CAM++ qua Sherpa-ONNX 1.13.8 tạo embedding từ giọng nói; Silero VAD tách lời nói khỏi khoảng im lặng.
- Mẫu CAM++ `3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx` được huấn luyện trên VoxCeleb; chất lượng với giọng Việt cần kiểm tra thực tế.
- Ba mẫu được chuẩn hóa và lấy tâm trung bình. Mẫu giọng chỉ giữ trong RAM trong phiên app; đóng phiên phải đăng ký lại, có nút Xóa mẫu giọng.
- Khi bật lọc (mặc định), app yêu cầu đủ ba mẫu. Mỗi đoạn VAD phải có ít nhất 2 giây để xác minh; thử một câu liền mạch 3–5 giây, không dùng lệnh một từ ở bước này.
- Đoạn dài được chia thành cửa sổ khoảng 3 giây, phần đuôi ngắn được gộp. Tất cả cửa sổ phải đạt ngưỡng để chạy Whisper; có cửa sổ không đạt thì bỏ cả lượt, không hiển thị text cũ.
- Ngưỡng cosine mặc định 0.60 là ngưỡng thử, chưa hiệu chỉnh. Điểm không phải xác suất nhận đúng. Có slider 0.30–0.90 và công tắc tắt lọc để đối chiếu Whisper thuần.
- VAD chỉ phát hiện lời nói, không biết ai nói. Chưa có tách giọng chồng nhau, camera chọn người, wake word hoặc phát hiện ai đang nói với bot. Đây không phải xác thực danh tính bảo mật.
- WAV gần nhất vẫn ở cache riêng như trước; nút Xóa mẫu giọng xóa embedding trong RAM, không xóa WAV cache.

Thử tối thiểu 10 câu của người đã đăng ký và 10 câu của một người khác (mỗi người nói riêng), cùng khoảng cách và âm lượng. Ghi điểm cửa sổ, chấp nhận/từ chối, thời gian kiểm tra giọng và thời gian Whisper. Tăng ngưỡng nếu nhận nhầm người khác; giảm nếu bỏ nhầm người đăng ký. Nếu hai nhóm điểm chồng nhau, không có một ngưỡng duy nhất giải quyết tốt cả hai: cần cải thiện mẫu đăng ký/âm thanh hoặc đổi model. Thử nói chồng nhau riêng để đánh giá giới hạn, không coi lọc lượt là tách nguồn âm.

Thư viện/model tải sẵn và được kiểm tra SHA-256 bởi `scripts/setup-speaker.sh`; không commit binary. License nguồn Sherpa-ONNX và 3D-Speaker nằm trong `assets/speaker`. Nguồn tham khảo:

- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8
- https://k2-fsa.github.io/sherpa/onnx/speaker-identification/index.html
- https://github.com/modelscope/3D-Speaker

## Thử model small

Model `small` multilingual dùng cho nhận dạng tiếng Việt, không phải `small.en`.
Model `small` đã được tải sẵn vào `assets/models/ggml-small.bin`.
SHA-256: `1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b`.
Build lại APK và chọn **small** trong app.
Model lớn hơn làm tăng dung lượng APK và yêu cầu bộ nhớ; tốc độ/chất lượng
cần đo trên điện thoại thực tế. Model cũ vẫn có thể chọn để so sánh.

## Bộ thử ban đầu

- “Xin chào, hôm nay bạn có khỏe không?”
- “Quay trái một chút rồi dừng lại.”
- “Nhắc tôi uống nước lúc tám giờ ba mươi.”
- “Tôi muốn đặt tên cho bạn là Mít.”
- “Nhiệt độ phòng là hai mươi bảy phẩy năm độ.”

Ghi 20–30 câu với đáp án chuẩn, cả giọng nói thường và có tiếng ồn.
Ghi model, máy, Android, audio duration, inference ms, lỗi bỏ/thêm/thay từ.
Không kết luận chất lượng model dựa trên một lần nhận dạng.

## Trạng thái xác minh ngày 02/10/2026

Đã tải Whisper tiny/base/small, CAM++ và thư viện Android Sherpa-ONNX.
Build APK debug thành công với JDK 17 và Gradle 8.2 offline, file tạm trên SSD.
7 unit test chạy qua (5 kiểm tra toán lọc giọng, 1 WAV, 1 test mẫu upstream).
APK debug khoảng 875 MiB vì đóng gói cả ba model Whisper và các ABI native.
Chưa kiểm chứng độ chính xác nhận diện người nói hoặc tốc độ trên điện thoại thật.

Upstream: https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
