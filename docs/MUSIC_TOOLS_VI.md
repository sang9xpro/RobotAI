# MCP nghe nhạc trên Android

Luồng hiện tại:

```text
Lệnh MP3 → backend tìm URL MP3 → MCP self.music.play → Android MediaPlayer
Lệnh YouTube → backend xác định bài, ca sĩ, từ khóa → MCP self.music.search
             → app Android tìm/chọn video → WebView mở YouTube IFrame
               → mặt robot đổi màu mắt, nhún nhẹ, hiện cột nhạc và điều khiển
```

Backend không giải mã nhạc thành PCM/Opus của hội thoại. Audio TTS vẫn dùng luồng hội thoại hiện có; app hạ âm lượng nhạc trong lúc bot nói.

## MCP của app

App đăng ký cùng endpoint MCP hiện có:

- `self.music.play(title, artist?, url)`: bắt đầu tải audio trực tiếp hoặc mở URL YouTube trong player hiển thị của app. Phản hồi thành công ban đầu là `loading`; không đồng nghĩa đã phát được nội dung.
- `self.music.search(title, artist?, searchQuery, videoUrl?)`: app tìm và chọn video YouTube theo chỉ dẫn của backend, rồi mở player hiển thị. Có thể truyền link video trực tiếp qua `videoUrl` để bỏ qua bước tìm.
- `self.music.status()`: trả tên bài, ca sĩ, trạng thái `loading`, `playing`, `paused`, `idle` hoặc `error` và thông báo thực tế.
- `self.music.stop()`: hủy tải/dừng nhạc, giải phóng player và audio focus.

Backend gắn thời điểm tạo/hết hạn vào lệnh play/search. Bước initialize MCP gửi `server_time_ms`; app dùng mốc này với đồng hồ monotonic để kiểm tra hạn lệnh dù giờ hệ thống điện thoại lệch. App chỉ chấp nhận lệnh phát/tìm khi đang foreground và trong lượt hội thoại, kiểm tra request trùng và hạn lệnh. Stop và status dùng được ngoài lượt hội thoại. Nhạc dừng khi app vào nền, mất kết nối, đổi tài khoản/role, ngủ hoặc người dùng nhấn Dừng nhạc/Ngắt lời. Đây là nhạc phát trên nền **màn hình robot đang mở**, chưa phải dịch vụ nghe nhạc khi app bị đóng.

Khi nhạc đang tải/phát/tạm dừng, micro hội thoại không tự thu để tránh nhận nhầm tiếng loa. Nhấn Dừng nhạc để nói câu tiếp theo. Tạm dừng/phát tiếp và dừng luôn có trong thẻ nhạc trên màn hình Ken/chế độ đế.

App gửi `music_playback` mỗi 15 giây trong khi foreground, có kết nối và nhạc đang hoạt động. Backend chỉ làm mới thời gian hoạt động cho phiên đã có role và đăng ký MCP nhạc; trạng thái idle/error không giữ phiên. Điều này tránh đóng WebSocket sau 60 giây yên lặng khi nghe bài dài mà không mở micro.

## Backend tìm bài và link

`play_music(songName)` tìm một kết quả duy nhất rồi gọi MCP Android; nếu app chưa đăng ký `self.music.play`, trả thông báo cập nhật/kết nối lại app. Không có đường phát nhạc bằng Player Opus của backend. `get_playlist(query?)` liệt kê/tìm bài không phân biệt dấu tiếng Việt; có thể tìm cả ca sĩ, tối đa 30 bài và khoảng 2.000 ký tự.

Provider `codex-chatgpt` chưa hỗ trợ function calling. `MusicCommandRouter` định tuyến các lệnh rõ ràng trong vòng đời Persona như:

- “Ken ơi, mở bài Nhạc thử Ken.”
- “Phát bài Nhạc thử Ken.”
- “Cho tôi nghe bài Nhạc thử Ken.”
- “Có những bài hát nào?”
- “Tìm bài Một nhà.”
- “Ken ơi, mở một bài hát cho nghe.” (chọn một bài có sẵn)
- “Bạn có phát nhạc MP3 được không?” (trả lời theo thư viện và MCP đang kết nối)

Nhánh này dùng công cụ đã đăng ký trong phiên và giữ cấu hình loại trừ tool. Provider có function calling cũng có thể gọi công cụ tìm bài rồi MCP nhạc.

