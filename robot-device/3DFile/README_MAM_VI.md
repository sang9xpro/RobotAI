# MẦM R4 — bộ vỏ robot cho OPPO Find X8

Bộ CAD tham số để **in bản mẫu và lắp thử**, lấy hình dáng từ bản phác thảo MẦM. Đơn vị **mm**. Thiết kế dùng hai trong bốn N20 đang có, một servo gật đầu, điện thoại Android và cụm điện tử nằm trong thân. Không phải bản đã nghiệm thu bằng robot thật.

Bản R4 bổ sung tháp gọn theo ảnh tham chiếu: vỏ thuôn 90,4 × 70 × 33 mm, vai bo, cổ ngắn, hai ốp bên màu kem. Xem `docs/THAY_DOI_R4.md`. Kế thừa R3: đầu thân dốc thoải, đuôi cao; bánh sau lớn, bánh trước nhỏ; một trụ và đĩa từ tính tròn thay khung kẹp. Xem `docs/THAY_DOI_R3.md`. Gói R1 giữ riêng để đối chiếu.

## Mở và in

- `STEP/assembly.step`: cụm lắp đầy đủ, có mô hình bao kích thước linh kiện mua ngoài.
- `STEP/magnetic_mount_preview.step`: xem gá từ tính khi tháo điện thoại.
- `STEP/internal_assembly.step`: mở nắp để xem vị trí pin, motor, ESP32 và bộ nguồn.
- `STEP/parts_layout.step`: bảng các chi tiết; **không phải một bàn in**.
- `STL/`: từng chi tiết in riêng, đã đặt theo hướng in dự kiến, Z nhỏ nhất bằng 0.
- `GLB/assembly.glb`: mô hình màu để xem nhanh.
- `docs/print_manifest.csv`: số lượng, vật liệu, hướng in, hỗ trợ.
- `docs/HUONG_DAN_LAP.md`: trình tự lắp và danh sách phụ kiện cơ khí.
- `docs/geometry_checks.json`, `docs/mating_checks.json`, `docs/dfam/`, `docs/motion_checks.json`: kết quả kiểm tra thực tế của bộ file.

**Đọc `docs/KET_QUA_KIEM_TRA.md` trước khi in.** Ống đệm `bearing_spacer` nhựa chỉ để thử fit; nên dùng hai ống đệm kim loại dài 17,6 mm như hướng dẫn.

**Không in cả assembly.step:** điện thoại, bo mạch, pin, servo, ổ bi và mắt trang trí trong đó chỉ để xem bố trí. Chỉ in các STL trong danh mục.

## Cấu hình đã dùng để dựng

| Giao diện | Kích thước / giả định |
|---|---|
| Điện thoại | OPPO Find X8 trần: 157,35 × 74,33 × tối đa 7,95 mm; 193 g. Chưa đo ốp của bạn. |
| Cụm camera | Mô hình bao camera Ø60 × 4 mm; gá từ tính lệch ngang 17 mm để tránh vùng này. Đây là giả định cần đối chiếu máy thật, không phải bản vẽ camera của OPPO. |
| Motor | N20 thân 26 × 12 × 10 mm, trục D Ø3 dài 9 mm. Khe gá 12,6 mm; cần đo N20 đang có, đặc biệt phiên bản có encoder. |
| Servo | Hitec HS-225MG: thân khoảng 31 × 32,4 × 16,8 mm theo hướng lắp; gá kẹp có chỗ tránh tai rộng. Hình bao tai và vị trí trục cần đối chiếu servo mua thực tế. Dùng horn 24T mua kèm, không in răng spline. |
| ESP32-S3 | Dành vùng 34 × 64 × 16 mm trên mặt khay, dùng standoff nylon / rãnh chỉnh. WROOM rời cần PCB mang riêng. |
| Driver | Vùng 20 × 25 × 8 mm. Loại driver điện còn phụ thuộc điện áp/dòng N20. |
| Nguồn | Ba vùng 43 × 21 × 13 mm; có thể xoay bo 90°. Không phải mọi module XL4015/LM2596 đều vừa. Chọn module nằm trong vùng này hoặc sửa tham số. |
| Pin | Pack 2S có bảo vệ, **tối đa 70 × 41 × 20 mm**, gồm cả phần BMS nhô ra. Khay lọt lòng 72,6 × 44,6 mm; đi dây ra trên. Điện áp vẫn cần xác nhận theo N20. |
| Cảm biến | 4 VL53L4CD và 2 VL53L0X, **board Pololu 13 × 18 mm**. Board GY-530 / board hãng khác có thể khác kích thước. |
| Puck từ tính / Qi | Puck tròn Ø56 × 7 mm, gá một trụ phía sau; người dùng chưa mua puck, phải đo sản phẩm chọn mua, kiểm tra đúng tâm cuộn dây và nhiệt. Không cam kết vừa đế OPPO AIRVOOC bất kỳ. |
| In | FDM bàn 220 × 220 mm; nozzle 0,4 mm; PETG cho chi tiết cứng, TPU 95A cho xích. Đây là giả định vì chưa có mẫu máy in của bạn. |

