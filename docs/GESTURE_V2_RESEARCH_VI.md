# Nâng cấp cử chỉ — 07/10/2026

## Repository đã đánh giá

Số sao chỉ phản ánh mức quan tâm, không chứng minh độ chính xác trên OPPO. Số liệu tra GitHub ngày 07/10/2026.

| Repository | Mức quan tâm | Phù hợp và quyết định |
|---|---:|---|
| [Google MediaPipe](https://github.com/google-ai-edge/mediapipe) | khoảng 37,2k sao | Nền tảng native đang dùng; tiếp tục dùng model và theo dõi VIDEO. Apache-2.0. |
| [MediaPipe samples](https://github.com/google-ai-edge/mediapipe-samples) | 2.848 sao | Có mẫu Android chính thức, hoạt động gần đây (push 06/10/2026). Đối chiếu API và vòng đời model; không thêm runtime web. Apache-2.0. |
| [Fingerpose](https://github.com/andypotato/fingerpose) | 510 sao | MIT, push gần nhất 01/05/2023. Chuyển phần tính góc gập ngón sang Kotlin, dùng 21 điểm MediaPipe với tọa độ z và hiệu chỉnh tỷ lệ ảnh. Quy tắc OK/rock/ILY/like là phần bổ sung của RobotAI. |
| [kinivi/hand-gesture-recognition-mediapipe](https://github.com/kinivi/hand-gesture-recognition-mediapipe) | 811 sao | Apache-2.0, push 16/07/2024. Tham khảo lịch sử điểm và bỏ phiếu theo thời gian; RobotAI triển khai cửa sổ ngắn riêng. Không nhập model MLP hoặc runtime Python vào app. |
| [HaGRID](https://github.com/hukenovs/hagrid) | 1.072 sao | Hữu ích cho hướng huấn luyện nhiều lớp sau này, không phải thư viện Android có thể thay trực tiếp. Chưa nhập dữ liệu, trọng số hoặc huấn luyện bằng repo này. |

## Những thay đổi đã áp dụng

- 12 cử chỉ: tim ngón tay, tim hai tay, vẫy chào, like, chữ V, nắm tay, đập tay, dislike, chỉ lên, I love you, OK, rock. Bảy nhãn stock của MediaPipe kết hợp quy tắc hình học và chuyển động.
- Ưu tiên tay đang ra hiệu khi tay còn lại trung tính, ngoài vùng hoặc bị cắt. Hai tín hiệu khác nhau đồng thời không được tùy tiện chọn; tim hai tay được kiểm tra trước.
- Cửa sổ tối đa 5 khung/900 ms, cần ít nhất 2 phiếu và tỷ lệ tối thiểu 60%. Giữ 350 ms cho tư thế thường, 750 ms cho đập tay để hạn chế phản ứng khi chỉ xòe tay, 180 ms cho vẫy đã có chuỗi đổi hướng. Khoảng khung tối đa 850 ms; chịu được gián đoạn ngắn 220 ms sau tín hiệu đã xác nhận.
- Có thể đổi cử chỉ trực tiếp, cách lần phát trước tối thiểu 1 giây. Giữ nguyên một cử chỉ không lặp vô hạn; hạ tay ít nhất 350 ms để lặp lại.
- Giao diện hiển thị tên cử chỉ đang xác nhận và tiến trình. Tab Người quen có hướng dẫn 12 tư thế.
- Camera được phép lấy mẫu mỗi 80 ms (không phải cam kết FPS). Nhận diện danh tính và toàn cảnh mỗi 500 ms, sớm hơn khi danh sách tracking mặt đổi hoặc đang đăng ký mặt. Pose mỗi 700 ms; không dùng cổ tay pose cũ để phủ quyết bàn tay của khung nhanh.
- Giữ nguyên ngưỡng mặt và điều kiện một người quen. Dữ liệu xử lý trên điện thoại, không gửi ảnh lên server. Backend không cần thay đổi.

## Nguồn và giấy phép

Phần công thức góc gập trong `FingerPoseClassifier.kt` được chuyển từ [FingerPoseEstimator.js](https://github.com/andypotato/fingerpose/blob/master/src/FingerPoseEstimator.js), bản tải ngày 07/10/2026 có SHA-256 `7d135663cf926b95a04cbe69577025e86570e863acb56cc660a33542a8d3f55e`. Bản quyền Andreas Schallwig (2020); nguyên văn MIT được đóng gói tại `app/src/main/assets/licenses/fingerpose-MIT.txt`. Không sao chép toàn bộ Fingerpose hoặc đưa TFJS vào Android.

## Giới hạn nghiệm thu

Test điểm giả lập kiểm tra hình học, tọa độ sâu, ảnh lật, thu phóng, tỷ lệ ảnh, tay phụ, người lạ và thời gian xác nhận. Test thiết bị kiểm tra model trên ảnh mẫu và các hiệu ứng. Hai loại test này không thay thế thử 12 cử chỉ qua camera thực tế. OK, rock và các hình tim là quy tắc bổ sung, chưa có bộ dữ liệu người thật để công bố độ chính xác. Cần đo số lần nhận đúng, phản ứng nhầm và độ trễ với nhiều góc tay/ánh sáng trước khi kết luận mức cải thiện.

## Kết quả build và kiểm thử

Build debug và APK instrumentation thành công bằng JDK 17. 57 domain test và 2 app unit test qua. OPPO CPH2651 qua USB: 9/9 instrumentation test qua, gồm 12 hiệu ứng có tên/không chặn thao tác/tự hết hạn, model một và hai tay, tay rời hình, model like đến UI Ken/dock, trạng thái người quen và body/pose khi không thấy mặt. Đã cài APK và mở lại RobotAI; giữ dữ liệu app. Sau khi khởi động lại backend local, reverse USB 8091/8092 và khôi phục phiên cũ, camera đã hiện lại tên người quen.

Camera thật lúc 18:28:43 ghi một sự kiện THUMBS_UP và RobotGestureUi displayed=true sau đó; chưa có người dùng xác nhận số lượt thử. Mẫu 31 dòng diagnostic V2 gồm 20 khung nhanh (trung vị analysisMs 81 ms), 11 khung sâu (199 ms). Log có lấy mẫu nên không dùng làm FPS hay benchmark đối chứng; analysisMs không bao gồm toàn bộ camera/MLKit/render. Bằng chứng local: .local/identity-test/gesture-v2-diagnostics-2026-10-07.log.
