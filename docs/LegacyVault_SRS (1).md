# SOFTWARE REQUIREMENTS SPECIFICATION (SRS)
## Hệ thống LegacyVault - Quản lý Di sản số & Bàn giao Tài sản Kỹ thuật số

**Phiên bản:** 1.0
**Chuẩn tham chiếu:** IEEE 830-1998 / IEEE 29148-2018
**Môn học:** SWP391
**Nhóm thực hiện:** 6 thành viên

---

## 1. Introduction

### 1.1 Purpose

Tài liệu này đặc tả các yêu cầu chức năng và phi chức năng của hệ thống **LegacyVault** - một nền tảng cho phép người dùng quản lý, mã hóa và thiết lập cơ chế bàn giao tự động các tài sản kỹ thuật số (tài khoản ngân hàng, ví crypto, mạng xã hội, tài liệu quan trọng) cho người thụ hưởng khi chủ sở hữu qua đời hoặc mất khả năng hoạt động. Tài liệu nhằm mục đích:

- Làm cơ sở thống nhất giữa các thành viên nhóm phát triển về phạm vi và hành vi hệ thống.
- Làm căn cứ để thiết kế cơ sở dữ liệu, kiến trúc hệ thống và test case.
- Phục vụ báo cáo đồ án môn SWP391.

### 1.2 Scope

Hệ thống LegacyVault cho phép:

- Chủ sở hữu (Vault Owner) tạo các "Vault" (kho lưu trữ) chứa tài sản số, mã hóa và chỉ định người thụ hưởng cho từng Vault.
- Thiết lập cơ chế **Dead Man's Switch (DMS)** - kiểm tra định kỳ tình trạng hoạt động của chủ sở hữu, tự động kích hoạt quy trình bàn giao nếu chủ sở hữu không phản hồi trong thời gian quy định.
- Người thi hành (Digital Executor/Trustee) gửi yêu cầu xác minh pháp lý (giấy chứng tử) khi quy trình bàn giao được kích hoạt.
- Người xác minh pháp lý (Legal Verifier/Notary) xác thực và ký điện tử để cấp phép mở khóa Vault.
- Người thụ hưởng (Beneficiary) xác thực danh tính (OTP/KYC) và nhận tài sản được bàn giao.
- Quản trị viên hệ thống (System Administrator) quản lý người dùng, phân quyền (RBAC), giám sát audit log và cấu hình chính sách hệ thống.

**Ngoài phạm vi (Out of scope):** Hệ thống **không** quản lý, lưu trữ hoặc chuyển tiền thật; Vault chỉ lưu trữ thông tin/secret (thông tin đăng nhập, khóa truy cập, tài liệu) liên quan đến tài sản, không thực hiện giao dịch tài chính thực tế. Dịch vụ eKYC và chữ ký số của bên thứ ba được triển khai dưới dạng **mock/giả lập** phục vụ mục đích đồ án.

### 1.3 Definitions, Acronyms, Abbreviations

| Thuật ngữ | Giải thích |
|---|---|
| Vault | Kho lưu trữ số chứa một hoặc nhiều tài sản, được mã hóa, gắn với 1 Beneficiary duy nhất |
| Digital Asset | Tài sản kỹ thuật số (tài khoản ngân hàng, ví crypto, mạng xã hội, tài liệu...) |
| Dead Man's Switch (DMS) | Cơ chế kiểm tra định kỳ "còn sống" của Owner; tự động kích hoạt bàn giao nếu không phản hồi |
| Executor / Trustee | Người thi hành, được Owner ủy quyền thực hiện quy trình bàn giao |
| Backup Executor | Người thi hành dự phòng, ở trạng thái inactive, được kích hoạt nếu primary executor không phản hồi |
| Beneficiary | Người thụ hưởng, nhận tài sản sau khi Vault được mở khóa |
| Legal Verifier / Notary | Người xác minh pháp lý, phê duyệt giấy chứng tử và ký điện tử |
| KYC | Know Your Customer - xác thực danh tính người dùng |
| RBAC | Role-Based Access Control - phân quyền theo vai trò |
| Claim Timeout | Thời hạn Beneficiary phải xác nhận nhận tài sản trước khi Vault bị khóa lưu trữ |
| Archived-locked | Trạng thái Vault chỉ còn giữ metadata, tài sản không còn truy cập được |
| Audit Log | Nhật ký ghi lại các hành động/truy cập trong hệ thống |

### 1.4 References

- IEEE Std 830-1998, *IEEE Recommended Practice for Software Requirements Specifications*.
- ISO/IEC/IEEE 29148:2018, *Systems and software engineering — Life cycle processes — Requirements engineering*.
- Tài liệu quyết định thiết kế nội bộ nhóm (docs/decisions.md).

### 1.5 Overview

Phần còn lại của tài liệu bao gồm: Mô tả tổng quan hệ thống (Mục 2), Yêu cầu chức năng chi tiết theo từng actor (Mục 3), Mô tả Use Case Diagram bằng văn bản (Mục 4), Yêu cầu phi chức năng (Mục 5), và các yêu cầu khác gồm business rule và data requirement (Mục 6).

---

## 2. Overall Description

### 2.1 Product Perspective

LegacyVault là một hệ thống web độc lập (standalone web application), kiến trúc theo mô hình client-server, backend cung cấp REST API, có tích hợp (dạng mock) với các dịch vụ xác thực bên thứ ba (eKYC, chữ ký số điện tử, cổng công chứng điện tử). Hệ thống không phụ thuộc vào hệ thống ngân hàng/tài chính thật; vai trò của hệ thống là "két sắt số" lưu trữ thông tin truy cập tài sản, không phải hệ thống thanh toán.

### 2.2 Product Functions

Tóm tắt các nhóm chức năng chính:

1. Quản lý Vault và tài sản số (tạo, mã hóa, chỉ định thụ hưởng).
2. Cơ chế Dead Man's Switch và quản lý Executor (chính/dự phòng).
3. Quy trình xác minh pháp lý và mở khóa Vault.
4. Quy trình bàn giao và nhận tài sản của Beneficiary (xác thực danh tính, tải xuống).
5. Quản trị hệ thống: người dùng, phân quyền, audit log, cấu hình chính sách.

### 2.3 User Classes and Characteristics

| Actor | Mô tả | Mức độ kỹ thuật |
|---|---|---|
| Vault Owner | Chủ sở hữu tài sản số, tạo và quản lý Vault, thiết lập DMS và Executor | Người dùng phổ thông, không yêu cầu kỹ thuật cao |
| Digital Executor / Trustee | Người được Owner ủy quyền thi hành bàn giao khi DMS kích hoạt | Người dùng phổ thông, cần hiểu quy trình pháp lý cơ bản |
| Beneficiary | Người thụ hưởng, nhận tài sản sau khi Vault mở khóa | Người dùng phổ thông |
| Legal Verifier / Notary | Bên thứ ba xác minh giấy tờ pháp lý (công chứng viên/luật sư) | Người dùng chuyên môn, quen thao tác xác minh tài liệu |
| System Administrator | Vận hành và giám sát hệ thống | Người dùng kỹ thuật (IT/Admin) |

### 2.4 Operating Environment

- **Server:** Ứng dụng backend chạy trên nền tảng cloud/on-premise hỗ trợ containerization (Docker).
- **Client:** Trình duyệt web hiện đại (Chrome, Edge, Firefox), giao diện responsive.
- **Cơ sở dữ liệu:** Hệ quản trị CSDL quan hệ (ví dụ PostgreSQL/MySQL) lưu metadata; dữ liệu tài sản nhạy cảm được mã hóa trước khi lưu trữ.
- **Tích hợp bên thứ ba:** eKYC, chữ ký số, cổng công chứng điện tử - triển khai dạng mock service cho mục đích đồ án.

