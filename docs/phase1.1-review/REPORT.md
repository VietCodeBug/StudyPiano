# StudyPiano — Báo cáo Đợt 1 + 1.1

## A. Tóm tắt

- Hoàn tất Đợt 1.1 trong phạm vi app Android all-in-one cá nhân; không làm web/cloud, renderer, luyện đàn mới, MIDI realtime, scoring, pedal hay audio engine.
- MusicXML được parse đến `END_DOCUMENT`, chỉ chấp nhận root `score-partwise`/`score-timewise`, tắt xử lý DOCDECL/entity. MusicXML thực tế có external DOCTYPE được chấp nhận mà không đọc file ngoài/không truy cập mạng; entity trong nội dung bị từ chối.
- Audio chỉ được ghi nhận là “nhận diện header”, hỗ trợ ID3v2.3, ID3v2.4 và MP3 frame không ID3; file quá ngắn/header hỏng có lỗi rõ, không tuyên bố đã xác minh phát được.
- Import/delete dùng journal bền vững trong `files/song_operations`, chung mutex với recovery trước khi thư viện phát dữ liệu. Cancellation sát commit kiểm tra lại DB trong `NonCancellable`, không xóa file nếu row đã commit.
- Backup format v2 lưu `song_assets.json`, giữ MIDI/MusicXML/reference audio và remap đường dẫn về `filesDir` mới. Backup v1 vẫn restore; chỉ tái tạo MIDI asset từ `songs/<id>/source.mid` vì đây là liên kết cũ có bằng chứng chắc chắn.
- Không có thay đổi ngoài phạm vi. Không reset, commit hoặc push.

## B. Baseline và đối chiếu nhận xét

- Branch: `main`.
- Baseline/HEAD khi bắt đầu: `8e80b57c3ea4d392bfdb1adc3bdf44b49be46cfd`.
- `AGENTS.md`: không tồn tại trong workspace.
- Toàn bộ working tree Đợt 1 được giữ nguyên và nằm trong patch đầy đủ.
- Nhận xét vẫn đúng trước khi sửa: `ContentPackImporter.kt` trả ngay tại START_TAG và dùng quét 8 KB; audio chỉ sniff header nhưng báo “hợp lệ”; `SongRepositoryImpl.kt` copy file trước DB, cancellation xóa thư mục vô điều kiện và delete dùng cache/`deleteOnExit`; `BackupRepositoryImpl.kt` copy thư mục songs nhưng không lưu SongAsset và giữ absolute path.

## C. Thay đổi chính theo file

| File/nhóm | Thay đổi | Lý do |
|---|---|---|
| `ContentPackImporter.kt`, `MusicXmlValidator.kt` | Full-document XML validation, root validation, DOCDECL/entity disabled | Phát hiện XML cắt/hỏng và chặn external access |
| `AudioHeaderSniffer.kt` | Sniff ID3v2.3/v2.4, frame-only MP3, OGG/WAV/MP4 | Nhận diện đúng hơn nhưng không đồng nhất với playable validation |
| `SongRepositoryImpl.kt` | Durable import/delete journal, mutex, startup/library recovery, cancellation reconciliation | Giữ DB/file nhất quán qua process death |
| `BackupRepositoryImpl.kt` | Backup v2 SongAsset và relative path; v1 MIDI fallback | Round-trip đủ tài nguyên và không giữ path máy cũ |
| `PianoTrainerDatabase.kt`, schema 6, SongAsset files | Room 5→6 không destructive | Liên kết tài nguyên theo một songId |
| Unit/instrumentation tests | XML, audio, recovery fixtures, backup round-trip, migration | Kiểm thử rủi ro mất dữ liệu/persistence |

## D. Dữ liệu và recovery

- Room: version 5 → 6; bảng `song_assets`, FK cascade về `imported_songs`, unique `(songId,type)`.
- Import ghi journal trước khi promote payload sang `files/songs/<songId>`, rồi commit Room. Recovery xóa orphan khi DB chưa commit; giữ/hoàn thiện bài khi DB đã commit.
- Delete chuyển folder vào payload của journal bền vững trước khi xóa DB. Recovery restore payload nếu row còn; dọn payload nếu row đã xóa.
- Fixture recovery mô phỏng trạng thái đĩa/DB sau process death; đây **không phải** test kill process thật.
- Giới hạn import giữ nguyên: MIDI 20 MB; pack nén 25 MB; mỗi entry 25 MB; giải nén 60 MB; 128 entries.

## E. Kết quả kiểm thử cuối

| Gate | Kết quả | Bằng chứng |
|---|---|---|
| Unit + Robolectric integration | PASS — 107 tests, 0 fail/error/skip | `test-results/unit/` |
| MusicXML unit | PASS | XML root đúng nhưng tail cắt; DOCTYPE external không được resolve |
| Instrumentation Android | PASS — 4/4 | `test-results/instrumentation/` |
| Migration 5→6 instrumentation | PASS — 1/1 | `DatabaseMigrationInstrumentedTest` |
| MusicXML instrumentation | PASS — 2/2 | truncated tail + external DOCTYPE trên Android 12 emulator |
| UI thư viện sau import và restart | PASS | `screenshots/phase1.1-library-after-import.png`, `phase1.1-library-after-restart.png`; 22 bài trước/sau restart |
| MIDI phần cứng | NOT_RUN — không có thiết bị MIDI vật lý trong môi trường | Không suy diễn từ emulator/unit test |

Các lần gate trung gian đã từng FAIL và đã sửa: journal thiếu tạo thư mục cha trước rename; fixture DOCTYPE sai cú pháp; JAXP feature không được parser Android hỗ trợ. Kết quả XML trong ZIP là lần chạy cuối PASS, không ghi đè lịch sử này thành chưa từng xảy ra.

## F. Lệnh gate cuối

```text
.\gradlew.bat --no-daemon --max-workers=1 --no-configuration-cache "-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=320m -XX:ReservedCodeCacheSize=128m -Xss512k -Dfile.encoding=UTF-8" :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat --no-daemon --max-workers=1 --no-configuration-cache "-Dorg.gradle.jvmargs=-Xmx896m -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=96m -Xss512k -Dfile.encoding=UTF-8" :app:connectedDebugAndroidTest
```

- APK: `app-debug.apk` (để ngoài ZIP), 20,511,841 bytes.
- SHA256: `62E06D48ED18790E1971F6D5CF5D74DF91B2F84DD17CA83A1D5F9A32CAAE4A3A`.

## G. Hạn chế/backlog

- Sniff audio không đảm bảo decoder của thiết bị phát được toàn bộ file; cố ý không sửa audio engine trong đợt này.
- MusicXML entity bị từ chối; external DTD không được tải, nên không thực hiện DTD validation. Đây là giới hạn an toàn được báo rõ, không âm thầm bỏ sheet đã khai báo.
- Backup v1 không có `song_assets.json`; chỉ MIDI chuẩn `source.mid` được liên kết lại. Không đoán ownership của XML/audio cũ nếu backup không có metadata.
- Recovery tests là fault-state fixtures, không phải kill-app automation.
- Kiểm tra thủ công còn lại: import pack thật từ Files trên điện thoại, force-stop/reboot thiết bị, mở lại bài và thử MIDI hardware.
