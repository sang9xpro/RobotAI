# Dạy Ken cử chỉ

## Sử dụng trên điện thoại

1. Đánh thức Ken, mở **Người quen**, tìm người đã đăng ký mặt và chọn **Dạy Ken cử chỉ**. Chỉ để người đó trong hình, đợi tên hiện. Camera ở phía trên; hình điểm bàn tay nằm trong phần hướng dẫn.
2. Chọn **Ngón cái** hoặc cử chỉ cần cải thiện, bấm **Thu 5 lượt**. App đếm ngược 2 giây, thu tư thế ổn định gần 1 giây rồi yêu cầu hạ tay khỏi hình. Mỗi lượt là một mẫu riêng, gồm tối đa 12 khung điểm.
3. Thu **Tay bình thường**: tay thả lỏng, cầm đồ vật, tư thế không muốn Ken phản ứng. Mỗi lượt giữ một tư thế đủ rõ rồi hạ tay. Không có bàn tay trong hình không được tính là mẫu tư thế bình thường.
4. Sau ít nhất 3 lượt cử chỉ và 3 lượt tay bình thường, bấm **Thử ngay**. Tín hiệu mẫu cá nhân phải gần mẫu đúng và cách mẫu dễ nhầm đủ xa. Hiệu ứng đi qua cùng điều kiện xác nhận danh tính, chủ sở hữu và thời gian giữ.
5. App giữ lại một mẫu thử để bạn đánh dấu **Đúng**, **Sai** hoặc **Không nhận được**. Nếu sai, chọn nhãn bạn thực sự làm trước khi bấm Sai; nhãn Tay bình thường dùng để sửa phản ứng nhầm. Mẫu thử đã giữ không tự đổi sang tư thế khác trong lúc chờ bạn xác nhận.
6. **Xem và sửa lượt thu** cho phép xem hình điểm, bỏ/dùng lại hoặc xóa một lượt. **Thêm cử chỉ mới** cho phép đặt tên và chọn một trong 12 hiệu ứng có sẵn. Tư thế thu mẫu hiện hỗ trợ một tay; vẫy chào/tim hai tay tiếp tục dùng luồng nhận diện hiện có.

Đóng phần Dạy Ken hoặc đổi tab sẽ dừng thu. Ngủ, camera dừng, nhiều người, sai người hoặc đổi cảnh làm gián đoạn lượt đang thu. Ken giữ thức trong phiên dạy khi màn hình còn mở. Không ghi ảnh/video; điểm bàn tay được lưu mã hóa theo tài khoản, riêng từng người. Xóa người quen xóa cả dữ liệu cử chỉ của họ.

## Học cá nhân và model đã huấn luyện

- **Mẫu cá nhân:** so khớp ba mẫu gần nhất của mỗi lớp sau chuẩn hóa vị trí, kích thước và tay trái/phải. Giữ hướng ảnh để phân biệt like/dislike. Có mẫu tay bình thường và các cử chỉ cạnh tranh để từ chối tư thế mơ hồ. Đây là học từ mẫu; không gọi nó là huấn luyện lại MediaPipe.
- Mẫu **Tay bình thường** đã xác nhận còn được dùng để chặn phản ứng từ bộ nhận diện stock khi tư thế hiện tại khớp rất gần mẫu âm, đủ xa các mẫu đúng. Tư thế chưa gặp không bị coi là mẫu âm chỉ vì model không nhận được. Điều kiện này chỉ áp dụng cho người sở hữu mẫu.
- **Model đã train:** bộ phân loại softmax nhỏ học trọng số từ đặc trưng 69 chiều: tọa độ 21 điểm, 5 góc gập và khoảng đầu ngón cái–trỏ. App chạy trực tiếp các trọng số bằng Kotlin, dùng thêm giới hạn khoảng cách tới mẫu tập học để từ chối tư thế ngoài dữ liệu. Công cụ xuất cùng bộ trọng số dưới dạng TFLite để dùng với runtime khác; tệp TFLite này là bộ phân loại, không phải model `gesture_recognizer.task` chứa cả bộ phát hiện bàn tay.
- **Kích hoạt/khôi phục:** nhập `model.json`, xem báo cáo, chọn Kích hoạt. Có thể quay lại mẫu cá nhân hoặc model trước. Model khác người, khác phiên bản đặc trưng, sai kích thước, số không hữu hạn, thiếu lớp bình thường, tập học/thử bị trộn hoặc không đạt kiểm tra sẽ bị từ chối. Chỉnh/xóa lượt mẫu tắt model đang dùng của người đó để tránh áp dụng phiên bản cũ ngoài ý muốn.

