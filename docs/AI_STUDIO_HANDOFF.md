# AI STUDIO HANDOFF REPORT — LOCAL SPRINT 1.1

**Đợt phát triển**: `LOCAL SPRINT 1.1 — MIDI PIPELINE, TIMING & DATA HARDENING`
**Repository**: `https://github.com/VietCodeBug/StudyPiano`
**Branch**: `main`
**Base Commit**: `f15552d8683219d7e4789f929052ca9d2db16e24`
**Môi trường dự án**: `compileSdk 35`, `targetSdk 35`, `minSdk 26`, JDK 17 Adoptium  
**Room Database Schema**: Version 4 (`MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`)

---

## 1. Tóm tắt các hạng mục Hardening & Nâng cấp trong Sprint 1.1

### Gate A — Quản lý Nhập File, Giới hạn Dung lượng & Room Database v4
- **Streaming Buffer & Giới hạn 20MB (`MAX_MIDI_FILE_SIZE_BYTES = 20MB`)**:
  - Kiểm tra dung lượng từ Document Provider; stream đọc từng block 64KB, tính dung lượng on-the-fly, ném `MidiFileTooLargeException` nếu vượt quá 20MB.
  - Tính toán mã băm SHA-256 trực tiếp trong quá trình stream, phát hiện trùng lặp (`DuplicateMidiException`).
- **Giao dịch Room Atomic & Dọn dẹp File**:
  - Lưu toàn bộ `song + tracks + notes + tempos + timeSignatures` trong một giao dịch Room duy nhất (`database.withTransaction`).
  - Nếu phân tích cú pháp hoặc cơ sở dữ liệu thất bại, tự động dọn sạch file tạm và thư mục bài hát.
- **Nâng cấp Room Database Version 4**:
  - Thêm cột `noteCount INTEGER NOT NULL DEFAULT 0` vào bảng `imported_songs` và tự động backfill từ `song_notes`.
  - Tạo bảng `song_time_signatures` kèm Foreign Key `CASCADE` và Index `songId`.
  - Khởi tạo mặc định `4/4` tại tick 0 nếu file MIDI không chứa nhịp phách.
  - Đổi tên `SampleDataSeeder` thành `DatabaseMaintenance`.

### Gate B — Engine Timeline Anchor, Đổi tốc độ mượt & Nhận diện Hợp âm Độc lập Thứ tự
- **Nhận diện hợp âm độc lập thứ tự (Order-Independent Chord Matching)**:
  - Cấu trúc `ExpectedChord(chordId, startMs, notes, expectedPitches: Set<Int>)`.
  - Hỗ trợ gõ các nốt trong hợp âm theo bất kỳ thứ tự nào ở cả hai chế độ **Wait Mode** và **Rhythm Mode** (ví dụ: gõ `G4 -> C4 -> E4` đều được ghi nhận đúng).
  - Nốt bấm thừa/sai được ghi nhận riêng mà không làm mất trạng thái các nốt đã gõ đúng trong hợp âm.
  - Trong Rhythm Mode: khi hết thời gian dung sai ($\pm 180$ms), các nốt chưa bấm trong hợp âm được ghi nhận là Missed và chuyển sang hợp âm tiếp theo một cách đồng bộ.
- **Timeline Anchor (`anchorTimeline`) & Giữ vững Playhead khi đổi tốc độ**:
  - Đóng băng thời gian bài hát vào `basePositionMs` và reset mốc monotonic `lastResumeMonotonicMs` trước khi đổi tốc độ, seek hoặc pause.
  - Tốc độ phát được giới hạn chặt chẽ trong khoảng `0.25x` đến `1.5x`.
- **Target BPM & Máy đếm nhịp động**:
  - Tính toán `speedMultiplier = targetInitialBpm / originalDefaultBpm`.
  - Máy đếm nhịp tự động bám theo `effectiveBpm = currentSegmentBpm * speedMultiplier` khi bài hát chuyển đoạn tempo.

### Gate C — Dữ liệu Playback, Vạch nhịp Động & Điều hướng An toàn
- **Mô hình `SongPlaybackData`**: Gom nhóm đầy đủ dữ liệu bài hát, nốt, track, tempo và số chỉ nhịp.
- **Vạch nhịp động (`BeatGridCalculator`)**: Tính toán vạch chia phách và vạch đầu ô nhịp dựa trên Tempo Map và Time Signatures thực tế thay vì cố định 500ms / 120 BPM.
- **Mã hóa URL an toàn**: Mã hóa toàn bộ tham số chuỗi bằng `Uri.encode` trong `Screen.kt`.
- **Xác thực Track**: Ngăn chặn bắt đầu luyện tập khi chưa chọn track nào (`activeTracks.isEmpty()`).

