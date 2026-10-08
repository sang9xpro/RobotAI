# So sánh Whisper trên whisper-test.wav

Câu tham chiếu: Đây là dung lượng lưu trữ, không phải ram. Khi chạy ram còn dành cho bộ nhớ đệm tính toán

Audio: WAV PCM16 mono 16 kHz, 8.64 giây. VAD tái hiện từ cấu hình app: 5.446 giây.

Chạy trên macOS CPU, 4 threads, cùng whisper.cpp; tiếng Việt vi, translate=false, không dùng initial prompt. Model turbo chưa lượng tử hóa. Không suy ra tốc độ Android từ phép thử này.

| Phép thử | Kết quả | Lỗi token / 20 |
|---|---|---|
| small / WAV gốc / greedy | Đây là dung nợm ní chữ không phải Ram thì chạy Ram còn dành cho bộ nhỏ đẹp tính toán | 6 (30%) |
| turbo / WAV gốc / greedy | Đây là dung lượng như chữ không phải RAM RAM còn dành cho bộ nguyên đẹp tính toán | 6 (30%) |
| turbo / WAV gốc / beam 5 | Đây là dung lượng niu trữ không phải RAM RAM còn dành cho bộ nguyên đẹp tính toán | 5 (25%) |
| turbo / VAD / greedy | Đây là dung lượng miêu trữ, không phải RAM RAM còn dành cho bộ nhuận đẹp tính toán | 5 (25%) |

Token được tách theo khoảng trắng/từ Unicode, bỏ dấu câu và viết hoa; tiếng Việt có từ nhiều âm tiết nên đây là lỗi token, không phải tỷ lệ sai từ ngôn ngữ học hay phần trăm chính xác tổng quát.

Lần chạy riêng lẻ greedy trên WAV gốc: small tổng 1.578 s gồm nạp model 0.536 s; turbo tổng 6.185 s gồm nạp model 1.751 s. Ước tính xử lý không tính nạp: 1.042 s và 4.433 s. Đây không phải benchmark nhiều lần. Lượt beam 5 có chạy đồng thời với một lượt small nên không dùng thời gian lượt đó để so sánh.

Kết luận: trên mẫu này turbo greedy có cùng 6 lỗi token như small; beam 5 giảm còn 5 lỗi. Cả raw và VAD đều nhận sai lưu trữ/đệm, và turbo bỏ Khi chạy. Chưa có bằng chứng đủ mạnh để thay model mặc định của app.
