# AI STUDIO HANDOFF REPORT — LOCAL SPRINT 2

**Đợt phát triển**: `LOCAL SPRINT 2 — PERSONAL PIANO STUDIO`
**Repository**: `https://github.com/VietCodeBug/StudyPiano`
**Branch**: `main`
**Base Commit**: `804a905e8d2588a18b8608648c88a896cf4cd68a`
**Phiên bản ứng dụng**: `versionCode = 3`, `versionName = "2.1.0"`
**Môi trường dự án**: `compileSdk 35`, `targetSdk 35`, `minSdk 26`, JDK 17 Adoptium  
**Room Database Schema**: Version 5 (`MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`, `MIGRATION_4_5`)

---

## 1. Tóm tắt các Gate phát triển trong Sprint 2

### Gate A — Tính chân thực của sản phẩm & Kiểu lỗi có định kiểu (Typed Errors/Events)
- **Ẩn chế độ hiển thị chưa hoàn thiện**: Ẩn `DisplayMode.SHEET_MUSIC` khỏi toàn bộ UI cho đến khi bộ dựng ký âm bản nhạc thật hoàn thành; mặc định fallback về `FALLING_NOTES`.
- **Loại bỏ toggle không chức năng**: Loại bỏ hoàn toàn switch `virtualPianoSoundEnabled` khỏi UI.
- **Hệ thống phân cấp lỗi và sự kiện có định kiểu**:
  - `UserFacingError`: `StorageError`, `MidiError`, `HardwareError`, `RecordingError`, `BackupError`, `GenericError`.
  - `UiEvent`: `ShowSnackbar`, `ShowToast`, `Navigate`, `TriggerCountIn`, `ScrollTo`.
- **An toàn StateFlow & Type-safety**: Loại bỏ toàn bộ ép kiểu không an toàn (`Array<Any>`) trong các hàm `combine` ở tất cả ViewModels, phân chia sub-group rõ ràng.

### Gate B — Xác thực Phần cứng BLE/USB MIDI & State Machine
- **2 Chế độ quét thiết bị**:
  - `MIDI_ONLY`: Quét BLE lọc theo BLE MIDI Service UUID chuẩn (`03B80E5A-EDE8-4B33-A028-523A73407830`).
  - `EXTENDED`: Quét BLE mở rộng không lọc UUID, đánh dấu thiết bị không quảng bá MIDI là `UNKNOWN_BLUETOOTH`, cung cấp CTA "Quét tương thích". Chỉ xác nhận kết nối sau khi `MidiManager.openBluetoothDevice()` và mở cổng input thành công.
- **Máy trạng thái kết nối phần cứng 8 trạng thái**:
  - `IDLE`, `CHECKING_PERMISSION`, `SCANNING_MIDI`, `SCANNING_EXTENDED`, `CONNECTING`, `CONNECTED`, `DISCONNECTING`, `ERROR`.
- **Giám sát tín hiệu phần cứng Real-time**:
  - Thống kê nốt, vận tốc lực gõ (velocity), kênh (channel), nguồn tín hiệu, số lượng Note On/Off và trạng thái bàn đạp vang CC64 (Sustain Pedal).
- **Tự động kết nối lại (Auto-reconnect)**: Lưu cấu hình trong DataStore và tự động kết nối khi thiết bị khả dụng.
- **Xuất nhật ký chẩn đoán an toàn (SAF Export)**: Che giấu địa chỉ MAC nhạy cảm, xuất tối đa 300 sự kiện MIDI thô qua Storage Access Framework.

### Gate C — Pipeline Tự do Chơi đàn & Thu âm (Free Play & Recording Pipeline)
- **Quản lý lưu trữ & Khuyến khích tệp**:
  - Tệp âm thanh `.m4a` lưu tại `context.filesDir/recordings/{recordingId}/audio.m4a` với cơ chế chuyển tiếp từ `pending_recordings/`.
- **Điều phối Microphone an toàn đa luồng (`AudioInputCoordinator`)**:
  - Đồng bộ hóa tài nguyên phần cứng Microphone giữa `MicrophonePitchDetector` và `FreePlayViewModel` ghi âm, ngăn chặn xung đột `AudioRecord`/`MediaRecorder`.
- **Vòng đời MediaRecorder an toàn**:
  - Quản lý trạng thái với khối `try-finally` đảm bảo `release()` giải phóng tài nguyên phần cứng trong mọi tình huống.
- **Bộ xuất Standard MIDI Format 0 (`MidiRecordingWriter`)**:
  - Sinh tệp `.mid` chuẩn với `MThd`, `MTrk`, PPQ 480, tempo meta, Note On/Off, CC64 sustain, mã hóa delta thời gian VLQ và End-of-Track (`FF 2F 00`), lưu tại `context.filesDir/recordings/{recordingId}/performance.mid`.