Ngày 09/10/2026, câu nói thực tế được PhoWhisper ghi thành “.kèn người mở một bài hát cho nghe.”, làm lệnh nhạc rơi sang hội thoại thường và robot nói chưa thể phát. Router hiện bỏ dấu câu thừa ở đầu, nhận biến thể “kèn người” và các yêu cầu “một bài hát” không nêu tên; backend chọn một bài trong thư viện. Nếu hỏi bài chưa có, Ken liệt kê các bài có thể phát thay vì nói chung rằng không thể nghe nhạc. Đã phát lại đúng văn bản STT này qua backend thật và MCP trên OPPO với role Ken: app báo `playing` cho Nhạc thử Ken. Kiểm tra này phát lại văn bản nhận dạng; chất lượng STT micro vẫn phụ thuộc môi trường tiếng ồn.

Nguồn hỗ trợ là **MP3 trên backend, URL audio trực tiếp và YouTube**. Lệnh “Mở bài hát Đếm thời gian trên YouTube” gọi `play_youtube`. Backend tách tên bài, quyết định ca sĩ/từ khóa nếu biết, rồi gửi `self.music.search`; chính app Android gọi YouTube Data API, chọn video có thể nhúng và phát. YouTube chạy bằng IFrame API trong thẻ nhạc hiển thị cạnh mặt Ken; không tách audio, tải MP3 từ YouTube hay phát khi app bị đóng/nằm nền. Nếu tự phát bị chặn, chạm nút Phát ngay trên video. Khi video báo lỗi, thẻ nhạc hiển thị mã lỗi thực tế.

Để tìm **bài bất kỳ** theo tên, tạo khóa YouTube Data API v3, giới hạn khóa cho ứng dụng Android `com.robotai.robot` và SHA-1 chứng chỉ ký APK, đồng thời giới hạn API là YouTube Data API v3. Đặt `YOUTUBE_API_KEY` trong `android-app/robot-app/gradle.properties` (chỉ ở máy build) hoặc biến môi trường khi build, rồi cài APK mới trên điện thoại. Không đặt khóa này trong backend. App gọi `search.list` với `type=video`, `videoEmbeddable=true`, ngôn ngữ Việt và vùng VN. Không có key, URL `https://youtu.be/VIDEO_ID` hoặc `https://www.youtube.com/watch?v=VIDEO_ID` vẫn mở được. Riêng yêu cầu đã báo cáo “Đếm thời gian” dùng kết quả NDMT “Đếm Thời Gian (Remake Version)” đã thử phát trên OPPO; muốn phiên bản khác hãy nói tên ca sĩ hoặc gửi link. YouTube có thể từ chối video riêng tư/không cho nhúng.

## Chuẩn bị thư viện

Cả server và dialogue cần dùng chung `xiaozhi.runtime.music-dir`; mặc định `uploads/music` tính từ thư mục chạy Java. Với bộ local hiện tại, đó là `server-java/uploads/music`.

MP3 được lấy trực tiếp từ thư mục; không đọc symlink/thư mục con. App nhận URL tương đối `/api/robot/music/audio?name=...`, ghép với API base URL của tài khoản, gửi token trong header tới endpoint đã xác thực. Token không được gửi tới URL nhạc bên ngoài. Endpoint gửi nguyên byte MP3 và hỗ trợ HTTP Range.

Đã có bài demo **Nhạc thử Ken.mp3**, là giai điệu tự tạo khoảng 96 giây để thử luồng phát nhạc. Có thể nói “Phát bài Nhạc thử Ken”. Bài demo này được tạo tại thư viện runtime local, không phải nội dung ca sĩ/nhạc thương mại.

Để dùng audio trên dịch vụ khác, tạo `catalog.json` trong thư mục nhạc:

```json
[
  {
    "title": "Tên bài",
    "artist": "Tên ca sĩ",
    "url": "https://your-music-host.example/song.mp3"
  }
]
```

URL ví dụ phải được thay bằng link audio thật. Catalog được đọc khi tìm, không cần restart khi sửa/thêm bài. Tên kèm ca sĩ giúp chọn đúng khi có nhiều bài trùng tên. Có thể upload MP3 bằng API hiện có `POST /api/file/music` với quyền `system:file:api:upload`.

