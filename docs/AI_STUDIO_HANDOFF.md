# AI STUDIO HANDOFF REPORT — LOCAL SPRINT 1

**Đợt phát triển**: `LOCAL SPRINT 1 — MIDI SONG PIPELINE & LANDSCAPE PRACTICE PLAYER`  
**Repository**: `https://github.com/VietCodeBug/StudyPiano`  
**Branch**: `main`  
**Base Commit**: `a053781`  
**Môi trường dự án**: `compileSdk 35`, `targetSdk 35`, `minSdk 26`, JDK 17 Adoptium  
**Trạng thái Build & Test Local**:
- Unit Tests: **25/25 PASS** (100%)
- `assembleDebug`: **PASS** -> `app\build\outputs\apk\debug\app-debug.apk`
- Dữ liệu Production: Sạch hoàn toàn, không chứa sample MIDI, không fake Bluetooth / device và không chứa dữ liệu bài hát giả lập.

---

## 1. Tóm tắt tính năng hoàn thành trong Sprint 1

### Milestone A — MIDI Domain Model & Parser Chuẩn Hóa
- Bộ parser `MidiFileParser` hỗ trợ Standard MIDI Format 0 & Format 1.
- Xử lý running status, Note-Off qua velocity 0, gán chordId cho các nốt đồng âm trong cửa sổ 25ms.
- Phân đoạn tempo segment chính xác, tính toán tổng thời lượng và xử lý sustain pedal (CC64).
- Tự động đóng các nốt chưa có Note-Off tại `End-of-Track` và xử lý exception an toàn khi gặp header lỗi.
- Kiểm thử đơn vị: 8/8 tests pass (`MidiParserUnitTest`).

### Milestone B — Nhập bài MIDI (SAF), Quản lý Thư viện & Bảng chuẩn bị luyện tập
- Tích hợp SAF (`OpenDocument` với mimeType `audio/midi`, `audio/x-midi`, `*/*`).
- Lưu trữ an toàn trong `context.filesDir/songs/{songId}/source.mid`, hash SHA-256 chống import trùng lặp.
- Màn hình thư viện `MySongsScreen`:
  - Tìm kiếm bài hát theo tên / tên file.
  - Sắp xếp: Gần đây nhất, Luyện tập gần nhất, Tên A–Z.
  - Bộ lọc bài hát yêu thích.
  - Đổi tên bài hát (Dialog cập nhật Room DB).
  - Xác nhận xóa bài (Cascade xóa metadata + tracks + notes + tempos và thư mục local).
  - BottomSheet chuẩn bị (`SongPreparationBottomSheet`): Cấu hình Track & Hand assignment (Tay phải = Xanh cyan/blue, Tay trái = Cam, Bỏ qua = Ignore), Điều chỉnh BPM gốc, Chọn chế độ luyện tập (*Chờ đúng nốt* / *Chạy theo nhịp*).
- Kiểm thử đơn vị Robolectric: 5/5 tests pass (`SongRepositoryUnitTest`).

### Milestone C — Đồng hồ phát lại & Timing Engine Đơn điệu
- Động cơ luyện tập `RealPracticeEngine` sử dụng `PracticeClock` trừu tượng hóa thời gian đơn điệu (`elapsedRealtime()`).
- Hỗ trợ đầy đủ: Play / Pause / Resume / Seek / Speed Multiplier (0.25x - 1.5x).
- Lặp đoạn A–B theo timestamp mili-giây: Kiểm tra ràng buộc $B > A$, reset trạng thái hợp âm đang gõ dở, vòng lặp timeline mượt mà và tăng `lapCounter`.
- Tự động tạm dừng (`onBackgroundPause`) khi ứng dụng vào background hoặc mất focus.
- Đảm bảo tính đơn nhiệm: 1 single ticker coroutine job, dọn dẹp sạch khi dispose.

### Milestone D — Giao diện Landscape Practice Player & Falling Notes Canvas
- Khóa hướng màn hình ngang cảm biến (`ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE`) khi vào phòng luyện và hoàn trả khi thoát.
- Bố cục chuẩn tỷ lệ Landscape:
  - Top Toolbar nhỏ gọn (~48dp): Thoát, tên bài, thời gian hiện tại / tổng, tốc độ phát, chế độ, nút Loop A-B, Luyện lại, Tạm dừng/Tiếp tục, Mở cài đặt, Toàn màn hình.
  - Khung Canvas nốt rơi (~67%): Tầm nhìn nốt rơi (2.5s / 4s / 6s), màu nốt chuẩn (*Tay phải = Xanh cyan/blue, Tay trái = Cam, Không màu tím/hồng*), vạch nhịp (beat grid) và vạch neon đón nốt phát sáng.
  - Bàn phím Piano tương tác (~33%): Hỗ trợ đa điểm chạm (multi-touch) gửi sự kiện `VIRTUAL_KEYBOARD`, highlight phím bấm, phím mục tiêu và số ngón tay.

