// Thành phần giao diện dùng chung cho các trang Vault Owner
import React from 'react';
import {Link, NavLink, useNavigate} from 'react-router-dom';
import {getUser, logout} from '../auth/auth.js';

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
];

export function OwnerLayout({title, subtitle, actions, children}) {
    const navigate = useNavigate();
    const user = getUser();
    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <header className="bg-surface-container-lowest border-b border-outline-variant/30">
                <div className="max-w-5xl mx-auto h-16 px-5 flex items-center justify-between">
                    <Link to="/home" className="flex items-center gap-2">
                        <span
                            className="w-9 h-9 rounded-xl bg-primary text-on-primary flex items-center justify-center">
                            <Icon name="shield_locked" className="text-[20px]"/>
                        </span>
                        <span className="font-headline-sm text-primary font-bold text-xl">LegacyVault</span>
                    </Link>
                    <div className="flex items-center gap-4">
                        <Link to="/me"
                              className="flex items-center gap-2 h-10 pl-1 pr-3 rounded-xl hover:bg-surface-container-low"
                              title={user?.email}>
    <span
        className="w-8 h-8 rounded-full bg-primary-fixed text-primary flex items-center justify-center text-sm font-bold">
        {(user?.name || '?')[0].toUpperCase()}
    </span>
                            <span className="hidden sm:block text-sm font-semibold text-on-surface">{user?.name}</span>
                        </Link>
                        <button onClick={handleLogout}
                                className="h-10 px-4 rounded-xl border border-outline-variant text-sm font-semibold flex items-center gap-2 hover:bg-surface-container-low">
                            <Icon name="logout" className="text-[18px]"/> Log out
                        </button>
                    </div>
                </div>
                <nav className="max-w-5xl mx-auto px-5 flex gap-1 overflow-x-auto">
                    {NAV.map((n) => (
                        <NavLink
                            key={n.to}
                            to={n.to}
                            className={({isActive}) =>
                                `flex items-center gap-1.5 px-3 py-3 text-sm font-semibold border-b-2 whitespace-nowrap ${
                                    isActive ? 'border-primary text-primary' : 'border-transparent text-on-surface-variant hover:text-primary'
                                }`}
                        >
                            <Icon name={n.icon} className="text-[18px]"/> {n.label}
                        </NavLink>
                    ))}
                </nav>
            </header>
            <main className="max-w-5xl mx-auto px-5 py-10">
                <div className="flex flex-wrap items-start justify-between gap-4 mb-8">
                    <div>
                        <h1 className="font-headline-md text-headline-md">{title}</h1>
                        {subtitle && <p className="mt-1 text-on-surface-variant">{subtitle}</p>}
                    </div>
                    {actions}
                </div>
                {children}
            </main>
        </div>
    );
}