### Gate D — Chuẩn hóa Ngôn ngữ & Làm sạch Bảng màu
- **Làm sạch chuỗi & Hardcode**: Loại bỏ hoàn toàn các chuỗi gán cứng "Victor VT02", chuyển thành tên thiết bị MIDI động hoặc chuỗi tổng quát chuẩn.
- **Chuẩn hóa Bảng màu**:
  - Tay phải: Xanh Electric Cyan/Blue (`#00E5FF`, `#38BDF8`, `#3B82F6`).
  - Tay trái: Cam ấm nổi bật (`#F97316`).
  - Đúng: Xanh lá (`#10B981`, `#22C55E`).
  - Sai: Đỏ (`#EF4444`).
  - **100% không còn màu tím hay hồng trong toàn bộ mã nguồn production**.

---

## 2. Bảng phân cấp 4 mức kiểm thử (Verification Matrix)

| Hạng mục / Tính năng | Cấp độ kiểm thử | Trạng thái | Ghi chú kiểm thử |
| :--- | :--- | :--- | :--- |
| **MidiFileParser (Format 0/1, 25ms Chords, Tempo, CC64, Time Signature)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 8/8 unit tests pass trong `MidiParserUnitTest` |
| **SongRepository (Stream 20MB limit, SHA-256, Room v4 atomic, Cascade delete)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 7/7 Robolectric tests pass trong `SongRepositoryUnitTest` |
| **Database Migration v3 -> v4 & Maintenance** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 1/1 Robolectric test pass trong `DatabaseMigrationUnitTest` |
| **RealPracticeEngine (Order-independent Chords, Timeline Anchor, Speed clamp, Active timer)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 5/5 unit tests pass trong `PracticeEngineUnitTest` |
| **Core Music & Geometry Helpers (NoteHelper, Geometry, BeatGrid)** | `VERIFIED_LOCAL` | **ĐÃ XÁC MINH** | 6/6 unit tests pass trong `PianoTrainerUnitTest` |
| **Giao diện Landscape Practice Player, Beat Grid & Falling Notes Canvas** | `EMULATOR_PENDING` | **CHỜ MÁY ẢO AI STUDIO** | Cần Preview / Emulator trên Google AI Studio để kiểm tra vạch nhịp động và layout nốt rơi |
| **SongPreparationBottomSheet, Track/Hand Selection & Validation Dialog** | `EMULATOR_PENDING` | **CHỜ MÁY ẢO AI STUDIO** | Cần Preview trên Google AI Studio để kiểm tra trải nghiệm chọn track |
| **Cảm ứng đa điểm bàn phím ảo (Multi-touch 3-5 ngón)** | `NEEDS_REAL_DEVICE` | **CẦN THIẾT BỊ THẬT** | Cần màn hình cảm ứng điện thoại thật để kiểm tra độ nhạy gõ hợp âm |
| **Độ mượt mà và thời gian phản hồi thực tế (Monotonic Ticker 40Hz)** | `NEEDS_REAL_DEVICE` | **CẦN THIẾT BỊ THẬT** | Cần điện thoại thật để đo FPS và độ phản hồi |
| **Kết nối Bluetooth LE MIDI / USB MIDI với đàn Piano điện** | `NEEDS_REAL_PIANO` | **CẦN ĐÀN PIANO THẬT** | Cần cắm USB MIDI hoặc kết nối BLE MIDI với đàn thật để kiểm tra nhận diện phím và độ trễ |
| **Nhận diện cao độ qua Microphone (Pitch Detection)** | `NEEDS_REAL_PIANO` | **CẦN ĐÀN PIANO THẬT** | Cần âm thanh piano acoustic/speaker thật để kiểm tra độ chính xác |

---

## 3. Hướng dẫn kiểm tra trên Google AI Studio

1. Kéo commit mới nhất từ branch `main`.
2. Mở Android Preview / Emulator:
   - Vào **Thư viện bài hát (My Songs)**.
   - Nhập 1 file MIDI bất kỳ và kiểm tra thông báo Toast hiển thị số lượng nốt thực tế (`noteCount`).
   - Mở bài hát, chỉnh sửa cấu hình track/tay, thử bỏ chọn tất cả các track để kiểm tra thông báo cảnh báo lỗi.
   - Chọn tốc độ BPM mong muốn và bấm **Bắt đầu luyện tập**.
   - Kiểm tra chuyển hướng màn hình ngang (**Landscape**), kiểm tra các vạch ô nhịp hiển thị tương ứng với nhịp bài hát.
   - Thử nghiệm tăng/giảm tốc độ trong Player và kiểm tra playhead không bị nhảy bất thường.
