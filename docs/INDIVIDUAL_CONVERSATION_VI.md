# Hội thoại riêng với Ken

Cập nhật 10/10/2026. Phạm vi: một người đối thoại tại mỗi thời điểm. Không có hội thoại nhóm, phân vai nhóm hoặc tách lời nói chồng nhau để trả lời từng người.

## Điều kiện nhận lượt

| Tình huống | Điều kiện |
|---|---|
| Người quen mở lời, không gọi tên | Mặt và giọng cùng một hồ sơ đã đăng ký; chỉ một người hiện diện; mặt hướng robot; TalkNet xác nhận người đó đang nói; quan sát camera không quá 500 ms; không có dấu hiệu giọng lẫn hoặc bất định. |
| Người lạ hoặc người ngoài khung hình | Có lời gọi trực tiếp, ví dụ “Ken ơi, hôm nay thế nào?”. Backend kiểm tra lại trên bản STT cuối. Chỉ nhắc đến Ken trong câu kể không mở lượt. |
| Tiếp tục hội thoại | Đúng giọng của người đang đối thoại, phiên chưa hết 180 giây kể từ lượt được nhận gần nhất; có bằng chứng thị giác hợp lệ, hoặc trong 30 giây sau khi loa phát xong. |
| Chạm micro | Người dùng chủ động mở lượt. Dùng khi thiết bị thiếu nhận dạng local hoặc bằng chứng thị giác chưa đủ. |
| Có dấu hiệu nhiều giọng | Lời gọi hoặc thao tác chủ động có thể dẫn đến một câu yêu cầu nói lại; không thực thi công cụ và không học ký ức từ lượt bất định. Lời nói nền được bỏ qua. |

“Nhận ra khuôn mặt”, “đang hướng mặt về robot” và “đang nói” là ba bằng chứng riêng. Theo dõi cơ thể hoặc khớp giọng không tự thay thế được việc xác nhận người đang nói. Mẫu nhận diện là gợi ý cho hội thoại, không phải xác thực để truy cập tài khoản khác.

## Luồng Android

Microphone thu PCM mono 16 kHz một lần, dùng Silero để nhận tiếng nói. Có 300 ms âm thanh trước khi bắt đầu nói; hết lượt sau khoảng 1,2 giây im lặng; riêng lời gọi chưa có yêu cầu sẽ đợi khoảng 4 giây. Giới hạn cả bản thu là 30 giây. Khoảng chờ không có tiếng nói được làm mới mỗi 15 giây.

Android 13 trở lên dùng `createOnDeviceSpeechRecognizer`, kiểm tra gói tiếng Việt và đưa chính PCM này vào recognizer qua file descriptor. Không tạo recognizer cloud. Máy không hỗ trợ dùng nút micro. Lời gọi và câu hỏi liên tục được giữ trong cùng bản thu, kể cả khi Ken đang ngủ.

Lượt tự động được đệm trong RAM; sau khi được bộ quy tắc local nhận mới gửi lên backend. Trong phiên hiện tại, backend STT nhận Opus 16 kHz và trả bản cuối; client không giả lập phụ đề từng phần. Lượt do nút micro mở có thể gửi âm thanh ngay. Backend cũ không có extension hội thoại riêng chỉ được dùng bằng nút micro.

Giọng khách đủ ít nhất 3 giây lời nói sạch, nhất quán mới tạo được một mẫu tạm. Mẫu chỉ nằm trong RAM, giữ một khách tại một thời điểm, không lưu thành hồ sơ người quen. Mẫu hết hạn sau 180 giây không có lượt hợp lệ và được xóa khi ngắt phiên hoặc đổi tài khoản. Mỗi lượt tiếp theo vẫn phải khớp giọng; câu quá ngắn có thể cần gọi lại tên. Khi TalkNet đã được kiểm chứng, một mặt khách đang nói có thể gắn với mẫu tạm theo tracking của phiên, không đăng ký khuôn mặt lâu dài.

## Backend và ngữ cảnh

Client thương lượng `robotai.contract_version=1`, `conversation_context_v1=true`, `audio_envelopes=["rai1"]`. Backend trả generation riêng, `mock=false`, `stt_mode=final_only`, Opus mono 16 kHz/60 ms. Gói RAI1 có header 24 byte và kiểm tra generation, turn, sequence; không thử giải mã thành raw Opus khi gói sai. Client cũ vẫn dùng giao thức cũ.

`listen.start`, `listen.stop`, `abort`, `playback` phải đúng transport session, generation và turn. `listen.stop` mang `speaker` và `listening_context`: UUID ngữ cảnh, revision, lý do mở lượt, tên robot, bằng chứng camera, bất định. STT cuối phải qua bộ quy tắc backend trước khi gọi LLM. Khi đổi người, server tạo UUID hội thoại mới và không nạp lịch sử chung của thiết bị. Một turn giữ tham chiếu tới chính hội thoại của nó để callback đến muộn không ghi vào người mới.

Prompt nhận tên/cách xưng hô đã làm sạch, thời gian, vị trí có sẵn và bằng chứng hiện tại. Chủ đề, câu hỏi đang chờ và hoạt động đã được xác nhận được lấy từ lịch sử riêng và kết quả công cụ của hội thoại này. Không suy đoán tuổi, giới tính, cảm xúc hoặc cảnh vật từ một kết quả nhận diện mặt. Khi tham chiếu không rõ, Ken hỏi lại ngắn gọn.