*(Giả định: hệ thống triển khai theo kiến trúc modular monolith hoặc microservices tùy giai đoạn đồ án, không ảnh hưởng đến đặc tả chức năng trong tài liệu này.)*

### 2.5 Constraints

- Hệ thống **không quản lý tiền thật**; Vault chỉ lưu thông tin/secret liên quan tài sản, không thực hiện giao dịch tài chính.
- Dịch vụ eKYC và chữ ký số là **mock**, không kết nối nhà cung cấp thật.
- Dữ liệu tài liệu pháp lý và secret tài sản phải được mã hóa đầu-cuối (end-to-end encryption) khi lưu trữ và truyền tải.
- Quan hệ dữ liệu: 1 Owner có thể sở hữu N Vault; mỗi Vault chỉ gắn với **đúng 1 Beneficiary**.
- Executor và Dead Man's Switch được gắn theo **Owner** (không gắn theo từng Vault riêng lẻ).

### 2.6 Assumptions and Dependencies

- Giả định người dùng cung cấp thông tin định danh chính xác khi đăng ký.
- Giả định giấy chứng tử số hóa (bản scan/PDF) là định dạng tài liệu xác minh chính được hệ thống chấp nhận.
- Hệ thống phụ thuộc vào dịch vụ gửi thông báo (email/SMS - giả định qua email là kênh chính) để cảnh báo DMS và thông báo bàn giao.
- Các mốc thời gian nghiệp vụ đã được nhóm thống nhất: thời gian kích hoạt Backup Executor là **14 ngày** kể từ khi Primary Executor không phản hồi; **Claim Timeout** cho Beneficiary là **60 ngày** kể từ khi Vault được mở khóa; sau khi hết hạn, Vault chuyển trạng thái **archived-locked** và được lưu trữ (chỉ metadata) trong **2 năm** trước khi có thể bị xóa vĩnh viễn.

---

## 3. Functional Requirements

### Nhóm: Chung cho tất cả Actor (Authentication)

*Các chức năng đăng ký/đăng nhập/đăng xuất áp dụng chung cho mọi actor (Vault Owner, Digital Executor, Beneficiary, Legal Verifier, System Administrator). Cơ chế phiên sử dụng Access Token (JWT, thời hạn ngắn) kết hợp Refresh Token (lưu httpOnly cookie) - xem chi tiết tại mục 5.2 Security.*

#### FR-24: Đăng ký tài khoản

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-24 |
| **Tên chức năng** | Đăng ký tài khoản |
| **Actor** | Tất cả actor (Vault Owner, Digital Executor, Beneficiary, Legal Verifier, System Administrator) |
| **Mô tả** | Người dùng mới tạo tài khoản trên hệ thống bằng email và mật khẩu; tài khoản được xác minh qua email trước khi sử dụng được đầy đủ chức năng. Vai trò mặc định khi tự đăng ký là Vault Owner/Beneficiary (người dùng phổ thông); vai trò Legal Verifier và System Administrator do Admin cấp riêng (xem FR-20), không tự đăng ký công khai. |
| **Input** | Họ tên, email, mật khẩu, xác nhận mật khẩu |
| **Output** | Tài khoản mới được tạo với trạng thái "Pending Verification"; email xác minh được gửi |
| **Precondition** | Email đăng ký chưa tồn tại trong hệ thống |
| **Main Flow** | 1. Người dùng truy cập màn hình "Đăng ký".<br>2. Người dùng nhập họ tên, email, mật khẩu, xác nhận mật khẩu.<br>3. Hệ thống kiểm tra định dạng email, độ mạnh mật khẩu, email chưa từng đăng ký.<br>4. Hệ thống hash mật khẩu (bcrypt/argon2) và tạo tài khoản với trạng thái "Pending Verification".<br>5. Hệ thống gửi email chứa liên kết/OTP xác minh.<br>6. Người dùng nhấn liên kết hoặc nhập OTP để xác minh.<br>7. Hệ thống chuyển trạng thái tài khoản thành "Active" và ghi log tạo tài khoản. |
| **Exception/Alternative Flow** | 3a. Nếu email đã tồn tại → hệ thống báo lỗi, gợi ý đăng nhập hoặc khôi phục mật khẩu.<br>3b. Nếu mật khẩu không đạt yêu cầu độ mạnh tối thiểu → hệ thống báo lỗi validation.<br>6a. Nếu liên kết/OTP xác minh hết hạn → người dùng có thể yêu cầu gửi lại email xác minh.<br>6b. Nếu người dùng không xác minh trong một khoảng thời gian nhất định (ví dụ 7 ngày) → tài khoản "Pending Verification" tự động bị xóa. |
| **Postcondition** | Tài khoản ở trạng thái "Active", sẵn sàng đăng nhập và sử dụng hệ thống theo vai trò mặc định |

#### FR-25: Đăng nhập hệ thống

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-25 |
| **Tên chức năng** | Đăng nhập hệ thống |
| **Actor** | Tất cả actor (Vault Owner, Digital Executor, Beneficiary, Legal Verifier, System Administrator) |
| **Mô tả** | Người dùng xác thực bằng email/username và mật khẩu để truy cập hệ thống theo đúng vai trò (role) đã được cấp. |
| **Input** | Email/username, mật khẩu |
| **Output** | Access Token, Refresh Token; thông tin phiên đăng nhập (userId, role, thời gian đăng nhập) |
| **Precondition** | Người dùng đã có tài khoản hợp lệ (đã xác minh, chưa bị khóa) |
| **Main Flow** | 1. Người dùng nhập email/username và mật khẩu tại màn hình đăng nhập.<br>2. Hệ thống kiểm tra thông tin đăng nhập với dữ liệu đã lưu (mật khẩu đã hash).<br>3. Hệ thống xác thực thành công, xác định vai trò (role) của người dùng.<br>4. Hệ thống phát hành Access Token và Refresh Token, lưu Refresh Token vào httpOnly cookie.<br>5. Hệ thống ghi log đăng nhập (thời gian, IP, thiết bị).<br>6. Hệ thống chuyển hướng người dùng đến giao diện tương ứng với vai trò. |
| **Exception/Alternative Flow** | 2a. Nếu email/username hoặc mật khẩu không đúng → hệ thống báo lỗi, tăng biến đếm số lần thử sai.<br>2b. Nếu số lần thử sai vượt ngưỡng cho phép → hệ thống tạm khóa tài khoản trong một khoảng thời gian và thông báo cho người dùng.<br>3a. Nếu tài khoản đang ở trạng thái bị khóa/vô hiệu hóa (do Admin) → hệ thống từ chối đăng nhập, hiển thị lý do.<br>3b. Nếu tài khoản chưa xác minh email (FR-24) → hệ thống từ chối đăng nhập, đề nghị xác minh trước. |
| **Postcondition** | Người dùng có phiên đăng nhập hợp lệ (Access Token/Refresh Token) để thao tác trên hệ thống theo đúng quyền hạn của vai trò |

