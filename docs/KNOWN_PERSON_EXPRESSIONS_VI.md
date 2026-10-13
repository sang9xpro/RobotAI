# Phản ứng biểu cảm người quen trên app robot

Bản triển khai ngày 11/10/2026 chạy trên Android, dùng SFace để xác định người quen và MobileFaceNet FER để đọc biểu cảm. Camera hoạt động khi Ken thức, app foreground và có quyền camera. Người trong hình không cần nói, đăng ký giọng hoặc có kết nối backend để Ken phản ứng trên màn hình.

## Cách dùng

1. Đăng ký khuôn mặt trong **Người quen** nếu chưa có.
2. Bật **Phản ứng biểu cảm người quen** (mặc định bật, lưu riêng theo tài khoản).
3. Đánh thức Ken. Để một người trong khung hình, đủ sáng, nhìn gần thẳng camera.
4. Khi Ken đã nhận diện, cười và giữ nét mặt khoảng một giây. Ken đổi mắt/miệng trên màn Ken, Hội thoại và mặt toàn màn hình trên đế.

| Biểu cảm được xác nhận | Ken phản ứng |
|---|---|
| Nụ cười | Mắt cong, miệng cười |
| Nét buồn rõ và duy trì đủ lâu | Mắt mềm, nghiêng nhẹ, thể hiện quan tâm |
| Ngạc nhiên | Mắt và nét mặt tò mò |
| Bình thường | Bình yên |
| Chưa rõ nhưng nhận đúng người quen | Chú ý nhẹ |

Trong tab **Thị giác** hoặc **Người quen**, dòng trạng thái biểu cảm cho biết Ken đang quan sát hay đã xác nhận nét mặt. Các nhãn mô tả biểu cảm nhìn thấy; app không khẳng định cảm xúc bên trong của người dùng. Hiện chưa có phản ứng riêng cho nhãn giận/sợ/ghê.

## Cách tính năng phối hợp với robot

- Hoạt động cả lúc `LISTENING` đang chờ yên lặng; không cần mở một lượt nói mới. Khi VAD ghi nhận lời nói gần đây, ưu tiên trạng thái nghe, rồi trở lại mood camera sau khoảng 1,5 giây yên lặng.
- Khi Ken đang nói hoặc xử lý câu, giữ mood hội thoại và miệng theo audio. Cử chỉ/chạm mới có ưu tiên cho hiệu ứng ngắn.
- Không phát thêm giọng, tự mở lượt, ghi cảm xúc vào ký ức hoặc gửi ảnh lên backend cho tính năng này.
- Không làm mới bộ đếm tự ngủ bằng biểu cảm thụ động; Ken vẫn tự ngủ theo chính sách hiện có. Khi ngủ hoặc app vào nền, camera dừng và phản ứng được xóa.
- Tạm dừng khi thu mẫu mặt/giọng hoặc dạy cử chỉ. Tắt biểu cảm không tắt nhận diện hay cử chỉ.

## Nhận diện và chống phản ứng nhầm

Chỉ đọc biểu cảm khi có đúng một mặt và một người hiện diện rõ, danh tính đã được SFace xác nhận qua bộ theo dõi. Cùng detector track, person track và vùng mặt phải khớp. Danh tính còn giữ qua cơ thể khi quay lưng không cho phép đọc biểu cảm mới.

Mặt phải đủ lớn (tối thiểu 120×120 px trong ảnh đang xử lý), không bị cắt khỏi ảnh, yaw ≤20°, pitch ≤15°, độ sáng crop trong ngưỡng. Khi mất mặt, nhiều người, đổi track/người, đổi cảnh, lỗi camera, dừng camera hoặc đổi tài khoản, phản ứng cũ bị xóa. Callback model kiểm phiên camera và phiên người trước khi ghi kết quả; ảnh/kết quả quá cũ bị bỏ.

MobileFaceNet chạy trên worker riêng, tối đa một crop đang xử lý, nhịp tối đa khoảng 5 Hz. Crop độc lập với bitmap camera; ảnh nguồn được giải phóng theo vòng đời analyzer. Tính năng dùng ONNX Runtime đã có trong app.

Kết quả qua smoothing theo thời gian, kiểm điểm/cách biệt lớp và thời gian giữ:

| Nhãn | Điểm bắt đầu | Thời gian giữ tối thiểu |
|---|---|---|
| Happy | 0,65 | 650 ms |
| Neutral | 0,60 | 650 ms |
| Sad | 0,75 | 1.600 ms |
| Surprised | 0,70 | 900 ms |

Khoảng cách với lớp thứ hai ≥0,20; ít nhất 3 quan sát (Sad ít nhất 5). Khi duy trì biểu cảm đã xác nhận, ngưỡng điểm giảm 0,08 để tránh chập chờn. Gián đoạn mẫu trên 750 ms khởi động lại xác nhận. Mood mất hiệu lực khi không có bằng chứng hỗ trợ mới trong 1,5 giây; mặt cũng phải còn được thấy gần đây. Điểm softmax không phải xác suất nhận đúng đã hiệu chỉnh.

