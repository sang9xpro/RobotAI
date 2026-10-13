# MẦM R4 — tháp gọn theo ảnh tham chiếu

Áp dụng dáng chân rộng, vai thuôn bo tròn, cổ ngắn và hai mảng ốp bên từ ảnh người dùng. Đây là thiết kế cơ khí mới; ảnh không cho biết cấu tạo bên trong robot mẫu.

- Vỏ tháp `neck_shroud`: 90,4 × 70 × 33 mm; đáy ở Z89, đỉnh Z122. Đế bắt vào thân 100 × 88 × 5 mm, giữ các vị trí vít cũ. Không phải toàn bộ cụm tháp đều nằm trong 90,4 × 70 mm.
- Gá servo được dịch 14 mm về giữa và hạ 10 mm so với R3, nhằm thu gọn vỏ. Trục gật ngang X đi qua Y12, Z102. Tâm đĩa từ tính X17, Y−3, Z145 ở tư thế chuẩn.
- Đĩa ngoài Ø62, hốc dự kiến Ø56,8 cho puck Ø56 × 7 mm; puck chưa được mua. Giữ gá lệch để tránh mô hình bao camera giả định.
- Cổ một trụ, che cơ cấu bằng vỏ thuôn. Mặt trước có khe chuyển động; không thể bịt kín khe này bằng vỏ cứng.
- Hai ốp trang trí màu kem tháo được. `tower_side_panel.stl` là bên trái: in một bản nguyên và một bản MIRROR theo X trong slicer, sau đó đặt đáy lên bàn. Không in hai bản cùng chiều. Dùng băng dính mỏng/keo tháo được tại hốc, chừa khe chu vi 0,3 mm. Không dùng ốp làm điểm chịu tải.
- Thân đầu thấp, đuôi cao, bánh sau lớn và bánh trước nhỏ giữ nguyên. Hai N20 dẫn động; hai N20 còn lại dự phòng.

Thông số servo, N20, board và khối bao camera vẫn cần đối chiếu linh kiện thật. Chưa có thử nghiệm lực servo, lực từ, chống lật hoặc độ bền. Đọc báo cáo kiểm tra trước khi in; các kiểm tra CAD chỉ xác nhận phạm vi hình học được nêu trong báo cáo.