#### FR-26: Đăng xuất hệ thống

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-26 |
| **Tên chức năng** | Đăng xuất hệ thống |
| **Actor** | Tất cả actor (Vault Owner, Digital Executor, Beneficiary, Legal Verifier, System Administrator) |
| **Mô tả** | Người dùng kết thúc phiên đăng nhập hiện tại; token của phiên bị thu hồi ngay lập tức. |
| **Input** | Access Token/Refresh Token của phiên hiện tại |
| **Output** | Refresh Token tương ứng bị thu hồi (revoke); phiên đăng nhập kết thúc |
| **Precondition** | Người dùng đang có phiên đăng nhập hợp lệ |
| **Main Flow** | 1. Người dùng chọn "Đăng xuất".<br>2. Hệ thống nhận yêu cầu đăng xuất kèm Refresh Token hiện tại.<br>3. Hệ thống thu hồi (revoke) Refresh Token tương ứng trong CSDL.<br>4. Hệ thống xóa cookie chứa Refresh Token phía client.<br>5. Hệ thống ghi log đăng xuất.<br>6. Hệ thống chuyển hướng người dùng về màn hình đăng nhập. |
| **Exception/Alternative Flow** | 2a. Nếu Refresh Token đã hết hạn hoặc không hợp lệ → hệ thống vẫn xóa phiên phía client và coi như đăng xuất thành công (không báo lỗi). |
| **Postcondition** | Token của phiên đã đăng xuất không còn hiệu lực để gọi API hoặc lấy Access Token mới |

---

### Nhóm: Vault Owner

#### FR-01: Tạo và mã hóa hồ sơ tài sản số

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-01 |
| **Tên chức năng** | Tạo và mã hóa hồ sơ tài sản số |
| **Actor** | Vault Owner |
| **Mô tả** | Owner tạo một Vault mới và thêm các tài sản số (tài khoản ngân hàng, ví crypto, mạng xã hội, tài liệu quan trọng) vào Vault; dữ liệu nhạy cảm được mã hóa trước khi lưu. |
| **Input** | Tên Vault, loại tài sản, thông tin chi tiết tài sản (username, secret/khóa truy cập, ghi chú), tệp đính kèm (nếu có) |
| **Output** | Vault/Asset record được tạo, trạng thái "Active", dữ liệu nhạy cảm ở dạng mã hóa trong CSDL |
| **Precondition** | Owner đã đăng nhập thành công và được xác thực |
| **Main Flow** | 1. Owner chọn "Tạo Vault mới".<br>2. Hệ thống hiển thị form nhập thông tin Vault (tên, mô tả).<br>3. Owner nhập thông tin và chọn "Thêm tài sản".<br>4. Owner chọn loại tài sản và nhập thông tin chi tiết (tài khoản, secret, tài liệu).<br>5. Hệ thống mã hóa dữ liệu nhạy cảm (AES-256 hoặc tương đương) trước khi lưu.<br>6. Owner xác nhận lưu.<br>7. Hệ thống lưu Vault/Asset vào CSDL, trạng thái "Active", ghi log hành động. |
| **Exception/Alternative Flow** | 4a. Nếu thiếu trường bắt buộc → hệ thống báo lỗi validation, yêu cầu nhập lại.<br>5a. Nếu quá trình mã hóa thất bại → hệ thống hủy thao tác lưu, thông báo lỗi hệ thống. |
| **Postcondition** | Vault/Asset được lưu ở trạng thái mã hóa, sẵn sàng để gán Beneficiary |

#### FR-02: Chỉ định người thụ hưởng cho Vault

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-02 |
| **Tên chức năng** | Chỉ định người thụ hưởng cho Vault |
| **Actor** | Vault Owner |
| **Mô tả** | Owner gán một Beneficiary duy nhất cho mỗi Vault (theo quy tắc 1 Vault - 1 Beneficiary). |
| **Input** | Vault ID, thông tin định danh Beneficiary (email/số điện thoại/tài khoản hệ thống) |
| **Output** | Liên kết Vault - Beneficiary được lưu; thông báo mời được gửi đến Beneficiary |
| **Precondition** | Vault đã tồn tại và thuộc quyền sở hữu của Owner đang đăng nhập |
| **Main Flow** | 1. Owner chọn Vault cần gán thụ hưởng.<br>2. Owner chọn "Gán người thụ hưởng".<br>3. Owner nhập thông tin định danh Beneficiary.<br>4. Hệ thống kiểm tra Beneficiary đã có tài khoản hay chưa.<br>5. Nếu chưa có, hệ thống gửi lời mời đăng ký qua email.<br>6. Hệ thống lưu liên kết Vault - Beneficiary.<br>7. Hệ thống gửi thông báo xác nhận cho Owner. |
| **Exception/Alternative Flow** | 2a. Nếu Vault đã có Beneficiary → hệ thống hỏi xác nhận thay thế (ghi đè) trước khi lưu.<br>4a. Nếu định danh không hợp lệ (email sai định dạng) → báo lỗi và yêu cầu nhập lại. |
| **Postcondition** | Vault có đúng 1 Beneficiary được gán, sẵn sàng cho quy trình bàn giao |

#### FR-03: Thiết lập Dead Man's Switch (DMS) và thời gian chờ

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-03 |
| **Tên chức năng** | Thiết lập Dead Man's Switch |
| **Actor** | Vault Owner |
| **Mô tả** | Owner cấu hình chu kỳ kiểm tra "còn sống" (check-in) và thời gian chờ trước khi hệ thống kích hoạt quy trình bàn giao. DMS được gắn theo Owner, áp dụng cho toàn bộ các Vault của Owner đó. |
| **Input** | Chu kỳ check-in (ví dụ: 30 ngày), số lần nhắc nhở, kênh thông báo (email) |
| **Output** | Cấu hình DMS được lưu và kích hoạt; lịch check-in tiếp theo được tạo |
| **Precondition** | Owner đã đăng nhập; có ít nhất 1 Vault đã được tạo |
| **Main Flow** | 1. Owner truy cập mục "Cài đặt Dead Man's Switch".<br>2. Owner nhập chu kỳ check-in và số ngày chờ trước khi kích hoạt bàn giao.<br>3. Owner xác nhận lưu cấu hình.<br>4. Hệ thống lưu cấu hình, đặt trạng thái DMS = "Active".<br>5. Hệ thống lên lịch gửi thông báo check-in định kỳ theo chu kỳ đã cấu hình. |
| **Exception/Alternative Flow** | 2a. Nếu chu kỳ nhập không hợp lệ (ví dụ ≤ 0) → hệ thống báo lỗi validation. |
| **Postcondition** | DMS ở trạng thái Active, hệ thống bắt đầu theo dõi và gửi check-in định kỳ cho Owner |

#### FR-04: Tải lên và mã hóa đầu-cuối tài liệu pháp lý

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-04 |
| **Tên chức năng** | Tải lên và mã hóa tài liệu pháp lý |
| **Actor** | Vault Owner |
| **Mô tả** | Owner tải lên các tài liệu pháp lý liên quan (di chúc, giấy tờ sở hữu) và hệ thống mã hóa đầu-cuối trước khi lưu trữ. |
| **Input** | Tệp tài liệu (PDF/ảnh scan), loại tài liệu, Vault liên quan (nếu có) |
| **Output** | Tài liệu được lưu ở dạng mã hóa, gắn với Vault hoặc hồ sơ Owner |
| **Precondition** | Owner đã đăng nhập |
| **Main Flow** | 1. Owner chọn "Tải lên tài liệu pháp lý".<br>2. Owner chọn tệp và loại tài liệu.<br>3. Hệ thống kiểm tra định dạng và dung lượng tệp.<br>4. Hệ thống mã hóa đầu-cuối tệp trước khi lưu trữ.<br>5. Hệ thống lưu metadata tài liệu (tên, loại, thời gian tải lên).<br>6. Hệ thống thông báo tải lên thành công. |
| **Exception/Alternative Flow** | 3a. Nếu định dạng/dung lượng không hợp lệ → hệ thống từ chối và báo lỗi.<br>4a. Nếu mã hóa thất bại → hệ thống hủy thao tác, thông báo lỗi. |
| **Postcondition** | Tài liệu pháp lý được lưu trữ an toàn, sẵn sàng cho quy trình xác minh sau này |

