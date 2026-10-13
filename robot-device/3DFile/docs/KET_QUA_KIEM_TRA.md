# Kiểm tra MẦM R4 — mẫu xem dáng và in thử

Đơn vị mm. Báo cáo tương ứng bộ STEP/STL trong gói R4, không phải thử nghiệm robot thật.

## Kiểm tra đã hoàn tất

- 25/25 STEP chi tiết hợp lệ, một solid mỗi file, thể tích dương; đáy Z=0 và XY vừa bàn 220×220 mm.
- 25/25 STL cuối kín, một thành phần liên thông, đo DfAM không có kết quả thiếu.
- Kiểm tra toàn bộ cặp khối lắp (trừ mắt trang trí), không còn giao cứng >0,1 mm³. Bốn cặp bánh/xích TPU có tải trước hướng kính 0,3 mm: bánh sau 483,754 mm³/cặp; bánh trước 300,380 mm³/cặp. Đây là chủ ý cho TPU, cần thử độ căng và ma sát thật.
- 27 đường trục vít Ø3 mm đã thử trên STEP lưu, không có vật liệu chắn >0,01 mm³. Chưa kiểm tra toàn bộ đầu vít, ê-cu, dụng cụ và dây dẫn.
- Bảy tư thế cổ −15°, −10°, −5°, 0°, +5°, +10°, +15° không giao cứng >0,1 mm³. Kiểm tra rời rạc, không phải quét liên tục hoặc mô phỏng dây. Giới hạn khởi động vẫn ±10° đến khi thử robot thật.
- Đã xem ảnh CAD từng chi tiết, trước/sau cụm lắp, bố trí trong thân và gá từ tính khi tháo điện thoại. Ảnh là ảnh CAD, không phải ảnh robot đã chế tạo.

Nguồn dữ liệu: `geometry_checks.json`, `mating_checks.json`, `motion_checks.json`, `dfam/summary.json`; mã SHA256 các file xuất ở `artifact_sha256.json`.

## Kết quả DfAM cần lưu ý

Nguồn giới hạn FDM/FFF: `dfam_process_limits.md`, sao chép từ skill dfam-check. Thành có đỡ ≥1,2 mm; tự đứng ≥1,6 mm; góc tự đỡ ≥45°. Đây là mức mặc định vì chưa biết máy in/filament. Các trường đo: `wall_thickness.p05_mm`, `wall_thickness.min_mm`, `overhangs.down_facing_area_below_limit_pct` trong JSON từng STL. Tối đa 2.500 mẫu ray-cast, không chứng minh toàn bộ bề mặt đều đủ dày.

- Số chi tiết có p05 ≥1,6 mm: 24. Các giá trị min nhỏ ở giao tuyến/mép vẫn cần soi preview lớp in và thử mẫu, không suy diễn thành chứng nhận bền.
- **bearing_spacer: p05=min=1,299 mm**, đạt giới hạn có đỡ 1,2 nhưng không đạt thành tự đứng 1,6. File nhựa chỉ để thử fit. Bộ lắp khuyến nghị **hai ống đệm kim loại dài 17,6 mm, ID 3,2–3,4 mm, OD chỉ tỳ vòng trong ổ 623ZZ**. Nếu dùng FDM phải tăng thành ≥1,6 mm, mở lỗ tâm bánh tương ứng và làm hai đầu tỳ đúng vòng trong, sau đó đo lại.
- Lỗ pilot tự ren Ø1,8 mm trên gá vỏ cổ nhỏ hơn khuyến nghị lỗ FDM 2,0 mm: dùng mũi khoan Ø1,8 làm sạch pilot sau in, kiểm tra bằng mẫu trước khi bắt vít Ø2,2; không coi lỗ nhỏ in ra là đúng kích thước ngay.
- Cần support cục bộ. Hướng in đề xuất dưới đây giảm diện tích mặt cần đỡ, không phải tỷ lệ thời gian/khối lượng support.

| Chi tiết | Phương án so với STL gốc | Mặt cần đỡ trước → sau | Chiều cao trước → sau |
|---|---|---|---|
| neck_shroud | flip_180_x | 15.3% → 6.4% | 33.0 → 33.0 mm |
| drive_wheel | flip_180_x | 26.6% → 6.6% | 32.0 → 32.0 mm |
| fender | flip_180_x | 4.2% → 1.4% | 30.0 → 30.0 mm |
| head_stem | rot_minus_90_y | 15.6% → 2.9% | 28.5 → 23.0 mm |

`flip_180_x`: lật 180° quanh X; `rot_minus_90_y`: xoay −90° quanh Y. Sau xoay đặt lại đáy lên bàn. Với trụ chịu tải phải cân nhắc độ bền theo lớp, không chỉ giảm support.

## Chưa xác minh

Lực giữ/chống xoay của puck từ tính; kích thước N20 và board của người dùng; lực kéo, dòng kẹt, sức servo, nhiệt Qi/nguồn, chống lật, tuổi thọ và phản ứng mép bàn. Puck Ø56×7 được người dùng chọn làm kích thước dự kiến, chưa có sản phẩm cụ thể. Cần vòng từ/ốp phù hợp trên điện thoại. Vị trí sạc, camera và kích thước có ốp phải đo thật.

## Bảng đo từng STL

| Chi tiết | min (mm) | p05 (mm) | Mặt cần đỡ (%) |
|---|---:|---:|---:|
| base | 0.302 | 2.998 | 1.3 |
| battery_tray | 2.401 | 2.7 | 0.7 |
| bearing_spacer | 1.299 | 1.299 | 0.0 |
| cable_clip | 2.0 | 2.0 | 4.8 |
| calibration_coupon | 4.0 | 4.0 | 0.0 |
| drive_wheel | 2.0 | 5.923 | 26.6 |
| electronics_deck | 1.604 | 3.0 | 0.0 |
| fender | 0.849 | 2.995 | 4.2 |
| front_sensor_bezel | 1.6 | 1.6 | 15.8 |
| front_sensor_carrier | 2.4 | 2.4 | 0.0 |
| head_stem | 0.167 | 2.298 | 15.6 |
| idler_wheel | 2.0 | 11.1 | 7.9 |
| io_panel | 1.22 | 3.0 | 0.0 |
| io_panel_blank | 2.656 | 3.0 | 0.0 |
| lid | 2.0 | 2.42 | 9.7 |
| magnetic_head | 2.0 | 2.0 | 0.0 |
| motor_cap | 1.302 | 2.224 | 0.0 |
| motor_cradle | 1.302 | 1.831 | 0.0 |
| neck_mount | 0.133 | 2.5 | 0.9 |
| neck_shroud | 0.748 | 2.188 | 15.3 |
| sensor_carrier | 1.803 | 2.4 | 0.0 |
| servo_cap | 1.055 | 3.0 | 0.0 |
| tensioner | 1.002 | 3.0 | 0.0 |
| tower_side_panel | 1.792 | 1.793 | 0.7 |
| track | 2.396 | 2.4 | 2.9 |
