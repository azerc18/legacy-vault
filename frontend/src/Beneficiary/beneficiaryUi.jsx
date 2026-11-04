// Thành phần giao diện dùng chung cho các trang Beneficiary
import React from 'react';
import { useTranslation } from 'react-i18next';
import NotificationBell from './NotificationBell.jsx';
import AppHeader, { PAGE_WIDTH, PageTitle } from '../Common/AppHeader.jsx';
import { humanize } from '../i18n/format.js';

export function Icon({ name, className = '' }) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

// BE trả LocalDateTime dạng "2026-10-16T10:30:00" (không có múi giờ)
export { formatDate } from '../i18n/format.js';

// Chỉ giữ icon; nhãn lấy từ enums.json (xem useEnumLabels)
export const ASSET_ICON = {
    BANK_ACCOUNT: 'account_balance',
    CRYPTO_WALLET: 'currency_bitcoin',
    SOCIAL_MEDIA: 'groups',
    DOCUMENT: 'description',
    OTHER: 'category',
};

// Nhãn enum theo ngôn ngữ hiện tại; chưa có bản dịch thì hiện humanize
export function useEnumLabels() {
    const { t } = useTranslation('enums');
    return {
        assetType: (v) => (v ? t(`assetType.${v}`, { defaultValue: humanize(v) }) : '—'),
        documentType: (v) => (v ? t(`documentType.${v}`, { defaultValue: humanize(v) }) : '—'),
    };
}

// Gộp VaultStatus + ClaimStatus thành 1 nhãn dễ hiểu
export function StatusBadge({ vaultStatus, claimStatus, myStatus }) {
    const { t } = useTranslation('beneficiary');
    let key = 'readyToClaim';
    let cls = 'bg-secondary-fixed text-on-secondary-fixed';
    if (myStatus === 'DECLINED' || myStatus === 'WAIVED') {
        key = myStatus === 'WAIVED' ? 'waived' : 'declined'; cls = 'bg-surface-container text-outline';
    } else if (vaultStatus === 'CLAIMED' || claimStatus === 'CLAIMED') {
        key = 'claimed'; cls = 'bg-success-container text-on-success-container';
    } else if (vaultStatus === 'ARCHIVED_LOCKED' || claimStatus === 'EXPIRED') {
        key = 'expired'; cls = 'bg-surface-container text-outline';
    } else if (myStatus === 'ACCEPTED') {
        key = 'accepted'; cls = 'bg-secondary-fixed text-on-secondary-fixed';
    } else if (claimStatus === 'PENDING') {
        key = 'inProgress'; cls = 'bg-warning-container text-on-warning-container';
    }
    return <span className={`shrink-0 text-xs font-semibold px-3 py-1 rounded-full ${cls}`}>{t(`status.${key}`)}</span>;
}

export function Alert({ type = 'error', children }) {
    if (!children) return null;
    const cls = {
        error: 'bg-error-container text-on-error-container',
        info: 'bg-secondary-fixed text-on-secondary-fixed',
        success: 'bg-success-container text-on-success-container',
        warning: 'bg-warning-container text-on-warning-container',
    }[type];
    return <div className={`mb-4 p-3 rounded-lg text-sm ${cls}`}>{children}</div>;
}

export const inputClass =
    'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary';
export const btnPrimary =
    'h-11 px-5 rounded-xl bg-primary text-on-primary font-bold flex items-center justify-center gap-2 hover:bg-primary-container disabled:opacity-60 disabled:cursor-not-allowed lv-pill';
export const btnOutline =
    'h-11 px-5 rounded-xl border border-outline-variant text-primary font-bold flex items-center justify-center gap-2 hover:bg-surface-container-low disabled:opacity-60 disabled:cursor-not-allowed';

// backLabel bỏ trống thì PageTitle tự dùng common:actions.back
export function BeneficiaryLayout({ title, subtitle, back, backLabel, children }) {
    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader extra={<NotificationBell />} />
            <main className={`${PAGE_WIDTH} py-10`}>
                <PageTitle back={{ to: back || '/home', label: backLabel }} title={title} subtitle={subtitle} />
                {children}
            </main>
        </div>
    );
}