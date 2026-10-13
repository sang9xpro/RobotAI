# RobotAI Admin — báo cáo triển khai MVP

Cập nhật 12/10/2026. Đã chỉnh trực tiếp `server-java/web` từ Xiaozhi và tích hợp API Java, giữ stack, đăng nhập, phân quyền, CRUD và credit upstream. Mã nguồn P0–P3 đã triển khai và qua kiểm thử tự động. Backend và web bản mới đã khởi chạy local theo yêu cầu kiểm tra của người dùng; Flyway V90 và V91 đã áp dụng thành công trên schema `robotai_ken`. P4 người quen và P5 thiết lập app/phần cứng vẫn là giai đoạn sau MVP trong kế hoạch.

Phiên chạy kiểm tra: web `http://localhost:8084` trả HTTP200; backend 8091, dialogue 8092; MySQL/Redis sẵn sàng; PhoWhisper-small nạp model thành công; gateway 8787 còn đăng nhập và đọc được danh sách model (không inference). App `0.3-individual-conversation` đã mở trên OPPO CPH2651 qua USB, giữ dữ liệu/đăng nhập hiện có, reverse 8091/8092 về Mac. Chưa coi việc khởi chạy này là nghiệm thu hội thoại cloud/native đầy đủ.

Sau khi bấm Làm mới, app báo tài khoản hiện tại chưa có cấu hình robot (role); nút Kết nối bị vô hiệu hóa. Cần tạo role trên web quản trị cho tài khoản này rồi Làm mới/chọn cấu hình trong app trước khi thử hội thoại.

## Chức năng đã bổ sung

| Phần | Kết quả |
|---|---|
| Giao diện | RobotAI, favicon mới, màu bạc hà, tiếng Việt mặc định, giữ tiếng Anh/Trung và dark/light. Locale có kiểm tra đủ key và placeholder. |
| Nhân vật Android | `/robot/profile`: xem thiết bị ảo của chính tài khoản và chọn nhân vật qua API owner hiện có; thêm thao tác Dùng trên Android tại danh sách nhân vật. |
| STT/LLM/TTS | Giữ form/provider hiện có; chọn độc lập từng cấu hình cho nhân vật. Thêm STT/TTS API tương thích OpenAI và base URL tùy chỉnh cho OpenAI LLM. |
| Kiểm tra AI | Dùng bản nháp chưa lưu. STT upload WAV và xem transcript; LLM nhập câu hỏi, xem câu trả lời/thời gian; TTS nhập Voice ID/câu thử, nghe URL ký qua hệ thống lưu trữ hiện có. Có thông báo gửi dữ liệu/tính phí trước thao tác. |
| Cache | STT/TTS/LLM nhận sự kiện thay đổi cấu hình tại HTTP; dialogue giữ broadcast Redis hiện có. Loại cache theo config ID kể cả khi đổi provider. LLM không đóng model đang được phiên cũ giữ khi loại cache. |
| Tóm tắt ký ức | Sửa tóm tắt thiết bị ảo thuộc tài khoản hiện tại. Giữ ID dạng chuỗi, API owner-only và danh sách/xóa hiện có. Kết quả lưu cũ không đóng nhầm cửa sổ sửa mới. |
| Nhạc | `/music`: upload MP3, sửa tên/ca sĩ, thêm link, xóa file/catalog, nghe thử có token; YouTube mở link. Có bảo vệ bản nháp, kiểm revision, khóa file và ghi catalog nguyên tử. Dialogue đọc chung định dạng catalog; tương thích catalog URL cũ. |
| Trạng thái | `/system/status`: backend, DB, worker STT, gateway LLM; cache 10 giây, kiểm tra tối đa 2 giây mỗi dịch vụ, không inference. Android/motor chưa có telemetry được hiển thị Chưa xác định/Mô phỏng. |
| Quyền/API | Migration mới V90 cấp quyền nhạc/trạng thái cho admin; giữ owner của profile/ký ức/config. Contract bổ sung gồm 7 đường dẫn trong `server-java/contracts/robotai-admin.openapi.yaml`. |
| Dịch phân quyền | 56 nhãn quyền và tên/mô tả hai vai trò mặc định được dịch theo mã ổn định; giao diện, tìm kiếm vai trò và danh sách/xuất người dùng theo ngôn ngữ đang chọn. V91 chuẩn hóa nhãn có sẵn trong DB sang tiếng Anh, giữ nhãn tùy chỉnh và các quyền đã cấp. |

