# Ken Animation v1 — bản duyệt

Ngày: 09/10/2026. Trạng thái: thiết kế đã được duyệt; đã chuyển sang Compose trong app.
Xem [triển khai Android](KEN_ANIMATION_ANDROID_VI.md).

Mở [bản xem trước tương tác](KEN_ANIMATION_PREVIEW.html) trực tiếp trong trình duyệt,
hoặc qua máy chủ local. File HTML chứa toàn bộ bộ dựng hình và chuyển động,
không tải font, thư viện hoặc tài nguyên từ Internet.

## Phạm vi bản duyệt

- 8 trạng thái tương ứng đầy đủ với `Phase` hiện có.
- 5 sắc thái tương ứng với `Mood` hiện có.
- 12 phản ứng tương ứng với `SocialGesture`, cùng một phản ứng khi chạm mặt.
- Nhịp nhạc, hướng nhìn, độ tinh nghịch, giảm chuyển động.
- Một bộ dựng hình và một đồng hồ dùng chung cho màn ngang và màn dọc.
- Ba chuỗi diễn thử; có tạm dừng, diễn lại, tốc độ 0,5×/1×/1,5× và toàn màn hình.

Bản duyệt HTML dùng Canvas 2D để xem nhanh và chỉnh art direction. Sau khi được
duyệt, bản native dùng `KenFace` Compose Canvas và `KenMotionEngine` chung;
các điểm gọi `RobotEyes`, `CompanionFace`, `DockFace` đã chuyển sang bộ mới.

## Ngôn ngữ hình ảnh

Ken có hai mắt bo mềm, cách nhau đủ rộng, một miệng nhỏ, ánh sáng bạc hà
và má hồng xuất hiện có chủ đích. Biểu cảm chủ yếu thay đổi hình dáng, độ mở,
bất đối xứng và hướng nhìn; không phụ thuộc hoàn toàn vào màu.

| Thành phần | Giá trị gốc |
| --- | --- |
| Nền mặt | `#050E13` |
| Mắt bình yên | `#79E9D5` |
| Mắt vui | `#A8EFC7` |
| Tim / má hồng | `#F2B8BD` |
| Điểm nhấn ý tưởng / bối rối | `#FFD79B` |
| Hệ tọa độ thiết kế | 430 × 300, scale đồng nhất theo cạnh giới hạn |
| Tâm mắt | x = ±62, y = 0 |
| Kích thước mắt gốc | 68 × 96 |
| Vị trí miệng | y = 67 |

Các cử chỉ dùng đường vẽ vector, tim, tia sáng và ký hiệu nhỏ. Không dùng emoji
hệ thống để tránh thay đổi hình dáng giữa thiết bị.

## Nhịp và nguyên tắc chuyển động

| Chuyển động | Quy tắc |
| --- | --- |
| Chuyển biểu cảm | Spring giảm chấn tới hạn, giữ vận tốc khi đổi đích, không reset mặt |
| Tốc độ spring | ω = 11 cho mặt; 18 cho miệng, tim và mắt nhìn; 8 cho mặt dịch theo mắt |
| Đổi hướng nhìn | Mắt dẫn trước, mặt theo chậm; giới hạn mắt ±17,5, mặt ±8 đơn vị |
| Nhịp thở lúc thức | Chu kỳ 4,7 s; biên độ tối đa 1,8 × độ tinh nghịch |
| Chớp mắt | Nhắm/mở trong 230 ms; các mốc 3,3 / 7,8 / 8,16 / 12,5 s trong chu kỳ 13,7 s |
| Phản ứng cử chỉ | Tổng 2.800 ms để tương thích `GestureReaction.DURATION_MS` |
| Lấy đà | 160 ms đầu; nén nhẹ, không áp dụng khi giảm chuyển động |
| Hiện phản ứng | Smoothstep từ 120 đến 360 ms |
| Trở lại | Smoothstep từ 1.900 đến 2.800 ms, spring tiếp tục ổn định về trạng thái nền |
| Ngủ | Mắt còn 3,5% độ mở, độ sáng 30%; nhịp thở 5,5 s |
| Nhạc diễn thử | Chu kỳ 1,2 s; chỉ lắc mặt khi nhàn rỗi, không có cử chỉ |

Độ tinh nghịch mặc định 65%. Đây là tham số để duyệt; chưa đề xuất đưa toàn bộ
điều khiển của xưởng animation vào giao diện sản phẩm.

## Trạng thái hội thoại

| Phase | Diễn hoạt |
| --- | --- |
| IDLE | Mắt mềm, nhịp thở nhẹ, miệng cười nhỏ |
| LISTENING | Mắt mở nhẹ, chú ý, vòng sáng nhỏ dưới miệng |
| FINALIZING | Một nhịp gật xác nhận, ba chấm chuyển tiếp |
| THINKING | Mắt liếc lên, bất đối xứng nhẹ, ba chấm nối tiếp |
| SPEAKING | Miệng mở theo phong bì âm lượng; giữ sắc thái cảm xúc |
| INTERRUPTED | Miệng khép nhanh, mắt trở lại chú ý |
| ERROR | Nghiêng đầu, ánh sáng ấm, miệng hơi bối rối; không chồng cử chỉ |
| SLEEPING | Khép mắt, hạ sáng, bỏ hiệu ứng cử chỉ và nhạc |

