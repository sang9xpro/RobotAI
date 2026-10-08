# Thử VieNeu-TTS v3 Turbo cho RobotAI

Đã tạo 8 mẫu offline bằng CPU macOS ARM, FP32, 4 threads, temperature 0.8, seed NumPy 42. Sáu mẫu chào giống nhau: Hải Đăng/Trúc Ly (Bắc), Adam/Thục Đoan (Nam), Quang Sơn/Ngọc Trân (Trung); thêm 2 mẫu thuật ngữ/số bằng Hải Đăng. Nhãn vùng miền lấy từ preset upstream, chưa phải đánh giá nghe chủ quan.

- Mã nguồn: https://github.com/pnnbao97/VieNeu-TTS — commit 85344322b7258b4e25479b692e8e3396baf9db34, SDK 3.8.3, cài theo uv.lock.
- Model: https://huggingface.co/pnnbao-ump/VieNeu-TTS-v3-Turbo — revision 61b85e3d937fbbacb387714180e8182823512523, onnx_update FP32.
- Codec: https://huggingface.co/OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX — revision ceff0d0749bfb3fa2d61149794ec6feef0d1e1ae.
- WAV PCM16, mono, 48 kHz. Chỉ giảm gain khi peak vượt 0.98 để tránh clipping; không nâng gain các giọng nhỏ. Tất cả file có mẫu hữu hạn, không clipping.
- Script sinh mẫu chạy HF_HUB_OFFLINE=1; không gửi audio/text lên máy chủ. Trọng số chỉ được tải ở bước chuẩn bị.
- artifacts dùng để tổng hợp preset/streaming: 520.7 MB (không tính các mảnh tải tạm, môi trường Python hoặc cloning). Giọng có sẵn không cần codec encoder, speaker encoder hay denoiser.

## Đo trên Mac

- Load model: 3.18 s.
- Tổng hợp 8 mẫu: 0.87–1.28 s; audio dài 4.64–6.40 s.
- RTF: 0.18–0.23.
- Streaming chunk đầu: 113 ms sau khi model đã nạp. Không tính thời gian phát loa, chưa xác minh chunk đầu có lời nói nghe được.
- RSS đỉnh toàn tiến trình Python: 1.54 GiB, không phải RAM riêng của model và không suy ra bộ nhớ Android.
- Một lượt mỗi câu/giọng; chưa phải benchmark nhiều lượt.

## Khả năng Android

Điện thoại ADB đang kết nối: CPH2651, chip MT6991, ARM64, Android API 36. /proc/meminfo: MemTotal 15745072 kB (~15.0 GiB), MemAvailable tại thời điểm đọc 6038300 kB (~5.76 GiB). Đây không phải thiết bị yếu đại diện cho mọi điện thoại Android cũ.

Mã nguồn upstream chưa có module Android, mục Mobile SDK còn chưa hoàn thành trong README. Đã kiểm tra các graph có thể nạp bằng ONNX Runtime 1.24.4 trên Mac và xuất onnx-io-schema.json. Chưa chạy graph, đo RTF hay đo RAM trên Android; chưa thay đổi APK.

Cần triển khai riêng, không thể nạp nguyên VieNeu vào OfflineTts của Sherpa-ONNX:

1. Thêm ONNX Runtime Android, quản lý 3 session model: prefill/decode_step/acoustic và session codec. Lưu external .data đúng đường, tránh giữ nhiều bản trọng số.
2. Chuyển sea-g2p (Rust) sang thư viện JNI Android hoặc triển khai pipeline tương thích: chuẩn hóa số/viết tắt, phonemizer, tokenizer. Không thay bằng đọc ký tự vì đầu vào model là phoneme.
3. Chuyển logic embedding, speaker anchor, lấy mẫu, repetition history, KV cache và điều kiện kết thúc từ engine NumPy sang Kotlin/C++.
4. Dùng preset speaker_emb/ref_codes. Giai đoạn đầu không cần clone giọng.
5. Phát PCM48k qua AudioTrack, hỗ trợ hủy và giải phóng session. Streaming codec hiện có 54 input/54 output gồm nhiều tensor state; cần port chính xác, có thể triển khai full decoder trước.
6. Đối chiếu tensor và audio với mẫu Mac, đo độ trễ/RAM/nhiệt độ trên điện thoại khi STT và TTS cùng tồn tại.

Không đổi kiến trúc sang TTS server: mục tiêu tiếp tục là chạy trên điện thoại. Port Android là phần triển khai tiếp theo, chưa hoàn tất ở phép thử nghe này.

## File

samples/tts/vieneu-v3-turbo/index.html: trang nghe có nút chọn giọng, lưu lựa chọn trong localStorage; không gửi lựa chọn đi đâu. metrics.json: số đo từng mẫu; onnx-io-schema.json: input/output của graph; model-manifest.json: revision, kích thước và SHA256 từng artifact.

android-app/tts-test/download_vieneu.py và generate_samples.py: tái hiện tải/sinh mẫu. Thư mục .work chứa dependency/model và bị bỏ qua bởi Git. Không commit/push.
