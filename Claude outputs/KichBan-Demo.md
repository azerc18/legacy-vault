# Kịch bản demo LegacyVault (~15–18 phút)

Demo đi theo **câu chuyện của một người**:

> Anh **An** lập kế hoạch thừa kế, rồi "im lặng". Hệ thống tự kích hoạt người thực thi **Minh**. Luật sư **Hùng** xác minh. Người thụ hưởng **Lan** nhận tài sản.

---

## 0. Chuẩn bị (làm TRƯỚC giờ demo)

### Tài khoản

Đăng ký sẵn và xác thực mail xong hết để khỏi chờ trong lúc demo.

| Vai | Email ví dụ | Ghi chú |
|---|---|---|
| Owner | an@... | tự đăng ký |
| Primary Executor | minh@... | tự đăng ký |
| Backup Executor | hoa@... | không bắt buộc |
| Beneficiary | lan@... | **có thể để chưa đăng ký** để demo "gắn tự động" ở luồng 6 |
| Legal Verifier | hung@... | đăng ký, rồi Admin bấm **Make Legal Verifier** |
| Admin | admin@gmail.com | tạo sẵn bởi `DataInitializer` |

> **Lưu ý xung đột lợi ích:** Verifier **không được** là Executor hay Beneficiary của An, nếu không sẽ không thấy hồ sơ.

### Trình duyệt

Mở 4 cửa sổ, mỗi cửa sổ một vai, để chuyển cảnh nhanh:

1. Chrome thường: Owner, sau đó dùng cho Admin.
2. Chrome ẩn danh: Executor.
3. Edge hoặc profile khác: Verifier.
4. Profile thứ 4: Beneficiary.

### Khác

- Mở sẵn hộp mail của từng tài khoản (mail kích hoạt, OTP ký, OTP xác minh Beneficiary).
- `application-local.yaml` phải có `app.kyc.mock.enabled: true`. Khi đó CCCD hợp lệ là **đúng 12 chữ số**.
- Chuẩn bị file `giay-chung-tu.pdf` và 1–2 file tài liệu nhỏ (pdf/jpg).
- Mở sẵn MySQL Workbench với các câu **SQL tua thời gian** ở cuối file.

> ⚠️ **Đừng demo** nhánh "Primary đã acknowledge nhưng không nộp hồ sơ". Nhánh này chưa có leo thang (lỗi đã biết).

---

## Luồng 1: Tham gia hệ thống (1 phút)

| Bước | Thao tác | Kết quả cần chỉ ra |
|---|---|---|
| 1 | Mở `/` (landing) và lướt qua phần tính năng | Giới thiệu bài toán: tài sản số bị "mất" khi chủ sở hữu qua đời |
| 2 | Bấm **Register**, gõ thử một mật khẩu yếu | Thanh độ mạnh mật khẩu đỏ, nút bị chặn |
| 3 | Đăng nhập bằng `an@...` (đã kích hoạt sẵn) | Vào trang `/home`, thấy các khu vực theo vai trò |

**Câu nói:** "Tài khoản phải xác thực email mới dùng được. Đăng nhập dùng JWT kèm refresh token, mật khẩu được băm bằng BCrypt."

## Luồng 2: Lập kế hoạch thừa kế (3 phút)

| Bước | Thao tác | Kết quả |
|---|---|---|
| 1 | **My vaults → New vault**, đặt tên "Tài khoản ngân hàng" | Vault ACTIVE. **Banner đỏ** "Your vaults are not protected yet" xuất hiện |
| 2 | Thêm 2 tài sản: Bank account và Crypto wallet | Dữ liệu nhạy cảm được mã hoá AES trước khi lưu |
| 3 | Upload tài liệu (Will.pdf) | Nút upload hiện tên file, có ✕ để bỏ |
| 4 | Chọn Beneficiary: `lan@...` | Nếu Lan chưa có tài khoản thì vault ở trạng thái *pending registration* |
| 5 | **Executors**: Primary `minh@...`, Backup `hoa@...` | Minh và Hoa nhận mail thông báo được chỉ định |
| 6 | **Dead Man's Switch**: chu kỳ 1 ngày, grace 1 ngày, 1 lần nhắc → Bật | Banner biến mất. Thấy hạn check-in tiếp theo |

