# SPRINT 3.2 — TÀI LIỆU NGHIỆM THU TRẢI NGHIỆM PHÒNG LUYỆN (MANUAL PLAYER ACCEPTANCE)

**Phiên bản:** Sprint 3.2 — Player Experience Only  
**Quy tắc trung thực:** Toàn bộ các hạng mục kiểm tra thủ công mặc định là `NOT_RUN`. Chỉ chuyển trạng thái `PASS` khi có đầy đủ bằng chứng kiểm tra thực tế (Thiết bị/Emulator, Độ phân giải, Ngày kiểm thử, và Kết quả quan sát). Không dùng kết quả unit test để tự ý đánh dấu PASS kiểm tra thủ công.

---

## 1. MA TRẬN NGHIỆM THU PHÒNG LUYỆN (PLAYER ACCEPTANCE MATRIX)

| Mã kiểm tra | Hạng mục kiểm tra | Tiêu chí đạt (Pass Criteria) | Trạng thái hiện tại | Bằng chứng / Thiết bị |
| :--- | :--- | :--- | :--- | :--- |
| **EXP-01** | **Nghe mẫu độc lập trong Wait Mode** | Khi bấm "Nghe mẫu" lúc đang ở WAIT_FOR_NOTE, timeline tự động chạy mượt mà từ đầu đến cuối section/bài, Falling Notes cuộn đồng bộ, phát đủ 2 tay, không cộng điểm/streak. Khi dừng, khôi phục đúng vị trí cũ. | `NOT_RUN` | Cần xác minh trực quan trên Emulator/Device |
| **EXP-02** | **Accompaniment phát đúng tay** | Khi chọn luyện Tay phải ở Rhythm Mode, tay trái tự động phát đệm đúng nhịp. Khi luyện Tay trái, tay phải tự động phát. Khi luyện 2 tay, app không tự phát nốt cần đánh. | `NOT_RUN` | Cần nghe âm thanh trên Emulator/Device |
| **EXP-03** | **Bàn phím ảo không kẹt nốt (Note Off)** | Nhấn phím ảo phát âm thanh, nhấc ngón tay tắt âm ngay lập tức. Chạm đa điểm không bị giữ nốt vô tận khi thả tay. Mặc định chỉ hiển thị (visualization). | `NOT_RUN` | Cần thao tác cảm ứng trên Emulator/Device |
| **EXP-04** | **Phản hồi thị giác gắn đúng nốt (Identity Feedback)** | Khi đánh đúng/sai một nốt C4, chỉ duy nhất nốt C4 đó tại thời điểm hit đổi màu trong 300ms. Các nốt C4 khác ở tương lai trên màn hình giữ nguyên màu tay. | `NOT_RUN` | Cần quan sát frame Canvas trên Emulator/Device |
| **EXP-05** | **Giao diện TopBar responsive (640×360)** | TopBar cao 44dp, vừa vặn không bị tràn (overflow) ở màn hình nhỏ 640×360. Tự ẩn sau 3s khi đang chơi, hiện lại khi tạm dừng hoặc chạm màn hình. | `NOT_RUN` | Cần kiểm tra layout trên màn hình 640×360 |
| **EXP-06** | **Chọn và luyện theo đoạn (`LearningSection`)** | Mở bảng tùy chỉnh chọn "Đoạn 1", Player nạp chính xác các nốt thuộc Đoạn 1, tua và lặp đúng ranh giới đoạn, kết thúc và hiển thị kết quả đúng đoạn. | `NOT_RUN` | Cần kiểm tra luồng chọn đoạn |
| **EXP-07** | **Đồng bộ âm thanh và hình ảnh (Audio-Visual Sync)** | Nốt chạm vạch hit reception line sai số $\le 20\text{ms}$ so với thời điểm âm thanh phát. Sau 20 lần pause/resume không bị trôi lệch tích lũy. | `NOT_RUN — NEEDS REAL DEVICE` | Cần đo đạc trên đàn/thiết bị thật |

---

## 2. NHẬT KÝ KIỂM THỬ THỰC TẾ (TEST LOGS)

*(Chưa có bản ghi thực nghiệm trực tiếp từ người dùng hoặc thiết bị vật lý)*

- **Thiết bị:** `Chưa thực hiện`
- **Kích thước màn hình:** `Chưa thực hiện`
- **APK Version:** `app-debug.apk (Sprint 3.2)`
- **Ngày kiểm tra:** `Chưa thực hiện`
- **Ghi chú:** Đã vượt qua 100% các automated regression tests trong [`PlayerExperienceRegressionTest.kt`](file:///d:/Ark_3/PianoApp/app/src/test/java/com/ian/pianotrainer/PlayerExperienceRegressionTest.kt) và [`SectionPracticeIntegrationTest.kt`](file:///d:/Ark_3/PianoApp/app/src/test/java/com/ian/pianotrainer/SectionPracticeIntegrationTest.kt).
