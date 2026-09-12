# StudyPiano — Báo cáo Đợt 1.3

## Phạm vi

- Chỉ sửa tính đúng đắn restore/recovery; không đổi UI, MIDI realtime, audio engine, renderer hay mở rộng tính năng.
- Giữ nguyên working tree Đợt 1.2; không reset, commit hoặc push.
- `AGENTS.md`: không tồn tại.
- Branch `main`; baseline đầy đủ `8e80b57c3ea4d392bfdb1adc3bdf44b49be46cfd`.

## Sửa ba lỗi review

### 1. Commit marker chính xác trong Room

- Database tăng version 6 → 7, migration không destructive tạo bảng `restore_commits(operationId, committedAt)`.
- Mỗi restore có UUID operationId riêng.
- Marker được insert ở cuối **cùng Room transaction** khôi phục dữ liệu. Nếu transaction rollback, marker không tồn tại; nếu commit, marker tồn tại.
- Startup recovery và error-handler chỉ căn cứ marker operationId này để chọn finalize hay rollback. Không còn suy đoán từ `(songId, MIDI hash)`.
- Sau khi finalize file và xóa journal thành công, marker mới được xóa.

### 2. State machine và topology thực tế

- Một implementation dùng chung: `RestoreOperationRecovery`, được gọi cả tại catch và trước khi thư viện phát dữ liệu.
- Journal theo dõi riêng intent/done của preserve/promote songs và recordings.
- State được ghi bằng file tạm, `fd.sync()`, rồi publish; nếu metadata không xác định được, recovery giữ dữ liệu và báo lỗi.
- PREPARED chỉ kiểm tra/cleanup file mới; tuyệt đối không di chuyển thư viện đang dùng.
- Rollback xem topology `active`, `old*`, `new*` cùng cờ `had*` và SHA-256 toàn cây. Nó chỉ đảo rename đã thực sự xảy ra.
- Finalize yêu cầu DB marker và xác minh hash toàn bộ songs/recordings mới, không chỉ MIDI.
- Journal chỉ bị xóa sau khi hash/trạng thái cuối đã xác minh. Rename fail giữ mọi bản để retry.
- Restore mới phải giải quyết toàn bộ RESTORE journal cũ dưới cùng `SONG_FILE_OPERATION_MUTEX` trước khi tạo operation mới.

### 3. Không rollback kép

- `swapRestoreFiles` không tự rollback nữa.
- Mọi lỗi/cancellation và startup đều đi qua duy nhất `RestoreOperationRecovery.reconcile()`.
- Reconcile idempotent: PREPARED không đụng active; partial preserve/promote đảo theo topology; marker commit finalize dữ liệu mới.

## Schema và migration

- Trước: Room 6.
- Sau: Room 7.
- File schema: `app/schemas/.../7.json`.
- Migration instrumentation 6→7 xác minh SongAsset cũ còn nguyên và bảng marker ghi/đọc được.

## Kiểm thử thực tế

| Gate | Kết quả |
|---|---|
| Regression restore/recovery Room file-backed + file thật | PASS — 11/11 |
| Process-death fixture tại PREPARED | PASS — thư viện songs/recordings cũ giữ nguyên theo nội dung/hash |
| Gián đoạn sau từng rename preserve/promote songs/recordings | PASS — 4 checkpoint |
| Rename rollback fail rồi retry | PASS — journal/payload giữ ở lần fail, lần retry phục hồi đúng; pass sau không đổi dữ liệu |
| Cùng songId + MIDI hash, sheet/audio khác, lỗi trước DB commit | PASS — toàn bộ hash file cũ giữ nguyên |
| DB commit xong trước cleanup | PASS — marker finalize toàn bộ file mới |
| Không có songs nhưng có recordings | PASS |
| Restore mới khi còn PREPARED journal | PASS — journal cũ xử lý trước |
| Full unit/Robolectric | PASS — 121 tests, 0 fail/error/skip |
| Instrumentation Android 12 | PASS — 7 tests, 0 fail/error |
| Migration 5→6 | PASS |
| Migration 6→7 | PASS |
| `assembleDebug` | PASS |
| MIDI phần cứng | NOT_RUN — ngoài phạm vi và không có thiết bị vật lý |

Các test gián đoạn là fault injection/fixture mô phỏng trạng thái sau process death; không phải test kill-process thực tế.

Lỗi gate trung gian được ghi đúng: thiếu import `assertFalse` trong test và một test Room dùng absolute path làm database name gây `SQLITE_CANTOPEN`; fixture đã chuyển sang database name chuẩn trong databasesDir. Không có lỗi implementation còn lại ở gate cuối.

## Lệnh cuối

```text
.\gradlew.bat --no-daemon --max-workers=1 --no-configuration-cache "-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=320m -XX:ReservedCodeCacheSize=128m -Xss512k -Dfile.encoding=UTF-8" :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat --no-daemon --max-workers=1 --no-configuration-cache "-Dorg.gradle.jvmargs=-Xmx896m -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=96m -Xss512k -Dfile.encoding=UTF-8" :app:connectedDebugAndroidTest
```

- APK: `study-piano-phase1.3-app-debug.apk`, 20,577,810 bytes.
- SHA256 APK: `E61F446F5AC9E867F6EFC97C4800D7026C65B624D2C6DA6D8935F978D67B7A62`.

## Giới hạn

- Không automation kill process thật tại instruction CPU; fault injection đặt sau từng rename và trước state update để tái tạo đúng topology gián đoạn.
- Nếu filesystem xuất hiện topology mơ hồ hoặc hash không khớp, recovery cố ý giữ journal và các bản dữ liệu, rồi báo lỗi thay vì tự chọn/xóa.
- Không chụp UI vì Đợt 1.3 không thay đổi giao diện.