Ngắt lời dùng AEC của Android và cùng bản thu local. Chỉ lời gọi trực tiếp hoặc giọng hiện tại được xác minh mới dừng LLM/TTS, xóa hàng đợi loa và mở lượt mới. Kiểm tra giọng nhanh dùng ngưỡng chặt hơn, sau đó kiểm tra lại toàn câu trước khi trả lời. Gói và callback của turn cũ bị loại. Android báo sequence sau khi `AudioTrack` đã trình bày mẫu; backend chỉ giữ những câu đã được phát đủ, không coi việc gửi xuống là người dùng đã nghe xong. Chế độ AEC phía server không bật đồng thời với AEC local.

## Ký ức riêng

Migration `V89__individual_person_memory.sql` tạo hồ sơ và các ký ức theo account + device + role + person UUID. API quản lý cho app đăng nhập chỉ dùng thiết bị ảo của chính tài khoản, và kiểm tra role thuộc tài khoản. Người quen có phần “Ký ức” trong thẻ người đó: xem, sửa, xóa và bật/tắt.

Tự ghi nhớ hiện dùng bộ trích xuất bảo thủ cho các câu tự khai báo rõ bằng tiếng Việt: “Tôi thích …”, “Sở thích của mình là …”, “Mình thường …”, “Tôi muốn bạn trả lời …”. Bỏ qua câu giả định, trích dẫn, câu hỏi và từ khóa thông tin nhạy cảm. Không khai thác lại lịch sử cũ; khách không có ký ức dài hạn. Đây là phạm vi trích xuất hiện tại, không phải bộ hiểu mọi cách diễn đạt sở thích.

Các ký ức được đưa vào prompt của lượt hiện tại, không sao chép vào metadata hoặc tóm tắt lịch sử. Sửa/xóa/tắt tăng revision; kết quả học mang revision cũ bị từ chối. Phiên đang mở đổi revision ký ức sẽ dùng ngữ cảnh mới ở lượt kế tiếp để tránh dùng lại nội dung đã xóa từ lịch sử. Xóa người quen sẽ xóa và tắt ký ức của người đó ở mọi role trên backend trước khi xóa mẫu local; lịch sử hội thoại đã lưu không bị xóa theo thao tác này.

API dưới `/api/robot/people/{personId}/memory?roleId=...`:

- `GET`: lấy `enabled`, `revision`, `facts`.
- `PUT`: sửa `{ "key": "preference", "value": "trà xanh" }`.
- `DELETE /{key}`: xóa một ký ức.
- `PATCH`: `{ "enabled": false }`.
- `DELETE`: xóa toàn bộ và tắt ghi nhớ.
- `DELETE /api/robot/people/{personId}`: quên người này ở mọi role của tài khoản.

## Mô hình và kiểm chứng thiết bị

TalkNet đã được xuất ONNX và đối chiếu PyTorch/ONNX Runtime trên CPU. MFCC Kotlin có bộ dữ liệu đối chiếu `python_speech_features` và kiểm thử. Mô hình 60 MB được đặt tại `app/src/main/assets/speaker/talknet.onnx`, thuộc nhóm binary bị Git ignore, cùng quy tắc với CAM++ và Silero. Có license đi kèm. Nguồn, commit và SHA256 được pin trong `services/active-speaker/model-manifest.json`.

Tạo lại asset từ thư mục gốc repository:

```sh
python3 -m venv .local/talknet-export
.local/talknet-export/bin/python -m pip install -r services/active-speaker/requirements.txt
.local/talknet-export/bin/python services/active-speaker/prepare.py
```

Nguồn chính: [TalkNet-ASD](https://github.com/TaoRuijie/TalkNet-ASD), [Android external audio source](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE).

Tự mở lượt bằng thị giác và ngắt lời bằng giọng chỉ bật trên thiết bị có báo cáo `files/conversation-readiness.json` đúng Android build fingerprint, app versionCode và SHA256 model tương ứng. Hai capability `activeSpeaker`, `bargeIn` có kết quả đo riêng. Mỗi capability cần ít nhất 100 câu có nhãn, 50 lần thử ngắt lời, chạy 60 phút, precision ≥ 98%, recall ≥ 90%, p95 âm thanh đầu ≤ 5 giây, p95 ngắt lời ≤ 1 giây. Chưa có số đo này cho điện thoại thật trong lần triển khai này.

APK debug có phiên kiểm chuẩn tương tác để thu số đo trước khi qualification. Chỉ bật tạm trong instrumentation và tự tắt khi phiên kết thúc; không ghi báo cáo đạt tự động:

```sh
adb install -r android-app/robot-app/app/build/outputs/apk/debug/app-debug.apk
adb install -r android-app/robot-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.robotai.robot.IndividualConversationPlatformTest#interactiveAcceptanceSession \
  -e conversation_calibration true -e minutes 60 \
  com.robotai.robot.test/androidx.test.runner.AndroidJUnitRunner
```

Trong phiên này, đăng nhập và kết nối backend, thử người quen hướng robot/quay đi, người khác nói bên cạnh, người lạ gọi tên, giọng ngoài hình, chỉ nhắc tên trong câu kể, nói ngập ngừng, loa phát lớn, ngắt lời, mất mạng và đổi người. Ghi nhãn các lượt để tính precision/recall; log thời gian nằm trong `files/measurements`. Đối chiếu âm thanh/camera thực tế và mức nhiệt/pin trong 60 phút. Bài `exportedModelRunsOnAndroidRuntime` chỉ kiểm tra runtime/shape, không tính là nghiệm thu nhận diện.

## Kết quả kiểm tra mã

Backend đã chạy toàn bộ kiểm thử Maven, gồm kiểm tra kiến trúc, admission, giao thức thực hello/listen/abort với STT giả lập và cơ sở dữ liệu H2 cho khóa ký ức/revision. Android đã chạy kiểm thử đơn vị và build APK ứng dụng/APK instrumentation. Không có thiết bị trong `adb devices`; chưa chạy các bài nền tảng hoặc nghiệm thu độ chính xác/độ trễ trên máy thật.
