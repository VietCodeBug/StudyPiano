# StudyPiano — Báo cáo Đợt 1.2

## Phạm vi và baseline

- Giữ nguyên working tree Đợt 1.1; không reset, commit hoặc push.
- `AGENTS.md` không tồn tại trong workspace.
- Branch `main`, baseline đầy đủ: `8e80b57c3ea4d392bfdb1adc3bdf44b49be46cfd`.
- Không thay đổi UI, cloud, renderer, logic luyện đàn, MIDI realtime, scoring, pedal hay audio engine.

## Thay đổi

### Restore an toàn

- Backup được giải nén vào cache staging và parse/validate toàn bộ metadata trước khi chạm dữ liệu hiện tại.
- Backup v2 bắt buộc có `data/song_assets.json`; từng asset phải tham chiếu song tồn tại, nằm dưới đúng `files/songs/<songId>/`, có file thật và đúng kích thước. MIDI có SHA-256 thì hash staged file phải khớp.
- Backup v1 hợp lệ vẫn được hỗ trợ qua đường dẫn chuẩn `<songId>/source.mid`; không suy đoán MusicXML/audio không có metadata.
- Restore dùng chung `SONG_FILE_OPERATION_MUTEX` với import/delete/recovery.
- Tệp mới được copy vào journal bền vững `files/song_operations/restore_*`. Bộ file cũ được giữ trong journal trước khi promote bộ mới. Nếu copy, Room transaction hoặc cancellation trước commit lỗi, filesystem được rollback. Nếu cancellation sau commit, fingerprint DB xác nhận commit và giữ bộ file mới.
- Journal RESTORE cũng được xử lý trước khi flow thư viện phát dữ liệu; lỗi rename không làm xóa bản còn lại.

### Delete rollback/cancellation

- Kết quả mọi rename phục hồi đều được kiểm tra.
- Nếu DB còn row mà rollback rename thất bại, payload và journal được giữ nguyên để lần recovery sau retry; lỗi trả về nói rõ journal được giữ.
- Nếu DB transaction đã commit xóa row, cancellation sau commit finalize xóa journal/payload, không khôi phục file mồ côi.
- Fault injection đi qua handler thật tại `DELETE_FILES_STAGED`, `DELETE_DB_COMMITTED`, `DELETE_BEFORE_ROLLBACK`, không gọi fixture journal là cancellation test.

### MusicXML

- Android parser chấp nhận `&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`, decimal và hexadecimal numeric character references trong text/attribute.
- Custom entity bị từ chối; external DOCTYPE không được resolve vì `FEATURE_PROCESS_DOCDECL=false`.
- Parser vẫn đọc đến `END_DOCUMENT`, XML bị cắt/hỏng bị từ chối.

## Bằng chứng test

| Gate | Kết quả |
|---|---|
| Unit/Robolectric integration | PASS — 115 tests, 0 fail/error/skip |
| Restore v2 thiếu asset metadata/file | PASS — fail trước khi thay DB/file cũ |
| Restore overwrite cùng songId lỗi trước DB | PASS — DB hash và file cũ nguyên vẹn |
| Restore sang filesDir khác | PASS — MIDI/MusicXML/audio có path mới, file tồn tại, MIDI hash đúng |
| Delete rollback rename fail/retry | PASS — payload+journal còn; recovery retry thành công |
| Cancellation thật trước/sau delete commit | PASS |
| Cancellation thật sau restore commit | PASS |
| MusicXML standard escapes/custom entity unit | PASS |
| Instrumentation Android 12 | PASS — 6/6, gồm 4 MusicXML + migration 5→6 |
| `assembleDebug` | PASS |
| MIDI phần cứng | NOT_RUN — không có thiết bị MIDI vật lý |

Các lỗi trung gian được ghi đúng: lần compile test đầu thiếu field bắt buộc của fixture; lần JUnit đầu hai test trả `Boolean` thay vì `Unit`. Đây là lỗi test fixture/chữ ký và đã được sửa trước gate cuối. Log cuối trong ZIP là lần PASS.

## Lệnh cuối

```text
.\gradlew.bat --no-daemon --max-workers=1 --no-configuration-cache "-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=320m -XX:ReservedCodeCacheSize=128m -Xss512k -Dfile.encoding=UTF-8" :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat --no-daemon --max-workers=1 --no-configuration-cache "-Dorg.gradle.jvmargs=-Xmx896m -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=96m -Xss512k -Dfile.encoding=UTF-8" :app:connectedDebugAndroidTest
```

- APK: `study-piano-phase1.2-app-debug.apk`, 20,577,810 bytes.
- SHA256 APK: `66ED81C5D876AED4D653427CE2A3AB30A0AB7780681E6143527418F5317EB6DC`.

## Giới hạn

- Test process-death vẫn dùng fault injection/fixture trạng thái bền vững, không phải automation kill process tại đúng CPU instruction.
- Fingerprint recovery restore dùng toàn bộ tập `(songId, fileHashSha256)` để quyết định Room song state đã commit. Khi bộ song và hash hoàn toàn giống nhau nhưng chỉ metadata lịch sử khác, quyết định này vẫn ưu tiên tính nhất quán file bài hát; các bảng DB vẫn được Room commit/rollback nguyên tử.
- Không chụp lại UI vì Đợt 1.2 không thay đổi giao diện. Ảnh persistence Đợt 1.1 vẫn giữ nguyên ngoài gói này.
