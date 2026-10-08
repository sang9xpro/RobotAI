# Nghiệm thu Android và backend — 05/10/2026

Phạm vi: app `android-app/robot-app`, Java `server-java` và các worker tại ranh giới tích hợp. Bản app: `0.2-companion-preview`, min Android 9. Chưa nghiệm thu cơ khí, motor hoặc firmware ESP32.

## Kết quả theo năm việc được yêu cầu

| Việc | Đã triển khai và xác minh | Phần chưa nghiệm thu |
|---|---|---|
| Hội thoại thật | DTO profile/role đúng quy ước; tài khoản thử riêng không phải admin; PhoWhisper, provider Codex và TTS `vi-VN-HoaiMyNeural`; queue audio giới hạn; ngắt lượt và tạo lại socket để loại audio cũ; timeout/retry TTS trước khi có audio | 30 lượt liên tiếp trên OPPO; p50/p95 của chuỗi đạt; mất Wi-Fi thật; ngắt lời bằng giọng nói |
| Đồng hành | Tab Ken, mắt/mood vui, tò mò, buồn ngủ, quan tâm; chạm, idle; biểu cảm theo từng câu và emotion backend | Mức đồng bộ theo câu/trạng thái, chưa lip-sync theo phoneme; chưa đo trải nghiệm lâu dài |
| Thị giác | Camera opt-in; ML Kit nhận diện mặt/vị trí/góc đầu và MediaPipe cử chỉ local; chụp trong bộ nhớ; gửi ảnh cần bật cho phép và nhấn gửi; Java VisionService và model thật nhận đúng ảnh đỏ tổng hợp | Độ chính xác mặt/cử chỉ trên người thật; góc đầu/vị trí mặt chưa phải eye tracking hay nhận dạng danh tính |
| Tiện ích và trí nhớ | Đồng hồ, nhắc việc lưu theo user, rearm sau boot/restart; thời tiết Open-Meteo thật qua backend HTTPS; ký ức summary xem/sửa/xóa theo user/role | Nhắc việc không exact alarm nên có thể trễ trong Doze; xóa summary không xóa lịch sử gốc, thông tin có thể được tổng hợp lại |
| Cổng robot | Android MCP `initialize`, `tools/list`, `tools/call`; transport/telemetry mô phỏng; consent, active turn, TTL, replay, speed/duration, pin, hazard và watchdog; STOP ưu tiên; backend cấp thời hạn cho đề xuất | Chưa có transport BLE/USB hoặc ESP32 thật; provider Codex được chọn hiện chỉ text/vision, không gọi robot tool; chưa nghiệm thu hành động LLM thật |

Không có đường điều khiển PWM/motor từ LLM. UI local và đề xuất MCP đều đi qua RobotLink/gate; mô phỏng dừng khi telemetry quá 750 ms, mất liên lạc, lệnh hết hạn hoặc lỗi kiểm tra. Gesture chỉ ảnh hưởng biểu cảm local.

## Bằng chứng đã có

- Build Java: 1.249 test, 0 failure/error, 6 skip. Đã sửa lỗi DTO và test kiến trúc trước đó.
- Android: build app/test APK thành công, 13 unit test qua.
- Gateway: 11 test qua; hỗ trợ một ảnh inline PNG/JPEG tối đa 2 MB, temporary image cleanup; hai request độc lập tối đa cùng lúc.
- API backend thật: login/profile, weather HTTPS, memory read/edit/delete, từ chối user/role khác, từ chối vision khi chưa consent, nhận đúng ảnh đỏ tổng hợp qua VisionService, logout thu hồi token đều qua.
- Emulator Android: 4 test login/logout/restore/chuyển user qua; 1 audio smoke với fixture qua, bao gồm abort và frame cũ. Đây không phải hội thoại STT/LLM/TTS thật.
- Lần nghiệm thu tính năng emulator đầu tiên qua toàn bài. Lần chạy lại sau khi sửa UI camera, các kiểm tra độc lập vẫn qua; toàn bài báo FAILED vì weather bị mạng chặn. Các kiểm tra độc lập ở lần chạy mới đã qua: touch mood; Java socket MCP initialize/tools/list thật; reminder reload/boot/account isolation; memory DB thật; foreign role rejection; camera emulated + model face/gesture local; chụp local không tự upload; vision model thật; MCP consent/replay/expiry/stop.
- Reminder qua force-stop rồi khởi động app ở tiến trình mới: phiên/role khôi phục và nhắc việc vẫn còn, kiểm tra phân tách tài khoản và xóa được lưu bền.

