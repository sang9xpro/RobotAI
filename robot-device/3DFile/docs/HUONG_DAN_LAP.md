> Cập nhật R4: xem `THAY_DOI_R4.md` cho gá servo thấp, tháp thuôn và hai ốp trang trí. Các thông tin R3 dưới đây về thân/xích/gá từ tính vẫn áp dụng, trừ kích thước tháp và vị trí trục đã thay đổi.

# Hướng dẫn lắp MẦM R4

## In thử trước

1. In `calibration_coupon.stl`, một `motor_cradle`, một `motor_cap` và một `sensor_carrier`. Coupon có hàng dưới Ø3,0 / 3,1 / 3,2 / 3,4 / 4,0; hàng trên Ø4,2 / 4,4 / 10,0 / 10,2 (đọc từ trái sang phải khi nhìn từ trên, cạnh dài nằm ngang).
2. Đo và thử N20: thân 26 × 12 × 10, trục D Ø3 dài 9 mm. Nếu khác, sửa gá và bánh trước khi in phần lớn. N20 1000:1 hoặc có encoder có thể dài hơn; nguồn/driver cũng chưa chốt chỉ từ tên N20.
3. Thử `magnetic_head` với puck từ tính Ø56 × 7 mm và vòng từ/ốp trên Find X8. Kiểm tra sức giữ và chống xoay, camera, nút bấm và khoảng hở khi gật. Không còn khung kẹp cơ khí.
4. In một xích TPU và hai bánh, thử độ căng trên gá trước khi in cặp còn lại. Không thay TPU bằng PLA/PETG cho vòng xích kín này.

## Phụ kiện cơ khí cần mua thêm

Số lượng dưới đây là lượng chuẩn bị, đã có dư nhỏ cho lắp thử; chiều dài vít phải kiểm tra sau khi in và thêm đệm.

| Phụ kiện | Chuẩn bị | Vị trí |
|---|---:|---|
| Ổ bi 623ZZ, 3 × 10 × 4 mm | 4 | Hai ổ cho mỗi bánh dẫn hướng phía trước |
| Ống đệm kim loại dài 17,6 mm, ID 3,2–3,4 mm, OD chỉ tỳ vòng trong 623ZZ | 2 | Giữa hai ổ mỗi bánh; kiểm tra OD với ổ mua thực tế |
| Insert đồng M3, thân ngoài tương thích lỗ Ø4,2, dài khoảng 5 mm | 16 | 6 ở phía trong nắp R2, 4 khay điện tử, 3 nắp kẹp servo + dự phòng |
| Vít M3 × 30 + ê-cu M3 | 2 | Gá đĩa từ tính vào trụ R4 |
| Vít tự ren nhựa Ø2,2 × 12 | 2 | Vỏ che cổ; làm sạch lỗ pilot Ø1,8 sau in |
| Puck từ tính Ø56 × 7 dự kiến, vòng từ/ốp tương thích | 1 bộ | Gá điện thoại, xác nhận lực giữ thực tế |
| Vít M3 × 8 | 8 | Khay điện tử, gá nhẹ |
| Vít M3 × 10 | 14 | Cap servo, gá cảm biến trước, tấm I/O, tăng xích |
| Vít M3 × 45 | 6 | Bắt nắp từ đáy thân R2; thử độ dài sau in |
| Vít M3 × 12 | 16 | Chắn xích, nẹp điện thoại, gá phụ |
| Vít M3 × 16 | 12 | 8 vít cho 4 gá chống rơi, dự phòng |
| Vít M3 × 20 | 8 | Bắt đế cổ xuyên nắp; kiểm tra không chạm bo bên dưới |
| Vít M3 × 35 | 8 | Kẹp hai N20 nâng 3 mm xuyên gá và đáy |
| Vít M3 × 35 | 6 | 2 trục bánh dẫn hướng; còn lại dự phòng |
| Vít M3 × 12–16 và ê-cu khóa | 1 bộ | Trục đỡ cổ phía đối diện servo, siết đến hết rơ nhưng không bó khớp |
| Vít chặn không đầu M3 × 8 | 2 | Khóa bánh chủ động lên mặt phẳng trục D; tarô lỗ pilot Ø2,5 sau in |
| Ê-cu M3 / ê-cu khóa M3 / vòng đệm M3 | 40 / 6 / 50 | Các mối bắt xuyên và trục quay |
| Vít M2 × 8 + ê-cu M2 | 4 | Gắn horn servo vào trụ head_stem qua hai lỗ cách 12 mm |
| Standoff nylon M2/M3 cao 4 mm và vít theo bo | Khoảng 20 | Gá 5 bo trên khay có rãnh; không để mặt hàn chạm vít kim loại |
| Horn 24T tương thích đúng servo + vít tâm đi kèm | 1 | Dùng chi tiết mua kèm servo. Chọn lỗ sẵn tương ứng hoặc khoan horn theo mẫu hai lỗ trụ head_stem; không khoan trục servo. |
| EVA/silicone dày 0,5–1 mm | 1 tấm | Điện thoại, N20, chống rung |
| EVA cho nắp kẹp servo khoảng 1,5–2 mm | 1 miếng | Bù độ hở giữa thân servo và cap |
| Dây đai Velcro rộng 10 mm | 2 | Giữ pack pin qua khe khay và đáy |
| Keo hai mặt mỏng, dây rút, đệm cách điện | 1 bộ | Giữ board cảm biến trên carrier, touch, dây điện |

