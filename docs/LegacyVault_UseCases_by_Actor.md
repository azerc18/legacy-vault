# Phân tích Use Case LegacyVault theo Actor

Nguồn: `LegacyVault_SRS__1_.md` (SRS v1.0, môn SWP391).
Tài liệu có **27 UC** (UC1-UC27, tương ứng FR-01 đến FR-27). Mã UC/FR giữ nguyên theo SRS để đối chiếu.
Ký hiệu: ⭐ = *Optional* trong SRS.

## 0. Tổng quan

| Actor | Số UC | Mã UC |
|---|---|---|
| Chung (mọi actor) | 3 | UC24, UC25, UC26 |
| Vault Owner | 7 | UC1-UC6, UC27 |
| Digital Executor / Trustee | 5 | UC7-UC11 |
| Legal Verifier / Notary | 4 | UC12-UC15 |
| Beneficiary | 4 | UC16-UC19 |
| System Administrator | 4 | UC20-UC23 |

> UC7 và UC16 do hệ thống tự kích hoạt (DMS timeout, Vault chuyển Unlocked). Nên thêm **System/Scheduler** làm secondary actor trong diagram.

---

## 1. Chung cho mọi Actor

| UC | FR | Tên | Tiền điều kiện | Luồng chính (tóm tắt) | Ngoại lệ chính |
|---|---|---|---|---|---|
| **UC24** | FR-24 | Đăng ký tài khoản | Email chưa tồn tại | Nhập họ tên/email/mật khẩu → validate → hash mật khẩu, tạo tài khoản "Pending Verification" → gửi email/OTP xác minh → xác minh xong chuyển "Active" | Email trùng; mật khẩu yếu; link hết hạn (gửi lại); quá 7 ngày không xác minh thì xóa tài khoản |
| **UC25** | FR-25 | Đăng nhập | Tài khoản đã xác minh, chưa bị khóa | Nhập email/mật khẩu → kiểm tra → phát hành Access Token + Refresh Token (httpOnly cookie) → ghi log → điều hướng theo role | Sai mật khẩu (đếm lần sai, vượt ngưỡng thì khóa tạm); tài khoản bị Admin khóa; chưa xác minh email |
| **UC26** | FR-26 | Đăng xuất | Đang có phiên hợp lệ | Gửi Refresh Token → revoke trong DB → xóa cookie → ghi log → về màn hình đăng nhập | Token hết hạn/không hợp lệ thì vẫn coi là đăng xuất thành công |

---

## 2. Vault Owner

| UC | FR | Tên | Tiền điều kiện | Luồng chính (tóm tắt) | Ngoại lệ chính |
|---|---|---|---|---|---|
| **UC1** | FR-01 | Tạo và mã hóa hồ sơ tài sản số | Đã đăng nhập | Tạo Vault mới → thêm tài sản (loại, username, secret, file) → hệ thống mã hóa AES-256 → lưu, trạng thái "Active" | Thiếu trường bắt buộc; mã hóa thất bại thì hủy lưu |
| **UC2** | FR-02 | Chỉ định người thụ hưởng | Vault tồn tại, thuộc Owner | Chọn Vault → nhập định danh Beneficiary → kiểm tra có tài khoản chưa (chưa có thì gửi lời mời) → lưu liên kết → thông báo Owner | Vault đã có Beneficiary (hỏi xác nhận ghi đè); định danh sai định dạng |
| **UC3** | FR-03 | Thiết lập Dead Man's Switch | Có ≥ 1 Vault | Vào cài đặt DMS → nhập chu kỳ check-in, số ngày chờ → lưu, DMS = "Active" → lên lịch gửi check-in | Chu kỳ ≤ 0 |
| **UC4** | FR-04 | Tải lên và mã hóa tài liệu pháp lý | Đã đăng nhập | Chọn file + loại tài liệu → kiểm tra định dạng/dung lượng → mã hóa đầu-cuối → lưu metadata | Sai định dạng/dung lượng; mã hóa thất bại |
| **UC5** ⭐ | FR-05 | Xem nhật ký hoạt động và lịch sử truy cập | Sở hữu ≥ 1 Vault | Chọn bộ lọc (Vault, thời gian) → truy vấn → hiển thị log giảm dần theo thời gian | Không có log thì hiển thị danh sách rỗng |
| **UC6** | FR-06 | Cập nhật/thu hồi quyền Executor | DMS **chưa** kích hoạt | Vào "Quản lý Executor" → thêm/sửa/thu hồi (chính/dự phòng) → xác nhận → cập nhật quyền → thông báo Executor | DMS đã kích hoạt thì từ chối thao tác |
| **UC27** | FR-27 | Xóa Vault | Vault ở trạng thái "Active" | Chọn Vault → "Xóa" → cảnh báo không thể hoàn tác → xác nhận (có thể step-up auth) → xóa/soft-delete cả Asset và tài liệu, gỡ Beneficiary → ghi Audit Log | Vault đã Pending/Unlocked/Claimed thì từ chối (BR-08); step-up auth thất bại thì hủy |

---

## 3. Digital Executor / Trustee

