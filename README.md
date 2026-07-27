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

1. Tạo `data/location` cho `FusedLocationProviderClient` và nguồn vị trí nền.
2. Tạo `domain` cho model/use case theo dõi thành viên hoặc thiết bị.
3. Tạo `ui/map`, `ui/tracking`, `ui/permission` theo feature.
4. Chỉ xin quyền background location khi flow sản phẩm thực sự cần và đã đáp ứng chính sách Google Play.