**Câu nói:**

- "Hệ thống bắt buộc phải có Executor trước rồi mới cho bật DMS. Banner dẫn người dùng đi đúng thứ tự."
- "Executor và Beneficiary không được trùng người, để tránh xung đột lợi ích."

## Luồng 3: Còn sống, check-in (1 phút)

| Bước | Thao tác | Kết quả |
|---|---|---|
| 1 | Ở trang DMS, bấm **Check in** | `lastCheckinAt` cập nhật, hạn mới được đẩy lùi |
| 2 | Mở **Activity** | Thấy "DMS check-in" trong nhật ký |

**Câu nói:** "Mỗi phút có một scheduler quét DMS quá hạn. Trước khi kích hoạt, hệ thống gửi mail nhắc. Nếu chưa nhắc được lần nào thì hệ thống hoãn 24 giờ, tối đa 3 lần, để tránh kích hoạt nhầm."

## Luồng 4: An "im lặng", hệ thống kích hoạt Executor (2 phút)

| Bước | Thao tác | Kết quả |
|---|---|---|
| 1 | Chạy **SQL #1** (tua hạn check-in về quá khứ), chờ khoảng 1 phút | DMS chuyển sang TRIGGERED |
| 2 | Mở mail của Minh | Có mail kích hoạt kèm mã |
| 3 | Minh đăng nhập → **Executor workspace** → mở hồ sơ "Estate of An" → **Acknowledge** | Stepper sang bước 2. Minh thấy danh sách vault, chỉ metadata, **không** thấy nội dung tài sản |
| 4 | *(Tuỳ chọn)* Chạy **SQL #2** (Primary quá 14 ngày chưa acknowledge), chờ khoảng 5 phút | Minh bị thu hồi, Hoa (Backup) được kích hoạt và nhận mail |
| 5 | *(Tuỳ chọn)* Admin → **Users**: nếu Owner không còn Executor dùng được thì hiện nút **Assign executor** | Admin cứu hộ, gán người mới |

**Câu nói:** "Executor chỉ được thấy danh sách tài sản, không đọc được nội dung, theo nguyên tắc quyền tối thiểu. Nội dung chỉ mở cho Beneficiary sau khi luật sư ký xác nhận."

## Luồng 5: Xác minh cái chết (4 phút)

| Bước | Thao tác | Kết quả |
|---|---|---|
| 1 | Minh: chọn vault, chọn file `giay-chung-tu.pdf` → **Submit** | Hồ sơ PENDING. Vault của An bị **Locked** |
| 2 | Chuyển sang An (tab Owner) | Vault hiện một nhãn **Locked**, rê chuột thấy lý do |
| 3 | Hùng (Verifier) → **Verification requests** → mở hồ sơ | Thông tin case, tài liệu, checklist |
| 4 | Hùng nhắn "Gửi bản scan rõ hơn"; Minh trả lời kèm file | Trao đổi hai chiều, có đính kèm |
| 5 | *(Tuỳ chọn)* Hùng **Reject** với một lý do | Minh nhận mail và thấy lý do. Hồ sơ đóng. Minh nộp hồ sơ mới |
| 6 | Hùng xem tài liệu → tick checklist → **Approve** | Trạng thái chuyển sang Awaiting signature |
| 7 | **Sign** → nhận OTP qua mail → nhập mã | Hồ sơ SIGNED, vault chuyển **UNLOCKED** |

**Câu nói:**

- "Phải mở tài liệu và hoàn thành checklist thì mới được duyệt."
- "Bước ký dùng OTP hết hạn sau 5 phút. Nếu đã duyệt mà không ký thì hồ sơ tự hết hạn."
- "Mọi bước đều được ghi vào audit log."