| UC | FR | Tên | Tiền điều kiện | Luồng chính (tóm tắt) | Ngoại lệ chính |
|---|---|---|---|---|---|
| **UC7** | FR-07 | Nhận thông báo kích hoạt bàn giao | DMS của Owner hết hạn mà không có check-in | Hệ thống phát hiện timeout → DMS = "Triggered" → gửi thông báo cho Primary Executor → Executor đăng nhập và xác nhận | Primary không phản hồi trong **14 ngày** thì thu hồi hoàn toàn quyền Primary, kích hoạt Backup (BR-03) |
| **UC8** | FR-08 | Xem danh sách tài sản được ủy quyền | Executor đã Active và đăng nhập | Vào "Tài sản được ủy quyền" → hiển thị Vault (metadata, chưa giải mã, Beneficiary) + hướng dẫn bàn giao | Executor chưa được kích hoạt thì từ chối ("Chưa đến thời điểm...") |
| **UC9** | FR-09 | Gửi yêu cầu xác minh pháp lý | Executor đã Active | Upload giấy chứng tử → chọn Vault cần mở → hệ thống mã hóa, lưu → tạo yêu cầu "Pending" → thông báo Legal Verifier | Sai định dạng file; đã có yêu cầu Pending cho cùng Vault (chặn trùng) |
| **UC10** ⭐ | FR-10 | Theo dõi tiến độ bàn giao | Đã gửi ≥ 1 yêu cầu | Vào "Tiến độ bàn giao" → hiển thị trạng thái từng Vault (Pending/Verified/Unlocked/Claimed/Archived-locked) | Không có Vault nào thì hiển thị danh sách rỗng |
| **UC11** ⭐ | FR-11 | Trao đổi tài liệu xác minh với Legal Verifier | Đã có yêu cầu xác minh | Mở yêu cầu → gửi tin nhắn/file bổ sung → lưu vào luồng trao đổi → thông báo Legal Verifier | File vượt dung lượng |

---

## 4. Legal Verifier / Notary

| UC | FR | Tên | Tiền điều kiện | Luồng chính (tóm tắt) | Ngoại lệ chính |
|---|---|---|---|---|---|
| **UC12** | FR-12 | Xem xét và phê duyệt/từ chối yêu cầu | Có yêu cầu "Pending" | Xem danh sách chờ → mở chi tiết → kiểm tra giấy chứng tử → Approve/Reject → cập nhật trạng thái → thông báo Executor | Reject mà không nhập lý do thì bị chặn |
| **UC13** ⭐ | FR-13 | Xác thực chữ ký số và tính hợp lệ tài liệu | Tài liệu đã gắn với yêu cầu | Mở tài liệu → "Xác thực chữ ký số" → gọi mock service → hiển thị hợp lệ/không hợp lệ | Service không phản hồi thì xử lý thủ công |
| **UC14** | FR-14 | Ký xác nhận điện tử để mở khóa Vault | Yêu cầu đã "Approved" | Mở yêu cầu → "Ký xác nhận mở khóa" → nhập mock OTP/PIN → ghi chữ ký → Vault chuyển "Unlocked" → thông báo Executor và Beneficiary | Xác thực chữ ký thất bại thì giữ nguyên trạng thái Vault |
| **UC15** ⭐ | FR-15 | Xem lịch sử hồ sơ đã xác minh | Đã đăng nhập | Vào "Lịch sử xác minh" → lọc (thời gian, Approved/Rejected) → hiển thị danh sách | Không có dữ liệu thì hiển thị danh sách rỗng |

---

## 5. Beneficiary

| UC | FR | Tên | Tiền điều kiện | Luồng chính (tóm tắt) | Ngoại lệ chính |
|---|---|---|---|---|---|
| **UC16** | FR-16 | Nhận thông báo khi có tài sản bàn giao | Vault "Unlocked" và đã gán Beneficiary | Hệ thống phát hiện Unlocked → xác định Beneficiary → gửi thông báo kèm hướng dẫn xác thực và Claim Timeout **60 ngày** | Chưa có tài khoản thì gửi kèm lời mời đăng ký |
| **UC17** | FR-17 | Xem tài sản sau khi xác thực danh tính | Vault "Unlocked", đã đăng nhập | Truy cập Vault → xác thực OTP/mock KYC → giải mã tạm thời → hiển thị danh sách và chi tiết tài sản | Sai OTP/KYC thì cho thử lại (giới hạn số lần); vượt giới hạn thì khóa tạm, cần Admin hỗ trợ |
| **UC18** | FR-18 | Tải xuống tài liệu/khóa truy cập | Đã xác thực ở UC17 | Chọn tài sản → kiểm tra phiên còn hiệu lực → giải mã → trả file → ghi log | Phiên hết hạn thì yêu cầu xác thực lại |
| **UC19** ⭐ | FR-19 | Xác nhận đã nhận bàn giao | Đã xem/tải ở UC17-18 | Vào Vault → "Xác nhận đã nhận" → xác nhận lần cuối → Vault chuyển "Claimed/Closed" → ghi log | Hủy xác nhận thì giữ nguyên trạng thái |

