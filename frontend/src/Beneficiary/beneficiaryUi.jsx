// Thành phần giao diện dùng chung cho các trang Beneficiary
import React from 'react';
import { useTranslation } from 'react-i18next';
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
    EMAIL: 'mail',
    E_WALLET: 'account_balance_wallet',
    EXCHANGE_ACCOUNT: 'currency_exchange',
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
    return <span className={`lv-glass-badge shrink-0 text-xs font-semibold px-3 py-1 rounded-full ${cls}`}>{t(`status.${key}`)}</span>;
}

export function Alert({ type = 'error', children }) {
    if (!children) return null;
    const cls = {
        error: 'bg-error-container/85 text-on-error-container border border-error/25',
        info: 'bg-secondary-fixed/85 text-on-secondary-fixed border border-secondary/25',
        success: 'bg-success-container/85 text-on-success-container border border-success/25',
        warning: 'bg-warning-container/85 text-on-warning-container border border-warning/25',
    }[type];
    return <div className={`mb-4 p-3.5 rounded-xl text-sm backdrop-blur-md shadow-sm ${cls}`}>{children}</div>;
}

export const inputClass =
    'lv-glass-inline w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary';
export const btnPrimary =
    'lv-btn-primary h-11 px-5 rounded-xl bg-primary text-on-primary font-bold flex items-center justify-center gap-2 hover:bg-primary-container disabled:opacity-60 disabled:cursor-not-allowed lv-pill';
export const btnOutline =
    'lv-btn-secondary h-11 px-5 rounded-xl border border-outline-variant text-primary font-bold flex items-center justify-center gap-2 hover:bg-surface-container-low disabled:opacity-60 disabled:cursor-not-allowed';

// backLabel bỏ trống thì PageTitle tự dùng common:actions.back
// headerTour: giá trị data-tour gắn vào khối tiêu đề trang, để hướng dẫn nhanh chỉ vào đúng tiêu đề
export function BeneficiaryLayout({ title, subtitle, back, backLabel, headerTour, children }) {
    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader />
            <main className={`${PAGE_WIDTH} py-10`}>
                <div data-tour={headerTour}>
                    <PageTitle back={{ to: back || '/home', label: backLabel }} title={title} subtitle={subtitle} />
                </div>
                {children}
            </main>
        </div>
    );
}
