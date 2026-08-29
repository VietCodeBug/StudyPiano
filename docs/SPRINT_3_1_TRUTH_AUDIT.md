# SPRINT 3.1 — KIỂM TOÁN TÍNH TRUNG THỰC (TRUTH AUDIT)

**Thời điểm kiểm toán:** 2026-08-29  
**Môi trường:** Android Native (Kotlin, Jetpack Compose, JDK 17, minSdk 26, targetSdk 35)  
**Base commit:** `e0f0a26e435c124bdc466a970677a8e7f033f052`

---

## 1. BẢNG TỔNG HỢP TRẠNG THÁI TRIỂN KHAI

Mỗi tính năng được phân loại theo 4 cấp độ trung thực:
- `REAL_IMPLEMENTATION`: Mã nguồn hoàn chỉnh, logic nghiệp vụ thực tế hoạt động, có unit test hoặc chạy thực tế.
- `PARTIAL_IMPLEMENTATION`: Đã có khung logic/xử lý toán học thực tế nhưng chưa hoàn chỉnh 100% hoặc còn hạn chế về tài nguyên/môi trường.
- `PLACEHOLDER`: Chỉ có giao diện tĩnh, mock, fallback text hoặc comment placeholder, chưa có động cơ thực sự bên dưới.
- `UNVERIFIED_HARDWARE`: Logic driver hoàn chỉnh nhưng yêu cầu phần cứng vật lý bên ngoài (đàn MIDI thật, Bluetooth LE MIDI, cáp OTG) chưa thể xác minh trên emulator thuần túy.

| Hạng mục | Trạng thái | Đánh giá chi tiết & Hướng xử lý |
| :--- | :--- | :--- |
| **SoundPoolPianoEngine** | `PARTIAL_IMPLEMENTATION` | **Thực tế:** Bộ tổng hợp âm thanh cộng gộp sóng hài PCM 44.1kHz (Additive synthesis + inharmonicity), không phải bộ đa sample thu âm từ đàn Grand Piano cơ thật.<br>**Xử lý:** Đổi toàn bộ nhãn hiển thị thành *"Âm thanh piano tổng hợp (Synth)"*, loại bỏ các từ "acoustic" / "piano cơ" gây hiểu nhầm. Cung cấp tùy chọn tắt âm thanh khi cắm đàn thật. |
| **MidiPlaybackScheduler** | `REAL_IMPLEMENTATION` | **Thực tế:** Bộ lập lịch phát nốt MIDI theo thời gian thực (tick-based), hỗ trợ lọc vai trò Track/Hand (`PRACTICE`, `ACCOMPANIMENT`, `MUTE`), đồng bộ vị trí playhead, seek, pause. Đã có 4 unit tests. |
| **MusicXmlSheetView** | `PLACEHOLDER` | **Thực tế:** Màn hình nhúng WebView `osmd_viewer.html` nhưng chưa bundle tệp thư viện JavaScript OpenSheetMusicDisplay thực tế (chỉ có fallback DOMParser đọc thẻ `<work-title>`).<br>**Xử lý:** Ẩn hoàn toàn tab SHEET khi không có MusicXML render thật. Với bài chỉ có MIDI, hiển thị giải thích: *"Bài này chỉ có dữ liệu MIDI nên chưa có bản nhạc khuông"*. |
| **osmd_viewer.html** | `PLACEHOLDER` | **Thực tế:** Chưa chứa bundle JS OSMD offline thực thụ. |
| **FallingNotesCanvas** | `REAL_IMPLEMENTATION` | **Thực tế:** Động cơ Canvas vẽ nốt rơi theo trục thời gian, vạch nhịp `BeatGridCalculator`, cửa sổ nốt nhìn thấy `VisibleNoteWindowSelector`. Cần chuẩn hóa hình học phím `PianoGeometry` 88 phím chính xác và tối ưu bộ nhớ không tạo Paint trong draw loop. |
| **PracticeReferenceKeyboard** | `REAL_IMPLEMENTATION` | **Thực tế:** Vẽ 88 phím đàn Canvas, hỗ trợ highlight nốt mục tiêu, nốt bấm thực tế, nốt đúng/sai. Cần giới hạn chiều cao tối đa <= 20% màn hình landscape và đặt chế độ chạm phím là tùy chọn cài đặt. |
| **HandSeparationEngine** | `REAL_IMPLEMENTATION` | **Thực tế:** Động cơ phân tách tay trái/phải đa chỉ số (Single note voice-leading + Chord split optimization qua $k$-cut minimizing spatial distance). Đã có 5 unit tests đạt chuẩn. |
| **ContentPackImporter** | `REAL_IMPLEMENTATION` | **Thực tế:** Động cơ giải nén gói ZIP `.pianopack`, phân tích `manifest.json`, nạp MIDI, lưu sheet MusicXML và audio MP3 cục bộ vào Room DB. Đã có 5 unit tests đạt chuẩn. |
| **Starter songs** | `PARTIAL_IMPLEMENTATION` | **Thực tế:** Hiện có các bài Public Domain (Ode to Joy, Amazing Grace, Jingle Bells, Mary Had a Little Lamb, Hot Cross Buns, Twinkle Twinkle, Frère Jacques). Tuy nhiên có tệp `Flower Dance - DJ Okawari` là bài thương mại có bản quyền.<br>**Xử lý:** Xóa bỏ ngay `Flower Dance` và mọi tệp thương mại khỏi asset/bundle. Tạo `licenses.json` minh bạch cho toàn bộ bài Public Domain. |
| **Bluetooth MIDI** | `UNVERIFIED_HARDWARE` | **Thực tế:** Driver `AndroidMidiDriver` quét BLE qua Service UUID `7772e5db-3868-4112-a1a9-f2669d106bf3` và mở cổng qua `MidiManager.openBluetoothDevice`. Yêu cầu thiết bị Android thật và đàn piano Bluetooth LE. |
| **USB MIDI** | `UNVERIFIED_HARDWARE` | **Thực tế:** Lắng nghe và mở cổng `MidiDevice` qua Android MIDI Service. Đã test qua virtual key events; cần đàn MIDI vật lý thật để kiểm tra độ trễ phần cứng. |
| **Microphone Pitch Detection** | `PARTIAL_IMPLEMENTATION` | **Thực tế:** Thu âm `AudioRecord` và thuật toán Autocorrelation / Difference Function $d(\tau)$ nhận diện cao độ đơn âm (monophonic). Bị nhiễu với hợp âm piano polyphonic. |
| **Free Play Recording** | `REAL_IMPLEMENTATION` | **Thực tế:** Ghi lại chuỗi sự kiện MIDI NoteOn/NoteOff kèm timestamp tương đối vào Room DB (`free_play_recordings`), hỗ trợ xuất và phát lại. |
| **Practice Scoring** | `REAL_IMPLEMENTATION` | **Thực tế:** `RealPracticeEngine` tính toán nốt đúng, nốt sai, nốt bỏ lỡ, độ lệch thời gian (timing delta). Cần nâng cấp dung sai động theo BPM và phân tích ô nhịp yếu nhất. |
| **Demo Playback** | `REAL_IMPLEMENTATION` | **Thực tế:** Tự động phát bài qua `MidiPlaybackScheduler` và phát âm qua `PianoAudioEngine` với playhead cuộn đồng bộ. |
| **Accompaniment Playback** | `REAL_IMPLEMENTATION` | **Thực tế:** Khi luyện 1 tay (ví dụ tay phải), app tự động đệm tay còn lại (tay trái) qua `PlaybackRole.ACCOMPANIMENT`. |

