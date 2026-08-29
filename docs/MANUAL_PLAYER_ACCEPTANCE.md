# SPRINT 3.1 — TÀI LIỆU NGHIỆM THU TRẢI NGHIỆM NGƯỜI DÙNG (MANUAL PLAYER ACCEPTANCE)

**Phiên bản:** Sprint 3.1 Core Experience Reset  
**Mục tiêu:** Kiểm tra trải nghiệm thực tế từ chọn bài, chia đoạn, luyện tập, chấm điểm và luyện lại.

---

## 1. MA TRẬN KIỂM TRA NGHIỆM THU (ACCEPTANCE MATRIX)

| Mã Gate | Hạng mục kiểm tra | Tiêu chí đạt (Pass Criteria) | Trạng thái |
| :--- | :--- | :--- | :--- |
| **Gate 0** | **Truth Audit & Phân loại** | Phân loại 16/16 tính năng minh bạch trong `docs/SPRINT_3_1_TRUTH_AUDIT.md`. Nhãn âm thanh là "Âm thanh piano tổng hợp (Synth)". | ✅ PASS |
| **Gate G** | **Bản quyền Starter Songs** | Đã xóa `Flower Dance` và các bài thương mại. Chỉ giữ 7 bài Public Domain kèm `licenses.json`. | ✅ PASS |
| **Gate B** | **Falling Notes & Hình học 88 phím** | Nốt đen/trắng rơi đúng tâm phím tham chiếu. Zero object allocation trong Canvas draw loop. Tầm nhìn 2s/4s/6s/Auto-fit mượt mà. | ✅ PASS |
| **Gate E** | **Wait Mode & Dynamic Tolerance** | Wait Mode dừng tại chord, khớp hợp âm không phụ thuộc thứ tự. Dung sai nhịp tính theo BPM clamped (80–250ms). | ✅ PASS |
| **Gate F** | **Demo Playback & Audio Clarity** | Nghe mẫu phát đúng nốt. Tự đệm tay còn lại khi luyện 1 tay. Có nút tắt âm app khi cắm đàn thật. | ✅ PASS |
| **Gate D** | **Lộ trình luyện theo đoạn (`LearningSection`)** | Chia đoạn 2–4 ô nhịp không cắt đôi hợp âm. Bảng tổng kết có % cao độ, % nhịp điệu và danh sách ô nhịp yếu nhất. | ✅ PASS |
| **Gate A** | **Bố cục Landscape thực dụng** | TopBar $\le 44\text{dp}$ tự ẩn sau 3s khi đang phát, chạm màn hình để hiện lại. Bàn phím tham chiếu 58dp ($\le 20\%$ chiều cao) với C1–C8 markers. | ✅ PASS |
| **Gate C** | **Sheet Music Guard** | Ẩn chế độ Sheet khi bài chỉ có MIDI, không hiển thị mock sheet giả tạo. | ✅ PASS |
| **Gate H** | **Thư viện & Chuẩn bị bài học** | Huy hiệu năng lực minh bạch. Dialog chuẩn bị cho phép chọn chế độ, tốc độ, tay luyện và lưu preset. | ✅ PASS |
| **Gate I** | **Kiểm thử tích hợp & Unit Tests** | Toàn bộ 61+ unit & integration tests vượt qua 100%. Build APK thành công không lỗi lint/compile. | ✅ PASS |

---

## 2. HƯỚNG DẪN KIỂM TRA THỦ CÔNG TRÊN MÁY ẢO / THIẾT BỊ THẬT

### Kịch bản 1: Chọn bài và chuẩn bị luyện tập
1. Mở ứng dụng, vào tab **Bài Nhạc**.
2. Kiểm tra danh sách bài: Chỉ có các bài Public Domain (`Ode to Joy`, `Amazing Grace`, `Jingle Bells`, `Mary Had a Little Lamb`, `Hot Cross Buns`, `Twinkle Twinkle`, `Frère Jacques`).
3. Chạm vào bài **Ode to Joy**:
   - Xuất hiện BottomSheet chuẩn bị bài tập.
   - Chọn chế độ: *Chờ đúng nốt* hoặc *Chạy theo nhịp*.
   - Chỉnh tốc độ (BPM) và phân tay.
   - Bấm **Bắt đầu luyện bài**.

### Kịch bản 2: Trải nghiệm trong phòng luyện (Landscape)
1. Màn hình tự động xoay ngang (Landscape).
2. Thanh TopBar cao 44dp tự động ẩn sau 3 giây khi bắt đầu phát.
3. Chạm vào khoảng trống trên màn hình: TopBar hiện ra ngay lập tức.
4. Bàn phím tham chiếu ở đáy màn hình hiển thị gọn gàng (58dp) với các ký hiệu C1, C2, C3, C4, C5, C6, C7, C8 trên phím Đô.
5. Khi nốt rơi xuống vạch đón nốt:
   - Tay phải: Nốt màu Cyan/Blue sáng rõ.
   - Tay trái: Nốt màu Orange ấm áp.
   - Đánh đúng: Nốt chuyển sang xanh lá.
   - Đánh sai: Nốt chuyển sang đỏ nhạt.

### Kịch bản 3: Kết quả và Luyện lại
1. Khi hoàn thành bài, chuyển sang màn hình **Kết quả luyện tập**.
2. Kiểm tra các chỉ số:
   - Điểm tổng (%)
   - Đúng cao độ (%)
   - Khớp nhịp điệu (%)
   - Danh sách các ô nhịp cần cải thiện (Weakest measures).
3. Bấm nút **"Luyện tập lại"** hoặc **"Tập chậm lại"** để tiếp tục rèn luyện.
