# Tài liệu dự án

| File | Nội dung | Trạng thái |
|---|---|---|
| `LegacyVault_SRS (1).md` | SRS v1.0 (FR-01 đến FR-27), viết trước đợt nâng cấp sau review giữa kỳ | **Cũ**: chưa có các tính năng thêm từ 31/10/2026 (phân bổ tài sản N:M cho nhiều Beneficiary, xác nhận chéo DMS, đồng thuận nhiều Executor, lời nhắn di nguyện, versioning tài liệu, gói VIP + VNPay, 2FA TOTP, đăng nhập Google, trợ lý AI, lưu file trên R2...). Giữ lại để đối chiếu mã FR gốc |
| `LegacyVault_UseCases_by_Actor.md` | 27 use case theo actor, dựng từ SRS v1.0 | Cũ theo SRS v1.0 |
| `backfill_audit_vault_id.sql` | Script chạy tay một lần để điền `audit_logs.vault_id` cho log cũ | **Lịch sử**: viết trước khi có Flyway. DB mới tạo bằng Flyway không cần chạy |

## SRS theo sản phẩm hiện tại

Bản **SRS v2** (giữ mã FR của v1, thêm FR-28 trở đi, có mục so sánh với v1) được viết lại từ code trên `main` ngày 06/11/2026 và đang được nhóm cập nhật trên Google Docs (thư mục Drive chung của nhóm). Khi cần chốt bản nộp, xuất bản mới nhất thành Markdown và thêm vào thư mục này.

## Tài liệu kỹ thuật

Hướng dẫn chạy local, profile, Flyway, scheduler, CI/CD và deploy Azure: xem [README ở thư mục gốc](../README.md).
