import React from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'motion/react';
import { ReducedMotionConfig, useMotionTokens, useShouldReduceMotion } from '@animate-ui/react/utils/motion';

// Huy hiệu xác thực dùng chung cho Owner, Executor và Beneficiary.
// "Mô phỏng" luôn ghi rõ, không hứa quá điều đã chứng minh.
const STYLE = {
    SELF_DECLARED: { icon: 'edit_note', cls: 'bg-surface-container text-on-surface-variant' },
    PENDING: { icon: 'hourglass_top', cls: 'bg-warning-container text-on-warning-container' },
    VERIFIED: { icon: 'verified', cls: 'bg-success-container text-on-success-container' },
    VERIFIED_SANDBOX: { icon: 'science', cls: 'bg-secondary-fixed text-on-secondary-fixed' },
    FAILED: { icon: 'error', cls: 'bg-error-container text-on-error-container' },
    EXPIRED: { icon: 'history', cls: 'bg-warning-container text-on-warning-container' },
};

function BadgeInner({ status, method, className = '' }) {
    const { t } = useTranslation('verification');
    const { spring, blur } = useMotionTokens();
    const reduce = useShouldReduceMotion();
    const key = STYLE[status] ? status : 'SELF_DECLARED';
    const s = STYLE[key];
    const hint = t(`badge.${key}.hint`);
    const title = method ? `${hint} · ${t(`methods.${method}.label`, { defaultValue: method })}` : hint;
    return (
        <motion.span
            key={`${key}-${method || ''}`}
            title={title}
            initial={{ opacity: 0, filter: blur.icon, transform: `scale(${reduce ? 1 : 0.92})` }}
            animate={{ opacity: 1, filter: 'blur(0px)', transform: 'scale(1)' }}
            transition={spring.pop}
            className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-full whitespace-nowrap ${s.cls} ${className}`}
        >
            <span className="material-symbols-outlined text-[14px]">{s.icon}</span>
            {t(`badge.${key}.label`)}
        </motion.span>
    );
}

export default function VerificationBadge(props) {
    return (
        <ReducedMotionConfig>
            <BadgeInner {...props} />
        </ReducedMotionConfig>
    );
}
