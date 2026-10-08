# Android Voice Test: PhoWhisper-large, Vulkan, STT/TTS theo phần

Bản 1.3-large-vulkan-stream (versionCode 4), ARM64, Android 9/API 28 trở lên (backend ggml yêu cầu Vulkan 1.2 trở lên; minSdk 28 để liên kết các hàm Vulkan 1.1 của Android). Native code vẫn tối ưu Release trong APK debug.

## Dùng thử

1. Build/cài `android-app/whisper-test/app/build/outputs/apk/debug/app-debug.apk`.
2. Chọn `phowhisper-large-q5_0` hoặc `phowhisper-large-f16`. Hai file đã được chép vào vùng riêng của app trên điện thoại CPH2651 dùng để đo. Cài đè APK giữ các file này; gỡ app sẽ xóa chúng.
3. Máy khác: nhấn **Nhập file PhoWhisper-large GGML**, chọn file tương ứng. Model large được nhập riêng, không đóng vào APK.
4. Bật **Dùng GPU Vulkan** để thử GPU. Đổi công tắc sẽ nạp lại model. Nếu GPU không khả dụng, app báo lỗi để bạn chọn CPU.
5. Có thể tắt lọc giọng khi đo STT thuần. Nếu bật lọc giọng, đăng ký đủ ba mẫu; các đoạn quá ngắn không được chấp nhận để xác minh người nói.
6. Bật **Nhận dạng trong lúc nói** để nhận kết quả dần; tắt để đo cả WAV như bản cũ. App hiển thị số đoạn đang xử lý/chờ và thời gian còn chờ sau khi nhấn Dừng.
7. TTS dùng giọng Hải Đăng đã chọn, phát qua AudioTrack ngay khi có phần audio đầu. Có thể Dừng, Phát lại, Lưu WAV sau khi tạo thành công.

## Giới hạn quan trọng

STT theo phần chưa phải Whisper streaming thực sự. Bộ chia đoạn thử nghiệm dùng năng lượng âm thanh, giữ 200 ms trước lời nói, chờ khoảng 600 ms im lặng, ưu tiên đoạn ít nhất 3 giây và giới hạn khoảng 12 giây. Ghi tối đa 30 giây. Tiếng nói nhỏ/tiếng ồn có thể gây chia sai. Chia nhỏ làm mất một phần ngữ cảnh; Whisper còn có chi phí encoder đáng kể cho mỗi đoạn, nên có thể chậm hơn nhận dạng cả WAV. Không hứa độ trễ thời gian thực khi RTF > 1.

Bộ lọc giọng chỉ quyết định có nhận một lượt nói hay không; không tách được hai người nói chồng nhau. Audio gốc của mỗi đoạn được giữ cho ASR.

TTS giải mã codec theo phần 4 frame (~320 ms), giữ toàn bộ trạng thái attention giữa các phần. Hàng đợi phát có giới hạn và tạo áp lực ngược lên bộ tạo audio; thống kê `tạo` trong UI vì vậy có thể bao gồm thời gian chờ phát. Audio đầu là lúc có PCM; bắt đầu phát là lúc gọi AudioTrack.play/write, không phải phép đo âm thanh đến tai người nghe. `RAM` là PSS toàn tiến trình, không phải chỉ riêng model. Khi tốc độ tạo chậm hơn phát có thể có khoảng ngắt.

## Chuẩn bị trên máy build

- NDK 25.2.9519653, SDK CMake 3.22.1, JDK 17, các runtime AAR đã chuẩn bị theo tài liệu TTS. Hai phiên bản ONNX Runtime vẫn dùng tên thư viện tách biệt để tránh crash đăng ký giọng.
- `python android-app/tts-test/setup_vulkan.py` chuẩn bị glslc/CMake trong `.work/vulkan-host`, Vulkan-Headers tag `vulkan-sdk-1.4.328.1`, SPIRV-Headers commit `cb42dec3830d3ac67fa449ecdc0c0f73d5e74498`. Cần Conda trong PATH hoặc biến CONDA_EXE trỏ tới Conda. Script dành cho macOS ARM64.
- `python android-app/tts-test/download_pho_large.py` tải checkpoint chính thức `vinai/PhoWhisper-large`, pin revision `b9136a44b5f2ca664bd0b8f74baecf1715f6eeeb`, kiểm SHA-256 `9e0077352544f497673395b313a9ef6d7cd2967598197c4d691d8042333060b2`.
- `convert_pho_mmap.py` là converter của whisper.cpp đã đổi sang torch.load mmap, tránh dựng thêm một bản model FP32 trong RAM. Cần môi trường Torch/Numpy và repo openai/whisper có mel_filters.npz:

```sh
android-app/.work/pho-env/bin/python android-app/tts-test/convert_pho_mmap.py \
  android-app/.work/phowhisper-large android-app/.work/openai-whisper android-app/.work/phowhisper-large
android-app/.work/whisper-analysis-build/bin/whisper-quantize \
  android-app/.work/phowhisper-large/ggml-model.bin \
  android-app/.work/phowhisper-large/ggml-phowhisper-large-q5_0.bin q5_0
```

FP16: `android-app/.work/phowhisper-large/ggml-model.bin` (3.094.623.708 byte, ~2.9 GiB). Q5: `android-app/.work/phowhisper-large/ggml-phowhisper-large-q5_0.bin` (1.080.732.108 byte, ~1.0 GiB). Q5 vẫn là model large, chỉ lượng tử hóa trọng số; cần so sánh chất lượng với FP16.

Tài liệu upstream: https://github.com/ggml-org/whisper.cpp ; model: https://huggingface.co/vinai/PhoWhisper-large .

## Kiểm tra

- 10 unit tests, gồm âm thanh WAV, phép tính giọng và chia đoạn không mất/lặp mẫu.
- Instrumentation: VieneuInstrumentedTest so sánh full/stream codec, kiểm tra ra audio trước khi hoàn tất và hủy giữa chừng; SpeakerRuntimeInstrumentedTest kiểm đăng ký giọng cùng runtime TTS.
- TtsPlaybackTest kiểm AudioTrack thực sự phát trong lúc model tiếp tục tạo và thao tác Dừng.
- WhisperBenchmarkTest có tùy chọn `-e tts true` để kiểm large GPU, TTS và xác minh giọng cùng giữ trong tiến trình.
- WhisperBenchmarkTest dùng WAV của người dùng, cùng cấu hình greedy/tiếng Việt/no timestamps, hai lượt dự kiến trên mỗi backend (đã đo lại CPU large ở foreground sau khi hệ thống dừng bài test chạy nền; xem báo cáo); JSON lưu trong cache app. Đây là đo trên một WAV/một điện thoại, chưa phải đánh giá WER diện rộng hay phép đo nhiệt độ được kiểm soát.

Bảng kết quả thực tế được lưu riêng trong ANDROID_VULKAN_BENCHMARK_VI.md.