`CALM` giữ dạng mặt gốc; `HAPPY` cong mắt và cười rộng hơn; `CURIOUS` tăng
độ mở/bất đối xứng; `SLEEPY` hạ mí và độ sáng; `CARING` thêm má hồng nhẹ,
nghiêng dịu và cười ấm.

## Bộ cử chỉ

| SocialGesture | Phản ứng |
| --- | --- |
| FINGER_HEART | Mắt chuyển thành tim, ba tim nhỏ bay lên |
| TWO_HAND_HEART | Mắt tim, nén mặt nhẹ, nét ôm và bảy tim nhỏ |
| WAVE | Nghiêng qua lại, ba đường vẫy nhỏ cạnh mặt |
| THUMBS_UP | Gật xác nhận, dấu kiểm và tia sáng |
| VICTORY | Nhún vui một lần, mười tia sáng tỏa ra |
| FIST_BUMP | Nảy có giảm chấn, vòng chạm dưới miệng |
| HIGH_FIVE | Nhún đón một lần, tia sáng tỏa ra |
| THUMBS_DOWN | Mắt dịu xuống, nghiêng hiểu ý, một nét giọt nhỏ |
| POINT_UP | Mắt mở và liếc lên, biểu tượng ý tưởng tự vẽ |
| I_LOVE_YOU | Mắt tim, má hồng, năm tim nhỏ |
| OK_SIGN | Gật nhẹ và dấu kiểm |
| ROCK_ON | Lắc nhẹ nhanh hơn, hai nốt nhạc tự vẽ |
| Chạm mặt | Nén mắt, nghiêng ngượng, má hồng và hai tia sáng |

## Phối hợp và ưu tiên

1. **Ngủ** quyết định độ mở, độ sáng và miệng; không nhận phản ứng mới.
2. **Lỗi** giữ biểu cảm dễ nhận ra; hủy phản ứng đang chạy và không nhận cử chỉ mới.
3. **Hội thoại** quyết định miệng. Cử chỉ khi nói không thay thế chuyển động miệng.
4. **Cử chỉ** thêm mắt, má, nghiêng và nét vẽ trong 2,8 s; cử chỉ mới thay đích,
   giữ pose và vận tốc nên không giật về mặt gốc.
5. **Nhạc** thêm nhịp nhỏ khi nhàn rỗi. Tạm bỏ lắc nhạc trong hội thoại/cử chỉ;
   hàng nhịp nhạc vẫn có thể hiển thị khi thức.

Khi giảm chuyển động: bỏ chớp mắt, thở, lấy đà, nhún và lắc; giữ chuyển biểu cảm
mềm, ký hiệu trạng thái và nét vẽ cử chỉ ở vị trí ổn định. Tạm dừng đóng băng
đồng hồ, spring, phản ứng và tiến độ câu chuyện. Trang ẩn dừng vòng render;
khi mở lại không nhảy qua khoảng thời gian ở nền.

## Giới hạn của bản HTML

- Miệng đang dùng âm lượng tổng hợp; chưa đánh giá độ khớp với tiếng nói thật.
- Nhịp nhạc là trang trí tổng hợp; không phải phân tích beat hoặc phổ nhạc.
- Hướng nhìn do thanh trượt điều khiển; chưa lấy dữ liệu camera.
- Dòng chú thích và nút điều khiển là công cụ duyệt, không phải giao diện đế cuối cùng.
- Vòng chớp mắt có biến thiên nhưng vẫn lặp sau 13,7 s. Khi tích hợp có thể dùng
  lịch chớp mắt có seed và ngẫu nhiên có giới hạn.
- Kết quả kiểm tra native được ghi riêng trong báo cáo triển khai Android.

## Tích hợp sau khi được duyệt

Đã chuyển bộ pose và hình học sang một `KenFace` Compose Canvas dùng chung,
một bộ điều phối chuyển động và một lớp hiệu ứng. Ánh xạ trực tiếp `Phase`,
`Mood`, `GestureReaction`, gaze và trạng thái nhạc hiện có. Miệng native dùng
năng lượng PCM tại vị trí đang phát của `AudioTrack`.

Kiểm chứng luồng ngủ/thức, ngắt lời, cử chỉ trong hội thoại, màn dọc/ngang,
vòng đời foreground và frame time trên điện thoại. Các điểm gọi bộ cũ đã được thay sau khi người dùng chấp thuận.
Nhịp nhạc vẫn là hiệu ứng trang trí; miệng native dùng PCM thực thay cho diễn thử.

## Kiểm chứng bản duyệt

- JavaScript qua `node --check`: đạt.
- Chọn được cả 8 trạng thái và 12 cử chỉ, có phản hồi trạng thái trên giao diện.
- Chuỗi hội thoại diễn đến hết, tiến độ 100%.
- Tạm dừng giữ nguyên tiến độ qua lần chụp màn hình; tiếp tục diễn được.
- Thử nhạc và thả tim lúc ngủ: Ken vẫn ngủ, không nhận phản ứng mới.
- Thử giảm chuyển động với trạng thái nói và phản ứng tim.
- Bố cục desktop 1280 px và điện thoại 390 px; màn 390 px không tràn ngang.
- Console trình duyệt không ghi lỗi/cảnh báo trong các lần kiểm tra.
- Ảnh duyệt: [desktop](KEN_ANIMATION_PREVIEW.jpg), [điện thoại](KEN_ANIMATION_MOBILE.jpg).

Các kiểm tra trên chỉ dành cho bản HTML. Kết quả kiểm chứng native nằm trong
[báo cáo triển khai Android](KEN_ANIMATION_ANDROID_VI.md).