## Chuẩn bị model và build

```bash
bash android-app/scripts/setup-expressions.sh
```

Script pin OpenCV Zoo revision `47534e27c9851bb1128ccc0102f1145e27f23f98`, kiểm SHA-256 `4f61307602fc089ce20488a31d4e4614e3c9753a7d6c41578c854858b183e1a9`. Model FP32 khoảng 4,8 MB, cũng được gọi từ `setup-identity.sh`. File model bị loại khỏi Git theo chính sách repository; script tái tạo asset khi clone dự án.

Metadata, thứ tự nhãn và giấy phép Apache 2.0 nằm trong [assets/identity/FACIAL_EXPRESSION_MODEL.md](../android-app/robot-app/app/src/main/assets/identity/FACIAL_EXPRESSION_MODEL.md) và `FACIAL_EXPRESSION_LICENSE`. Đầu vào RGB NCHW 112×112, chuẩn hóa về [-1,1]. Đầu ra `[1,7]` là logits; app dùng softmax ổn định một lần. Hàm căn 5 điểm được dùng chung với SFace, nhưng SFace giữ đầu vào 0–255.

Từ `android-app/robot-app`, dùng JDK 17:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :domain:test
```

Kiểm tra điện thoại được chọn rõ theo serial, từ root:

```bash
python3 tools/acceptance/run_expressions_phone.py --serial DEVICE_SERIAL --with-regressions
```

Script cập nhật APK, giữ dữ liệu app; bài nhận diện dùng hồ sơ tạm riêng. Thêm `--skip-install` nếu APK hiện tại đã cài. Log/metrics/ảnh màn hình trong `.local/expression-test/DEVICE_SERIAL/`.

## Kiểm chứng và giới hạn

Build APK app và instrumentation thành công. **104 domain + 6 app unit test đạt**, gồm 17 test mới về thời gian xác nhận, đổi người/track, callback cũ, dữ liệu không hợp lệ, TTL, nhãn không hỗ trợ, ưu tiên hiển thị và giữ mặt cười khi chờ nghe.

Lượt kiểm tra trên OPPO CPH2651, serial `WCFE6XCI9PYLDAGM`: **15/15 instrumentation test đạt** trong khoảng 53 giây. Năm bài mới kiểm:

- Model MobileFaceNet thật + detector ML Kit + crop nhận nụ cười trong ảnh mẫu, bảo toàn ảnh nguồn và đóng tài nguyên.
- Hồ sơ chỉ có mẫu mặt, không có mẫu giọng/micro/backend, vẫn tạo được mood cười qua pipeline IdentityController; mất mặt chỉ còn cơ thể, nhiều mặt và dừng camera xóa phản ứng; phiên camera cũ không phục hồi phản ứng.
- Mặt chưa đăng ký không có phản ứng biểu cảm.
- Công tắc UI lưu riêng theo tài khoản, không tắt cử chỉ/nhận diện.
- Kết quả model thật đi qua bộ lọc/mood vào host dùng chung, hiển thị mặt cười trên Ken/đế trong `LISTENING` yên lặng; mặt con nhận tham số CALM vẫn dùng pose từ host đúng.

Mười bài hồi quy về animation nói/ngủ/cử chỉ, model bàn tay, SFace, giữ người qua cơ thể và runtime mặt/giọng cùng chạy cũng đạt. Model đo 20 lần sau warm-up trên ảnh mẫu đạt p50 **6 ms**, p95 **13 ms** trong lượt đầu. Đây là thời gian crop-to-scores, chưa phải độ trễ toàn camera-to-screen, và không phải đo pin/nhiệt dài hạn.

Bản cuối sau khi hoàn thiện kiểm tra tuổi ảnh camera đã build lại, toàn bộ JVM test vẫn đạt và **5/5 bài biểu cảm qua trên máy ảo Android ARM64 `RobotAI_API36_1`**. OPPO đã được cài bản có tính năng và hoàn thành lượt 15 bài phía trên; lần cập nhật APK cuối bị dừng vì điện thoại khóa, nên chưa xác nhận bản APK cuối được cài lại trên OPPO. APK cuối nằm tại `android-app/robot-app/app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `0606d4e060d01fd9ab5f06b3baa381b481f6aeb27da575b95c69b4d65fd01ec0`.

Các ảnh mẫu và hồ sơ thử xác nhận đường chạy model/pipeline/màn hình. Chưa có đo precision/recall biểu cảm người thật, đặc biệt nét buồn/ngạc nhiên, ánh sáng khó và người Việt Nam. Các ngưỡng hiện tại là cấu hình khởi đầu; cần thử trực tiếp nhiều người trước khi kết luận độ chính xác hoặc hiệu năng chạy liên tục.
