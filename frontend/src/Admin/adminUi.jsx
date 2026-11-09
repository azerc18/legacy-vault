// Layout + hộp thoại dùng chung cho các trang Admin
import React, {useEffect, useState} from 'react';
import {Link, Navigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {getUser} from '../auth/auth.js';
import {btnPrimary, btnOutline, btnDanger} from '../Owner/ownerUi.jsx';
import AppHeader, {PAGE_WIDTH, PageTitle} from '../Common/AppHeader.jsx';
import {adminApi} from '../api/adminApi.js';
import './admin.css';

const NAV = [
    {to: '/admin', key: 'overview', icon: 'dashboard', end: true},
    {to: '/admin/users', key: 'users', icon: 'group'},
    {to: '/admin/roles', key: 'roles', icon: 'shield_person'},
    {to: '/admin/vaults', key: 'vaults', icon: 'inventory_2'},
    {to: '/admin/audit-logs', key: 'auditLogs', icon: 'history'},
    {to: '/admin/alerts', key: 'alerts', icon: 'notifications_active'},
    {to: '/admin/payments', key: 'payments', icon: 'receipt_long'},
    {to: '/admin/plans', key: 'plans', icon: 'sell'},
    {to: '/admin/notifications', key: 'notifications', icon: 'unsubscribe'},
    {to: '/admin/beneficiary-locks', key: 'beneficiaryLocks', icon: 'lock_open'},
];

export const isAdmin = (user) => (user?.roles || []).includes('ADMIN');

export function AdminLayout({title, subtitle, actions, children}) {
    const {t} = useTranslation('admin');
    const user = getUser();

    const [openAlerts, setOpenAlerts] = useState(0);
    useEffect(() => {
        if (!isAdmin(user)) return;
        adminApi.countOpenAlerts().then((r) => setOpenAlerts(r?.open ?? 0)).catch(() => {});
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    if (!user) return <Navigate to="/login" replace/>;
    if (!isAdmin(user)) return <Navigate to="/home" replace/>;

    const nav = NAV.map(({key, ...n}) => ({
        ...n,
        label: t(`nav.${key}`),
        ...(n.to === '/admin/alerts' ? {badge: openAlerts} : {}),
    }));

    return (
        <div className="lv-admin bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader workspace={t('workspace')} home="/admin" nav={nav}/>
            <main className={`${PAGE_WIDTH} py-10`}>
                <PageTitle kicker={t('kicker')} title={title} subtitle={subtitle} right={actions} className="mb-8"/>
                {children}
            </main>
        </div>
    );
}

// Hộp xác nhận có ô nhập lý do (tuỳ chọn), dùng cho khoá tài khoản / tạm dừng DMS
export function ReasonDialog({
                                 open,
                                 title,
                                 message,
                                 confirmLabel,
                                 danger,
                                 askReason,
                                 askEmail,                 // hiện ô email thay cho ô lý do
                                 placeholder,
                                 busy,
                                 onConfirm,
                                 onCancel
                             }) {
    const {t} = useTranslation('admin');
    const [reason, setReason] = useState('');
    if (!open) return null;
    return (
        <div className="fixed inset-0 z-40 bg-black/40 flex items-center justify-center px-4" onClick={onCancel}>
            <div className="bg-surface-container-lowest rounded-2xl p-6 w-full max-w-md"
                 onClick={(e) => e.stopPropagation()}>
                <h3 className="font-title-lg text-title-lg">{title}</h3>
                {message && <p className="mt-2 text-sm text-on-surface-variant">{message}</p>}
                {askReason && (
                    <textarea
                        rows={3} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)}
                        placeholder={t('dialog.reasonPlaceholder')}
                        className="w-full mt-4 px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary"
                    />
                )}
                {askEmail && (
                    <input
                        type="email" autoFocus value={reason} onChange={(e) => setReason(e.target.value)}
                        placeholder={placeholder || 'name@example.com'} maxLength={255}
                        className="w-full mt-4 h-11 px-4 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary"
                    />
                )}
                <div className="mt-6 flex justify-end gap-3">
                    <button onClick={() => {
                        setReason('');
                        onCancel();
                    }} className={btnOutline}>{t('common:actions.cancel')}
                    </button>
                    <button
                        onClick={() => {
                            onConfirm(reason.trim());
                            setReason('');
                        }}
                        disabled={busy || (askEmail && !/^\S+@\S+\.\S+$/.test(reason.trim()))}
                        className={danger ? btnDanger : btnPrimary}
                    >
                        {busy ? t('wait') : (confirmLabel || t('common:actions.confirm'))}
                    </button>
                </div>
            </div>
        </div>
    );
}

export function Pager({page, totalPages, onChange}) {
    const {t} = useTranslation('admin');
    if (!totalPages || totalPages <= 1) return null;
    return (
        <div className="flex justify-between items-center mt-4 text-sm">
            <button onClick={() => onChange(page - 1)} disabled={page === 0}
                    className="font-semibold text-secondary disabled:text-outline">← {t('pager.prev')}
            </button>
            <span className="text-on-surface-variant">{t('pager.page', {page: page + 1, total: totalPages})}</span>
            <button onClick={() => onChange(page + 1)} disabled={page + 1 >= totalPages}
                    className="font-semibold text-secondary disabled:text-outline">{t('pager.next')} →
            </button>
        </div>
    );
}

/* ---------- Thành phần "hồ sơ công chứng" dùng chung ---------- */

// Con dấu trạng thái. tone: ok | warn | danger | info | muted
export function Stamp({tone = 'muted', icon, flat, children, title}) {
    return (
        <span title={title} className={`lv-stamp is-${tone}${flat ? ' is-flat' : ''}`}>
            {icon && <span className="material-symbols-outlined" style={{fontSize: 14}}>{icon}</span>}
            {children}
        </span>
    );
}

// Màu con dấu theo trạng thái nghiệp vụ (dùng chung cho mọi trang admin)
export const STATUS_TONE = {
    // user
    ACTIVE: 'ok', LOCKED: 'danger', PENDING: 'warn',
    // vault
    UNLOCKED: 'info', CLAIMED: 'ok', ARCHIVED_LOCKED: 'muted',
    // DMS
    SUSPENDED: 'warn', TRIGGERED: 'danger', PENDING_CONFIRMATION: 'warn',
    // thanh toán
    SUCCESS: 'ok', FAILED: 'danger',
    // audit
    FAILURE: 'danger',
    // cảnh báo
    HIGH: 'danger', MEDIUM: 'warn', OPEN: 'danger', ACKNOWLEDGED: 'muted',
    // thông báo
    SENT: 'ok', QUEUED: 'info', READ: 'muted',
};
export const toneOf = (status) => STATUS_TONE[status] || 'muted';

// Tờ hồ sơ: khung giấy có tiêu đề + mã hồ sơ (tab) + nút bên phải
export function Dossier({title, tab, right, className = '', bodyClass = 'p-5', children}) {
    return (
        <section className={`lv-dossier ${className}`}>
            {(title || tab || right) && (
                <header className="flex flex-wrap items-center justify-between gap-3 px-5 pt-5 pb-3.5 border-b border-outline-variant/60">
                    <div className="flex items-center gap-3 min-w-0">
                        {title && <h2 className="lv-dossier-title truncate">{title}</h2>}
                        {tab && <span className="lv-dossier-tab">{tab}</span>}
                    </div>
                    {right}
                </header>
            )}
            <div className={bodyClass}>{children}</div>
        </section>
    );
}

// Thẻ số liệu cho trang tổng quan
export function StatCard({icon, tone, label, value, foot, to, children}) {
    const body = (
        <div className="lv-stat">
            <span className={`lv-stat-seal${tone ? ` is-${tone}` : ''}`}>
                <span className="material-symbols-outlined" style={{fontSize: 22}}>{icon}</span>
            </span>
            <div className="min-w-0 flex-1">
                <p className="lv-stat-label">{label}</p>
                <p className="lv-stat-value mt-1">{value}</p>
                {foot && <p className="lv-stat-foot">{foot}</p>}
                {children}
            </div>
        </div>
    );
    return to
        ? <Link to={to} className="lv-feature lv-feature-link lv-dossier block hover:border-secondary">{body}</Link>
        : <div className="lv-feature lv-feature-link lv-dossier">{body}</div>;
}

// 199000 -> "199.000 ₫"
export function formatVnd(n) {
    if (typeof n !== 'number') return '—';
    return `${n.toLocaleString('vi-VN')} ₫`;
}