---

## 2. KẾ HOẠCH HÀNH ĐỘNG CHO SPRINT 3.1
1. **Gate G**: Dọn sạch các tệp có rủi ro bản quyền (`Flower Dance`, v.v.), chỉ giữ bài Public Domain được cấp phép, tạo `licenses.json`.
2. **Gate B**: Chuẩn hóa `PianoGeometry` 88 phím cho Falling Notes và Reference Keyboard, tối ưu hiệu năng không cấp phát bộ nhớ trong frame vẽ, tầm nhìn 2s/4s/6s/auto-fit.
3. **Gate E**: Chuẩn hóa Wait Mode (chờ đủ hợp âm, không kẹt nốt) và Rhythm Mode (dung sai tính theo BPM clamped, count-in 0/1/2 ô nhịp).
4. **Gate F**: Đồng bộ Demo Playback và âm thanh Piano Synth (nhãn trung thực 100%).
5. **Gate D**: Lộ trình luyện theo đoạn `LearningSection` (2-4 ô nhịp, các bước từ Nghe mẫu -> Chờ nốt từng tay -> Hai tay tăng dần 50%..100%, phân tích điểm yếu).
6. **Gate A**: Thiết kế lại bố cục Landscape tập trung nội dung (TopBar <= 44dp tự ẩn sau 3s, Bàn phím tham chiếu 52-68dp <= 20% chiều cao).
7. **Gate C**: Ẩn chế độ Sheet khi bài chỉ có MIDI hoặc chưa có parser MusicXML thực tế.
8. **Gate H**: Màn hình chuẩn bị bài học & thư viện bài hát trung thực.
9. **Gate I**: Kiểm thử tích hợp MIDI fixture và tài liệu nghiệm thu `docs/MANUAL_PLAYER_ACCEPTANCE.md`.
