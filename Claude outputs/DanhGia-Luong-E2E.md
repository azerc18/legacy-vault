# Đánh giá các luồng end-to-end (theo hành trình, không theo role)

Đánh giá dựa trên code mới nhất trên máy, đọc ngày 30/10: 45 file backend vừa đổi và 17 file frontend.

Ký hiệu: ✅ chạy trọn | ⚠️ chạy được nhưng có lỗ hổng | ⛔ có thể kẹt vĩnh viễn

## Tổng quan

| # | Luồng | Trạng thái | Mức hoàn thiện |
|---|---|---|---|
| 1 | Tham gia hệ thống | ✅ | 95% |
| 2 | Lập kế hoạch thừa kế | ⚠️ | 90% |
| 3 | Còn sống: nhắc nhở và check-in | ✅ | 95% |
| 4 | Kích hoạt khi chủ vault "im lặng" | ⛔ | 80% |
| 5 | Xác minh cái chết | ✅ | 95% |
| 6 | Bàn giao cho người thụ hưởng | ✅ | 90% |
| 7 | Thay đổi kế hoạch khi còn sống | ⚠️ | 90% |
| 8 | Giám sát và an ninh | ⚠️ | 85% |
| 9 | Thông báo xuyên suốt | ⚠️ | 80% |

**Tổng:** khoảng **90%**. Còn **1 điểm kẹt thật** ở luồng 4 và **1 lỗi dữ liệu** ở luồng 8.

---

## Luồng 1: Tham gia hệ thống ✅

```
Đăng ký → mail xác thực → kích hoạt tài khoản → đăng nhập (JWT + refresh)
        └→ nếu có vault đang chờ email này: tự gắn làm Beneficiary (linkPendingBeneficiaryVaults)
Quên mật khẩu → OTP/mail → đặt lại → đăng nhập
```

- Chính sách mật khẩu ở FE và BE đã khớp (8–72 ký tự, đủ 4 nhóm).
- Header và câu chữ đã được thống nhất ở lượt sửa UI vừa rồi.

## Luồng 2: Lập kế hoạch thừa kế ⚠️

```
Tạo vault → thêm tài sản / tài liệu (mã hoá) → chọn Beneficiary
        → chỉ định Executor (Primary, Backup) → bật DMS (BE bắt buộc phải có Executor trước)
```

- **Lỗ hổng:** vẫn tạo được vault khi chưa có Executor hoặc DMS. Hiện chỉ được giảm thiểu bằng **ProtectionBanner**, chưa chặn.
  - Chấp nhận được nếu banner luôn hiện.
  - Muốn chặt hơn thì cho tạo vault xong rồi chuyển thẳng sang wizard "Executor → DMS".

## Luồng 3: Còn sống, nhắc nhở và check-in ✅

```
DMS ACTIVE → ReminderScheduler gửi nhắc trước hạn → Owner check-in → hạn mới
          → quá hạn + grace: hoãn 24h (tối đa N lần, nhắc bù) → TRIGGERED
Admin: tạm dừng / tiếp tục DMS (SUSPENDED)
```

## Luồng 4: Kích hoạt khi chủ vault "im lặng" ⛔

```
TRIGGERED → kích hoạt Primary + mail → Primary acknowledge
   ├ Primary không acknowledge sau 14 ngày → thu hồi, kích hoạt Backup  ✅
   ├ Không còn Executor dùng được (thu hồi / tài khoản bị khoá) → Admin cứu hộ, gán người mới  ✅ (BE + FE đã có)
   └ ⛔ Hai trường hợp dưới đây không ai xử lý được
```

**Trường hợp ⛔ A:** Primary **không acknowledge** và Owner **không có Backup**.

- `escalateIfPrimaryUnresponsive` chỉ ghi `log.warn` rồi return.
- Admin cứu hộ bị chặn vì `ExecutorRescueRule.isUsable(primary)` vẫn trả về true (ACTIVE, user ACTIVE).
- Kết quả: vault kẹt vĩnh viễn.

**Trường hợp ⛔ B:** Primary **đã acknowledge nhưng không nộp giấy chứng tử** (B-03, Bách).

- Không có nhánh leo thang nào cho trường hợp này. Admin cũng không cứu được vì cùng lý do trên.

**Sửa gọn nhất** (chỉ đụng `ExecutorRescueRule`): coi Executor là "không dùng được" khi đã ACTIVE quá N ngày mà chưa có hồ sơ xác minh nào.

```java
public static boolean isUsable(ExecutorAssignment a, boolean hasSubmitted, LocalDateTime now, int waitDays) {
    if (a.getStatus() != ExecutorStatus.ACTIVE || a.getExecutorUser() == null
            || a.getExecutorUser().getStatus() != UserStatus.ACTIVE) return false;
    // ⛔ A + B: kích hoạt quá waitDays ngày mà chưa nộp hồ sơ nào -> cho Admin cứu hộ
    return hasSubmitted || a.getActivatedAt() == null || a.getActivatedAt().plusDays(waitDays).isAfter(now);
}
```

