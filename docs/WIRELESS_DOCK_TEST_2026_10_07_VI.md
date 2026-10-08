# Chế độ đế sạc không dây — kiểm thử 07/10/2026

Đã build APK debug và cài cập nhật lên OPPO CPH2651, giữ dữ liệu ứng dụng.
Giao diện nền đen, mắt cyan/quầng sáng xanh, chớp mắt và miệng chuyển động
dùng hội thoại rảnh tay hiện có. Chỉ tự vào chế độ khi app đang mở, đã đăng nhập
và Android báo nguồn sạc không dây. Cần role và quyền micro để tự kết nối.

## Kết quả tự động

- Build `:app:assembleDebug :app:assembleDebugAndroidTest`: thành công, JDK 17.
- Unit tests: 2 app + 21 domain, không có lỗi.
- `WirelessDockFlowTest`: 4/4 qua trên AVD riêng `RobotAI_API36_1`.
  Kiểm tra USB/AC không kích hoạt, wireless kích hoạt và tự nghe, màn hình ngang,
  pin đầy vẫn giữ chế độ, ngủ/đánh thức, nhấc ra ngắt kết nối và khôi phục hướng màn hình,
  vào nền dừng kết nối, cập nhật nguồn sạc khi trở lại, thoát thủ công và đặt lại,
  tạo lại Activity, và không bỏ qua đăng nhập.
- `LoginFlowTest`: 4/4 qua. Kiểm tra lưu/khôi phục phiên, logout/đổi tài khoản,
  token hết hạn, mất mạng và sai mật khẩu.

Lỗi nhóm Compose khi trả về sớm từ lambda `Column` đã được sửa bằng nhánh
`if/else`. Test đăng nhập kiểm tra trạng thái kết nối thay vì đợi dòng chữ
“Đã kết nối backend”, vì hội thoại rảnh tay chuyển ngay sang trạng thái nghe.
Wake trì hoãn cũng kiểm tra thế hệ kết nối để không kết nối lại sau khi rời đế.

Log máy ảo: `.local/android-emulator/test-dock.log`,
`.local/android-emulator/test-login.log`.
Ảnh UI: `.local/android-emulator/dock-preview.png` (chụp từ Compose).

## Thử với phần cứng

Đã xác nhận OPPO CPH2651 khi đặt lên đế thật: `Wireless powered=true`,
`USB powered=false`, `PlugType=4`, `UpdatesStopped=false`, trạng thái đang sạc.
Không dùng `dumpsys battery set` trên điện thoại thật.
RobotAI tự chuyển sang mặt Ken ngang toàn màn hình; ảnh tại
`.local/android-emulator/oppo-dock-wireless.png`.

Mac kết nối vào hotspot của OPPO; ADB qua mạng giữ được kết nối sau khi tháo
cáp USB. Đã bật backend local và đăng nhập tài khoản thử `ken_accept_a` bằng
runner hiện có. Khi nhấc ra, `PlugType=0`, `Wireless powered=false`, app trở về
giao diện thường; người dùng xác nhận và ảnh tại
`.local/android-emulator/oppo-undocked.png`. AppOps `RECORD_AUDIO` có thời gian
kết thúc, không còn ghi `(running)`: micro hội thoại đã dừng. Camera nhận diện
foreground vẫn theo hành vi hiện có của màn Ken.

Đặt lại: Android báo `PlugType=4`, `Wireless powered=true`, `USB powered=false`;
mặt Ken tự hiện lại (ảnh `.local/android-emulator/oppo-redocked.png`). Người
dùng xác nhận “Mặt tự hiện lại và Ken đã trả lời” sau khi nói câu thử. Như vậy
luồng đặt lên → hội thoại → nhấc ra/dừng micro → đặt lại đã qua trên đế thật.
Đây là xác nhận chức năng một lượt bằng người dùng, không phải benchmark
độ trễ âm thanh hay thử nghiệm hội thoại dài.

Nếu miếng nhận Qi cắm USB chỉ báo nguồn USB, không thể phân biệt nó với cáp
USB bằng tín hiệu pin hiện tại. Cần xác nhận tín hiệu thực tế trước khi chọn
phương án nhận diện bổ sung. Các đế Qi native đều kích hoạt vì Android không
cung cấp danh tính đế từ broadcast pin.
