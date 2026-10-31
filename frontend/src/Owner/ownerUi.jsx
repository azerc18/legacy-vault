// Thành phần giao diện dùng chung cho các trang Vault Owner
import React from 'react';
import ProtectionBanner from './ProtectionBanner.jsx';
import AppHeader, {PAGE_WIDTH, PageTitle} from '../Common/AppHeader.jsx';

export function Icon({name, className = ''}) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

// LocalDateTime ("2026-10-16T10:30:00") hoặc Instant ("...Z")
export function formatDate(value, withTime = false) {
    if (!value) return '—';
    const d = new Date(value);
    return withTime
        ? d.toLocaleString('en-GB', {dateStyle: 'medium', timeStyle: 'short'})
        : d.toLocaleDateString('en-GB', {dateStyle: 'medium'});
}

// "VAULT_CREATED" -> "Vault created"
export const humanize = (s) => (s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : '—');

export const ASSET_TYPES = [
    {value: 'BANK_ACCOUNT', label: 'Bank account'},
    {value: 'CRYPTO_WALLET', label: 'Crypto wallet'},
    {value: 'SOCIAL_MEDIA', label: 'Social media'},
    {value: 'DOCUMENT', label: 'Document'},
    {value: 'OTHER', label: 'Other'},
];

export const DOCUMENT_TYPES = [
    {value: 'WILL', label: 'Will'},
    {value: 'OWNERSHIP_PAPER', label: 'Ownership paper'},
    {value: 'IDENTITY_CARD', label: 'Identity card'},
    {value: 'OTHER', label: 'Other'},
];

export function Alert({type = 'error', children}) {
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
    'w-full h-11 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary';
export const btnPrimary =
    'h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center justify-center gap-2 hover:bg-primary-container disabled:opacity-60 disabled:cursor-not-allowed';
export const btnOutline =
    'h-11 px-5 rounded-xl border border-outline-variant text-primary font-bold inline-flex items-center justify-center gap-2 hover:bg-surface-container-low disabled:opacity-60 disabled:cursor-not-allowed';
export const btnDanger =
    'h-11 px-5 rounded-xl bg-error-container text-on-error-container font-bold inline-flex items-center justify-center gap-2 hover:bg-error hover:text-on-error disabled:opacity-60 disabled:cursor-not-allowed';

export function Card({title, icon, children, className = ''}) {
    return (
        <section
            className={`bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 ${className}`}>
            {title && (
                <h2 className="font-title-lg text-title-lg flex items-center gap-2 mb-4">
                    {icon && <Icon name={icon} className="text-secondary"/>} {title}
                </h2>
            )}
            {children}
        </section>
    );
}

// Hộp xác nhận (dùng cho thay thế người thụ hưởng / executor, xoá vault)
export function ConfirmDialog({open, title, message, confirmLabel = 'Confirm', danger, busy, onConfirm, onCancel}) {
    if (!open) return null;
    return (
        <div className="fixed inset-0 z-40 bg-black/40 flex items-center justify-center px-4" onClick={onCancel}>
            <div className="bg-surface-container-lowest rounded-2xl p-6 w-full max-w-md"
                 onClick={(e) => e.stopPropagation()}>
                <h3 className="font-title-lg text-title-lg">{title}</h3>
                <p className="mt-2 text-sm text-on-surface-variant">{message}</p>
                <div className="mt-6 flex justify-end gap-3">
                    <button onClick={onCancel} className={btnOutline}>Cancel</button>
                    <button onClick={onConfirm} disabled={busy} className={danger ? btnDanger : btnPrimary}>
                        {busy ? 'Please wait...' : confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}

const NAV = [
    {to: '/owner/vaults', label: 'My vaults', icon: 'inventory_2'},
    {to: '/owner/dms', label: "Dead Man's Switch", icon: 'favorite'},
    {to: '/owner/executors', label: 'Executors', icon: 'badge'},
    {to: '/owner/activity', label: 'Activity', icon: 'history'},
    {to: '/billing', label: 'Billing & VIP', icon: 'workspace_premium'},
];

export function OwnerLayout({title, subtitle, back, actions, children}) {
    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader nav={NAV}/>
            <main className={`${PAGE_WIDTH} py-10`}>
                <ProtectionBanner/>
                <PageTitle back={back} title={title} subtitle={subtitle} right={actions}/>
                {children}
            </main>
        </div>
    );
}
