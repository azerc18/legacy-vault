// Nhãn/định dạng dùng chung cho các trang Legal Verifier (khớp enum LegalVerificationStatus của BE)

export const STATUS_LABEL = {
    PENDING: 'Pending review',
    APPROVED: 'Approved, awaiting signature',
    SIGNED: 'Signed',
    REJECTED: 'Rejected',
};

export const STATUS_CLASS = {
    PENDING: 'bg-warning-container text-on-warning-container',
    APPROVED: 'bg-secondary-fixed text-on-secondary-fixed',
    SIGNED: 'bg-success-container text-on-success-container',
    REJECTED: 'bg-error-container text-on-error-container',
};

// BE trả LocalDateTime "2026-10-16T10:30:00"
export function formatDate(value, withTime = false) {
    if (!value) return '—';
    const d = new Date(value);
    return withTime
        ? d.toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' })
        : d.toLocaleDateString('en-GB', { dateStyle: 'medium' });
}

// UUID dài -> hiển thị 8 ký tự đầu
export const shortId = (id) => (id ? String(id).slice(0, 8).toUpperCase() : '—');
