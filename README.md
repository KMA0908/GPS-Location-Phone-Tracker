# GPS Location Phone Tracker — Base Project

Base Android này được rút gọn từ cấu trúc của `UltraCamera`, giữ lại các thành phần dùng chung và loại bỏ toàn bộ code đặc thù camera, quảng cáo, billing và Firebase.

Ads SDK, Firebase, Google Services, Crashlytics và Billing **chưa được tích hợp**; các phần này có thể thêm độc lập sau khi flow sản phẩm đã ổn định.

## Nền tảng đã có

- Kotlin, Android Gradle Plugin và Version Catalog.
- MVVM với `BaseActivity`, `BaseFragment`, `BaseDialogFragment`, `BaseViewModel`.
- Hilt dependency injection và dispatcher qualifiers.
- DataStore Preferences với ví dụ đếm số lần mở app.
- StateFlow cho UI state và navigation state.
- ViewBinding, edge-to-edge UI, Material 3.
- Hai flavor `production` và `staging`.
- Unit test mẫu cho `NavigationManager`.

## Cấu trúc chính

```text
app/src/main/java/com/nhn/gps/location/phone/tracker/
├── base/             # Base UI và ViewModel
├── data/local/       # DataStore/local sources
├── di/               # Hilt modules và qualifiers
├── navigation/       # Destination + navigation state
└── ui/main/          # Feature mẫu
```

## Chạy project

```bash
./gradlew :app:assembleProductionDebug
./gradlew :app:testProductionDebugUnitTest
```

## Phát triển tiếp tính năng GPS

1. Vị trí được lấy bằng `FusedLocationProviderClient` chỉ khi `MainActivity` đang hiển thị.
2. Chia sẻ vị trí và kiểm tra cảnh báo vùng dừng khi app xuống nền hoặc bị đóng.
3. App chỉ xin quyền vị trí khi đang sử dụng; không khai báo background location, foreground location service hay tự chạy lại sau khi khởi động máy.
4. Nếu bổ sung theo dõi nền ở phiên bản sau, cần thiết kế lại disclosure, runtime permission và hồ sơ khai báo Google Play trước khi phát hành.