#### FR-05: Xem nhật ký hoạt động và lịch sử truy cập *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-05 |
| **Tên chức năng** | Xem nhật ký hoạt động và lịch sử truy cập |
| **Actor** | Vault Owner |
| **Mô tả** | Owner xem lại lịch sử các hành động và truy cập vào Vault của mình. |
| **Input** | Vault ID (tùy chọn để lọc), khoảng thời gian |
| **Output** | Danh sách log hoạt động (thời gian, hành động, người thực hiện, IP) |
| **Precondition** | Owner đã đăng nhập và sở hữu ít nhất 1 Vault |
| **Main Flow** | 1. Owner truy cập mục "Nhật ký hoạt động".<br>2. Owner chọn bộ lọc (Vault, khoảng thời gian).<br>3. Hệ thống truy vấn log tương ứng.<br>4. Hệ thống hiển thị danh sách log theo thời gian giảm dần. |
| **Exception/Alternative Flow** | 3a. Nếu không có dữ liệu log phù hợp → hệ thống hiển thị danh sách rỗng kèm thông báo. |
| **Postcondition** | Owner nắm được lịch sử truy cập/hoạt động liên quan Vault |

#### FR-06: Cập nhật hoặc thu hồi quyền truy cập của Executor

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-06 |
| **Tên chức năng** | Cập nhật/thu hồi quyền truy cập của Executor |
| **Actor** | Vault Owner |
| **Mô tả** | Khi còn hoạt động (DMS chưa kích hoạt), Owner có thể chỉ định, thay đổi hoặc thu hồi quyền của Digital Executor (chính và dự phòng) bất kỳ lúc nào. |
| **Input** | Thông tin Executor (chính/dự phòng), hành động (thêm/sửa/thu hồi) |
| **Output** | Danh sách Executor được cập nhật; Executor bị thu hồi mất quyền truy cập ngay lập tức |
| **Precondition** | Owner đã đăng nhập; DMS chưa ở trạng thái kích hoạt bàn giao |
| **Main Flow** | 1. Owner truy cập mục "Quản lý Executor".<br>2. Owner chọn thêm mới, chỉnh sửa hoặc thu hồi một Executor.<br>3. Hệ thống xác nhận thao tác với Owner.<br>4. Hệ thống cập nhật danh sách Executor và quyền tương ứng.<br>5. Hệ thống gửi thông báo cho Executor liên quan về thay đổi quyền. |
| **Exception/Alternative Flow** | 2a. Nếu Owner cố thu hồi Executor khi DMS đã kích hoạt quy trình bàn giao → hệ thống từ chối thao tác, thông báo lý do. |
| **Postcondition** | Quyền của Executor được cập nhật chính xác, phản ánh đúng trạng thái mới nhất |

#### FR-27: Xóa Vault

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-27 |
| **Tên chức năng** | Xóa Vault |
| **Actor** | Vault Owner |
| **Mô tả** | Owner xóa một Vault (kèm toàn bộ tài sản bên trong) khi Vault chưa bước vào quy trình bàn giao. Đây là thao tác không thể hoàn tác, chỉ áp dụng cho Vault ở trạng thái "Active". |
| **Input** | Vault ID cần xóa, xác nhận của Owner (kèm xác thực bổ sung nếu áp dụng step-up authentication) |
| **Output** | Vault và các Asset liên quan bị xóa (hoặc chuyển "soft-deleted" theo chính sách retention); liên kết Beneficiary bị gỡ bỏ |
| **Precondition** | Owner đã đăng nhập; Vault thuộc quyền sở hữu của Owner và đang ở trạng thái "Active" (chưa bị DMS kích hoạt, chưa Unlocked) |
| **Main Flow** | 1. Owner chọn Vault cần xóa từ danh sách Vault.<br>2. Owner chọn "Xóa Vault".<br>3. Hệ thống hiển thị cảnh báo xác nhận (nêu rõ hậu quả không thể hoàn tác, liệt kê số lượng tài sản/tài liệu sẽ bị xóa).<br>4. Owner xác nhận xóa (nhập lại mật khẩu hoặc mã xác thực nếu áp dụng step-up authentication).<br>5. Hệ thống xóa (hoặc soft-delete) Vault cùng toàn bộ Asset và tài liệu pháp lý liên quan, gỡ liên kết Beneficiary.<br>6. Hệ thống ghi log hành động xóa Vault vào Audit Log. |
| **Exception/Alternative Flow** | 1a. Nếu Vault đã bị DMS kích hoạt hoặc đang trong quy trình bàn giao (Pending/Unlocked/Claimed) → hệ thống từ chối xóa, thông báo lý do và gợi ý liên hệ Admin nếu cần xử lý đặc biệt.<br>4a. Nếu xác thực bổ sung thất bại → hệ thống hủy thao tác xóa. |
| **Postcondition** | Vault không còn tồn tại (hoặc ở trạng thái soft-deleted theo chính sách dữ liệu), không còn xuất hiện trong danh sách Vault của Owner |

---

### Nhóm: Digital Executor / Trustee

#### FR-07: Nhận thông báo kích hoạt quy trình bàn giao

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-07 |
| **Tên chức năng** | Nhận thông báo kích hoạt quy trình bàn giao |
| **Actor** | Digital Executor / Trustee |
| **Mô tả** | Khi Owner không phản hồi check-in trong thời gian quy định, hệ thống tự động kích hoạt DMS và gửi thông báo cho Primary Executor; nếu Primary Executor không phản hồi trong 14 ngày, Backup Executor được kích hoạt và Primary Executor bị thu quyền hoàn toàn. |
| **Input** | Sự kiện hệ thống: DMS timeout của Owner tương ứng |
| **Output** | Thông báo (email/hệ thống) gửi đến Executor; trạng thái Executor cập nhật thành "Active" |
| **Precondition** | DMS của Owner đã hết thời gian chờ mà không có phản hồi check-in |
| **Main Flow** | 1. Hệ thống phát hiện Owner không phản hồi check-in quá thời hạn cấu hình.<br>2. Hệ thống chuyển trạng thái DMS của Owner sang "Triggered".<br>3. Hệ thống gửi thông báo kích hoạt cho Primary Executor.<br>4. Executor đăng nhập và xác nhận đã nhận thông báo. |
| **Exception/Alternative Flow** | 3a. Nếu Primary Executor không phản hồi trong 14 ngày → hệ thống tự động thu hồi hoàn toàn quyền của Primary Executor, kích hoạt Backup Executor và gửi thông báo tương ứng. |
| **Postcondition** | Executor hợp lệ (chính hoặc dự phòng) đã được thông báo và có quyền thực hiện các bước tiếp theo của quy trình bàn giao |

#### FR-08: Xem danh sách tài sản được ủy quyền quản lý

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-08 |
| **Tên chức năng** | Xem danh sách tài sản được ủy quyền quản lý và hướng dẫn bàn giao |
| **Actor** | Digital Executor / Trustee |
| **Mô tả** | Sau khi được kích hoạt, Executor xem được danh sách các Vault/tài sản thuộc Owner cùng hướng dẫn bàn giao do Owner để lại. |
| **Input** | Executor ID (từ phiên đăng nhập) |
| **Output** | Danh sách Vault (metadata, chưa giải mã), hướng dẫn bàn giao kèm theo |
| **Precondition** | Executor đã được kích hoạt (trạng thái Active) và đăng nhập thành công |
| **Main Flow** | 1. Executor đăng nhập vào hệ thống.<br>2. Executor truy cập mục "Tài sản được ủy quyền".<br>3. Hệ thống truy vấn danh sách Vault thuộc Owner tương ứng.<br>4. Hệ thống hiển thị danh sách Vault (tên, loại tài sản, Beneficiary được gán) và hướng dẫn bàn giao (nếu Owner đã ghi chú). |
| **Exception/Alternative Flow** | 1a. Nếu Executor chưa được kích hoạt → hệ thống từ chối truy cập, hiển thị thông báo "Chưa đến thời điểm thực hiện quyền". |
| **Postcondition** | Executor nắm được phạm vi tài sản cần xử lý bàn giao |

