# Test hội thoại rảnh tay — 06/10/2026

## Kết luận

**Chưa đạt toàn bộ bài test 2 lượt.** Đã hoàn tất 1 lượt thật từ đánh thức bằng
nút → lắng nghe → stream audio → tự kết thúc câu → STT → LLM → phát phản hồi →
tự nghe tiếp. Lượt thứ hai nhận STT và sinh câu trả lời nhưng Edge TTS timeout.
Chưa kiểm chứng đánh thức bằng giọng nói “Ken ơi”.

## Môi trường và cách chạy

- Điện thoại OPPO CPH2651, ADB `WCFE6XCI9PYLDAGM`.
- Micro điện thoại thu mẫu WAV tiếng Việt dài 4,8 giây phát từ loa Mac.
- Backend Java local, PhoWhisper small, Codex gateway và Edge TTS thật.
- Instrumentation `HandsFreeAcceptanceTest`, không gọi `listen()` / `finish()`.
- Test dùng tài khoản nghiệm thu local; khôi phục session lưu trước đó sau test.

```bash
python3 tools/acceptance/run_phone.py --serial WCFE6XCI9PYLDAGM \
  --test handsfree --turns 2 --audio-source mac-speaker
```

## Kết quả

| Kiểm tra | Kết quả |
| --- | --- |
| Unit test domain và Opus | 22 tests, không có failure; domain được chạy lại |
| Build app và APK instrumentation | Đạt |
| Ngủ → đánh thức bằng nút → tự lắng nghe | Đạt |
| Audio trước mẫu trong 2 giây chờ, lần chạy cuối | 0 frame |
| Audio stream trong lúc lắng nghe | Đạt; lượt hoàn chỉnh upload 108 frame |
| Tự kết thúc câu, không bấm kết thúc | Đạt; backend nhận `listen.stop` |
| STT tiếng Việt | Có bản chép lời; chất lượng còn hạn chế |
| Lượt 1 phát phản hồi và tự nghe tiếp | Đạt; download 92 frame |
| Lượt 2 phát phản hồi | Lỗi Edge TTS timeout |
| Đánh thức bằng giọng nói thật | Chưa test |

Bản chép lời nhận được: “andron còn dành cho bộ nhớ đến tính toán.”
Lượt 1 trả lời: “KENA, bạn đang nói về bộ nhớ và khả năng tính toán của Android à?”
Độ trễ từ tự kết thúc thu đến AudioTrack trình bày mẫu không im lặng đầu tiên:
19.163 ms (19,163 giây), chỉ một mẫu, không phải p50/p95 hoặc đo âm học độc lập.

Lượt 2 backend vẫn sinh text “KENA, bạn muốn hỏi về bộ nhớ hay khả năng tính toán
của Android?” nhưng log lúc 16:24:01 ghi `Edge TTS timed out`. Client báo
“Backend không tạo được tiếng trả lời; hãy thử lại”. Instrumentation thất bại
với 1/2 lượt hoàn tất.

## Các lỗi ghi nhận trước lần chạy cuối

Docker ban đầu không chạy; đã mở Docker và phục hồi container MySQL/Redis.
LLM gateway lúc đầu trả 503; sau restart đã đọc được 7 model và sinh phản hồi.
Hai lần thử trước có 29 và 30 frame được gửi trước khi phát mẫu. Chưa xác định
đó là âm thanh môi trường, âm thanh khởi động bộ nhận dạng hay phát hiện nhầm;
không dùng lần chạy cuối có 0 frame để phủ nhận quan sát này.

## Giới hạn so với yêu cầu STT streaming

App stream Opus về backend trong lúc nghe. Tuy nhiên `PhoWhisperSttService`
gom PCM của cả câu và chỉ gọi inference sau khi audio stream kết thúc.
Chưa có STT partial theo thời gian thực trong lúc người dùng nói.
Ngưỡng tự kết thúc cục bộ là 20 frame yên lặng × 60 ms = 1,2 giây; unit test
kiểm tra ngưỡng này, khoảng nghỉ trong câu, tiếng động ngắn và giới hạn 30 giây.
Chưa đo trực tiếp sai số endpoint 1,2 giây trên thiết bị trong bài nghiệm thu này.

## Dữ liệu gốc

- `.local/acceptance/WCFE6XCI9PYLDAGM-handsfree-result.json`
- `.local/acceptance/WCFE6XCI9PYLDAGM-handsfree-instrumentation.log`
- `.local/acceptance/handsfree-first-attempt.json`
- `.local/acceptance/handsfree-second-attempt.json`
- `.local/xiaozhi-dialogue.log`

Các file `.local` không commit. Thay đổi lần này bổ sung bài test, lựa chọn
`--test handsfree` và báo cáo; không sửa thuật toán micro hay provider STT/TTS.
