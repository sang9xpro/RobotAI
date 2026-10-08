# Android Emulator riêng cho RobotAI

Đã thiết lập ngày 04/10/2026 trên Mac ARM64. Runtime emulator/platform-tools và image Google Play ARM64 Android 16 API 36.1 được sao chép từ SDK hiện có vào **`.local/android-emulator/sdk`**. AVD `RobotAI_API36_1`, toàn bộ userdata/cache và Android emulator home nằm trong `.local/android-emulator/`, được Git ignore. Không sao chép dữ liệu hoặc tài khoản từ điện thoại/AVD khác.

Máy ảo: 4 vCPU, RAM 2 GB, màn hình 1080×1920, userdata 4 GB, ADB **`emulator-5556`**. Runtime ban đầu khoảng 3,5 GB; userdata sẽ tăng theo sử dụng. Không dùng số đo emulator để kết luận độ trễ mic/loa, AEC, pin hoặc hiệu năng model trên OPPO.

Từ root dự án:

```bash
python3 tools/android-emulator/local.py start
python3 tools/android-emulator/local.py status
python3 tools/android-emulator/local.py install
python3 tools/android-emulator/local.py stop
```

Có thể mở `start.command` trong thư mục này bằng Finder. `start --headless` dành cho test không cần cửa sổ. Khi emulator đang chạy, `start` từ chối chiếm lại port. `stop`/`install`/test xác minh đúng tên AVD trước khi thao tác; không nhắm điện thoại OPPO hoặc máy ảo khác.

`status` exit code 0 khi `owned=true` và `boot_completed=1`. Lần boot đầu đã xác minh hoàn tất trong khoảng 28 giây. `install` cài APK debug đã build, cấp RECORD_AUDIO, tạo ADB reverse cho API 8091/WebSocket 8092 rồi mở app.

Build app vẫn dùng Android SDK build tools đã cấu hình trong `android-app/robot-app/local.properties`; thư mục emulator riêng chỉ chứa các package cần chạy máy ảo.

## Test đăng nhập

Build `:app:assembleDebug :app:assembleDebugAndroidTest` trước. Chạy fixture tài khoản test và instrumentation:

```bash
python3 tools/auth-fixture/server.py
# Terminal khác, tại root:
python3 tools/android-emulator/local.py test-login
```

Fixture chạy 8891/8892 loopback; không dùng user/password thật và không inference LLM/STT/TTS. Test kiểm UI, Android Keystore, HTTP login/check/logout và token header trên WebSocket. Đã chạy qua 4 tests (12,032 giây): login/restore/logout/đổi user, sai mật khẩu, token hết hạn và mất mạng. Log tại `.local/android-emulator/test-login.log`.

Test audio mock cần `python3 tools/mock-voice/server.py --inject-late`, sau đó `python3 tools/android-emulator/local.py test-mock`. Chỉ xác nhận client với audio fixture, không phải toàn luồng inference thật.

## Tái tạo runtime

```bash
python3 tools/android-emulator/local.py setup --source-sdk /absolute/path/to/Android/sdk
```

Source SDK cần có `emulator`, `platform-tools`, `licenses`, `system-images/android-36.1/google_apis_playstore/arm64-v8a`. Setup không tự tải hoặc chấp nhận license mới, không ghi đè AVD đã có. Runtime/profile này dành cho host macOS ARM64 hiện tại.

Tài liệu chính thức: [Android Emulator](https://developer.android.com/studio/run/emulator), [đường dẫn SDK/AVD qua biến môi trường](https://developer.android.com/tools/variables).