- **Thư viện bản thu âm hoàn chỉnh**:
  - Phát/Tạm dừng/Tua/Dừng, đổi tên bản thu, xóa nguyên tử (DB + tệp vật lý), xuất `.mid` và `.m4a` qua SAF.

### Gate D — Practice Player Tối ưu hóa & Hiệu năng Cao
- **Giao diện ngang co giãn linh hoạt (Responsive Landscape)**:
  - Tương thích tốt với các tỷ lệ và độ phân giải màn hình từ 640x360 đến 1280x720.
- **Đếm nhịp chuẩn bị (Count-in Option)**:
  - Tùy chọn Tắt / 1 ô nhịp / 2 ô nhịp trước khi bắt đầu ở chế độ Rhythm với âm thanh đếm nhịp và thẻ đếm trực quan.
- **Lưu cấu hình luyện tập (`SongPracticePreset`)**:
  - Bảng `song_practice_presets` trong Room v5 lưu trữ cấu hình đoạn lặp A-B, tay luyện tập, BPM mục tiêu, tốc độ phát, tầm nhìn nốt rơi và tùy chọn tăng tốc độ dần (Gradual speed-up).
- **Thuật toán tìm kiếm nhị phân nốt rơi (`VisibleNoteWindowSelector`)**:
  - Tìm kiếm cửa sổ hiển thị theo khoảng $O(\log N)$ có tính đến `maxDurationMs`, không cấp phát đối tượng trên mỗi khung hình render (zero allocation).
- **Giảm tải thị giác (Clutter Reduction)**:
  - Tự động ẩn nhãn tên nốt khi chiều rộng làn phím < 18dp.
- **Bảng màu chuẩn**: Tay phải: Cyan/Blue (`#38BDF8`), Tay trái: Cam (`#F97316`), Đúng: Xanh lá (`#10B981`), Sai: Đỏ (`#EF4444`). 100% không tím, không hồng.

### Gate E — Tiến trình, Mục tiêu & Nhật ký Luyện tập
- **Bộ đếm thời gian chủ động (Active Practice Timer)**:
  - Chỉ tính thời gian gõ đàn thực tế (không tính thời gian tạm dừng/background).
  - Tổng hợp dữ liệu theo múi giờ thiết bị (`ZoneId.systemDefault()`).
- **Ghi nhận phiên luyện tập Free Play**:
  - Tự động lưu phiên `FREE_PLAY` nếu thời gian chơi $\ge 10$s sau 120s không thao tác.
- **Mục tiêu luyện tập hàng ngày (Daily Goal)**:
  - Lưu trữ mục tiêu từ 5 đến 120 phút (mặc định 20 phút) trong DataStore với thanh trượt trực quan.
- **Bảng điều khiển tiến trình (Progress Dashboard)**:
  - Bộ lọc 7 ngày / 30 ngày / Tất cả thời gian.
  - Độ chính xác bình quân gia quyền ($\sum \text{Correct} / \sum \text{Expected}$).
  - Thống kê 5 cao độ bấm sai nhiều nhất (5 Weakest Pitches).
  - Chuỗi ngày luyện tập liên tục dài nhất (Longest streak).
  - Xóa phiên luyện tập với hộp thoại xác nhận an toàn.

### Gate F — Sao lưu, Khôi phục & Đặt lại Toàn bộ (Backup, Restore & Reset)
- **Sao lưu thủ công qua SAF**:
  - Nén toàn bộ dữ liệu thành tệp `piano-trainer-backup-YYYYMMDD-HHmm.zip` gồm `manifest.json`, dữ liệu các bảng Room dạng JSON, tệp cấu hình preferences JSON, các tệp MIDI nguồn và các bản thu âm `.mid` / `.m4a`.
- **Khôi phục an toàn (Safe Restore with ZipSlip Protection)**:
  - Kiểm tra đường dẫn tệp chống tấn công ZipSlip, giới hạn luồng nén (tối đa 5000 mục, 500MB), dàn dựng dữ liệu tạm thời trước khi ghi đè cơ sở dữ liệu.
- **Xóa toàn bộ dữ liệu người dùng (`resetAllUserData`)**:
  - Xóa sạch cơ sở dữ liệu Room, dọn dẹp thư mục `filesDir/songs`, `filesDir/recordings`, tệp tạm và đưa DataStore về mặc định.

---

## 2. Bảng phân cấp kiểm thử (Verification Matrix)

