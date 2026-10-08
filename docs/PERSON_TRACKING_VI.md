# Theo dõi và cải thiện nhận diện người quen

Ken xác nhận danh tính bằng mẫu mặt đã đăng ký, sau đó giữ liên kết với người đang được theo dõi. Khi mặt quay đi nhưng cơ thể vẫn được phát hiện và ghép rõ qua các khung hình, tên vẫn hiển thị và có thể tiếp tục tương tác cử chỉ.

## Cách dùng

1. Đánh thức Ken, để camera thấy đủ mặt và phần thân trên. Chờ tên xuất hiện.
2. Quay đầu hoặc quay người trong khi vẫn ở trong hình. Trong dock/thị giác, trạng thái chuyển sang **Đang theo dõi: [tên]** khi dựa vào cơ thể.
3. Giữ bàn tay trong hình và ở gần người để thử like, chữ V, vẫy tay hoặc tim. Phiên bản này vẫn chỉ phản ứng cử chỉ khi có đúng một người hiện diện rõ ràng.
4. Khi bị che hoàn toàn, Ken báo tạm mất dấu và ngừng nhận cử chỉ. Bộ nhớ mất dấu tối đa 1,5 giây; ghép lại cần bằng chứng ngoại hình và vị trí, không chỉ người mới đứng cùng chỗ. Ra khỏi hình lâu hơn cần xác nhận mặt lại.

Không cần đăng ký lại hồ sơ cũ để dùng phiên bản mới. Kho hồ sơ mã hóa phiên bản 1 được đọc tương thích; lần ghi tiếp theo chuyển sang phiên bản 2, giữ nguyên mẫu đăng ký mặt/giọng.

## Các tín hiệu đang sử dụng

| Tín hiệu | Vai trò |
|---|---|
| ML Kit + SFace | Xác nhận hồ sơ người quen qua mặt; 3 lần khớp gần nhau để thiết lập danh tính |
| EfficientDet-Lite0 int8 | Phát hiện người và các lớp vật thể COCO, kể cả khi không nhận được mặt |
| Chuyển động, vùng người | Dự đoán vị trí và ghép qua các khung hình |
| Ngoại hình cơ thể | Histogram màu 3 vùng thân; ngân hàng tối đa 8 mô tả trong phiên |
| MediaPipe Pose Lite | Vai/cổ tay để ghép bàn tay với người; nếu không có điểm tư thế rõ, dùng vùng cơ thể và điều kiện chỉ một người |
| Vật thể gần người | Bằng chứng phụ có trọng số nhỏ; không tự gán tên từ vật thể hoặc quần áo |
| Toàn cảnh | So sánh nền ngoài vùng người; thay đổi lớn xóa liên kết để xác nhận lại |
| Giọng đã đăng ký | Đối chiếu kết quả người nói với một người đang theo dõi; không tự gán danh tính cơ thể từ giọng |

Bộ theo dõi hiện tại là triển khai bảo thủ dùng vị trí, vận tốc làm mượt, ngoại hình màu và ngữ cảnh vật thể. Đây không phải bản triển khai đầy đủ BoT-SORT hoặc model neural person-ReID. Không có suy luận toàn cảnh bằng VLM trong vòng lặp camera; luồng hỏi AI về ảnh hiện có vẫn dùng thao tác gửi ảnh của người dùng.

Khi hai vùng người chồng nhau đáng kể, chi phí ghép gần bằng nhau hoặc khuôn mặt mâu thuẫn, liên kết danh tính bị bỏ để xác nhận lại. Mã theo dõi cơ thể tách khỏi mã hồ sơ đã đăng ký. Mất camera/ngủ/vào nền/đổi camera xóa bộ nhớ theo dõi; không dùng quần áo hôm trước để nhận danh tính hôm sau.

## Tự cải thiện mẫu mặt

Trong **Người quen**, công tắc **Tự cải thiện nhận diện người quen** mặc định bật theo yêu cầu triển khai. Tắt công tắc để dừng bổ sung mẫu mới; mẫu bổ sung đã lưu vẫn tham gia nhận diện.

- Mẫu đăng ký gốc không bị thay đổi bởi tự học.
- Cần một người đã xác nhận mặt, không đang thu mặt/giọng, mặt tối thiểu 120 pixel, góc ngang không quá 20° và góc dọc không quá 15°.
- Điểm nhận diện phải đạt ít nhất `max(0,65; ngưỡng mặt + 0,05)` và phải khớp trực tiếp với mẫu gốc, không chỉ mẫu đã tự học.
- Cần ít nhất 5 quan sát nhất quán trong 2 giây, không có khoảng cách giữa hai quan sát quá 800 ms. Mẫu trùng bị bỏ. Chỉ bổ sung tối đa một mẫu mỗi 30 giây cho một hồ sơ.
- Giữ tối đa 24 mẫu bổ sung và một phiên bản trước đó trong kho mã hóa trên máy. Không lưu ảnh camera, ngoại hình cơ thể hay vật thể thành hồ sơ lâu dài.
- **Hoàn tác lần học gần nhất** phục hồi bộ mẫu bổ sung trước lần cập nhật gần nhất; chỉ hoàn tác một lần. **Xóa mẫu tự học** giữ mẫu đăng ký gốc và có thể hoàn tác.
- Thu lại mặt chủ động thay mẫu đăng ký và bắt đầu lại bộ mẫu bổ sung.

