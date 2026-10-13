# MẦM R3 — dáng ô tô, xích bất đối xứng, gá từ tính

Theo mô tả mới của bạn:

- Đầu thân (-Y) thấp, dốc thoải dài; phần đuôi (+Y) cao và dựng hơn. Vỏ trên dịch các tiết diện cao về phía sau; mặt đặt cổ vẫn Z=84 mm.
- Bánh sau truyền động có đường kính mặt chạy Ø52 mm, bánh trước Ø36 mm (đường kính vành giữ lớn hơn 3 mm). Hai tâm ở Y=+40/Z=31 và Y=−40/Z=23, bảo đảm đáy hai bánh cùng cao độ. Hai vòng xích TPU và chắn xích dựng lại theo tiếp tuyến chung, không kéo giãn mô hình xích R1.
- Bỏ khung kẹp, nẹp điện thoại và gá Qi cũ. Một trụ giữa gắn đĩa từ tính tròn; có vỏ che cơ khí tháo được. Servo và trục đỡ vẫn nằm bên trong, không dùng trục nhựa in thay spline servo.
- Người dùng đã chọn kích thước dự kiến puck Ø56 × 7 mm. Gá tròn có lòng Ø56,8, sâu 7 mm; cố định puck bằng keo mỏng/đai phi kim theo sản phẩm mua thực tế. Puck phải có chức năng giữ từ tính nếu muốn giữ điện thoại, không chỉ có sạc Qi.
- Điện thoại nhô về trước trục gật khoảng 24 mm tại mặt lưng. Đĩa đặt lệch 17 mm theo chiều ngang để tránh hình bao camera; vị trí cuộn sạc và vòng từ trên máy phải đối chiếu thực tế. Tâm điện thoại Z=153 mm.
- Vít nắp thân bắt từ đáy; giữ mặt vỏ trên sạch. Hai vòng cảm biến phía trước màu tối, thân kem và mảng xanh trên đỉnh.

## Chi tiết không dùng lại từ R1/R2

Không trộn base/lid, bánh, xích, chắn xích, gá N20 và cụm đầu giữa các phiên bản. Khung kẹp cũ được chuyển sang `archive/legacy_clamp/`, không có trong danh mục in R3. Gói R1 giữ riêng để tham khảo.

## Phụ kiện thay đổi

- 6 vít M3×45 bắt nắp từ đáy lên 6 insert trong nắp.
- 8 vít M3×35 cho gá N20 đã nâng 3 mm; xác nhận chiều dài sau in và lắp đệm.
- 2 vít M3×30 + ê-cu cho đĩa từ tính với trụ; đầu vít/ê-cu không được nhô vào mặt lưng điện thoại.
- 2 vít tự ren nhựa Ø2,2 × 12 cho mặt sau vỏ che cổ, lỗ pilot Ø1,8 trên hai tai gá. Không dùng vít quá dài chạm servo/dây.
- Đế từ tính tròn dự kiến Ø56×7, cùng ốp hoặc vòng từ tương thích ở phía điện thoại. Cần đo lực giữ và mô-men chống xoay trước khi chạy robot.
- Ống đệm giữa các ổ bi nên dùng kim loại, dài 17,6 mm; file nhựa chỉ để thử fit như báo cáo DfAM.

Bản này để xem dáng và chế tạo mẫu, chưa xác nhận kích thước N20, board ESP32, puck mua thực tế hoặc tải động. Chỉ in các file có tên trong `print_manifest.csv`; hình bao điện thoại/điện tử trong assembly không phải chi tiết in.