#### FR-09: Gửi yêu cầu xác minh pháp lý (giấy chứng tử)

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-09 |
| **Tên chức năng** | Gửi yêu cầu xác minh pháp lý để mở khóa kho lưu trữ |
| **Actor** | Digital Executor / Trustee |
| **Mô tả** | Executor tải lên giấy chứng tử và gửi yêu cầu xác minh pháp lý đến Legal Verifier để mở khóa Vault. |
| **Input** | Tệp giấy chứng tử (bản scan), danh sách Vault cần mở khóa, ghi chú (nếu có) |
| **Output** | Yêu cầu xác minh được tạo với trạng thái "Pending", chuyển đến Legal Verifier |
| **Precondition** | Executor đã được kích hoạt và có quyền truy cập Vault tương ứng |
| **Main Flow** | 1. Executor chọn "Gửi yêu cầu xác minh pháp lý".<br>2. Executor tải lên tệp giấy chứng tử.<br>3. Executor chọn các Vault cần mở khóa.<br>4. Hệ thống mã hóa và lưu tệp, tạo yêu cầu xác minh trạng thái "Pending".<br>5. Hệ thống thông báo cho Legal Verifier về yêu cầu mới. |
| **Exception/Alternative Flow** | 2a. Nếu định dạng tệp không hợp lệ → hệ thống báo lỗi, yêu cầu tải lại.<br>4a. Nếu đã tồn tại yêu cầu "Pending" cho cùng Vault → hệ thống từ chối tạo yêu cầu trùng lặp. |
| **Postcondition** | Yêu cầu xác minh pháp lý được ghi nhận, chờ Legal Verifier xử lý |

#### FR-10: Theo dõi tiến độ bàn giao *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-10 |
| **Tên chức năng** | Theo dõi tiến độ bàn giao từng tài sản |
| **Actor** | Digital Executor / Trustee |
| **Mô tả** | Executor theo dõi trạng thái bàn giao của từng Vault (đang chờ xác minh, đã mở khóa, đã nhận, hết hạn...). |
| **Input** | Executor ID |
| **Output** | Danh sách Vault kèm trạng thái bàn giao hiện tại |
| **Precondition** | Executor đã gửi ít nhất 1 yêu cầu xác minh |
| **Main Flow** | 1. Executor truy cập mục "Tiến độ bàn giao".<br>2. Hệ thống truy vấn trạng thái từng Vault liên quan.<br>3. Hệ thống hiển thị danh sách kèm trạng thái (Pending/Verified/Unlocked/Claimed/Archived-locked). |
| **Exception/Alternative Flow** | 2a. Nếu không có Vault nào đang trong quy trình → hiển thị danh sách rỗng. |
| **Postcondition** | Executor nắm được tiến độ tổng thể quy trình bàn giao |

#### FR-11: Trao đổi tài liệu xác minh với Legal Verifier *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-11 |
| **Tên chức năng** | Trao đổi tài liệu xác minh với Legal Verifier qua kênh tích hợp |
| **Actor** | Digital Executor / Trustee |
| **Mô tả** | Executor và Legal Verifier trao đổi bổ sung tài liệu/thông tin liên quan đến yêu cầu xác minh thông qua kênh nhắn tin/tệp đính kèm tích hợp trong hệ thống. |
| **Input** | Yêu cầu xác minh ID, nội dung tin nhắn hoặc tệp bổ sung |
| **Output** | Tin nhắn/tệp được lưu vào lịch sử trao đổi của yêu cầu xác minh |
| **Precondition** | Đã tồn tại yêu cầu xác minh giữa Executor và Legal Verifier |
| **Main Flow** | 1. Executor mở yêu cầu xác minh tương ứng.<br>2. Executor chọn "Gửi bổ sung/trao đổi".<br>3. Executor nhập nội dung hoặc đính kèm tệp.<br>4. Hệ thống lưu và hiển thị trong luồng trao đổi của yêu cầu.<br>5. Hệ thống thông báo cho Legal Verifier về nội dung mới. |
| **Exception/Alternative Flow** | 3a. Nếu tệp đính kèm vượt quá dung lượng cho phép → hệ thống báo lỗi. |
| **Postcondition** | Nội dung trao đổi được lưu lại làm căn cứ xử lý yêu cầu xác minh |

---

### Nhóm: Legal Verifier / Notary

#### FR-12: Xem xét và phê duyệt/từ chối yêu cầu xác minh giấy chứng tử

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-12 |
| **Tên chức năng** | Xem xét và phê duyệt/từ chối yêu cầu xác minh |
| **Actor** | Legal Verifier / Notary |
| **Mô tả** | Legal Verifier kiểm tra tính hợp lệ của giấy chứng tử và các tài liệu liên quan, sau đó phê duyệt hoặc từ chối yêu cầu. |
| **Input** | Yêu cầu xác minh ID, tài liệu đính kèm, quyết định (phê duyệt/từ chối), lý do (nếu từ chối) |
| **Output** | Trạng thái yêu cầu cập nhật thành "Approved" hoặc "Rejected"; thông báo gửi đến Executor |
| **Precondition** | Legal Verifier đã đăng nhập; tồn tại yêu cầu xác minh trạng thái "Pending" |
| **Main Flow** | 1. Legal Verifier truy cập danh sách yêu cầu xác minh đang chờ.<br>2. Legal Verifier chọn 1 yêu cầu để xem chi tiết.<br>3. Legal Verifier kiểm tra tài liệu đính kèm (giấy chứng tử...).<br>4. Legal Verifier chọn "Phê duyệt" hoặc "Từ chối" (kèm lý do).<br>5. Hệ thống cập nhật trạng thái yêu cầu.<br>6. Hệ thống thông báo kết quả cho Executor. |
| **Exception/Alternative Flow** | 4a. Nếu chọn từ chối mà không nhập lý do → hệ thống yêu cầu nhập lý do bắt buộc trước khi xác nhận. |
| **Postcondition** | Yêu cầu xác minh có trạng thái xử lý rõ ràng, Executor nhận được kết quả |

#### FR-13: Xác thực chữ ký số và tính hợp lệ của tài liệu pháp lý *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-13 |
| **Tên chức năng** | Xác thực chữ ký số và tính hợp lệ tài liệu pháp lý |
| **Actor** | Legal Verifier / Notary |
| **Mô tả** | Legal Verifier sử dụng công cụ tích hợp (mock) để kiểm tra chữ ký số/tính toàn vẹn của tài liệu pháp lý trước khi phê duyệt. |
| **Input** | Tài liệu pháp lý cần xác thực |
| **Output** | Kết quả xác thực (hợp lệ/không hợp lệ) hiển thị cho Legal Verifier |
| **Precondition** | Tài liệu đã được tải lên và gắn với yêu cầu xác minh |
| **Main Flow** | 1. Legal Verifier mở tài liệu trong yêu cầu xác minh.<br>2. Legal Verifier chọn "Xác thực chữ ký số".<br>3. Hệ thống gọi dịch vụ xác thực (mock service).<br>4. Hệ thống hiển thị kết quả xác thực (hợp lệ/không hợp lệ) kèm chi tiết. |
| **Exception/Alternative Flow** | 3a. Nếu dịch vụ xác thực không phản hồi → hệ thống báo lỗi và cho phép Legal Verifier xử lý thủ công. |
| **Postcondition** | Kết quả xác thực được lưu lại làm căn cứ hỗ trợ quyết định phê duyệt |