## Training trên máy tính

Trong app, **Xuất dữ liệu** mở bộ chọn nơi lưu tệp JSON. Người dùng chủ động xuất; app không tự gửi dữ liệu lên server. Tệp gồm điểm, nhãn cử chỉ, mã người/phiên và trạng thái dùng/bỏ, không chứa mẫu mặt/giọng.

Thu tối thiểu **4 phiên riêng cho mỗi nhãn**, mỗi phiên ít nhất 5 lượt (cử chỉ và Tay bình thường đều cần). Mỗi lần bấm Thu 5 lượt tạo phiên mới. Nên trải các phiên qua nhiều thời điểm, góc và ánh sáng; mục tiêu thực tế 30–50 lượt độc lập cho mỗi nhãn trước khi nghiệm thu. Mức tối thiểu chỉ cho phép chạy quy trình, không đảm bảo chất lượng ngoài dữ liệu.

Từ thư mục project:

```sh
python3 tools/gesture-training/train.py /duong/dan/ken-gestures.json --output .local/my-gesture-model
```

Training dùng Python standard library, không cần TensorFlow/GPU. Nếu tệp có nhiều người, thêm `--person ID`. Có thể chọn `--epochs 200 --seed 42`. Các phiên được giữ nguyên trong từng tập: tối thiểu 10 lượt/lớp ở tập học, 5 ở tập hiệu chỉnh, 5 ở tập thử. Chỉ tập hiệu chỉnh được dùng chọn ngưỡng; tập thử không dùng chọn trọng số/ngưỡng. Dữ liệu thiếu hoặc không chia được theo phiên sẽ báo lỗi, không xuất model giả.

Kết quả:

- `model.json`: model và báo cáo để **Nhập model** trong app.
- `report.json`: confusion matrix, recall từng lớp, tỷ lệ phản ứng nhầm trên tay bình thường, các mã phiên thuộc từng tập, SHA-256 dữ liệu và thông số training.
- Model đủ điều kiện nhập khi accuracy ít nhất 85%, recall mỗi lớp ít nhất 80%, false-positive rate với lớp bình thường không quá 5% trên tập thử, với tối thiểu 5 lượt/lớp. Đây là tiêu chí kỹ thuật ban đầu, không phải chứng nhận độ chính xác thực tế.

Xuất thêm TFLite (tùy chọn):

```sh
python3 -m venv .local/gesture-training-env
.local/gesture-training-env/bin/python -m pip install -r tools/gesture-training/requirements-export.txt
.local/gesture-training-env/bin/python tools/gesture-training/export_tflite.py .local/my-gesture-model/model.json --output .local/my-gesture-model/gesture_head.tflite
```

Đưa `model.json` về điện thoại rồi mở **Dạy Ken → Nhập model → Kích hoạt**. Model không đạt kiểm tra vẫn có báo cáo để tìm lớp cần thu bổ sung; app không cho kích hoạt. Sau kích hoạt cần thử camera thật: các lượt đúng, các tư thế thường không ra hiệu, nhiều góc, độ trễ và thao tác khôi phục.

## Kiểm thử và phạm vi

`GestureTeachingTest` kiểm tra recorder, người lạ/nhiều người/tay ngoài hình, gián đoạn, chuyển tư thế, lặp mẫu, đặc trưng khớp Python, mẫu âm và cách ly theo người. `GestureTeachingPlatformTest` kiểm tra kho mã hóa, model bàn tay thật trên ảnh mẫu, import/activate/rollback, phản hồi đóng băng mẫu và giao diện. `tools/gesture-training/test_training.py` kiểm tra chia phiên, training, nhãn và FlatBuffer TFLite có cùng trọng số.

Các fixture và model trong Android test được tạo từ điểm giả lập và chỉ đóng gói vào APK kiểm thử. Kết quả tốt trên fixture không chứng minh độ nhạy tay thật. Chưa có bộ mẫu đã gán nhãn của người dùng để train model production. Không tự thay model mặc định bằng model fixture.

Nguồn tham chiếu: [MediaPipe tùy chỉnh cử chỉ](https://developers.google.com/edge/mediapipe/solutions/customization/gesture_recognizer), [TensorFlow Lite schema](https://github.com/tensorflow/tensorflow/blob/v2.18.0/tensorflow/lite/schema/schema.fbs). Lớp feature/training/runtime nhỏ là phần triển khai của RobotAI; giữ MediaPipe để phát hiện bàn tay và Fingerpose MIT để tính góc như bản V2.
