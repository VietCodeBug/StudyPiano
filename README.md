# Piano Trainer - Native Android Application

Ứng dụng hỗ trợ học và luyện tập Piano tự động (Offline, Native Android Kotlin, Jetpack Compose, Room Database, MIDI Engine).

## 📥 Tải trực tiếp file APK để cài đặt

- Link tải trực tiếp file APK đã build sẵn: [**Tải StudyPiano.apk (v1.0)**](release/StudyPiano.apk?raw=true)
- Hoặc vào thư mục [`release/`](release/) và bấm tải file `StudyPiano.apk`.

## Hướng dẫn build cục bộ (Local Build)

### 1. Yêu cầu môi trường
- **Java JDK**: Cài đặt JDK 17 và cấu hình biến môi trường `JAVA_HOME`.
- **Android SDK**: Cài đặt Android SDK (Build Tools 35.0.0, SDK Platform 35).

### 2. Cấu hình `local.properties`
Tạo file `local.properties` ở thư mục gốc dự án nếu chưa có và trỏ tới đường dẫn Android SDK trên máy của bạn:

**Trên Windows:**
```properties
sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
```

**Trên macOS/Linux:**
```properties
sdk.dir=/Users/<YourUsername>/Library/Android/sdk
```

### 3. Biên dịch và Build Debug APK bằng Gradle Wrapper

**Trên Windows (Command Prompt / PowerShell):**
```cmd
gradlew.bat assembleDebug
```

**Trên macOS / Linux:**
```bash
./gradlew assembleDebug
```

### 4. Chạy Unit Tests
```cmd
gradlew.bat testDebugUnitTest
```

### 5. Vị trí file APK xuất ra
Sau khi build thành công, file APK nằm tại:
`app/build/outputs/apk/debug/app-debug.apk`

---
*Lưu ý: Ứng dụng hoạt động 100% Offline, không yêu cầu tài khoản, Firebase hay Google API key.*
