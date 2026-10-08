# Benchmark Android: PhoWhisper-large và Vulkan

Đo ngày 02/10/2026, OPPO CPH2651, Android 16, ARM64, GPU Mali-G925-Immortalis MC12, Vulkan 1.3. APK 1.3-large-vulkan-stream. WAV của người dùng: mono 16 kHz, 8,64 giây. Greedy, tiếng Việt, không dịch, không timestamp; cùng dữ liệu trên các model/backend. Không chạy speaker gate/TTS trong lúc đo STT của bảng.

## Kết quả

Hai số ngăn bởi `/` là lượt đầu / lượt tiếp theo trong cùng context. CPU large được đo một lượt ở foreground. Hai phép đo small ban đầu chạy instrumentation không mở Activity; large dùng ActivityScenario để giữ foreground. Không kiểm soát nhiệt độ hay ứng dụng khác, nên không coi chênh lệch vài phần trăm là đáng kể.

| Model | Backend | Nạp (s) | Nhận dạng (s) | RTF | PSS tiến trình cuối (GiB) |
|---|---|---:|---:|---:|---:|
| small | CPU | 0.52 | 13.97 / 13.89 | 1.62 / 1.61 | 0.67 |
| small | Vulkan | 0.53 | 5.96 / 4.83 | 0.69 / 0.56 | 1.13 |
| large-q5_0 | CPU | 1.88 | 55.67 | 6.44 | 1.54 |
| large-q5_0 | Vulkan | 1.63 | 30.38 / 31.91 | 3.52 / 3.69 | 1.99 |
| large-f16 | Vulkan | 4.77 | 30.81 / 29.70 | 3.57 / 3.44 | 3.95 |

Large Q5 Vulkan cũng đã được đo trước đó: 30,09 / 29,25 giây, PSS ~1,93 GiB. Lượt đo foreground sau đó: 30,38 / 31,91 giây. Khoảng quan sát ~29–32 giây, vẫn chậm hơn thời gian thực. Encoder chiếm khoảng 26–29 giây/lượt. Chia đoạn không loại bỏ được chi phí này và có thể làm hàng đợi dài hơn.

Phép đo CPU large đầu tiên chạy nền: nạp ~203,75 giây, lượt đầu 67,27 giây; hệ thống dừng tiến trình ở lượt sau (`OTHER KILLS BY SYSTEM`, Cached(nirvana)[instrumentation]). Không có log native crash mới ở thời điểm đó. Đã đo lại ở foreground thành công: nạp 1,88 giây, nhận dạng 55,67 giây. Số chạy nền không được dùng làm số chuẩn trong bảng.

## Chất lượng trên WAV này

Câu gốc: “Đây là dung lượng lưu trữ, không phải ram. Khi chạy ram còn dành cho bộ nhớ đệm tính toán”.

Large FP16, large Q5 CPU/GPU đều cho:

> đây là dung lượng lưu trữ không phải ram khi chạy ram còn dành cho bộ nhận đệm tính toán.

Vẫn sai “bộ nhớ đệm” thành “bộ nhận đệm”. Lượng tử hóa Q5 không làm thay đổi transcript trên mẫu này; chưa có bộ kiểm tra đủ lớn để kết luận về WER chung.

## TTS và tương thích runtime

- AudioTrack phát thực sự trong lúc model tạo phần tiếp theo; nút Dừng đã qua kiểm thử. Câu “Xin chào, mình là robot của bạn.” ở một lượt stochastic: nạp 2705 ms, PCM đầu 625 ms tính sau nạp, gọi bắt đầu phát 677 ms, tạo 1882 ms, audio 1,68 giây, RTF 1,12. Lần đầu tổng chờ còn cộng thời gian nạp model; giữ model nạp sẵn giảm phần chờ này.
- Full/stream codec greedy: sai số tuyệt đối trung bình ~5,94e-8; stream 6 phần, 1,76 giây audio, PCM đầu 705 ms, tổng tạo 2432 ms. Các con số khác giữa hai bài test do greedy/stochastic và lịch chạy khác nhau.
- Giữ large Q5 Vulkan cùng TTS và speaker gate: TTS PCM đầu 720 ms, đăng ký ba mẫu và xác minh giọng qua kiểm tra, PSS toàn tiến trình ~3,06 GiB. Đây không phải RAM riêng của model hay số VRAM riêng.

## Kiểm tra đã hoàn tất

- Build APK/debug instrumentation: thành công.
- 10 unit tests: không lỗi.
- VieneuInstrumentedTest, SpeakerRuntimeInstrumentedTest, TtsPlaybackTest: 3 bài qua trên APK cuối; kiểm tra chuẩn hóa/BPE, audio hữu hạn, full/stream, hủy tạo, tạo lại, đăng ký giọng cùng runtime TTS, AudioTrack phát sớm và Dừng.
- WhisperBenchmarkTest: small CPU/GPU, large FP16 GPU, large Q5 CPU foreground, large Q5 GPU foreground + TTS/speaker gate đều hoàn tất.

Dữ liệu JSON gốc ở `android-app/.work/benchmark-results/`. Model và checksum ở `android-app/.work/phowhisper-large/ggml-manifest.json`. Hướng dẫn build/dùng thử: [ANDROID_LARGE_VULKAN_STREAM_VI.md](ANDROID_LARGE_VULKAN_STREAM_VI.md).

Vulkan và lượng tử hóa là các tính năng upstream của [whisper.cpp](https://github.com/ggml-org/whisper.cpp); model được lấy từ [VinAI PhoWhisper-large](https://huggingface.co/vinai/PhoWhisper-large). Các thời gian và RAM ở đây là đo trên thiết bị, không phải thông số upstream.
