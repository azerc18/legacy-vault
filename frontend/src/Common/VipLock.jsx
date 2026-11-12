import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import UpgradeDialog from './UpgradeDialog.jsx';

const Icon = ({ name, className = '' }) => <span className={`material-symbols-outlined ${className}`}>{name}</span>;

// Nhãn nhỏ "VIP" gắn cạnh tính năng thuộc gói VIP
export function VipChip({ className = '' }) {
    const { t } = useTranslation('common');
    return (
        <span className={`inline-flex items-center gap-1 rounded-full bg-primary/15 text-primary px-2 py-0.5 text-[11px] font-bold leading-none ${className}`.trim()}>
            <Icon name="workspace_premium" className="text-[13px]" />
            {t('vip.chip')}
        </span>
    );
}

// Tính năng VIP khi hiện ở tài khoản FREE: làm mờ, không thao tác được, bấm vào thì mở hộp nâng cấp.
// locked = false (đã là VIP hoặc chưa biết gói) thì hiện nguyên như bình thường.
// feature: PlanFeature để hộp nâng cấp hiện câu riêng (vd. BENEFICIARY_LIMIT); bỏ trống thì dùng câu chung.
export function VipLocked({ locked, children, className = '', feature, label }) {
    const { t } = useTranslation('common');
    const [open, setOpen] = useState(false);
    if (!locked) return children;
    return (
        <div className={`relative ${className}`.trim()}>
            <div aria-hidden="true" inert className="opacity-45 blur-[1.5px] select-none pointer-events-none">{children}</div>
            <button type="button" onClick={() => setOpen(true)}
                    className="absolute inset-0 rounded-xl flex items-center justify-center gap-2 text-sm font-bold text-primary hover:bg-primary/5 transition-colors">
                <Icon name="lock" className="text-[18px]" />
                {label || t('vip.lockedLabel')}
                <VipChip />
            </button>
            <UpgradeDialog open={open} onClose={() => setOpen(false)} feature={feature} message={feature ? undefined : t('vip.upgradeMessage')} />
        </div>
    );
}
