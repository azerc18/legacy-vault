// Layout + hộp thoại dùng chung cho các trang Admin
import React, {useEffect, useState} from 'react';
import {Navigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {getUser} from '../auth/auth.js';
import {btnPrimary, btnOutline, btnDanger} from '../Owner/ownerUi.jsx';
import AppHeader, {PAGE_WIDTH, PageTitle} from '../Common/AppHeader.jsx';
import {adminApi} from '../api/adminApi.js';

const NAV = [
    {to: '/admin/users', key: 'users', icon: 'group'},
    {to: '/admin/roles', key: 'roles', icon: 'shield_person'},
    {to: '/admin/vaults', key: 'vaults', icon: 'inventory_2'},
    {to: '/admin/audit-logs', key: 'auditLogs', icon: 'history'},
    {to: '/admin/alerts', key: 'alerts', icon: 'notifications_active'},
];

export const isAdmin = (user) => (user?.roles || []).includes('ADMIN');

export function AdminLayout({title, subtitle, children}) {
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
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader icon="admin_panel_settings" workspace={t('workspace')} home="/admin/users" nav={nav}/>
            <main className={`${PAGE_WIDTH} py-10`}>
                <PageTitle title={title} subtitle={subtitle}/>
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