#### FR-14: Ký xác nhận điện tử để cấp phép mở khóa Vault

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-14 |
| **Tên chức năng** | Ký xác nhận điện tử để cấp phép mở khóa kho lưu trữ |
| **Actor** | Legal Verifier / Notary |
| **Mô tả** | Sau khi phê duyệt yêu cầu, Legal Verifier ký xác nhận điện tử để chính thức cấp phép mở khóa các Vault liên quan. |
| **Input** | Yêu cầu xác minh ID (đã Approved), chữ ký điện tử (mock OTP/PIN xác nhận) |
| **Output** | Vault chuyển trạng thái "Unlocked"; thông báo gửi đến Executor và Beneficiary |
| **Precondition** | Yêu cầu xác minh đã ở trạng thái "Approved" |
| **Main Flow** | 1. Legal Verifier mở yêu cầu đã phê duyệt.<br>2. Legal Verifier chọn "Ký xác nhận mở khóa".<br>3. Hệ thống yêu cầu xác thực bổ sung (mock OTP/PIN chữ ký số).<br>4. Legal Verifier xác nhận.<br>5. Hệ thống ghi nhận chữ ký điện tử, cập nhật trạng thái Vault thành "Unlocked".<br>6. Hệ thống gửi thông báo cho Executor và Beneficiary liên quan. |
| **Exception/Alternative Flow** | 3a. Nếu xác thực chữ ký thất bại → hệ thống từ chối thao tác, giữ nguyên trạng thái Vault. |
| **Postcondition** | Vault được mở khóa hợp lệ, sẵn sàng để Beneficiary truy cập |

#### FR-15: Xem lịch sử các hồ sơ đã xác minh *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-15 |
| **Tên chức năng** | Xem lịch sử các hồ sơ đã xác minh và trạng thái pháp lý liên quan |
| **Actor** | Legal Verifier / Notary |
| **Mô tả** | Legal Verifier xem lại danh sách các yêu cầu đã xử lý (phê duyệt/từ chối) trước đó. |
| **Input** | Khoảng thời gian, trạng thái lọc (Approved/Rejected) |
| **Output** | Danh sách lịch sử yêu cầu xác minh kèm trạng thái |
| **Precondition** | Legal Verifier đã đăng nhập |
| **Main Flow** | 1. Legal Verifier truy cập mục "Lịch sử xác minh".<br>2. Legal Verifier chọn bộ lọc (nếu cần).<br>3. Hệ thống truy vấn và hiển thị danh sách. |
| **Exception/Alternative Flow** | 3a. Nếu không có dữ liệu phù hợp bộ lọc → hiển thị danh sách rỗng. |
| **Postcondition** | Legal Verifier có cái nhìn tổng quan về lịch sử xử lý của mình |

---

### Nhóm: Beneficiary

#### FR-16: Nhận thông báo khi có tài sản được bàn giao

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-16 |
| **Tên chức năng** | Nhận thông báo khi có tài sản số được bàn giao |
| **Actor** | Beneficiary |
| **Mô tả** | Khi Vault được Legal Verifier mở khóa, hệ thống gửi thông báo cho Beneficiary tương ứng để bắt đầu quy trình nhận tài sản. |
| **Input** | Sự kiện hệ thống: Vault chuyển trạng thái "Unlocked" |
| **Output** | Thông báo (email/hệ thống) gửi đến Beneficiary, kèm thời hạn xác nhận nhận tài sản (Claim Timeout 60 ngày) |
| **Precondition** | Vault đã được mở khóa và có Beneficiary được gán |
| **Main Flow** | 1. Hệ thống phát hiện Vault chuyển trạng thái "Unlocked".<br>2. Hệ thống xác định Beneficiary được gán cho Vault.<br>3. Hệ thống gửi thông báo kèm hướng dẫn xác thực danh tính và thời hạn nhận tài sản.<br>4. Beneficiary nhận và mở thông báo. |
| **Exception/Alternative Flow** | 2a. Nếu Beneficiary chưa có tài khoản hệ thống → hệ thống gửi lời mời đăng ký kèm thông báo. |
| **Postcondition** | Beneficiary được thông báo và có thể bắt đầu quy trình xác thực để nhận tài sản |

#### FR-17: Xem danh sách và chi tiết tài sản thừa kế sau khi xác thực danh tính

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-17 |
| **Tên chức năng** | Xem danh sách và chi tiết tài sản thừa kế sau khi xác thực danh tính (OTP/KYC) |
| **Actor** | Beneficiary |
| **Mô tả** | Beneficiary phải xác thực danh tính (OTP/KYC - mock) trước khi được phép xem chi tiết tài sản trong Vault đã mở khóa. |
| **Input** | Thông tin xác thực (OTP/KYC), Vault ID |
| **Output** | Danh sách và chi tiết tài sản trong Vault (đã giải mã tạm thời cho phiên xem) |
| **Precondition** | Vault ở trạng thái "Unlocked"; Beneficiary đã đăng nhập |
| **Main Flow** | 1. Beneficiary mở thông báo và truy cập Vault được bàn giao.<br>2. Hệ thống yêu cầu xác thực danh tính (gửi OTP hoặc gọi mock KYC).<br>3. Beneficiary nhập OTP/hoàn tất KYC.<br>4. Hệ thống xác thực thành công, giải mã tạm thời dữ liệu tài sản cho phiên xem.<br>5. Hệ thống hiển thị danh sách và chi tiết tài sản. |
| **Exception/Alternative Flow** | 3a. Nếu OTP sai hoặc KYC thất bại → hệ thống từ chối truy cập, cho phép thử lại (giới hạn số lần).<br>3b. Nếu vượt quá số lần thử → hệ thống tạm khóa truy cập và yêu cầu hỗ trợ từ Admin. |
| **Postcondition** | Beneficiary xem được nội dung tài sản đã được xác thực quyền truy cập |

#### FR-18: Tải xuống tài liệu/khóa truy cập tài sản đã giải mã

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-18 |
| **Tên chức năng** | Tải xuống tài liệu hoặc khóa truy cập tài sản đã được giải mã |
| **Actor** | Beneficiary |
| **Mô tả** | Sau khi xác thực thành công, Beneficiary tải xuống tài liệu/thông tin truy cập tài sản ở dạng đã giải mã. |
| **Input** | Asset ID cần tải xuống |
| **Output** | Tệp/thông tin tài sản ở dạng giải mã được gửi về client Beneficiary |
| **Precondition** | Beneficiary đã xác thực danh tính thành công (FR-17) |
| **Main Flow** | 1. Beneficiary chọn tài sản cần tải xuống.<br>2. Hệ thống kiểm tra lại phiên xác thực còn hiệu lực.<br>3. Hệ thống giải mã dữ liệu tài sản.<br>4. Hệ thống trả về tệp/thông tin cho Beneficiary tải xuống.<br>5. Hệ thống ghi log hành động tải xuống. |
| **Exception/Alternative Flow** | 2a. Nếu phiên xác thực hết hạn → hệ thống yêu cầu xác thực lại trước khi cho tải xuống. |
| **Postcondition** | Beneficiary có được tài liệu/thông tin truy cập tài sản; log tải xuống được ghi nhận |

