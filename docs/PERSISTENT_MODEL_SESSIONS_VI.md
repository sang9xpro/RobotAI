# Phiên mô hình liên tục theo tài khoản

Bản nâng cấp áp dụng cho provider `codex-chatgpt` trong gateway hội thoại RobotAI. Mỗi WebSocket đã xác thực có một UUID gateway riêng, một tiến trình Codex riêng và một thread riêng. Cùng một ChatModel được cache theo cấu hình không chứa trạng thái hội thoại; Persona nhận bản model được bind riêng. Định danh chủ sở hữu lấy từ thiết bị/tài khoản đã được backend xác thực.

## Vòng đời

1. App đăng nhập, tải xong cấu hình nhân vật rồi tự mở WebSocket.
2. Backend gửi `session/state=preparing`, khởi tạo thread và chạy một lượt nội bộ ngắn để làm nóng kết nối/cache. Lượt nội bộ không lưu thành tin nhắn của người dùng.
3. Chỉ sau khi làm nóng thành công, backend trả `hello` với `ready=true`. Lỗi hoặc tài khoản chưa chọn nhân vật trả `session/state=failed` rồi đóng socket.
4. Các lượt sau giữ tiến trình/thread và chỉ bổ sung tin nhắn mới. Backend vẫn giữ toàn bộ lịch sử làm nguồn chuẩn; khi tóm tắt, sửa/xóa bộ nhớ, đổi người nói hoặc cắt câu trả lời do ngắt lời, gateway đồng bộ lại thread để tránh lặp hoặc giữ ngữ cảnh đã bị loại bỏ.
5. Khi ngắt kết nối, backend dừng tổng hợp/phát âm thanh, thu hồi lượt đang chạy, chờ tạo và lưu tóm tắt, rồi mới xóa phiên gateway và giải phóng bộ nhớ. Cleanup chỉ thực hiện một lần cho mỗi Persona.

Chuyển màn hình và chế độ ngủ local dừng truyền/nhận âm thanh của hội thoại nhưng giữ kết nối trong foreground. Đăng xuất, nút ngắt kết nối, mất socket hoặc app vào background kết thúc phiên. Micro vẫn chỉ hoạt động theo các điều kiện foreground, màn hội thoại, quyền micro và quyền sở hữu của thu mẫu giọng. Cần APK mới để có tự kết nối sau đăng nhập và hiểu trạng thái chuẩn bị/sẵn sàng.

## Giới hạn và thu hồi

| Thiết lập | Mặc định |
|---|---:|
| `KEN_LLM_MAX_SESSIONS` | 16 phiên cư trú |
| `KEN_LLM_MAX_ACTIVE` | 4 lượt/làm nóng đồng thời |
| `KEN_LLM_TIMEOUT` | 90 giây mỗi lượt |
| `KEN_LLM_SESSION_LEASE` | 120 giây |
| Heartbeat Java → gateway | 30 giây |
| `conversation.summary-close-timeout-seconds` | 60 giây |

Quá tải trả 429 thay vì mở thêm tiến trình không giới hạn. Mỗi phiên chỉ chạy một lượt tại một thời điểm. User A không lấy được phiên của User B bằng định danh từ frontend; UUID gateway chỉ được gửi bởi backend có token nội bộ. Phiên app đã xác thực không bị đóng bởi timeout im lặng của nhân vật. Thiết bị legacy giữ cơ chế timeout hiện có.

Tắt app/socket thông thường tạo tóm tắt trước khi đóng mô hình. Nếu tạo tóm tắt hoặc lưu DB thất bại/quá thời gian, backend ghi cảnh báo và vẫn đóng phiên để tránh giữ tài nguyên vô hạn; lịch sử tin nhắn đã lưu riêng vẫn dùng được để phục hồi. Backend tắt mềm chờ các tác vụ cleanup. Nếu tiến trình Java chết đột ngột, lease gateway thu hồi kết nối mất heartbeat; không thể bảo đảm tạo tóm tắt tại thời điểm crash, nhưng các tin nhắn đã ghi vào DB còn nguyên.