---

## 6. System Administrator

| UC | FR | Tên | Tiền điều kiện | Luồng chính (tóm tắt) | Ngoại lệ chính |
|---|---|---|---|---|---|
| **UC20** | FR-20 | Quản lý người dùng, vai trò và RBAC | Đăng nhập quyền Admin | Tìm/lọc user → chọn user → đổi role hoặc trạng thái (active/khóa) → áp dụng ngay → ghi log | Admin cuối cùng không được tự thu hồi quyền của mình |
| **UC21** ⭐ | FR-21 | Giám sát audit log và cảnh báo bất thường | Đăng nhập Admin | Vào "Audit Log" → lọc → xem danh sách → hệ thống tự đánh dấu sự kiện bất thường (nhiều lần login sai, truy cập ngoài giờ) và gửi cảnh báo | Không có bất thường thì không tạo cảnh báo |
| **UC22** ⭐ | FR-22 | Cấu hình chính sách mã hóa/sao lưu/DMS mặc định | Đăng nhập Admin | Sửa tham số (mã hóa, lịch backup, mặc định DMS/claim timeout) → lưu → validate → áp dụng cho cấu hình **mới** (không hồi tố) → ghi log | Giá trị không hợp lệ (số âm) thì từ chối |
| **UC23** ⭐ | FR-23 | Quản lý tích hợp dịch vụ bên thứ ba | Đăng nhập Admin | Chọn dịch vụ (eKYC/chữ ký số/notary gateway) → cập nhật cấu hình hoặc bật/tắt → kiểm tra kết nối mock → hiển thị trạng thái | Kết nối lỗi thì báo "Lỗi kết nối" và giữ dịch vụ ở trạng thái tắt |

---

## 7. Vấn đề phát hiện trong SRS

### A. Thiếu UC (quan trọng nhất)

1. **Owner check-in (phản hồi DMS):** UC3 chỉ thiết lập DMS. Không có UC nào cho Owner bấm "Tôi còn sống" để reset bộ đếm. Đây là chức năng cốt lõi của Dead Man's Switch.
2. **Owner ghi hướng dẫn bàn giao:** UC8 nói Executor xem "hướng dẫn bàn giao do Owner để lại", nhưng không UC nào cho Owner tạo nội dung này.
3. **Xem/sửa Vault và Asset:** chỉ có tạo (UC1) và xóa (UC27), thiếu xem danh sách, sửa, xóa từng Asset.
4. **Quên/đặt lại mật khẩu:** UC24 nhắc "khôi phục mật khẩu" nhưng không có UC tương ứng.
5. **Admin mở khóa Beneficiary bị khóa OTP:** UC17 yêu cầu "hỗ trợ từ Admin" nhưng UC20 chỉ nói chung về khóa/mở tài khoản.
6. **Quy trình tự động của hệ thống** (Claim Timeout 60 ngày → Archived-locked, retention 2 năm, kích hoạt Backup Executor) chưa được mô hình hóa thành UC, dù có BR-03/04/05.

### B. Quan hệ include/extend trong mục 4 của SRS cần sửa

| Quan hệ trong SRS | Vấn đề | Đề xuất |
|---|---|---|
| UC24 `<<extend>>` UC25 | Sai ngữ nghĩa, đăng ký không "mở rộng" đăng nhập | Bỏ quan hệ; hoặc UC25 có alternative flow "chưa có tài khoản → UC24" |
| UC9 `<<include>>` UC11 | Mâu thuẫn: UC11 là Optional, chỉ xảy ra "khi cần bổ sung hồ sơ" | Đổi thành **UC11 `<<extend>>` UC9** |
| UC12 `<<extend>>` UC13 | Chiều mũi tên extend phải đi từ UC mở rộng sang UC gốc | **UC13 → UC12** (đúng vì UC13 là bước hỗ trợ tùy chọn) |
| UC25 `<<include>>` mọi UC | Đúng ý nghĩa nhưng vẽ 24 mũi tên làm rối diagram | Chỉ ghi chú "Precondition: đã đăng nhập" |
| UC7 `<<extend>>` "kịch bản Backup Executor" | Kịch bản này chưa là UC riêng nên extend không có đích rõ | Tách thành UC mới (hệ thống kích hoạt Backup Executor) |

### C. Vấn đề khác

- **Role khi đăng ký:** FR-24 chỉ nói mặc định là Owner/Beneficiary, nhưng Executor cũng cần tài khoản. SRS chưa nói Executor được tạo bằng cách nào (Owner mời hay Admin gán).
- **Thiếu secondary actor:** UC7, UC16, UC22, UC23 liên quan Scheduler/Email Service/Mock eKYC. Nên thể hiện trong diagram.
- **UC6 và BR-03:** UC6 cấm Owner thu hồi Executor khi DMS đã kích hoạt, còn BR-03 cho hệ thống tự thu hồi Primary sau 14 ngày. Hai quy tắc nhất quán, nhưng nên ghi rõ trong UC6 rằng "chỉ chặn Owner, không chặn hệ thống".
