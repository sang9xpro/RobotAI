# PhoWhisper-small: thử trên WAV của người dùng

Câu tham chiếu: Đây là dung lượng lưu trữ, không phải ram. Khi chạy ram còn dành cho bộ nhớ đệm tính toán

Nguồn: https://huggingface.co/vinai/PhoWhisper-small
Revision: a86b604c346caf7148c37512eafe783a16420adb
Trọng số gốc SHA-256: 9ec6f07c5bd321fc8f477cf778c43c3fd94b0e33a9f20283fc61cc6d22cbfded
GGML FP16 SHA-256: db8de368e822fce5dcdce7c0c4c80c4b554c27655516dea5ec8c093e22f58785

Chuyển đổi bằng script upstream convert-h5-to-ggml.py của whisper.cpp đang dùng trong app. Không sửa trọng số/fine-tune, không đưa câu tham chiếu vào prompt.
WAV gốc: PCM16 mono 16 kHz, 8.64 giây. Đoạn VAD tái hiện như app: 5.446 giây.
Cấu hình so sánh: CPU macOS, 4 threads, language=vi, transcribe, -mc 0, -nt (không sinh timestamp), không initial prompt.

| Phép thử | Kết quả | Lỗi token / 20 | Xử lý không tính nạp model* |
|---|---|---|---|
| Whisper small / raw / greedy | Đây là dung nợm ní chữ không phải răm thì chạy răm còn dành cho bộ nhỏ đẹp tính toán | 8 (40%) | 0.957 s |
| large-v3-turbo / raw / greedy | Đây là dung lượng như chữ không phải RAM RAM còn dành cho bộ nguyên đẹp tính toán | 6 (30%) | 3.891 s |
| PhoWhisper-small / raw / greedy | đây là dung lượng lưu trữ không phải ram khi chặng ram còn dành cho bộ nhóm đạn tính toán. | 3 (15%) | 1.004 s |
| PhoWhisper-small / raw / beam 5 | đây là dung lượng lưu trữ không phải ram khi chặng ram còn dành cho bộ nhẫn đại tính toán. | 3 (15%) | 1.240 s |
| PhoWhisper-small / VAD / greedy | đây là dung lượng lưu trữ không phải gram khi trạm gram còn dành cho bộ nhóm đen tính toán. | 5 (25%) | 0.967 s |

* Một lượt chạy riêng lẻ, chưa phải benchmark nhiều lần; không suy ra tốc độ điện thoại.
Token tách theo khoảng trắng/từ Unicode, bỏ qua viết hoa và dấu câu. Đây là tỷ lệ lỗi token kiểu WER, không phải độ chính xác tổng quát; từ tiếng Việt có thể gồm nhiều token.

## Kiểm tra chuyển đổi

Transformers 4.46.3 + PyTorch, CPU FP32, greedy vi/transcribe, attention_mask được truyền, return_timestamps=False cho kết quả trùng bản GGML -nt:
đây là dung lượng lưu trữ không phải ram khi chặng ram còn dành cho bộ nhóm đạn tính toán.

Ở chế độ timestamp mặc định của CLI (gần cấu hình app hiện tại), PhoWhisper tạo thêm “and” và ước lượng kết thúc 30 giây dù WAV chỉ 8.64 giây. -nt loại bỏ từ thêm và kết quả trùng Transformers; đây là lý do dùng chế độ này cho bảng so sánh.
Lưu ý: small trước đây có 6 lỗi trong chế độ timestamp; bảng này có 8 lỗi vì chế độ -nt nhận RAM thành răm hai lần. Không trộn hai cấu hình.

## Kết luận

PhoWhisper-small cải thiện trên đúng mẫu này: lưu trữ và Khi được giữ; còn chạy → chặng, nhớ → nhóm, đệm → đạn. Beam 5 không giảm số lỗi. VAD có 5 lỗi, WAV gốc có 3 lỗi; chưa nên cắt audio cho STT theo VAD ở mẫu này. Một mẫu chưa chứng minh ưu thế tổng quát.
Chưa thay đổi app/APK. Trọng số chuyển đổi ở android-app/.work/phowhisper-small/ggml-model.bin; các log và transcript ở android-app/.work. Nếu tích hợp, cần cấu hình no_timestamps riêng cho PhoWhisper và kiểm tra bộ câu rộng hơn.