| Hạng mục / Tính năng | Cấp độ kiểm thử | Trạng thái | Ghi chú kiểm thử |
| :--- | :--- | :--- | :--- |
| **MidiFileParser (Format 0/1, Chords, Tempo, CC64, Time Signature)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 8/8 tests pass trong `MidiParserUnitTest` |
| **MidiRecordingWriter (Standard MIDI Format 0, PPQ 480, VLQ, Meta)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 2/2 tests pass trong `MidiRecordingWriterUnitTest` |
| **SongRepository (Streaming 20MB limit, SHA-256, Room v5 atomic, Presets CRUD)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 7/7 tests pass trong `SongRepositoryUnitTest` |
| **Database Migration v1 -> v5 & Maintenance** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 1/1 test pass trong `DatabaseMigrationUnitTest` |
| **RealPracticeEngine (Order-independent Chords, Timeline Anchor, Speed clamp)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 5/5 tests pass trong `PracticeEngineUnitTest` |
| **VisibleNoteWindowSelector (Interval binary search, 100k notes benchmark < 50ms)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 4/4 tests pass trong `VisibleNoteWindowUnitTest` |
| **ProgressRepository (Weighted accuracy, Top 5 Weak pitches, Streaks calculation)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 3/3 tests pass trong `ProgressAggregationUnitTest` |
| **BackupRepository (Zip archive, Room restore, ZipSlip security rejection)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 2/2 tests pass trong `BackupRestoreUnitTest` |
| **AudioInputCoordinator (Thread-safe mutex, Conflict prevention)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 2/2 tests pass trong `AudioInputCoordinatorUnitTest` |
| **Core Music & Geometry Helpers (NoteHelper, Geometry, BeatGrid)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 7/7 tests pass trong `PianoTrainerUnitTest` |
| **Tổng số Unit Tests thực tế** | `VERIFIED_LOCAL` | **41/41 PASS (100%)** | Chạy thành công với `gradlew.bat testDebugUnitTest` |
| **Biên dịch APK Debug** | `VERIFIED_LOCAL` | **PASS** | `gradlew.bat assembleDebug` thành công |
| **Kiểm tra phân tích tĩnh Lint** | `VERIFIED_LOCAL` | **PASS (0 Errors)** | `gradlew.bat lintDebug` thành công |
| **Giao diện Free Play, Rising Trails & Audio Recording UI** | `EMULATOR_PENDING` | **CHỜ MÁY ẢO AI STUDIO** | Cần Preview/Emulator trên AI Studio để kiểm tra hiệu ứng hạt nốt bay và thanh điều khiển |
| **Giao diện Landscape Practice Player, Count-In Overlay & Presets** | `EMULATOR_PENDING` | **CHỜ MÁY ẢO AI STUDIO** | Cần Preview/Emulator trên AI Studio để kiểm tra thẻ đếm nhịp và lưu preset |
| **Giao diện Progress Dashboard & Settings Backup / Restore** | `EMULATOR_PENDING` | **CHỜ MÁY ẢO AI STUDIO** | Cần Preview/Emulator trên AI Studio để kiểm tra SAF picker và biểu đồ tiến trình |
| **Cảm ứng đa điểm bàn phím ảo (Multi-touch 3-5 ngón)** | `NEEDS_REAL_DEVICE` | **CẦN THIẾT BỊ THẬT** | Cần màn hình cảm ứng điện thoại thật để kiểm tra độ nhạy gõ hợp âm |
| **Ghi âm Microphone thật & Phát lại tệp `.m4a`** | `NEEDS_REAL_DEVICE` | **CẦN THIẾT BỊ THẬT** | Cần phần cứng micro và loa điện thoại thật để kiểm tra chất lượng âm thanh |
| **Kết nối Bluetooth LE MIDI / USB MIDI với đàn Piano điện thật** | `NEEDS_REAL_PIANO` | **CẦN ĐÀN PIANO THẬT** | Cần cắm USB MIDI hoặc kết nối BLE MIDI với đàn thật để kiểm tra nhận diện phím và độ trễ |

---

## 3. Hướng dẫn kiểm tra trên Google AI Studio

1. Kéo commit mới nhất từ branch `main`.
2. Mở Android Preview / Emulator:
   - Vào **Chơi tự do (Free Play)**:
     - Gõ phím đàn ảo và kiểm tra hiệu ứng vệt nốt bay lên (Rising Trails).
     - Bật tính năng thu âm (có/không kèm Microphone), chơi một đoạn nhạc ngắn và bấm Lưu.
     - Kiểm tra tệp bản thu xuất hiện trong danh sách và bấm Phát lại.
   - Vào **Thư viện bài hát (My Songs)**:
     - Mở bài hát, chọn cấu hình tay/đoạn lặp A-B và lưu thành Preset.
     - Bắt đầu luyện tập ở chế độ Rhythm để kiểm tra màn hình đếm nhịp chuẩn bị (Count-In).
   - Vào **Tiến trình (Progress)**:
     - Kiểm tra biểu đồ tiến trình, mục tiêu ngày và danh sách 5 nốt gõ sai nhiều nhất.
   - Vào **Cài đặt (Settings)**:
     - Thử tạo bản sao lưu dữ liệu ra file ZIP và kiểm tra khôi phục bản sao lưu.