Gateway chỉ giảm ngân sách skill bằng cấu hình riêng của tiến trình (`skills.max_context_tokens=1`), không thay đổi skill của ứng dụng Codex. Bộ ước lượng context Codex dùng 512 token dự trù thay vì 4.000 token cho công cụ không được gửi, để hội thoại ngắn với context 4.096 không bị tóm tắt sau mỗi lượt. Model và Fast mode giữ lựa chọn hiện tại. Làm nóng và tóm tắt đều sử dụng quota mô hình.

## Mức suy luận theo cấu hình model

Trong trang `/config/model`, provider `codex-chatgpt` có mục **Mức suy luận**. Danh sách lấy từ `model/list` của Codex qua gateway, theo model đang chọn; `codex-default` dùng khả năng của model mặc định. Chỉ chọn được các mức model hỗ trợ. Mức thấp thường giảm thời gian xử lý và quota so với mức cao; không có cam kết thời gian cố định. Không tự bật Fast mode.

Giá trị `reasoningEffort` được lưu trong `sys_config`, trả về qua API cấu hình và gửi thành `reasoning_effort` cho cả mở/làm nóng phiên lẫn các lượt chat, kiểm tra văn bản và Vision. Cấu hình cũ chưa có giá trị giữ cách ánh xạ `enableThinking=false → low`, `true → medium`. Sau khi lưu, chọn cấu hình cho nhân vật và kết nối lại; phiên đang chạy giữ mức lúc mở phiên. Gateway từ chối mức không thuộc danh sách hỗ trợ của model.

Khảo sát catalog ngày 13/10/2026: cả bảy model có `low`, `medium`, `high`, `xhigh`, `max`; các model Sol, Astra và Terra có thêm `ultra`. Catalog hiện không cung cấp `none` hoặc `minimal`, nên giao diện không hiển thị các mức này. Danh sách cập nhật khi tải lại catalog.

## Kiểm chứng 12/10/2026

- 18 test Python: xác thực, HTTP/SSE, hai chủ sở hữu, định danh trùng, lock một lượt/phiên, hủy, lease, giới hạn và giải phóng tài nguyên; history chỉ bổ sung phần mới.
- Java: các test adapter, handshake readiness, thứ tự lưu trước khi đóng, tóm tắt khi còn câu hỏi chưa trả lời, timeout/failure, compaction đang chạy và giao thức reconnect đều đạt. Test gateway thật opt-in riêng của Maven không bật; có khảo sát thật bên dưới.
- 11 test web đạt; web build/type-check thành công. Frontend chỉ báo kết nối sau `hello/ready`, không báo theo sự kiện mở WebSocket.
- APK Android debug và APK test biên dịch thành công. Chưa cài APK mới/chạy test trên điện thoại thật trong lượt nâng cấp này.
- Gateway thật: hai tiến trình/thread độc lập; làm nóng 4.700/7.333 ms. Ba lượt có TTFT 2.001/2.413/2.153 ms, tổng 2.043/2.611/2.644 ms. Lượt tiếp tục giữ đúng thread và nhớ được dữ kiện trước đó; đóng phiên kết thúc tiến trình.
- Backend thật: hai tài khoản thử nghiệm đăng nhập, nhận `preparing → ready` (5.668/7.818 ms), lưu phản hồi mô hình (3.099/3.383 ms từ lúc gửi câu hỏi), sau ngắt lưu tóm tắt vào MySQL và thu hồi hai phiên. Các số đo mẫu không phải cam kết latency.
- Edge TTS có timeout trong kiểm thử. Vòng đời mô hình và lưu tóm tắt đã được xác minh độc lập với phát giọng; TTFT ở trên chưa bao gồm TTS/phát âm thanh.

Chi tiết API: [gateway README](../services/llm-gateway/README.md). Các bản đo và script kiểm chứng chỉ nằm trong `.local`, không chứa trong source được commit.
