# RobotAI Android client

Client thu microphone/encode Opus, WebSocket và playback; STT/LLM/TTS chạy trên backend.

Bản `0.2-companion-preview` có các tab Ken, Hội thoại, Thị giác, Tiện ích, Ký ức và Đế robot:

- Mặt robot phản ứng khi chạm, nhàn rỗi, nghe và nói; biểu cảm theo từng câu và emotion backend.
- Camera tự bật khi robot thức và app foreground; không phụ thuộc tab. Hồ sơ người quen mã hóa trên máy, SFace nhận diện mặt, CAM++ xác định người nói theo lượt. Chụp/gửi ảnh qua VisionService vẫn cần cho phép và nhấn gửi riêng.
- Đồng hồ, nhắc việc lưu theo tài khoản và khôi phục sau restart; thời tiết thật qua backend/Open-Meteo.
- Xem/sửa/xóa bản tóm tắt ký ức theo user/role. Xóa tóm tắt không xóa lịch sử gốc.
- Cổng MCP `self.robot.action` và telemetry mô phỏng, kiểm quyền, tuổi lệnh, replay và watchdog trước khi chấp nhận hành động. Chưa có transport ESP32 thật.

App cần Android 9 trở lên và chạy foreground. Gọi tên bằng SpeechRecognizer local cần Android 12 trở lên và gói nhận dạng phù hợp. Nhắc việc không dùng exact alarm nên có thể trễ trong Doze. Hướng nhìn hiện là vị trí mặt/góc đầu, chưa phải eye tracking.

- [Camera và nhận diện người đối thoại](../../docs/IDENTITY_CAMERA_VI.md)
- [Tương tác 6 cử chỉ với người quen](../../docs/GESTURE_INTERACTION_VI.md)
- [Login/logout, user context và chọn role](../../docs/ANDROID_LOGIN_VI.md)
- [Android Emulator riêng trong dự án](../../tools/android-emulator/README.md)
- [PhoWhisper backend](../../docs/BACKEND_PHOWHISPER_VI.md)
- [Nghiệm thu bản đồng hành 05/10/2026](../../docs/COMPANION_ACCEPTANCE_2026_10_05_VI.md)

Build với JDK 17:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :domain:test
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Đăng nhập bằng tài khoản backend hiện có; API 8091, dialogue 8092. Khi chưa có role, tạo role ở trang quản trị và nhấn Làm mới trong app. Không dùng account fixture để kết nối backend thật.

Nghiệm thu thật dùng tài khoản thử riêng trong schema local của dự án. Từ root RobotAI:

```bash
python3 tools/acceptance/prepare_features.py
python3 tools/acceptance/run_phone.py --serial DEVICE_SERIAL --test features
python3 tools/acceptance/run_phone.py --serial DEVICE_SERIAL --test reminders --skip-install
python3 tools/acceptance/run_phone.py --serial DEVICE_SERIAL --turns 30 --skip-install
python3 tools/acceptance/run_phone.py --serial DEVICE_SERIAL --test handsfree --turns 2
```

Bài voice thu micro thật. Mặc định phát mẫu bằng loa Mac, đợi phát xong rồi mới kết thúc thu; đặt điện thoại gần loa. `--audio-source phone-speaker` là loopback loa/micro trên điện thoại, có thể bị xử lý chống vọng làm sai nhận dạng. Không tính test fixture/mock là nghiệm thu 30 lượt thật. p50/p95 đo từ thao tác kết thúc thu đến AudioTrack trình bày mẫu PCM không im lặng đầu tiên, chưa phải phép đo âm học độc lập.

### Chế độ đế sạc không dây

Khi app đang mở và đã đăng nhập, đặt điện thoại lên sạc không dây sẽ tự chuyển
sang mặt Ken ngang toàn màn hình: nền đen, mắt xanh cyan có quầng sáng xanh,
chớp mắt và miệng chuyển động khi trả lời. Sau khi chọn role và cấp quyền micro,
app tự kết nối WebSocket và dùng hội thoại rảnh tay hiện có. Camera nhận diện
vẫn theo quyền đã cấp và vòng đời foreground, không hiện preview trên mặt robot.