## Dùng cấu hình cloud/local

1. Vào Cấu hình nhận dạng giọng nói, Tổng hợp giọng nói hoặc Mô hình; tạo/sửa cấu hình.
2. Với STT/TTS tương thích OpenAI, chọn **OpenAI compatible**, nhập **Model ID**, **API Base URL** và API key nếu dịch vụ yêu cầu. Base URL thường là `https://<host>/v1`; adapter ghép `/audio/transcriptions` hoặc `/audio/speech`. Dịch vụ phải thực sự hỗ trợ các endpoint và payload này.
3. Với LLM chọn OpenAI, nhập model, API key và base URL của API tương thích. Bỏ trống base URL dùng `https://api.openai.com/v1`. Các provider và gateway/local đã có được giữ lại.
4. Bấm Kiểm tra kết nối để thử bản nháp, rồi lưu cấu hình. Test không tự lưu credential/cấu hình.
5. Trong Nhân vật, chọn riêng STT, LLM, TTS và giọng; có thể nhập Voice ID riêng cho OpenAI-compatible TTS. Lưu nhân vật, chọn dùng trên Android, rồi kết nối lại/tải lại profile. Không tự fallback sang nhà cung cấp khác.

OpenAI-compatible STT hiện nhận PCM mono 16 kHz, signed 16-bit, tối đa 30 giây; test web dùng RIFF WAV cùng định dạng. Ngôn ngữ là tiếng Việt, kết quả là transcript cuối, chưa có partial streaming/hotwords của adapter này. TTS trả MP3, hỗ trợ Voice ID và tốc độ 0.25–4; không hỗ trợ cao độ hoặc voice cloning. Giọng/model tiếng Việt cụ thể phụ thuộc dịch vụ được cấu hình, chưa được xác nhận bằng live test.

Credential chỉ gửi tới backend và được backend dùng để gọi provider; danh sách config hiện có không trả key/secret. Để giữ key cũ khi sửa, để trống ô key. Adapter speech không theo redirect; OpenAI LLM dùng HTTP client không theo redirect. Có thể giới hạn host cho các adapter OpenAI trên **cả HTTP và dialogue** bằng biến môi trường `ROBOTAI_AI_ALLOWED_HOSTS=api.example.com,localhost` hoặc JVM `-Drobotai.ai.allowed-hosts=api.example.com,localhost`. Danh sách là hostname chính xác, không wildcard; bỏ trống cho phép host HTTP(S) do người có quyền cấu hình nhập. Đây không phải chính sách mạng cho mọi adapter legacy.

## Chạy bản mới

Web dùng Node đáp ứng `engines` hiện có (đã kiểm tra bằng Node 22.21.1). Trong `server-java/web`, chạy `npm run dev`, mở `http://localhost:8084`; Vite proxy HTTP sang 8091 và WebSocket sang 8092. `VITE_API_BASE_URL=/api`; service bổ sung dùng `/robot/...` hoặc `/user/...`, tránh lặp `/api/api`. `npm run build` tạo `web/dist` để dùng với nginx hiện có.

Java dùng JDK 21; cả `xiaozhi-server` và `xiaozhi-dialogue` đã được đóng gói tại `target/*-6.1.0.jar`. Khi triển khai phải dùng cả hai bản mới để cập nhật API và adapter/dialogue. Khởi động HTTP theo cấu hình môi trường hiện có sẽ áp dụng Flyway V90; không sửa lại V89. Sau thay đổi quyền cần đăng nhập lại/đồng bộ permission.