### Milestone E — Động cơ chấm điểm, Chế độ chờ nốt & Lưu trữ tiến độ
- **Chế độ Chờ đúng nốt (Wait-for-note)**: Gom nhóm hợp âm bắt đầu trong 25ms, cho phép người dùng bấm đồng thời hoặc tuần tự không phân biệt thứ tự trước sau.
- **Chế độ Theo nhịp (Rhythm)**: Cửa sổ chấm điểm dung sai $\pm 180$ms, phân loại Chính xác (Correct), Sớm (Early), Muộn (Late), Sai cao độ (Wrong) và Quá thời gian (Missed).
- Bộ đếm thời gian luyện tập thực tế đơn điệu, không tính thời gian tạm dừng.
- Lưu trữ kết quả `PracticeSession` vào Room DB và cập nhật `lastPracticedAt` cho bài hát.
- Kiểm thử đơn vị: 6/6 tests pass (`PracticeEngineUnitTest`).

---

## 2. Bảng phân cấp 4 mức kiểm thử (Verification Matrix)

| Hạng mục / Tính năng | Cấp độ kiểm thử | Trạng thái | Ghi chú kiểm thử |
| :--- | :--- | :--- | :--- |
| **MidiFileParser (Format 0, 1, Chords, Tempo, CC64)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 8/8 unit tests pass trong `MidiParserUnitTest` |
| **Import MIDI & Room Storage (SHA-256, Rename, Delete, Tracks)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 5/5 Robolectric tests pass trong `SongRepositoryUnitTest` |
| **RealPracticeEngine (Wait-for-note Chords, Rhythm Scoring, Seek, Loop A-B, Timer, Speed)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 6/6 unit tests pass trong `PracticeEngineUnitTest` |
| **NoteHelper & Domain Core logic** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 6/6 unit tests pass trong `PianoTrainerUnitTest` |
| **Giao diện Landscape Practice Player & Falling Notes Canvas** | `EMULATOR_PENDING` | **CHỜ MÁY ẢO AI STUDIO** | Cần Preview / Emulator trên Google AI Studio để kiểm tra trực quan layout và animation nốt rơi |
| **SongPreparationBottomSheet & Track/Hand Assignment UI** | `EMULATOR_PENDING` | **CHỜ MÁY ẢO AI STUDIO** | Cần Preview trên Google AI Studio để xác nhận tương tác BottomSheet |
| **Cảm ứng đa điểm bàn phím ảo (Multi-touch on-screen keyboard)** | `NEEDS_REAL_DEVICE` | **CẦN THIẾT BỊ THẬT** | Cần màn hình cảm ứng điện thoại Android thật để test gõ hợp âm 3-5 ngón đồng thời |
| **Độ trễ âm thanh & Engine Ticker 40Hz trên phần cứng thực** | `NEEDS_REAL_DEVICE` | **CẦN THIẾT BỊ THẬT** | Cần điện thoại thật để đo độ mượt mà khi nốt rơi |
| **Kết nối Bluetooth LE MIDI / USB MIDI với đàn Piano điện** | `NEEDS_REAL_PIANO` | **CẦN ĐÀN PIANO THẬT** | Cần kết nối với đàn Roland/Yamaha qua Bluetooth/USB MIDI để đo độ trễ phần cứng thực tế |
| **Nhận diện nốt qua Microphone (Pitch Detection)** | `NEEDS_REAL_PIANO` | **CẦN ĐÀN PIANO THẬT** | Cần âm thanh piano thật ở môi trường thực tế để kiểm tra bộ lọc nhiễu |

---

## 3. Hướng dẫn kiểm tra trên Google AI Studio

1. Kéo commit mới nhất từ branch `main` của repository `https://github.com/VietCodeBug/StudyPiano`.
2. Mở Android Preview / Emulator:
   - Vào mục **Thư viện bài hát (My Songs)**.
   - Bấm nút **+ Nhập bài hát MIDI** và chọn 1 file `.mid` bất kỳ.
   - Nhấn vào bài hát để mở **Bảng chuẩn bị luyện tập (SongPreparationBottomSheet)**.
   - Chọn tay (Tay phải / Tay trái / Cả 2 tay), chọn chế độ (*Chờ đúng nốt* hoặc *Chạy theo nhịp*), chỉnh BPM và bấm **Bắt đầu luyện tập**.
   - Quan sát màn hình tự động chuyển sang hướng nằm ngang (**Landscape**).
   - Kiểm tra nốt rơi xuống vạch hitline và bàn phím ảo highlight đúng nốt cần gõ.