Chạm mặt để hiện điều khiển trong 6 giây; chạm lúc ngủ để đánh thức. Có các nút
Ngủ/Đánh thức, Ngắt lời, Kết nối lại khi mất kết nối và Thoát chế độ đế.
Back cũng thoát. Thoát thủ công giữ giao diện thường đến khi nhấc ra rồi đặt lại.
Nhấc khỏi sạc sẽ dừng hội thoại, ngắt kết nối và khôi phục hướng màn hình/thanh hệ thống.
Micro vẫn dừng khi app vào nền; app không tự mở từ nền khi đặt lên đế.

Nhận diện dùng `ACTION_BATTERY_CHANGED` + `BATTERY_PLUGGED_WIRELESS`, không dựa
vào trạng thái pin đang tăng: pin đầy vẫn ở chế độ đế. USB/AC không kích hoạt.
Android chỉ báo loại nguồn sạc, vì vậy mọi sạc không dây đều được coi là đế;
chưa phân biệt đế robot cụ thể bằng BLE/NFC. Điện thoại/receiver Qi cần được
Android báo là sạc không dây; receiver Qi cắm USB có thể chỉ được báo là USB.

Instrumentation `WirelessDockFlowTest` dùng fixture auth loopback 8891/8892
và `dumpsys battery` để kiểm tra chuyển màn hình, pin đầy, thoát, mở lại app và
đăng nhập. Đây là kiểm thử tín hiệu Android mô phỏng; vẫn cần thử với điện thoại
và đế Qi thật để xác nhận firmware báo nguồn sạc chính xác.

Sau khi build APK debug và khởi động máy ảo riêng, chạy fixture
`python3 tools/auth-fixture/server.py`, rồi
`python3 tools/android-emulator/local.py test-dock`.

### Hội thoại rảnh tay

Sau khi kết nối và cấp quyền micro, Ken tự nghe khi thức, tự gửi câu sau
1,2 giây yên lặng và nghe lượt tiếp theo sau khi phát hết phản hồi. Không phải
bấm Nghe/Kết thúc câu; nút kết thúc vẫn dùng được để kết thúc sớm. Khi phát
loa hoặc xử lý câu, micro hội thoại dừng (chưa hỗ trợ ngắt lời bằng giọng nói).
Tự ngủ sau 2 phút không có tiếng nói/tương tác; thời gian xử lý, phát phản hồi
và đăng ký giọng không tính vào thời gian chờ. Khi ngủ chỉ bộ nhận dạng tên
on-device nghe “Ken” / “Ken ơi”, không truyền audio lên backend. Cần Android
12+ có dịch vụ nhận dạng và gói tiếng Việt; giao diện báo nếu không khả dụng.
Nút đánh thức là đường dự phòng thủ công. App vẫn chỉ hoạt động foreground.

Tham khảo vòng Listening → Speaking → Listening trong Xiaozhi `application.cc`.
Android dùng endpoint năng lượng PCM cục bộ: xác nhận 180 ms, đệm đầu câu
300 ms, im lặng 1200 ms, giới hạn một câu khoảng 30 giây. Backend nhận mode
`manual` vì client tự gửi `listen.stop`; không phải chế độ bấm giữ trên UI.
Đây chưa phải VAD học máy: cần thử giọng nhỏ, quạt/TV và khoảng cách micro trên
thiết bị thật để hiệu chỉnh ngưỡng. Âm thanh chờ trước khi có tiếng nói không
được upload.

Test `handsfree` dùng micro thật và backend thật, đánh thức bằng thao tác tương
đương nút Đánh thức, không gọi `listen()` hoặc `finish()`. Kiểm tra audio gửi
trong lúc nghe, tự kết thúc, STT, phát trả lời và tự nghe tiếp. Bài này không
xác nhận đánh thức bằng câu gọi. Kết quả lần chạy 06/10/2026:
[báo cáo test rảnh tay](../../docs/HANDSFREE_TEST_2026_10_06_VI.md).
