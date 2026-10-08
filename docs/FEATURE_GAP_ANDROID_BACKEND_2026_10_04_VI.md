# Đối chiếu mục tiêu RobotAI — Android và backend

Ngày đánh giá: 04/10/2026. Phạm vi: `android-app/robot-app` và `server-java`; các worker inference chỉ xét ở ranh giới tích hợp Java. Không nghiệm thu firmware, cơ khí, động cơ, cảm biến hoặc web quản trị.

## Kết luận

Đã có app Android và đã tái sử dụng backend Java/Xiaozhi thực sự. Hiện trạng phù hợp với bản nền hội thoại có giao diện robot; chưa đạt bộ tính năng robot đồng hành tương đương LOOI. Phần thiếu lớn nhất là thị giác trên Android, hành vi/cảm xúc tự chủ, tiện ích để bàn và tích hợp lệnh robot.

Lần đánh giá này đọc trực tiếp source, đối chiếu test report hiện có và nguồn LOOI chính thức; không chạy lại test, không chạy hội thoại live, không sửa source chức năng. “Có triển khai” không đồng nghĩa đã nghiệm thu trên điện thoại thật. Không tính phần trăm hoàn thành vì chưa có bộ tiêu chí và trọng số được chốt.

## Chuẩn đối chiếu