Các bo chưa có mã module chính xác được thể hiện bằng **khối bao kích thước**, không giả làm CAD linh kiện đã xác minh. Danh mục STEP Parts đã được tra cứu trước; lưu tại `docs/catalog_search.json`. Không tìm thấy đúng N20/HS-225MG/ToF/DRV8833. Kết quả ESP32-S3 là module chip, không phải board USB đang chưa xác định, nên không dùng sai model đó cho giá đỡ.

## Những phần có thể chỉnh

- `src/lib/params.py`: kích thước tham chiếu, tọa độ lắp, màu, khe hở.
- `src/lib/shapes.py`: các hàm tạo từng chi tiết; kích thước gá servo, pin, module, Qi và các rãnh được ghi trực tiếp tại đây.
- `src/lib/assembly_layout.py`: vị trí các chi tiết và khối bao linh kiện.
- `src/*.py`: mỗi file xuất một chi tiết hoặc cụm lắp qua skill cad của text-to-CAD.

Không phải mọi kích thước đều đã phụ thuộc tự động vào một tham số chung: khi thay thân máy/motor/servo phải cập nhật cả giao diện gá và vị trí tương ứng rồi chạy lại kiểm tra, không chỉ sửa tên linh kiện.

## Dựng lại

Nếu chuyển sang máy khác, tạo Python venv và cài `requirements-cad.txt`; không cần đóng gói `.runtime`. Script DfAM đo hình học từ skill gốc đã được sao chép nguyên bản vào `checks/dfam_tool.py`.

Môi trường riêng `.runtime` dùng cadgen 0.7.6 và build123d 0.11.1, giữ nguyên môi trường CAD cũ của máy.

```bash
cd "/Volumes/SSD_Sang/Source Code/RobotAI/robot-device/3DFile"
.runtime/bin/python build.py --checks --review
.runtime/bin/python checks/dfam_batch.py
CADGEN_CACHE_DIR="$PWD/.cadgen-cache" .runtime/bin/python checks/motion.py
```

`build.py` chạy model của cadgen, xuất STEP/GLB và tạo STL in từ **đúng STEP đã lưu**, dùng OCCT với sai số dây cung 0,04 mm / góc 0,15 rad. Lý do: bộ chia lưới cadgen 0.7.6 tạo cạnh hở ở một số hình BRep hợp lệ; bản lưới đó giữ riêng trong `tmp/cadgen_mesh`, không dùng để in. Ngoài ra đã tăng bán kính trụ vít nắp từ 6 lên 6,4 mm để trụ nối với thành vỏ bằng phần giao có thể tích, tránh tiếp tuyến gây cạnh lưới không manifold. Không sửa lưới bằng cách làm đầy tùy ý. STL cuối trong `STL/` được kiểm tra lại bằng trimesh và dfam-check.

## Nguồn kích thước

- [OPPO Find X8 chính thức](https://www.oppo.com/en/smartphones/series-find-x/find-x8/specs/)
- [HS-225MG — Hitec](https://hitecrcd.com/hs-225mg-metal-gear-mighty-mini-servo/)
- [Datasheet HS-225MG](https://www.hiteccs.com/public/uploads/data_sheet/HCS_HS-225MG_Specsheetv2.2_1-1729889510.pdf)
- [Micro metal gearmotors — Pololu](https://www.pololu.com/category/60/micro-metal-gearmotors)
- [VL53L4CD carrier](https://www.pololu.com/product/3692), [VL53L0X carrier](https://www.pololu.com/product/2490)

Thông số gá, độ căng TPU và độ rơ lỗ in phải xác nhận bằng mẫu thử. Chưa thử tải, nhiệt servo/Qi, lực kéo N20, chống lật, độ bám xích hay khoảng dừng trước mép bàn trên phần cứng thật.
