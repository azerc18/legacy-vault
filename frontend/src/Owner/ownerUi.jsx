// Thành phần giao diện dùng chung cho các trang Vault Owner
import React from 'react';
import {useTranslation} from 'react-i18next';
import ProtectionBanner from './ProtectionBanner.jsx';
import AppHeader, {PAGE_WIDTH, PageTitle} from '../Common/AppHeader.jsx';
import NotificationBell from '../Beneficiary/NotificationBell.jsx';


export function Icon({name, className = ''}) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

export { formatDate, humanize } from '../i18n/format.js';

// "VAULT_CREATED" -> "Vault created"
export const ASSET_TYPES = [
    {value: 'BANK_ACCOUNT', label: 'Bank account'},
    {value: 'CRYPTO_WALLET', label: 'Crypto wallet'},
    {value: 'SOCIAL_MEDIA', label: 'Social media'},
    {value: 'DOCUMENT', label: 'Document'},
    {value: 'EXCHANGE_ACCOUNT', label: 'Exchange account'},
    {value: 'E_WALLET', label: 'E-wallet'},
    {value: 'EMAIL', label: 'Email account'},
    {value: 'OTHER', label: 'Other'},
];

export const DOCUMENT_TYPES = [
    {value: 'WILL', label: 'Will'},
    {value: 'OWNERSHIP_PAPER', label: 'Ownership paper'},
    {value: 'IDENTITY_CARD', label: 'Identity card'},
    {value: 'OTHER', label: 'Other'},
];

export const ASSET_ICON = {
    BANK_ACCOUNT: 'account_balance',
    CRYPTO_WALLET: 'currency_bitcoin',
    SOCIAL_MEDIA: 'share',
    DOCUMENT: 'description',
    EXCHANGE_ACCOUNT: 'currency_exchange',
    E_WALLET: 'account_balance_wallet',
    EMAIL: 'mail',
    OTHER: 'category',
};

export function Alert({type = 'error', children}) {
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
    'lv-glass-inline w-full h-11 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary';
export const btnPrimary =
    'lv-btn-primary h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center justify-center gap-2 hover:bg-primary-container disabled:opacity-60 disabled:cursor-not-allowed lv-pill';
export const btnOutline =
    'lv-btn-secondary h-11 px-5 rounded-xl border border-outline-variant text-primary font-bold inline-flex items-center justify-center gap-2 hover:bg-surface-container-low disabled:opacity-60 disabled:cursor-not-allowed';
export const btnDanger =
    'lv-btn-danger h-11 px-5 rounded-xl bg-error-container text-on-error-container font-bold inline-flex items-center justify-center gap-2 hover:bg-error hover:text-on-error disabled:opacity-60 disabled:cursor-not-allowed';

export function Card({title, icon, children, className = ''}) {
    return (
        <section
            className={`lv-glass-regular bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 ${className}`}>
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
export function ConfirmDialog({open, title, message, confirmLabel, danger, busy, onConfirm, onCancel}) {
    const {t} = useTranslation(['vault', 'common']);
    if (!open) return null;
    return (
        <div className="lv-modal-backdrop fixed inset-0 z-40 bg-black/40 dark:bg-black/70 backdrop-blur-md flex items-center justify-center px-4 animate-in fade-in duration-200" onClick={onCancel}>
            <div className="lv-glass-secure lv-surface-secure bg-surface-container-lowest text-on-surface border border-outline-variant/30 dark:border-white/15 rounded-2xl sm:rounded-3xl p-6 w-full max-w-md shadow-2xl animate-in zoom-in-95 duration-200"
                 onClick={(e) => e.stopPropagation()}>
                <h3 className="font-title-lg text-title-lg font-bold text-on-surface dark:text-white">{title}</h3>
                <p className="mt-2 text-sm text-on-surface-variant dark:text-white/70">{message}</p>
                <div className="mt-6 flex justify-end gap-3">
                    <button onClick={onCancel} className={btnOutline}>{t('common:actions.cancel')}</button>
                    <button onClick={onConfirm} disabled={busy} className={danger ? btnDanger : btnPrimary}>
                        {busy ? t('ui.pleaseWait') : (confirmLabel || t('common:actions.confirm'))}
                    </button>
                </div>
            </div>
        </div>
    );
}

export function OwnerLayout({title, subtitle, back, actions, children}) {
    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader extra={<NotificationBell source="owner" />}/>
            <main className={`${PAGE_WIDTH} py-10`}>
                <ProtectionBanner/>
                <PageTitle back={back} title={title} subtitle={subtitle} right={actions}/>
                {children}
            </main>
        </div>
    );
}
