# Báo cáo kiểm thử tính năng RobotAI — 04/10/2026

## Kết luận

Chưa đạt toàn bộ: tìm thấy **2 lỗi tái hiện được** làm bộ kiểm thử thất bại. Các test Android và chuỗi provider thật đã qua, nhưng chưa nghiệm thu microphone Android → WebSocket Java → STT → LLM → TTS → loa Android với tài khoản thật.

Không chỉnh source chức năng trong lần kiểm thử này. Log ở `.local/full-audit/` (ignored). Chromium phục vụ Playwright đã được cài vào cache người dùng; lỗi thiếu runtime ban đầu đã được xử lý.

## Kết quả chạy mới

| Phạm vi | Kết quả |
|---|---|
| Web Vitest | 68 file, 537 test PASS |
| Web build và vue-tsc | PASS; cảnh báo bundle >500 kB |
| Web oxlint và ESLint | PASS |
| Web Playwright Chromium | 10 PASS, 1 FAIL — trùng DOM id app |
| Backend Maven toàn reactor, JDK 21.0.6 | 1.242 test: 1.235 PASS, 1 FAIL, 6 SKIP |
| Gateway Python unittest | 10 PASS |
| Mock voice protocol | 4 PASS; gồm 30 lượt Opus, abort/audio cũ, frame sai |
| Android build APK và test APK | PASS; 71 task thực thi với --rerun-tasks |
| Android unit test | 8 domain + 1 app PASS, chạy mới |
| Android Emulator LoginFlowTest | 4 PASS, 13,961 giây |
| Android Emulator RobotSmokeTest | 1 PASS, 11,724 giây |
| Java provider thật STT → LLM → TTS | 1 PASS, tổng 9.010 ms |

Backend từng module: common 82, service 293, AI 305, dialogue 290, server 272. Lần đầu Maven dùng JDK mặc định 25; đã chạy lại toàn bộ với JDK 21 để xác nhận lỗi không do JDK khác phiên bản.

Android login dùng fixture riêng: login sai/đúng, restore Keystore, token hết hạn, mất mạng, logout/đổi account và WebSocket auth header. Audio smoke dùng backend mock: thu mic, Opus, phát fixture, ngắt lời, ngủ/thức/reconnect và lượt tiếp theo. Hai nhóm này không xác nhận inference backend thật.

## Issue 1 — Backend vi phạm hợp đồng response, bộ test không xanh

Mức độ: trung bình; chặn bộ kiểm tra chất lượng backend.

- Vị trí: `server-java/xiaozhi-server/src/main/java/com/xiaozhi/user/UserController.java:67` và `:74`.
- `robotProfile()` và `selectRobotRole()` trả `ApiResponse<RobotProfileService.Profile>` trực tiếp.
- Test `ServiceLayerArchTest.controllerReturnTypesAreRespOnly` yêu cầu controller chỉ trả DTO Resp và các kiểu được cho phép; `RobotProfileService.Profile` không đạt quy tắc này.
- Tái hiện bằng `JAVA_HOME=<JDK21> mvn -o -s scripts/robotai-maven-settings.xml test` tại `server-java`.
- Kết quả: 2 architecture violations, 1 test FAIL, Maven BUILD FAILURE. Không có bằng chứng từ test này rằng endpoint bị lỗi runtime hoặc lộ credential.
- Hướng sửa: định nghĩa response DTO ở tầng web theo quy ước dự án, chuyển Profile/RoleView sang DTO trước khi trả về; giữ kiểm tra kiến trúc.
- Bằng chứng: `.local/full-audit/java-jdk21-test.log`, `server-java/xiaozhi-server/target/surefire-reports/com.xiaozhi.architecture.ServiceLayerArchTest.txt`.

## Issue 2 — Web trùng id app

Mức độ: thấp; HTML không bảo đảm ID duy nhất và chặn một test E2E.

- Vị trí: `server-java/web/index.html:12` và `server-java/web/src/App.vue:25` cùng khai báo `<div id="app">`.
- Tái hiện: `npx playwright test --reporter=list` tại `server-java/web` sau khi cài Chromium.
- Test `e2e/navigation.spec.ts:33` thất bại: selector `#app` khớp 2 phần tử, Playwright báo strict mode violation.
- Hướng sửa: giữ ID mount trong index.html, đổi wrapper App.vue sang class và cập nhật style tương ứng. Không dùng `.first()` để che lỗi DOM.
- Bằng chứng: `.local/full-audit/web-e2e-final.log`; screenshot trong `server-java/web/test-results/`.

## Giới hạn nghiệm thu và chất lượng voice

Đọc số lượng bản ghi của schema local: 1 user, 1 device, **0 role**. Đây là điểm chặn cấu hình, chưa được phân loại là lỗi source. Không có credential tài khoản thật được cung cấp để chạy luồng xác thực production. Cần role thuộc user, gắn provider và đăng nhập app để kiểm hội thoại thật, đổi role/context giữa phiên, ngắt lời và reconnect qua Java. Không tự thay đổi account hoặc cấu hình provider của user.

Provider thật dùng WAV 16 kHz đã có → PhoWhisper-small → gateway Codex → Edge TTS. STT 768 ms, token LLM đầu 5.881 ms, LLM hoàn tất 6.682 ms, TTS 1.439 ms, tổng 9.010 ms. MP3 41.328 byte, mono 24 kHz, dài 6,888 giây theo ffprobe. Kết quả JSON/audio ở `.local/full-voice-test/`; log `.local/full-audit/live-voice.log`. Lần live này gọi inference thật và tiêu thụ một lượt Codex.

STT vẫn nhận sai cụm “bộ nhớ đệm” thành “bộ nhớ điện”; TTS test mặc định là `zh-CN-XiaoyiNeural`, chưa đánh giá phát âm tiếng Việt. Đây là quan sát chất lượng trên một mẫu, không phải WER hay p50/p95 và không phải latency mic → loa. VieNeu backend/partial STT/P2 chưa được nghiệm thu ở đây.

6 test Java mặc định bị skip: 2 migration guard thiếu database test trống riêng; 1 image publishing guard; 3 live test cần opt-in. Trong đó LiveVoicePipelineTest đã chạy riêng và PASS. Chưa chạy migration trên database trống, kiểm image publishing hay từng provider bên ngoài chưa có cấu hình. Không dùng database người dùng để chạy migration test có DROP TABLE.

Web E2E hiện chỉ có 11 test cho login công khai, validation và navigation. Chưa bao phủ E2E CRUD thiết bị/role/agent/config, phân quyền, chat, upload và memory bằng tài khoản thật; các phần này có unit/controller test nhưng không đồng nghĩa đã nghiệm thu toàn luồng UI → DB.

## Lệnh chính và log

- Web: `npm run test:run`, `npm run build`, `npm run lint`, `npx playwright test --reporter=list`.
- Backend: `mvn -o -s scripts/robotai-maven-settings.xml test` với JAVA_HOME JDK 21.
- Android: JDK 17, `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :domain:test --rerun-tasks`.
- Emulator: `python3 tools/android-emulator/local.py install`, `test-login`, `test-mock`; chỉ AVD RobotAI_API36_1.
- Gateway: `PYTHONPATH=services/llm-gateway python3 -m unittest discover -s services/llm-gateway/tests -v`.
- Protocol: `python3 -m unittest discover -s tools/mock-voice -v`.
- Live: lệnh trong `docs/FULL_VOICE_TEST_VI.md`, bật `KEN_VOICE_LIVE=1`.