## Luồng 6: Bàn giao cho người thụ hưởng (3 phút)

| Bước | Thao tác | Kết quả |
|---|---|---|
| 1 | Mở mail của Lan | Thông báo vault được bàn giao. Chuông thông báo trong app có chấm đỏ |
| 2 | *(Nếu Lan chưa đăng ký)* Lan đăng ký và xác thực mail | Vault **tự gắn** vào tài khoản Lan |
| 3 | Lan → vault → **Verify your identity**: nhận OTP qua mail, nhập CCCD 12 số | Claim thành công |
| 4 | Xem chi tiết tài sản, tải tài liệu Will.pdf | File được giải mã khi tải về |
| 5 | **Finish and close** → xem trước (cảnh báo nếu còn mục chưa xem) → xác nhận | Vault ARCHIVED, sẽ bị xoá vĩnh viễn theo hạn |

**Câu nói:** "Nếu Beneficiary không nhận trong thời hạn thì hệ thống tự đánh dấu hết hạn. Vault đã lưu trữ sẽ bị xoá vĩnh viễn để không giữ dữ liệu nhạy cảm lâu hơn cần thiết."

## Luồng 7 và 8: Thay đổi kế hoạch và giám sát (2 phút)

| Bước | Thao tác | Kết quả |
|---|---|---|
| 1 | Admin → **Audit log**: lọc theo An | Toàn bộ hành trình từ đầu đến giờ |
| 2 | Admin → **Alerts** | Cảnh báo, ví dụ nhiều lần đăng nhập sai (thử sai mật khẩu 5–6 lần trước khi demo) |
| 3 | Admin → **Users** → khoá một tài khoản; **Roles** → xem phân quyền | RBAC động |
| 4 | *(Nếu còn thời gian)* Trợ lý AI ở góc phải | Hỏi "How do I set up the Dead Man's Switch?" |

---

## SQL tua thời gian (MySQL)

```sql
-- #1 Kích hoạt DMS của An ngay (scheduler chạy mỗi 1 phút).
-- reminders_sent = 1 để bỏ qua cơ chế hoãn 24h.
UPDATE dms_configs
SET next_checkin_due_at = NOW() - INTERVAL 1 MINUTE, reminders_sent = 1
WHERE owner_id = (SELECT id FROM users WHERE email = 'an@...');

-- #2 (tuỳ chọn) Primary quá 14 ngày chưa acknowledge -> chuyển sang Backup (scheduler chạy mỗi 5 phút).
-- Chỉ chạy khi Minh CHƯA bấm Acknowledge.
UPDATE executor_assignments
SET activated_at = NOW() - INTERVAL 15 DAY
WHERE executor_type = 'PRIMARY'
  AND owner_id = (SELECT id FROM users WHERE email = 'an@...');

UPDATE notifications
SET sent_at = NOW() - INTERVAL 15 DAY
WHERE type = 'EXECUTOR_ACTIVATED'
  AND recipient_id = (SELECT id FROM users WHERE email = 'minh@...');
```

## Phương án dự phòng

| Sự cố | Cách xử lý |
|---|---|
| Mail đến chậm | Mở bảng `notifications` hoặc log server để lấy nội dung. OTP đã được băm nên không đọc được từ DB, khi đó dùng tính năng gửi lại sau 60 giây |
| Scheduler chưa chạy | Chờ thêm 1 phút ở luồng 4, hoặc 5 phút với SQL #2. Trong lúc chờ, nói tiếp về bảo mật và kiến trúc |
| Giảng viên hỏi về lỗi đã biết | Trả lời thẳng: "Nhánh Primary đã nhận việc nhưng bỏ dở hiện chưa tự leo thang. Hướng sửa là cho Admin cứu hộ khi quá 14 ngày không có hồ sơ." |
