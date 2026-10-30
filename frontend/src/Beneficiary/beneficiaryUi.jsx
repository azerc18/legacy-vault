// Thành phần giao diện dùng chung cho các trang Beneficiary
import React from 'react';
import NotificationBell from './NotificationBell.jsx';
import AppHeader, { PAGE_WIDTH, PageTitle } from '../Common/AppHeader.jsx';

export function Icon({ name, className = '' }) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

// BE trả LocalDateTime dạng "2026-10-16T10:30:00" (không có múi giờ)
export function formatDate(value, withTime = false) {
    if (!value) return '—';
    const d = new Date(value);
    return withTime
        ? d.toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' })
        : d.toLocaleDateString('en-GB', { dateStyle: 'medium' });
}

export const ASSET_TYPE = {
    BANK_ACCOUNT: { label: 'Bank account', icon: 'account_balance' },
    CRYPTO_WALLET: { label: 'Crypto wallet', icon: 'currency_bitcoin' },
    SOCIAL_MEDIA: { label: 'Social media', icon: 'groups' },
    DOCUMENT: { label: 'Document', icon: 'description' },
    OTHER: { label: 'Other', icon: 'category' },
};

export const DOCUMENT_TYPE = {
    WILL: 'Will',
    OWNERSHIP_PAPER: 'Ownership paper',
    IDENTITY_CARD: 'Identity card',
    OTHER: 'Other',
};

// Gộp VaultStatus + ClaimStatus thành 1 nhãn dễ hiểu
export function StatusBadge({ vaultStatus, claimStatus }) {
    let label = 'Ready to claim';
    let cls = 'bg-secondary-fixed text-on-secondary-fixed';
    if (vaultStatus === 'CLAIMED' || claimStatus === 'CLAIMED') {
        label = 'Claimed'; cls = 'bg-success-container text-on-success-container';
    } else if (vaultStatus === 'ARCHIVED_LOCKED' || claimStatus === 'EXPIRED') {
        label = 'Expired'; cls = 'bg-surface-container text-outline';
    } else if (claimStatus === 'PENDING') {
        label = 'In progress'; cls = 'bg-warning-container text-on-warning-container';
    }
    return <span className={`shrink-0 text-xs font-semibold px-3 py-1 rounded-full ${cls}`}>{label}</span>;
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
    'h-11 px-5 rounded-xl bg-primary text-on-primary font-bold flex items-center justify-center gap-2 hover:bg-primary-container disabled:opacity-60 disabled:cursor-not-allowed';
export const btnOutline =
    'h-11 px-5 rounded-xl border border-outline-variant text-primary font-bold flex items-center justify-center gap-2 hover:bg-surface-container-low disabled:opacity-60 disabled:cursor-not-allowed';

export function BeneficiaryLayout({ title, subtitle, back, backLabel, children }) {
    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader extra={<NotificationBell />} />
            <main className={`${PAGE_WIDTH} py-10`}>
                <PageTitle back={{ to: back || '/home', label: backLabel || 'Back' }} title={title} subtitle={subtitle} />
                {children}
            </main>
        </div>
    );
}
