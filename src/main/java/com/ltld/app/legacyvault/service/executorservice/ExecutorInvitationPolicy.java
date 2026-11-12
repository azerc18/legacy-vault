package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hạn trả lời lời mời làm Executor: người được Owner chỉ định có app.executor.invitation-expiry-days ngày
 * (mặc định 7) để đồng ý hoặc từ chối; còn app.executor.invitation-reminder-days ngày (mặc định 3) thì
 * được gửi mail nhắc một lần. Quá hạn, {@link ExecutorInvitationExpiryScheduler} tự thu hồi để vị trí
 * không bị treo mãi ở PENDING.
 *
 * <p>Tách riêng để nơi chỉ định (FR-06), nơi trả lời và job quét dùng chung một luật, và để test
 * dựng được policy với số ngày tuỳ ý.
 */
@Component
public class ExecutorInvitationPolicy {

    private final int expiryDays;
    private final int reminderDays;

    public ExecutorInvitationPolicy(@Value("${app.executor.invitation-expiry-days:7}") int expiryDays,
                                    @Value("${app.executor.invitation-reminder-days:3}") int reminderDays) {
        this.expiryDays = expiryDays;
        this.reminderDays = reminderDays;
    }

    public int expiryDays() {
        return expiryDays;
    }

    public int reminderDays() {
        return reminderDays;
    }

    /** Hạn trả lời tính từ lúc Owner chỉ định. */
    public LocalDateTime expiresAt(LocalDateTime assignedAt) {
        return assignedAt.plusDays(expiryDays);
    }

    /**
     * Lời mời đã quá hạn chưa? Dùng cả ở lúc trả lời (chặn ngay, không chờ job) và ở job quét.
     * Dòng không phải lời mời đang chờ, hoặc dữ liệu cũ không có hạn, thì không bao giờ "hết hạn".
     */
    public boolean isExpired(ExecutorAssignment assignment, LocalDateTime now) {
        return assignment.getStatus() != ExecutorStatus.REVOKED
                && assignment.getResponseStatus() == ExecutorResponseStatus.PENDING
                && assignment.getInvitationExpiresAt() != null
                && !assignment.getInvitationExpiresAt().isAfter(now);
    }

    /** Lời mời còn đang mở: chờ phản hồi, chưa bị thu hồi, có hạn và hạn chưa tới. */
    public boolean isOpen(ExecutorAssignment assignment, LocalDateTime now) {
        return assignment.getStatus() != ExecutorStatus.REVOKED
                && assignment.getResponseStatus() == ExecutorResponseStatus.PENDING
                && assignment.getInvitationExpiresAt() != null
                && assignment.getInvitationExpiresAt().isAfter(now);
    }

    /** Số ngày còn lại, làm tròn lên, tối thiểu 1 — chỉ để hiển thị trong mail nhắc. */
    public int daysLeft(LocalDateTime expiresAt, LocalDateTime now) {
        long hours = java.time.Duration.between(now, expiresAt).toHours();
        return (int) Math.max(1, (hours + 23) / 24);
    }
}
