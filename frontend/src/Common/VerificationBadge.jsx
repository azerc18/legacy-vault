import React from 'react';
import { useTranslation } from 'react-i18next';

// Huy hiệu xác thực dùng chung cho Owner và Executor.
// "Mô phỏng" luôn ghi rõ, không hứa quá điều đã chứng minh.
const STYLE = {
    SELF_DECLARED: { icon: 'edit_note', cls: 'bg-surface-container text-on-surface-variant' },
    PENDING: { icon: 'hourglass_top', cls: 'bg-warning-container text-on-warning-container' },
    VERIFIED: { icon: 'verified', cls: 'bg-success-container text-on-success-container' },
    VERIFIED_SANDBOX: { icon: 'science', cls: 'bg-secondary-fixed text-on-secondary-fixed' },
    FAILED: { icon: 'error', cls: 'bg-error-container text-on-error-container' },
    EXPIRED: { icon: 'history', cls: 'bg-warning-container text-on-warning-container' },
};

export default function VerificationBadge({ status, method, className = '' }) {
    const { t } = useTranslation('verification');
    const key = STYLE[status] ? status : 'SELF_DECLARED';
    const s = STYLE[key];
    const hint = t(`badge.${key}.hint`);
    const title = method ? `${hint} · ${t(`methods.${method}.label`, { defaultValue: method })}` : hint;
    return (
        <span title={title}
              className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-full whitespace-nowrap ${s.cls} ${className}`}>
            <span className="material-symbols-outlined text-[14px]">{s.icon}</span>
            {t(`badge.${key}.label`)}
        </span>
    );
}