Ưu tiên ống đệm kim loại theo bảng trên. Ống đệm `bearing_spacer` in chỉ để thử fit: thành 1,3 mm chưa đạt ngưỡng FDM tự đứng 1,6 mm. Chi tiết in có Ø6/Ø3,4 × 17,6 mm. Nếu nó chạm phớt ổ bi thực tế, thay bằng ống kim loại ID3,2 / OD phù hợp **chỉ tỳ vòng trong** của ổ bi, chiều dài 17,6 mm. Không ép hai vòng ngoài bằng một đai ốc trục.

## Trình tự lắp

### 1. Thân dưới

- Ép insert M3 vào 6 boss **phía trong nắp**, 4 trụ khay điện tử và 3 lỗ cap servo. Sáu trụ xuyên thân dưới chỉ là lỗ dẫn vít, không lắp insert vào đó.
- Bắt hai `motor_cradle` ở phía sau, đặt N20 trục hướng ra hai bên. Lót foam mỏng, đóng `motor_cap`; bốn vít mỗi bên xuyên xuống đáy, ê-cu và vòng đệm bên ngoài. Không ép móp vỏ motor.
- Lắp `battery_tray`, luồn hai đai. Pack pin có bảo vệ nằm thấp trong thân; dây đi qua miệng khay, không bị ép dưới mặt khay.
- Lắp bốn `sensor_carrier` lên trụ đáy. Board Pololu nằm trong hai ray, mặt phát/thu hướng xuống qua cửa sổ. Dán ở mép PCB, không che quang học. Header nên hàn dây thấp.
- Lắp hai `front_sensor_carrier` ở mặt trước, board hướng ra trước. Lắp hai `front_sensor_bezel` màu tối từ ngoài vào lỗ tròn Ø16, cố định bằng ít keo tháo được, không để keo che khẩu độ Ø12. Kiểm tra trường nhìn ToF sau lắp; không lắp thêm kính chưa hiệu chuẩn. Tấm I/O bắt phía sau; nếu cổng/switch thực tế khác kích thước thì dùng `io_panel_blank` và chỉnh lỗ trong source.

### 2. Bánh xích

- Tarô M3 lỗ vít chặn trên hub bánh chủ động. Trục D lắp đủ sâu, mặt phẳng quay về vít chặn. Hub có khe hở với thành vỏ; không ép bánh cọ vào vỏ.
- Ép hai 623ZZ vào mỗi bánh dẫn hướng, đặt spacer giữa **vòng trong**. Đặt đầu vít trục vào lỗ tiếp cận ở thành thân rồi lắp `tensioner` phía ngoài.
- Lắp vòng xích TPU. Gờ giữa mặt trong chạy trong rãnh bánh. Đường kính lòng vòng được giảm 0,6 mm so với bánh để có tải trước đàn hồi; đây là chủ ý, không phải khe lắp cho vật liệu cứng.
- Dịch tấm tăng xích trong rãnh, chỉnh đủ căng để truyền lực bằng ma sát và không tuột gờ. Không kéo căng đến mức làm N20 nặng tải. Khóa hai vít tấm tăng xích, rồi bắt hai `fender`.
- Đây là **xích TPU truyền động ma sát**, không phải dây đai răng tiêu chuẩn. Độ trượt, giãn và lực cản phải thử với vật liệu thực tế. Trục N20 chịu tải hướng kính ở bánh sau; chưa có xác nhận tuổi thọ dưới tải robot.

### 3. Điện tử