#### FR-19: Xác nhận đã nhận bàn giao để đóng hồ sơ *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-19 |
| **Tên chức năng** | Xác nhận đã nhận bàn giao để đóng hồ sơ tài sản tương ứng |
| **Actor** | Beneficiary |
| **Mô tả** | Beneficiary xác nhận đã hoàn tất việc nhận tài sản, hệ thống đóng hồ sơ Vault tương ứng (trước khi hết Claim Timeout 60 ngày). |
| **Input** | Vault ID, xác nhận từ Beneficiary |
| **Output** | Vault chuyển trạng thái "Claimed/Closed" |
| **Precondition** | Beneficiary đã tải xuống/xem tài sản (FR-17, FR-18) |
| **Main Flow** | 1. Beneficiary truy cập Vault đã nhận.<br>2. Beneficiary chọn "Xác nhận đã nhận bàn giao".<br>3. Hệ thống yêu cầu xác nhận lần cuối.<br>4. Beneficiary xác nhận.<br>5. Hệ thống cập nhật trạng thái Vault thành "Claimed/Closed" và ghi log. |
| **Exception/Alternative Flow** | 3a. Nếu Beneficiary hủy xác nhận → hệ thống giữ nguyên trạng thái hiện tại (chưa đóng hồ sơ). |
| **Postcondition** | Vault được đóng hồ sơ chính thức, kết thúc quy trình bàn giao cho Vault đó |

---

### Nhóm: System Administrator

#### FR-20: Quản lý danh mục người dùng, vai trò và RBAC

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-20 |
| **Tên chức năng** | Quản lý danh mục người dùng, vai trò và phân quyền truy cập (RBAC) |
| **Actor** | System Administrator |
| **Mô tả** | Admin quản lý danh sách tài khoản người dùng, gán vai trò (Owner/Executor/Beneficiary/Legal Verifier/Admin) và cấu hình quyền truy cập tương ứng. |
| **Input** | Thông tin người dùng, vai trò cần gán/thu hồi |
| **Output** | Danh sách người dùng và phân quyền được cập nhật |
| **Precondition** | Admin đã đăng nhập với quyền quản trị |
| **Main Flow** | 1. Admin truy cập mục "Quản lý người dùng".<br>2. Admin tìm kiếm/lọc danh sách người dùng.<br>3. Admin chọn người dùng cần chỉnh sửa vai trò/quyền.<br>4. Admin cập nhật vai trò hoặc trạng thái tài khoản (active/khóa).<br>5. Hệ thống lưu thay đổi và áp dụng ngay lập tức.<br>6. Hệ thống ghi log thao tác quản trị. |
| **Exception/Alternative Flow** | 4a. Nếu Admin cố tự thu hồi quyền quản trị của chính mình mà không còn Admin nào khác → hệ thống từ chối thao tác. |
| **Postcondition** | Danh mục người dùng và phân quyền phản ánh đúng trạng thái mới nhất |

#### FR-21: Giám sát audit log và cảnh báo truy cập bất thường *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-21 |
| **Tên chức năng** | Giám sát nhật ký bảo mật (Audit Log) và cảnh báo truy cập bất thường |
| **Actor** | System Administrator |
| **Mô tả** | Admin theo dõi audit log toàn hệ thống và nhận cảnh báo khi phát hiện hành vi truy cập bất thường vào các Vault. |
| **Input** | Bộ lọc log (thời gian, người dùng, loại hành động) |
| **Output** | Danh sách audit log, cảnh báo bất thường (nếu có) |
| **Precondition** | Admin đã đăng nhập |
| **Main Flow** | 1. Admin truy cập mục "Audit Log".<br>2. Admin áp dụng bộ lọc tìm kiếm.<br>3. Hệ thống hiển thị danh sách log tương ứng.<br>4. Hệ thống tự động đánh dấu các sự kiện bất thường (ví dụ: nhiều lần đăng nhập thất bại, truy cập ngoài giờ) và gửi cảnh báo cho Admin. |
| **Exception/Alternative Flow** | 4a. Nếu không phát hiện bất thường → không có cảnh báo được tạo. |
| **Postcondition** | Admin nắm được tình trạng bảo mật hệ thống theo thời gian thực |

#### FR-22: Cấu hình chính sách mã hóa, sao lưu và quy tắc mặc định DMS *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-22 |
| **Tên chức năng** | Cấu hình chính sách mã hóa, sao lưu dữ liệu và quy tắc mặc định cho Dead Man's Switch |
| **Actor** | System Administrator |
| **Mô tả** | Admin cấu hình các thông số hệ thống mặc định: thuật toán/chu kỳ mã hóa, lịch sao lưu dữ liệu, và giá trị mặc định cho DMS (chu kỳ check-in, thời gian kích hoạt backup executor, claim timeout). |
| **Input** | Các tham số cấu hình hệ thống |
| **Output** | Cấu hình hệ thống được cập nhật và áp dụng cho các thiết lập mới |
| **Precondition** | Admin đã đăng nhập với quyền quản trị |
| **Main Flow** | 1. Admin truy cập mục "Cấu hình hệ thống".<br>2. Admin chỉnh sửa các tham số (chính sách mã hóa, lịch sao lưu, giá trị mặc định DMS/claim timeout).<br>3. Admin lưu cấu hình.<br>4. Hệ thống xác thực giá trị hợp lệ và áp dụng.<br>5. Hệ thống ghi log thay đổi cấu hình. |
| **Exception/Alternative Flow** | 4a. Nếu giá trị nhập không hợp lệ (ví dụ số âm) → hệ thống từ chối lưu, báo lỗi. |
| **Postcondition** | Cấu hình hệ thống mới được áp dụng cho các Vault/Owner thiết lập sau đó (không hồi tố các cấu hình đã tồn tại, trừ khi Admin chọn áp dụng lại) |

#### FR-23: Quản lý tích hợp với dịch vụ xác thực bên thứ ba *(Optional)*

| Trường | Nội dung |
|---|---|
| **Mã chức năng** | FR-23 |
| **Tên chức năng** | Quản lý tích hợp với dịch vụ xác thực bên thứ ba (eKYC, chữ ký số, cổng công chứng điện tử) |
| **Actor** | System Administrator |
| **Mô tả** | Admin quản lý cấu hình kết nối (mock) đến các dịch vụ xác thực bên thứ ba được hệ thống sử dụng trong quy trình KYC và xác minh pháp lý. |
| **Input** | Thông tin cấu hình dịch vụ (endpoint mock, trạng thái bật/tắt) |
| **Output** | Danh sách tích hợp và trạng thái hoạt động được cập nhật |
| **Precondition** | Admin đã đăng nhập với quyền quản trị |
| **Main Flow** | 1. Admin truy cập mục "Quản lý tích hợp bên thứ ba".<br>2. Admin chọn dịch vụ cần cấu hình (eKYC/chữ ký số/notary gateway).<br>3. Admin cập nhật thông tin cấu hình hoặc bật/tắt dịch vụ.<br>4. Hệ thống lưu cấu hình và kiểm tra kết nối (mock).<br>5. Hệ thống hiển thị trạng thái kết nối. |
| **Exception/Alternative Flow** | 4a. Nếu kiểm tra kết nối thất bại → hệ thống hiển thị trạng thái "Lỗi kết nối" và giữ dịch vụ ở trạng thái tắt. |
| **Postcondition** | Danh sách tích hợp bên thứ ba phản ánh đúng trạng thái cấu hình hiện tại |

---

## 4. Use Case Diagram (mô tả bằng văn bản)

**Actor: Chung (mọi actor)**
- UC24: Đăng ký tài khoản
- UC25: Đăng nhập hệ thống
- UC26: Đăng xuất hệ thống