Đây là cập nhật kho mẫu nhận diện, không huấn luyện lại trọng số model. Mẫu chỉ dựa vào cơ thể chưa đủ điều kiện lưu lâu dài. Giới hạn chất lượng giữ mục tiêu cải thiện có kiểm soát; không bảo đảm độ chính xác tăng sau mọi lần cập nhật.

## Kiểm thử và nghiệm thu

Domain kiểm tra giữ danh tính qua 60 giây không có mặt nhưng cơ thể liên tục hiện diện; di chuyển; gián đoạn phát hiện cơ thể; grace khi chỉ còn mặt; mất dấu/rời hình; người khác thế chỗ; mặt mâu thuẫn; đi cắt nhau; đổi cảnh/ngủ/xóa hồ sơ; tự học cần mẫu gốc, chất lượng, thời gian, độ đa dạng và cooldown; cử chỉ phải thuộc vùng người/cổ tay phù hợp.

Kết quả triển khai ngày 07/10/2026: build APK thành công; 48 domain test và 2 app unit test qua. Android trên máy giả lập: 17 bài qua, 1 bài live enrollment bỏ qua vì camera phòng ảo không có mặt; bài pipeline đăng ký/đọc lại bằng ảnh chân dung thật qua. OPPO CPH2651 qua 5/5 bài model/pipeline/lưu hồ sơ. Backend hội thoại build thành công, 4 bài kiểm tra danh tính/turn qua và đã khởi động lại, hai cổng 8091/8092 được nối tới app qua USB.

Log camera OPPO thật đã ghi nhận danh tính tiếp tục từ nguồn `BODY` khi góc mặt khoảng 55–76°, cùng các khung không phát hiện mặt. Đây là bằng chứng duy trì liên kết trong một lượt quan sát; chưa phải đánh giá tỷ lệ chính xác tổng thể. Tại thời điểm kiểm tra chưa có mẫu tự học mới được lưu và chưa có sự kiện like trong log của lượt này.

Đã điều chỉnh giới hạn khoảng cách giữa hai khung cử chỉ từ 550 lên 850 ms và cửa sổ chuyển động vẫy tay lên 2,4 giây sau khi log OPPO có khung 550–623 ms. Điểm cử chỉ vẫn 0,65, thời gian giữ tối thiểu vẫn 600 ms; test nhịp 650 ms và từ chối khoảng trống 900 ms qua.

Cập nhật ở lượt sửa like tiếp theo: người dùng đã thấy animation trên OPPO; log cũng ghi nhận mẫu mặt tự học được lưu. Cử chỉ tay chuyển sang tracking video, riêng like dùng ngưỡng bắt đầu 0,60/duy trì 0,55; các cử chỉ stock khác giữ 0,65/0,60. Chỉ áp dụng khoảng cách cổ tay khi pose thấy đủ hai cổ tay trong hình, nếu thiếu thì dùng vùng cơ thể của một người đã xác nhận. Chi tiết kiểm thử và nghiệm thu độ nhạy nằm trong [tài liệu cử chỉ](GESTURE_INTERACTION_VI.md).

Android kiểm tra model phát hiện người/tư thế thật trên ảnh mẫu Google, pipeline ML Kit/SFace đăng ký–lưu–đọc lại, theo dõi khi kết quả mặt bị thiếu, tương thích kho mã hóa cũ, hoàn tác và tên trên UI khi chỉ còn cơ thể. Việc bỏ kết quả mặt trên ảnh tĩnh là kiểm thử tích hợp; chưa thay thế nghiệm thu quay lưng, che khuất, đổi người và nhiệt/hiệu năng qua camera OPPO thật.

Các thông số ghép và grace là giá trị ban đầu cần hiệu chỉnh trên điện thoại. Histogram màu có giới hạn với người mặc giống nhau, thay đổi ánh sáng mạnh, cơ thể quá nhỏ hoặc che khuất hoàn toàn. Bước nghiệm thu thực tế cần ghi số lần đổi nhầm người, tỷ lệ giữ tên, độ trễ và nhiệt máy trước khi khẳng định cải thiện trong sử dụng.

## Nguồn model và dữ liệu mẫu

- Object Detector: https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector
- Pose: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android
- EfficientDet-Lite0 int8 v1: https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite
  SHA-256: `0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb`
- Pose Landmarker Lite float16 v1: https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
  SHA-256: `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`
- Ảnh tư thế: https://storage.googleapis.com/mediapipe-assets/pose.jpg
  SHA-256: `c8a830ed683c0276d713dd5aeda28f415f10cd6291972084a40d0d8b934ed62b`
- Ảnh chân dung: https://storage.googleapis.com/mediapipe-assets/portrait.jpg
  SHA-256: `a6f11efaa834706db23f275b6115058fa87fc7f14362681e6abe14e82749de3e`

Các ảnh mẫu chỉ thuộc APK kiểm thử; không đưa vào hồ sơ thật hoặc APK ứng dụng.