Cả `AdminUserServiceImpl` (cờ `executorRescueNeeded`) lẫn `AdminExecutorRescueServiceImpl` đều gọi rule này, nên một chỗ sửa áp dụng cho cả hai.

- `hasSubmitted`: entity hồ sơ không có khoá ngoại tới assignment, nên thêm một method derived query vào `LegalVerificationRequestRepository`:

  ```java
  boolean existsByOwnerIdAndExecutorIdAndCreatedAtAfter(UUID ownerId, UUID executorUserId, LocalDateTime since);
  ```

  Gọi với `since = a.getActivatedAt()` để chỉ tính những hồ sơ nộp sau lần kích hoạt này.
- `waitDays` lấy từ `config.getBackupActivationWaitDays()`.

## Luồng 5: Xác minh cái chết ✅

```
Executor nộp giấy chứng tử (chọn vault) → Verifier mở tài liệu → checklist → Approve → ký bằng mã → vault UNLOCKED
                                                                          └ Reject (lý do) → Executor nộp lại hồ sơ mới
Hai bên nhắn tin, có đính kèm file (đóng khi REJECTED) · Approve quá hạn mà không ký → tự hết hạn
```

- Vault bị khoá chỉnh sửa trong lúc có hồ sơ mở (`VaultModificationGuard`). FE hiện một nhãn "Locked".

## Luồng 6: Bàn giao cho người thụ hưởng ✅

```
VaultsUnlocked → mail + thông báo trong app cho Beneficiary (có retry) → đăng nhập
  → xác minh danh tính (OTP / KYC mock) → claim → xem / tải tài sản và tài liệu
  → đóng vault (xem trước) → ARCHIVED → purge theo hạn
Không claim kịp → ClaimTimeoutScheduler đánh dấu hết hạn
```

- **Lưu ý:** Beneficiary chưa có tài khoản thì vault chờ ở trạng thái pending, khi họ đăng ký sẽ tự được gắn. Cần test tay một lần trọn đường này.

## Luồng 7: Thay đổi kế hoạch khi còn sống ⚠️

- Đổi Beneficiary và đổi Executor đều có xác nhận thay thế và mail báo người bị thay ✅.
- Xoá vault: đã sửa lỗi `TransientPropertyValueException`. `findFileUrlsByVaultId` + `deleteByVaultId` đã có trong code ✅.
- ⚠️ Xem luồng 8: khi xoá thất bại vẫn bị ghi log thành công.

## Luồng 8: Giám sát và an ninh ⚠️

```
Mọi hành động → AuditEvent → audit log (Owner xem log của mình, Admin xem toàn bộ)
SecurityAlertScheduler (5 phút) → cảnh báo → Admin xác nhận
Admin: khoá / mở tài khoản, cấp / thu role, CRUD role, tạm dừng DMS, cứu hộ Executor
```

**⚠️ Chưa sửa: `AuditEventListener` vẫn dùng `AFTER_COMPLETION` + `REQUIRES_NEW`.**

- Hậu quả: transaction rollback vẫn ghi log SUCCESS (chính là lỗi "không xoá được mà vẫn có log").
- Cách sửa đã gửi trước đó: tách thành `onCommit` (`AFTER_COMMIT`, `fallbackExecution = true`) và `onRollback` (`AFTER_ROLLBACK`, chỉ lưu khi `result == FAILURE`).

## Luồng 9: Thông báo xuyên suốt ⚠️

- Mail: kích hoạt Executor, thay Executor, quyết định xác minh, Verifier trả lời, mở khoá vault ✅.
- **Mới ở BE:** thông báo trong app cho Executor (`/api/notifications/me`, `unread-count`, `PATCH /{id}/read`) với các loại `VERIFIER_REPLY` và `VERIFICATION_DECIDED`.
  - ⚠️ **FE chưa gọi các API này.** `NotificationBell` chỉ có ở trang Beneficiary và đọc `/beneficiaries/notifications`.
  - Cần thêm chuông cho Executor, ví dụ `extra={<NotificationBell source="executor" />}` trong `AppHeader` ở `ExecutorHome` và `ExecutorCase`.

---

## Việc cần làm, theo thứ tự ưu tiên

| # | Việc | Ai | Ước lượng |
|---|---|---|---|
| 1 | ⛔ Sửa `ExecutorRescueRule` để gỡ kẹt luồng 4 (cả A và B) | Bách / Toan | 1–2h |
| 2 | ⚠️ Tách `AuditEventListener` thành commit / rollback | Toan | 30' |
| 3 | ⚠️ Gắn điều kiện cho 5 `EmptyState` (Admin ×3, OwnerVaultDetail, VerificationMessages) | Toan | 10' |
| 4 | ⚠️ Chuông thông báo cho Executor ở FE | FE | 1h |
| 5 | Test tay luồng 6: Beneficiary chưa có tài khoản | cả nhóm | 30' |
| 6 | Dọn: `App.jsx` khai báo route `/home` 2 lần (dòng 56 và 90) | FE | 1' |