- [LOOI trên Google Play](https://play.google.com/store/apps/details?hl=en-US&id=com.TangibleFuture.looiRobot): hội thoại, đánh thức bằng giọng nói, nhận diện mặt/vật thể/cử chỉ, cảm xúc và tính cách phát triển qua tương tác. Trang được truy cập ghi cập nhật 21/09/2026.
- [Trang sản phẩm LOOI](https://looirobot.com/products/looi-robot): bổ sung trí nhớ, hành vi dựa vào cảm nhận/trạng thái, giấc mơ, tracking camera, game vận động, thời tiết, đồng hồ chờ và nhắc việc. Đây là các nhóm công bố, không phải chứng nhận thực nghiệm cho mọi phiên bản Android.

## Ma trận tính năng

| Yêu cầu / nhóm tính năng | Android hiện tại | Backend hiện tại | Đánh giá toàn luồng |
|---|---|---|---|
| App điện thoại riêng | Kotlin/Compose, màn hình robot, cấu hình kết nối | API phục vụ tài khoản và profile robot | Có triển khai |
| Tái sử dụng Java/Xiaozhi | WebSocket tương thích giao thức Xiaozhi | REST, dialogue WebSocket, phiên, STT/LLM/TTS, role, lịch sử | Đáp ứng hướng kiến trúc |
| Dùng Android cũ | minSdk 28 (Android 9); inference hội thoại chuyển lên server | Java gọi provider/worker | Một phần: chưa kiểm chứng hiệu năng, nhiệt/pin trên máy đích |
| Đăng nhập, logout, khôi phục phiên, chọn nhân vật | Auth UI, lưu phiên bằng Keystore, xử lý token, chọn role | Profile/select role theo user; kiểm tra ownership | Có triển khai và test thành phần/fixture; chưa nghiệm thu tài khoản thật toàn luồng |
| Nghe → hiểu → trả lời bằng giọng nói | Thu mic, Opus, WebSocket, phụ đề, playback | STT, LLM, TTS, provider PhoWhisper/Codex | Có các khối; còn thiếu nghiệm thu mic Android → Java → loa Android thật |
| Hội thoại tiếng Việt | Gửi audio lên server | PhoWhisper theo đoạn; chuỗi provider thật đã có báo cáo PASS | Một phần; chưa chốt chất lượng giọng Việt và độ trễ sản phẩm |
| Phụ đề trong lúc nói | Có xử lý partial/revision ở nhánh mock | PhoWhisper hiện không gọi partial callback | Chưa đạt trên đường PhoWhisper thật |
| Ngắt lời tự nhiên | Nút Ngắt; nhánh Java đóng socket sau abort | Có cancel/interrupt trong dialogue | Một phần; chưa có bằng chứng barge-in bằng giọng nói/AEC toàn luồng |
| Ngủ, gọi tên đánh thức | Mắt ngủ, giảm sáng, on-device SpeechRecognizer | Không truyền audio ngủ lên backend từ app | Bản thử; cần Android 12+ và dịch vụ/gói tiếng Việt phù hợp |
| Mắt và biểu cảm | Chớp mắt, đổi màu theo phase, hiệu ứng khi nói/nghĩ/ngủ | Có nền persona hội thoại | Một phần; UI thể hiện trạng thái, chưa phải hệ cảm xúc theo nội dung/ngữ cảnh |
| Tính cách và hành vi tự chủ | Chưa thấy behavior engine, phản ứng chạm/vắng người/môi trường | Role/prompt là nền cho tính cách lời nói | Chưa đạt hành vi đồng hành |
| Trí nhớ và cá nhân hóa | Chưa có UI xem/sửa/xóa ký ức | Lịch sử DB, tóm tắt, API đọc/xóa summary | Có nền; chưa đạt ký ức cá nhân có quản lý đầy đủ trong app |
| Nhận diện mặt/người/cử chỉ | Chưa có pipeline camera hoặc quyền CAMERA | Không thấy tích hợp tương ứng cho app | Chưa có trong app |
| Hiểu đồ vật/cảnh | Chưa chụp/gửi ảnh, chưa có lựa chọn quyền riêng tư tương ứng | VisionService gọi model đa phương thức khi được cấu hình | Backend có nền; Android chưa nối |
| Tracking camera, chụp ảnh | Chưa thấy capture/tracking/gallery | Chưa có luồng gắn với app | Chưa có; chuyển động bám người cần nghiệm thu phần cứng ở scope sau |
| Thời tiết, đồng hồ chờ, nhắc việc | Chưa có màn hình/luồng thực thi | Có khung MCP/tool nhưng chưa thấy tool thời tiết/nhắc việc chuyên biệt trong source đã rà | Chưa có luồng sản phẩm; MCP bên ngoài nếu có cần kiểm tra cấu hình riêng |
| Game vận động, giấc mơ | Chưa thấy module/nội dung | Chưa thấy luồng tương ứng | Chưa có |
| Cầu nối lệnh robot và telemetry | UI ghi đế mô phỏng; chưa có BLE transport/quyền Bluetooth | Khung công cụ chung chưa thành luồng hành động dành cho app | Chưa có tích hợp; chưa đánh giá motor/cảm biến thật |
| Hoạt động khi mất mạng | Có UI/mắt và thao tác local; lỗi kết nối được xử lý | Hội thoại phụ thuộc kết nối | Chưa có hội thoại offline tích hợp trong robot-app |

## Những giới hạn ảnh hưởng nghiệm thu

1. **Hai đường giao thức có khả năng khác nhau.** `RobotViewModel.receive()` chỉ chấp nhận RAI1 khi hello đánh dấu mock. Java thật dùng nhánh legacy/raw Opus. `interrupt()` của nhánh này chủ động đóng kết nối để tránh audio cũ; người dùng phải kết nối lại. Không chuyển kết quả test mock thành kết luận production đã hỗ trợ turn ID/partial/ngắt lời liên tục.
2. **Chưa phải hội thoại rảnh tay liên tục.** Nhánh Java gửi listen mode manual; UI có Nghe/Kết thúc câu. Microphone giới hạn 500 frame × 60 ms = 30 giây/lượt. Hết phát trở về IDLE, chưa tự mở lượt nghe tiếp. Wake bằng giọng nói có thể mở lượt đầu sau hello, không giải quyết việc nghe liên tục.
3. **Android cũ có hai mức hỗ trợ.** App minSdk 28, nhưng wake local đòi SDK >=31. Máy Android 9–11 có thể đủ điều kiện cài nhưng không dùng được adapter gọi tên hiện tại; có nút đánh thức. Cần kiểm tra mic/codec, RAM, nhiệt, pin và wake false-positive trên máy cụ thể.
4. **Chỉ chạy foreground.** `MainActivity.onStop()` ngắt kết nối và dừng wake. Chế độ ngủ trong app không tương đương dịch vụ luôn nghe khi app ở nền hoặc màn hình bị khóa.
5. **Memory cần phân biệt lịch sử/tóm tắt với ký ức cá nhân.** Backend đã có lịch sử và summary thật. API xóa summary có triển khai, nhưng `DatabaseChatMemory.delete(ownerId, roleId)` vẫn ném UnsupportedOperationException; không thể kết luận đã có thao tác “quên toàn bộ tôi”.
6. **README upstream không phải danh sách tính năng đã nghiệm thu.** Không mặc nhiên cộng RAG, voiceprint hay memory graph được README quảng bá vào mức hoàn thành của app này nếu chưa tìm được triển khai và luồng sử dụng tương ứng.

## Bằng chứng kiểm thử đang có

Đối chiếu trực tiếp XML hiện có: Java 197 report file, 1.242 test, 1 fail, 0 error, 6 skip; Android unit 3 report file, 9 test, 0 fail/error/skip. Đây là kết quả lưu từ lần chạy trước, không phải test mới của lần đánh giá này.

[Báo cáo kiểm thử 04/10](FULL_FEATURE_TEST_2026_10_04_VI.md) ghi nhận Android build PASS, 4 login instrumentation test và 1 audio smoke test PASS trên emulator với fixture/mock. Java provider thật STT → LLM → TTS PASS trên một file WAV; tổng 9.010 ms, không phải độ trễ mic → loa. Mẫu đó có lỗi STT và dùng giọng TTS `zh-CN-XiaoyiNeural`, nên chưa chứng minh chất lượng TTS tiếng Việt.

Backend còn một test kiến trúc thất bại: `UserController.robotProfile/selectRobotRole` trả `RobotProfileService.Profile` thay vì DTO Resp theo quy ước. Đây là lỗi quality gate; test không chứng minh endpoint hỏng runtime. Báo cáo cũ còn ghi cấu hình local thiếu role và chưa có nghiệm thu tài khoản thật; lần này không đọc lại DB nên không khẳng định đó vẫn là trạng thái vận hành hiện tại.

## Thứ tự hoàn thiện đề xuất

1. **Khép kín hội thoại thật:** sửa lỗi DTO, nghiệm thu account/role/provider thật, chọn giọng Việt, đo p50/p95 từ kết thúc nói đến tiếng trả lời đầu tiên; kiểm mất mạng, đổi role, logout và abort. Mục tiêu đề xuất: ít nhất 30 lượt liên tiếp trên điện thoại đích không lẫn audio hoặc context; đây là tiêu chí đề xuất, chưa phải kết quả.
2. **Hoàn thiện cảm giác đồng hành:** màn hình mặt robot, trạng thái vui/tò mò/buồn ngủ, phản ứng chạm và nhàn rỗi, đồng bộ biểu cảm với câu nói. Có thể thử đầy đủ mà chưa cần motor.
3. **Thị giác:** phát hiện mặt và hướng nhìn local trước; sau đó cử chỉ, chụp ảnh và hỏi về cảnh qua VisionService. Chỉ gửi ảnh khi người dùng bật tính năng.
4. **Tiện ích và trí nhớ:** đồng hồ, nhắc việc lưu bền qua restart, thời tiết có dữ liệu thật; giao diện ký ức xem/sửa/xóa, phân tách theo người dùng.
5. **Tích hợp robot:** thêm cổng lệnh/telemetry Android, giả lập các tình huống mất liên lạc, lệnh hết hạn và dừng trước khi nghiệm thu với ESP32 thật. Hành động LLM phải qua bộ kiểm tra, không điều khiển motor trực tiếp.

## Ý kiến cho tính năng tương lai

- **Đồng hành tiếng Việt:** tên gọi, cách xưng hô, nhớ sở thích có thể chỉnh; kể chuyện và luyện hội thoại theo persona. Đây là hướng cá nhân hóa nên ưu tiên.
- **Chế độ máy yếu:** giới hạn FPS/độ phân giải camera, giảm tải khi nóng, đo pin/nhiệt trong app; giữ inference nặng ở server.
- **Trợ lý tập trung:** Pomodoro, nhắc nghỉ/uống nước, giờ yên lặng và lời chào theo lịch; dễ đem lại giá trị ngay trên điện thoại.
- **Ký ức minh bạch:** hiển thị robot nhớ gì, sửa thông tin sai, “quên điều này”, tách ký ức khỏi log hội thoại.
- **Offline cơ bản:** mắt, đồng hồ, nhắc việc và lệnh local vẫn chạy khi mất server; hội thoại offline là một hạng mục riêng nếu máy đủ tài nguyên.
- **Kịch bản hành vi tùy biến:** cho người dùng chọn phản ứng khi chạm, khi thấy mặt hoặc khi đến giờ; mở rộng tool nhà thông minh sau khi luồng nền ổn định.

## Vị trí source chính

- Android UI và foreground: `android-app/robot-app/app/src/main/java/com/robotai/robot/MainActivity.kt`.
- Phiên, audio control, mock/legacy, interrupt: `android-app/robot-app/app/src/main/java/com/robotai/robot/RobotViewModel.kt`.
- Thu âm và wake: `android-app/robot-app/app/src/main/java/com/robotai/robot/data/Microphone.kt`, `LocalWakeWord.kt`.
- Auth: `android-app/robot-app/app/src/main/java/com/robotai/robot/auth/`.
- Platform/dependency: `android-app/robot-app/app/build.gradle`, `app/src/main/AndroidManifest.xml`.
- Profile: `server-java/xiaozhi-server/src/main/java/com/xiaozhi/user/RobotProfileService.java`, `UserController.java`.
- Memory: `server-java/xiaozhi-ai/src/main/java/com/xiaozhi/ai/llm/memory/DatabaseChatMemory.java`, `server-java/xiaozhi-server/src/main/java/com/xiaozhi/memory/MemoryController.java`.
- Vision: `server-java/xiaozhi-ai/src/main/java/com/xiaozhi/ai/llm/service/VisionService.java`.
- STT: `server-java/xiaozhi-ai/src/main/java/com/xiaozhi/ai/stt/providers/PhoWhisperSttService.java`.