Ảnh emulator xác nhận camera đã nằm gọn trong khung, không che mặt robot hoặc các điều khiển. Đã sửa thêm trường hợp LLM kết thúc luồng không có nội dung: bổ sung lời báo lỗi thay vì để app chờ TTS vô hạn; hai test mới đã qua và dialogue server đã được khởi động bằng bản mới.

Báo cáo máy được lưu trong `.local/acceptance/` (ignored). Tài khoản nghiệm thu và token không được đưa vào tài liệu hoặc lệnh instrumentation.

## Hội thoại OPPO: chưa đạt chuỗi 30

OPPO CPH2651, Android 16/API 36, serial `WCFE6XCI9PYLDAGM`. Hai lượt loopback loa/micro đã phát được phản hồi thật, giữ tiền tố KENA rồi KENB. Abort phục hồi được một lượt; test endpoint TCP không tồn tại được phát hiện. Tuy nhiên phụ đề acoustic sai nhiều; sau hồi phục mạng có lượt chỉ nhận “1.” và test thất bại. Thử loa Mac trước đó chỉ nhận “.”. Không dùng những lần này làm bằng chứng đạt 30 lượt hoặc chất lượng STT tiếng Việt.

Bài nghiệm thu được sửa để chờ xác nhận loa Mac phát xong rồi mới dừng thu; trước đó delay truyền trạng thái ADB có thể cắt mất cuối mẫu. Cài lại APK kiểm thử cũng có lần quá 150 giây. OPPO sau đó hiện màn hình khóa, chưa hiển thị hộp thoại cài lại test. Bài voice mới yêu cầu phụ đề khớp ít nhất hai cụm của mẫu tiếng Việt, không chấp nhận chữ bất kỳ từ tiếng nền. Bản sửa đồng bộ speaker đã build, chưa chạy được trên OPPO đang khóa. Chưa có kết quả chuỗi 30 trên bản mới.

Độ trễ trong app và test là **thao tác kết thúc thu → AudioTrack trình bày mẫu PCM không im lặng đầu tiên**, ngưỡng biên độ 200. Đây chưa phải phép đo độc lập từ âm tiết cuối đến âm thanh đầu tiên bằng micro đo ngoài. Chưa công bố p50/p95 nghiệm thu khi chuỗi chưa đạt. Test “mất mạng” hiện dùng endpoint TCP không tồn tại, chưa tương đương cắt Wi-Fi thật.

## Cách chạy lại

Từ root dự án, sau khi backend/worker đã chạy:

```bash
python3 tools/acceptance/prepare_features.py
python3 tools/acceptance/run_phone.py --serial WCFE6XCI9PYLDAGM --test features
python3 tools/acceptance/run_phone.py --serial WCFE6XCI9PYLDAGM --test reminders --skip-install
python3 tools/acceptance/run_phone.py --serial WCFE6XCI9PYLDAGM --turns 30 --skip-install
```

Đặt OPPO cạnh loa tích hợp Mac cho bài acoustic. Khi chỉ thay test, dùng `--skip-app-install`. `--audio-source phone-speaker` là tự phát bằng loa điện thoại và thu qua mic, có thể bị xử lý chống vọng. Không chạy song song các test backend/phone dùng cùng tài khoản vì logout/role/socket có thể ảnh hưởng bài khác.

APK: `android-app/robot-app/app/build/outputs/apk/debug/app-debug.apk`.

## Giới hạn sử dụng

App chạy foreground; rời app ngắt audio/socket và wake. Hội thoại hiện bấm bắt đầu/kết thúc, không phải rảnh tay luôn nghe. PhoWhisper trả final theo đoạn, chưa partial subtitle thật. Camera được giải phóng khi rời tab thị giác. Weather dùng tọa độ nhập tay, mặc định TP.HCM; chưa lấy GPS. Wake local cần Android 12+ và dịch vụ/ngôn ngữ trên máy phù hợp. Chưa đo pin/nhiệt trên Android cũ, chưa đầy đủ mọi tính năng LOOI.

Lần chạy lại lúc 14:03 gặp HTTP 302 `authenticationrequired` từ mạng, chuyển hướng tới cổng xác thực nội bộ; Java từ chối chuyển hướng và test weather thất bại. Đây là khác biệt trạng thái môi trường so với lần weather API thật đã qua lúc 11:47. Không bỏ qua xác thực hoặc thay dữ liệu giả.

HTTPS weather giữ xác minh chứng chỉ. Môi trường local cần CA của mạng; CA được macOS xác minh rồi thêm vào bản truststore riêng của dự án. Không sửa truststore toàn máy hoặc bỏ xác minh TLS.