V90 bổ sung `system:robot:music:list`, `system:robot:music:update`, `system:robot:status`; chỉ gán sẵn cho auth-role admin. Người vận hành có thể gán thêm qua quản lý phân quyền hiện có. Quyền hệ thống không thay scope account của API RobotAI.

V91 chỉ cập nhật `sys_permission.name`, `sys_auth_role.authRoleName` và `sys_auth_role.description` khi mã và nội dung khớp nhãn mặc định. Không sửa V1 hay ID/mã quyền/quan hệ cấp quyền. Database local đã chuyển đủ 56 tên quyền cùng tên/mô tả hai vai trò mặc định sang tiếng Anh, gồm ba nhãn RobotAI trước đó bằng tiếng Việt. Kiểm tra trước/sau xác nhận ID, mã, đường dẫn, timestamp và 66 quan hệ cấp quyền không thay đổi. Snapshot metadata trước V91: `.local/backups/access-control-before-v91.json` (quyền file 0600). Danh sách nhãn giao diện nằm tại `web/src/locales/accessControl.ts`; quyền/vai trò chưa biết và nhãn tùy chỉnh dùng nội dung trả về từ API.

Health URL để trống thì worker/gateway trả Chưa xác định. Cấu hình tại môi trường server bằng các property:

```yaml
robotai:
  admin:
    stt-health-url: http://127.0.0.1:PORT/health
    gateway-health-url: http://127.0.0.1:PORT/health
```

Thay PORT/đường dẫn bằng endpoint health thật của triển khai. URL cố định, không có userinfo/query/fragment; không dùng endpoint inference. Trạng thái HTTP 2xx chỉ xác nhận endpoint trả lời. `checkedTime` là ISO timestamp; `latencyMs=null` nghĩa chưa đo, không phải 0 ms.

Nhạc dùng `RuntimePathConfig.resolveMusicDir()` của triển khai hiện có, gồm MP3 và `catalog.json`. MP3 upload tối đa 30 MB, tên lưu sinh mới; catalog tối đa 1 MB/2000 mục. Hai nguồn fileName/url loại trừ nhau. Revision thay đổi khi catalog hoặc inventory file thay đổi; save/delete stale trả HTTP409 và giữ dữ liệu. Web preview phát tại trình duyệt, chưa có transport phát từ web lên điện thoại.

## Bằng chứng kiểm thử

Bản cập nhật dịch phân quyền: web build/type-check và lint PASS; Vitest coverage PASS **71 file / 561 test**, helper dịch và catalog đạt 100% coverage; hai Playwright Chromium bổ sung PASS (API fixture tiếng Trung/Anh, tìm kiếm theo tên đã dịch, đổi locale trong phiên và lưu đúng ID quyền). Maven package với migration V91 PASS (bỏ chạy lại Java unit test vì chỉ thêm SQL). Migration được chạy hai lần trên bản sao MySQL riêng: nhãn tùy chỉnh giữ nguyên, ID/quyền giữ nguyên, lần hai không thay đổi dữ liệu. Flyway V91 đã áp dụng thành công trên database local đang chạy. Log mới: `/tmp/robotai-acl-build.log`, `/tmp/robotai-acl-lint.log`, `/tmp/robotai-acl-tests.log`, `/tmp/robotai-acl-e2e.log`, `/tmp/robotai-acl-package.log`; báo cáo Chromium `/tmp/robotai-acl-playwright-report/index.html`. Coverage web toàn bộ hiện tại: statements 52.25%, branches 40.84%, functions 44.34%, lines 52.89%, qua threshold repository.

Baseline web trước chỉnh sửa MVP: 68 file, 537 test qua; build qua. Bộ kiểm tra MVP trước bản cập nhật dịch phân quyền:

| Kiểm tra | Kết quả |
|---|---|
| Web build (vue-tsc + Vite) | PASS |
| Web lint (oxlint + ESLint) | PASS, không lỗi/cảnh báo lint |
| Vitest với coverage | PASS, 70 file / 556 test |
| Playwright Chromium | PASS, 18 hành trình; gồm profile, phân quyền, nhạc, trạng thái và STT/TTS cloud bản nháp; dùng API fixture |
| Maven package toàn reactor | PASS, 1321 test được phát hiện, 1315 qua / 6 bỏ qua, không failure/error |
| Review theo ECC | APPROVE, không còn finding HIGH/MEDIUM |
| OpenAPI YAML + git diff whitespace | PASS |

Java wire tests dùng HTTP localhost để xác nhận multipart WAV, model/key/Voice ID/tốc độ, HTTP lỗi và định dạng MP3. Kiểm thử còn xác nhận key cũ/shared owner, DTO, WAV lỗi không gọi provider, HTTP409 qua global advice, catalog được dialogue đọc, cache đổi provider và health body bị treo được hủy đúng hạn. Unit/UI regression bảo vệ request cũ, ID lớn và bản nháp. Không gọi inference bên thứ ba trong bộ test này.

Coverage web toàn bộ mã nguồn: statements **52.16%**, branches **40.70%**, functions **44.22%**, lines **52.79%**, qua threshold hiện có của repository. Statements của ba màn mới: MusicLibrary **81.73%**, RobotProfile **90.47%**, SystemStatus **84%**. Chưa đạt 80% cho mọi branch/function mới và chưa đo coverage Java; không tuyên bố đạt 80% toàn dự án.

Sáu bài bỏ qua: live voice pipeline (chưa bật `KEN_VOICE_LIVE`), live Codex LLM (chưa bật `KEN_LLM_LIVE`), mẫu WAV PhoWhisper không có đường dẫn fixture, hai Flyway guard không có MySQL thử riêng (`XIAOZHI_MIGRATION_TEST_URL`), image publish guard vì repository không có workflow publish upstream. Migration V90 đã được kiểm tra schema/quy tắc quyền từ mã nguồn nhưng chưa chạy trên MySQL riêng. Không chạy migration guard vào database người dùng: V1 của guard có DROP TABLE.

Log của lượt xác nhận nằm tại `/tmp/robotai-java-package.log`, `/tmp/robotai-web-build.log`, `/tmp/robotai-web-lint.log`, `/tmp/robotai-web-coverage.log`, `/tmp/robotai-e2e-all.log`. Báo cáo Playwright tại `/tmp/robotai-playwright-report/index.html`. File trong `/tmp` chỉ là bằng chứng tạm thời.

## Nghiệm thu còn lại và giai đoạn sau

Trước phát hành: chạy V90/migration guard trên MySQL rỗng dành riêng cho test; dùng tài khoản thử để kiểm tra credential/model/voice thật, Android refresh/reconnect và nhạc MCP; kiểm tra mobile/dark/light/keyboard trên môi trường triển khai. Bản web/backend build không thay thế nghiệm thu microphone/Opus/loa Android. Các adapter legacy giữ cơ chế phiên/client riêng, cần kiểm tra đổi config trong phiên thật theo từng provider.

P4 chưa triển khai: đồng bộ metadata người quen từ Android và quản trị ký ức theo person UUID. P5 chưa triển khai: channel cấu hình có acknowledgement từ điện thoại, cử chỉ, chẩn đoán thiết bị và motor thật. Hai giai đoạn này có phụ thuộc Android/transport riêng như kế hoạch; MVP không cung cấp nút điều khiển giả cho chúng.

ECC dùng bản đã cài tại `server-java/.agents`: workflow `feature-dev`, planner, `vue-patterns`, `contract-first`, `api-design`, `verification-loop`, `e2e-testing`; có review lại sau khi sửa finding. Giữ nguyên thay đổi Android/backend có sẵn, không tạo commit. Dịch vụ local đã khởi chạy bằng JAR mới trong lượt kiểm tra tiếp theo theo yêu cầu người dùng.