- Khay điện tử bắt trên bốn trụ. Lắp board bằng standoff nylon 4 mm và rãnh dài; các khối màu xanh trong STEP chỉ là vùng dự trù.
- Điện thoại dùng mic, loa và camera sẵn có. ESP32 nhận BLE, điều khiển driver và đọc cảm biến.
- Hai vùng lõm dưới nắp dành cho điện cực chạm / module touch thấp; phần nhựa còn lại 2 mm. Dùng dây hàn thay header cao, xác nhận độ nhạy TTP223 sau khi đóng nắp.
- Công tắc nhận biết điện thoại chưa có model xác định: dự trù gắn công tắc cần gạt mini ở mép giá dưới bằng pad/giá phụ theo mẫu mua thực tế. Có thể dùng tín hiệu Qi của ứng dụng cho phiên bản đầu; hình bao công tắc chưa được kiểm tra trong assembly.
- Nguồn motor, servo và logic chia nhánh; chung mass theo sơ đồ điện được chốt sau. Không cấp 2S trực tiếp vào ESP32 hoặc motor 6 V. Chừa dây mềm đi lên cổ, giữ bằng `cable_clip` hoặc dây rút ở chân đế cổ.

### 4. Cổ và gá từ tính R4

- Bắt `neck_mount` qua nắp bằng các vít và ê-cu, lắp servo với foam và `servo_cap`. Trục servo đưa về trung tính trước khi gắn horn 24T.
- Gắn horn vào bên trái `head_stem` bằng hai vít M2, dùng vít M3 tại gối đỡ phía đối diện. Cấu trúc bên trong có trục đỡ; phía ngoài nhìn thấy một trụ giữa.
- Bắt `magnetic_head` vào trụ bằng hai vít M3×30 tại tai dưới, dùng vòng đệm/ê-cu. Hai vít nằm ngoài vùng lòng puck.
- Lắp puck từ tính dự kiến Ø56×7 mm. Không in nam châm; phải mua phụ kiện từ tính thật. Cố định puck mà không che mặt tiếp xúc và không đưa kim loại vào vùng cuộn sạc.
- Lắp `neck_shroud`, đi dây qua khe trước của vỏ che, cố định bằng hai vít tự ren Ø2,2×12 ở phía sau vào lỗ pilot Ø1,8. Chừa vòng dây mềm cho chuyển động.
- Đặt điện thoại lên puck qua vòng từ/ốp tương thích. Kiểm tra lực giữ, lực chống xoay và khoảng hở camera. Khối bao camera chỉ là giả định thiết kế.
- Khởi động với biên độ nhỏ; giới hạn CAD dự kiến ±10°. Chỉ dùng giới hạn này khi báo cáo `motion_checks.json` tương ứng xác nhận không va chạm; chưa xác nhận biến dạng dây hoặc tải động.
- Đóng nắp bằng sáu vít M3×45 từ đáy lên insert trong nắp. Không dùng hướng bắt vít của R1.

## Thiết lập in

- PETG: lớp 0,20 mm, tối thiểu 4 vòng thành, 5 lớp đáy/nắp, infill khoảng 30–40%; gá motor/cổ/trụ head_stem nên 5–6 vòng thành và 45–60% infill. Điều chỉnh nhiệt theo filament/máy của bạn.
- TPU 95A: hai xích in nằm nghiêng theo STL (mặt bên tiếp xúc bàn), lớp 0,20 mm, 3–4 vòng thành; thành vòng 2,4 mm nên dùng điền kín. Hỗ trợ nếu slicer phát hiện gờ giữa cần đỡ, tránh support khó gỡ trong vòng.
- Chi tiết được xuất theo hướng in dự kiến; không tự xoay lắp ráp thành hướng in. Một số lỗ ngang/miệng kẹp và gờ bánh cần support cục bộ. Báo cáo dfam có diện tích mặt cần support theo giới hạn 45°, không thay thế preview slicer.
- In nắp úp để mặt đỉnh phẳng làm đáy, hỗ trợ cục bộ vùng tai vít nếu cần. In bánh chủ động có support dưới vai hub; có thể lật mặt ngoài xuống để giảm support theo báo cáo orientations.
- Các lỗ vít nhỏ có thể cần doa bằng tay; kiểm tra coupon trước khi chỉnh hệ số bù XY. Không bật tự co/giãn toàn bộ mô hình để chữa một lỗ.

## Kiểm chứng ngoài phạm vi CAD

Cần thử thật: lực kéo và dòng kẹt N20, độ bám/giãn xích, độ rơ trục, sức giữ điện thoại, nhiệt servo/Qi/nguồn, thời lượng pin, chống lật và phản xạ mép bàn. Thông số điện và kích thước N20/board hiện có của bạn chưa được xác nhận. Bộ file này không thay thế các phép thử đó.