Kết nối OPPO qua USB dùng `adb reverse tcp:8091 tcp:8091` và `adb reverse tcp:8092 tcp:8092`. Khi dùng Wi-Fi, API/WebSocket trong tài khoản phải trỏ tới địa chỉ backend mà điện thoại truy cập được.

## Hiệu ứng robot

Màn hình Ken và chế độ đế hiển thị mắt chuyển cyan/hồng, nhún nhẹ và nụ cười khi player ở trạng thái `playing`. Thẻ nhạc có tên bài, ca sĩ, thông báo tải/lỗi và nút điều khiển. Cột nhạc là hoạt ảnh trang trí, không phải phân tích FFT hay thu âm micro.

## Build và kiểm thử

Android dùng JDK 17:

```bash
cd android-app/robot-app
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Cập nhật bằng `adb install -r` để giữ dữ liệu app. Cần cập nhật cả app và backend cho giao thức mới.

Backend:

```bash
cd server-java
mvn -s scripts/robotai-maven-settings.xml -pl xiaozhi-dialogue,xiaozhi-server -am -DskipTests package
```

Khởi động lại Java để nạp mã mới rồi kết nối lại robot. Đảm bảo `play_music` và MCP nhạc không bị loại trừ trong phiên/role.

Kiểm thử tự động gồm:

- `LocalMusicToolsTest`: tìm bài/ca sĩ, URL catalog, đường dẫn/symlink, trường hợp trùng tên/thiếu tool, và xác nhận backend gọi MCP mà không gọi Player audio.
- `MusicCommandRouterTest`: định tuyến lệnh rõ ràng, tránh câu phủ định và giữ trạng thái tool bị tắt.
- `RobotMusicAudioControllerTest`: byte MP3 nguyên bản, HTTP Range và yêu cầu xác thực.
- `MusicUrlTest`: kiểm tra URL YouTube/audio và chỉ chuyển token tới endpoint audio của backend.
- `YouTubeMusicPlayerTest`: xác nhận backend gửi tên bài/từ khóa/link trực tiếp qua MCP, kể cả lời gọi chỉ có tên bài.
- `YouTubeMusicSearchTest`: xác nhận app gửi yêu cầu tìm video có thể nhúng, chọn bài đúng và nhận link trực tiếp không cần API key.
- `YouTubePlatformTest`: xác nhận trình phát YouTube hiển thị và trạng thái `playing` trên OPPO với video thực.
- `MusicPlatformTest`: MCP và MediaPlayer thật trên OPPO; kiểm tra hiệu ứng, pause/resume, replay, completion và hủy lúc đang tải.
- `MusicBackendPhoneTest`: input chữ → Java thật → WebSocket MCP → native MediaPlayer trên OPPO đọc MP3 có xác thực. Kiểm thử này không đo STT/micro hay âm học loa.

Ngày 09/10/2026 đã build/cài trên OPPO CPH2651 (`WCFE6XCI9PYLDAGM`), giữ dữ liệu app. Bộ kiểm thử backend chọn lọc đạt 11/11; unit test Android và 3 bài `YouTubeMusicSearchTest` trên OPPO đạt. `YouTubePlatformTest` phát video NDMT và `MusicBackendPhoneTest` với nguyên văn “Mở bài hát Đếm thời gian trên youtube” đạt `playing` qua backend → `self.music.search` → app. Bài thử MP3 cũ qua `self.music.play` cũng đạt `playing`. Những bài thử này dùng input chữ; chưa đo STT micro hoặc âm lượng loa. Chưa có API key ở app nên chỉ xác nhận tìm kiếm Data API bằng server giả lập trên OPPO, còn bài “Đếm thời gian” dùng kết quả đã chọn sẵn. Không dùng emulator.

Các API Android được dùng theo [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) và [audio focus](https://developer.android.com/media/optimize/audio-focus).

YouTube dùng [IFrame Player API](https://developers.google.com/youtube/iframe_api_reference) và [Data API `search.list`](https://developers.google.com/youtube/v3/docs/search/list). [Chính sách YouTube](https://developers.google.com/youtube/terms/developer-policies) yêu cầu player hiển thị trên màn hình khi nội dung đang phát; vì vậy app không phát YouTube bằng player audio ẩn sau mặt robot.