**Actor: Vault Owner**
- UC1: Tạo và mã hóa hồ sơ tài sản số
- UC2: Chỉ định người thụ hưởng cho Vault
- UC3: Thiết lập Dead Man's Switch
- UC4: Tải lên và mã hóa tài liệu pháp lý
- UC5: Xem nhật ký hoạt động và lịch sử truy cập
- UC6: Cập nhật/thu hồi quyền truy cập của Executor
- UC27: Xóa Vault

**Actor: Digital Executor / Trustee**
- UC7: Nhận thông báo kích hoạt quy trình bàn giao
- UC8: Xem danh sách tài sản được ủy quyền quản lý
- UC9: Gửi yêu cầu xác minh pháp lý
- UC10: Theo dõi tiến độ bàn giao
- UC11: Trao đổi tài liệu xác minh với Legal Verifier

**Actor: Legal Verifier / Notary**
- UC12: Xem xét và phê duyệt/từ chối yêu cầu xác minh
- UC13: Xác thực chữ ký số và tính hợp lệ tài liệu
- UC14: Ký xác nhận điện tử để cấp phép mở khóa Vault
- UC15: Xem lịch sử các hồ sơ đã xác minh

**Actor: Beneficiary**
- UC16: Nhận thông báo khi có tài sản được bàn giao
- UC17: Xem danh sách/chi tiết tài sản sau xác thực danh tính
- UC18: Tải xuống tài liệu/khóa truy cập tài sản
- UC19: Xác nhận đã nhận bàn giao

**Actor: System Administrator**
- UC20: Quản lý danh mục người dùng, vai trò và RBAC
- UC21: Giám sát audit log và cảnh báo bất thường
- UC22: Cấu hình chính sách mã hóa/sao lưu/quy tắc DMS mặc định
- UC23: Quản lý tích hợp dịch vụ bên thứ ba

**Quan hệ include/extend gợi ý:**
- UC24 (Đăng ký) `<<extend>>` UC25 (Đăng nhập) - người dùng mới cần đăng ký trước khi có thể đăng nhập lần đầu.
- UC25 (Đăng nhập) `<<include>>` bắt buộc đối với mọi use case còn lại của tất cả actor (mọi thao tác đều yêu cầu phiên đăng nhập hợp lệ).
- UC27 (Xóa Vault) chỉ khả dụng khi Vault ở trạng thái "Active" (ràng buộc, không phải include/extend).
- UC9 (Gửi yêu cầu xác minh) `<<include>>` UC11 (Trao đổi tài liệu) khi cần bổ sung hồ sơ.
- UC12 (Phê duyệt yêu cầu) `<<extend>>` UC13 (Xác thực chữ ký số) như một bước hỗ trợ tùy chọn.
- UC17 (Xem tài sản sau xác thực) `<<include>>` bước xác thực OTP/KYC là điều kiện bắt buộc.
- UC7 (Nhận thông báo kích hoạt) `<<extend>>` kịch bản kích hoạt Backup Executor khi Primary Executor không phản hồi.

---

## 5. Non-Functional Requirements

### 5.1 Performance
- Thời gian phản hồi trung bình cho các thao tác đọc (xem danh sách Vault, tài sản) ≤ 2 giây trong điều kiện tải bình thường.
- Hệ thống hỗ trợ tối thiểu 500 người dùng đồng thời *(giả định theo quy mô đồ án)*.

### 5.2 Security
- Toàn bộ dữ liệu nhạy cảm (secret tài sản, tài liệu pháp lý) phải được mã hóa đầu-cuối khi lưu trữ (at-rest) và truyền tải (in-transit, TLS 1.2+).
- Áp dụng RBAC nghiêm ngặt: mỗi actor chỉ truy cập được dữ liệu thuộc phạm vi quyền của mình.
- Beneficiary bắt buộc xác thực đa yếu tố (OTP/KYC) trước khi truy cập tài sản đã mở khóa.
- Giới hạn số lần thử xác thực sai (OTP/đăng nhập) để chống brute-force.
- Toàn bộ hành động nhạy cảm (mở khóa Vault, thu hồi Executor, thay đổi RBAC) phải được ghi vào Audit Log không thể chỉnh sửa.

### 5.3 Usability
- Giao diện trực quan, phù hợp cho người dùng không chuyên về kỹ thuật (Owner, Beneficiary).
- Cung cấp hướng dẫn từng bước (wizard) cho quy trình thiết lập Vault và DMS lần đầu.
- Thông báo lỗi rõ ràng, bằng ngôn ngữ thân thiện với người dùng phổ thông.

### 5.4 Reliability
- Cơ chế DMS phải hoạt động ổn định, không được bỏ sót việc gửi thông báo check-in hoặc kích hoạt bàn giao đúng hạn (độ tin cậy ≥ 99.5%).
- Dữ liệu được sao lưu định kỳ để đảm bảo không mất mát khi có sự cố hệ thống.

### 5.5 Scalability
- Kiến trúc hệ thống cho phép mở rộng theo chiều ngang (horizontal scaling) khi số lượng Owner/Vault tăng.
- Thiết kế module hóa cho phép bổ sung thêm loại tài sản số hoặc dịch vụ xác thực bên thứ ba mới mà không ảnh hưởng lớn đến các module hiện có.

---

## 6. Other Requirements

### 6.1 Business Rules

- BR-01: 1 Owner có thể sở hữu N Vault; mỗi Vault chỉ được gán **đúng 1 Beneficiary**.
- BR-02: Executor (chính và dự phòng) và cấu hình Dead Man's Switch được gắn theo **Owner**, áp dụng chung cho toàn bộ Vault của Owner đó.
- BR-03: Backup Executor ở trạng thái "Inactive" cho đến khi Primary Executor không phản hồi trong **14 ngày** kể từ khi được thông báo kích hoạt; khi đó Backup Executor được kích hoạt và Primary Executor bị **thu hồi quyền hoàn toàn**.
- BR-04: Beneficiary có **60 ngày (Claim Timeout)** kể từ khi Vault chuyển "Unlocked" để xác nhận nhận tài sản; nếu quá hạn, Vault tự động chuyển trạng thái **"Archived-locked"** (chỉ giữ metadata, không còn truy cập được nội dung tài sản).
- BR-05: Vault ở trạng thái "Archived-locked" được lưu trữ **2 năm** trước khi đủ điều kiện xóa vĩnh viễn theo chính sách retention.
- BR-06: Hệ thống chỉ đóng vai trò lưu trữ thông tin/secret tài sản, **không thực hiện bất kỳ giao dịch tài chính thực tế nào**.
- BR-07: Dịch vụ eKYC và chữ ký số điện tử được triển khai dưới dạng **mock service** phục vụ mục đích đồ án, không kết nối nhà cung cấp thật.
- BR-08: Owner chỉ được phép xóa Vault khi Vault đang ở trạng thái **"Active"** (chưa bị Dead Man's Switch kích hoạt, chưa chuyển "Unlocked"); Vault đã bước vào quy trình bàn giao không thể bị Owner tự ý xóa.

### 6.2 Data Requirements

- Dữ liệu tài sản (asset secret) và tài liệu pháp lý phải được mã hóa bằng thuật toán mã hóa mạnh (ví dụ AES-256) trước khi lưu trữ; khóa giải mã không được lưu trữ cùng vị trí với dữ liệu đã mã hóa.
- Mọi thay đổi trạng thái quan trọng của Vault (Active → Unlocked → Claimed/Archived-locked) phải được lưu vết đầy đủ trong Audit Log kèm timestamp và actor thực hiện.
- Dữ liệu định danh người dùng (KYC) chỉ được lưu ở mức tối thiểu cần thiết để xác thực, tuân thủ nguyên tắc tối thiểu hóa dữ liệu (data minimization).

---

*Hết tài liệu.*