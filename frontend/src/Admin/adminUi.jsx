// Layout + hộp thoại dùng chung cho các trang Admin
import React, {useState} from 'react';
import {Link, Navigate, NavLink, useNavigate} from 'react-router-dom';
import {getUser, logout} from '../auth/auth.js';
import {Icon, btnPrimary, btnOutline, btnDanger} from '../Owner/ownerUi.jsx';
import UserAvatar from '../Common/UserAvatar.jsx';

const NAV = [
    {to: '/admin/users', label: 'Users', icon: 'group'},
    {to: '/admin/vaults', label: 'Vaults', icon: 'inventory_2'},
];

export const isAdmin = (user) => (user?.roles || []).includes('ADMIN');

export function AdminLayout({title, subtitle, children}) {
    const navigate = useNavigate();
    const user = getUser();

    if (!user) return <Navigate to="/login" replace/>;
    if (!isAdmin(user)) return <Navigate to="/home" replace/>;

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <header className="bg-surface-container-lowest border-b border-outline-variant/30">
                <div className="max-w-6xl mx-auto h-16 px-5 flex items-center justify-between">
                    <Link to="/admin/users" className="flex items-center gap-2">
                        <span
                            className="w-9 h-9 rounded-xl bg-primary text-on-primary flex items-center justify-center">
                            <Icon name="admin_panel_settings" className="text-[20px]"/>
                        </span>
                        <span>
                            <span
                                className="font-headline-sm text-primary font-bold text-xl block leading-tight">LegacyVault</span>
                            <span
                                className="text-[11px] text-secondary font-semibold tracking-wide uppercase block">Administration</span>
                        </span>
                    </Link>
                    <div className="flex items-center gap-4">
                        <Link to="/me"
                              className="flex items-center gap-2 h-10 pl-1 pr-3 rounded-xl hover:bg-surface-container-low"
                              title={user?.email}>
                            <UserAvatar name={user?.name} hasAvatar={user?.hasAvatar} version={user?.avatarVersion} />
                            <span className="hidden sm:block text-sm font-semibold text-on-surface">{user?.name}</span>
                        </Link>
                        <button onClick={handleLogout}
                                className="h-10 px-4 rounded-xl border border-outline-variant text-sm font-semibold flex items-center gap-2 hover:bg-surface-container-low">
                            <Icon name="logout" className="text-[18px]"/> Log out
                        </button>
                    </div>
                </div>
                <nav className="max-w-6xl mx-auto px-5 flex gap-1">
                    {NAV.map((n) => (
                        <NavLink
                            key={n.to}
                            to={n.to}
                            className={({isActive}) =>
                                `flex items-center gap-1.5 px-3 py-3 text-sm font-semibold border-b-2 ${
                                    isActive ? 'border-primary text-primary' : 'border-transparent text-on-surface-variant hover:text-primary'
                                }`}
                        >
                            <Icon name={n.icon} className="text-[18px]"/> {n.label}
                        </NavLink>
                    ))}
                </nav>
            </header>
            <main className="max-w-6xl mx-auto px-5 py-10">
                <h1 className="font-headline-md text-headline-md">{title}</h1>
                {subtitle && <p className="mt-1 text-on-surface-variant">{subtitle}</p>}
                <div className="mt-8">{children}</div>
            </main>
        </div>
    );
}

// Hộp xác nhận có ô nhập lý do (tuỳ chọn), dùng cho khoá tài khoản / tạm dừng DMS
export function ReasonDialog({
                                 open,
                                 title,
                                 message,
                                 confirmLabel = 'Confirm',
                                 danger,
                                 askReason,
                                 busy,
                                 onConfirm,
                                 onCancel
                             }) {
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
                        placeholder="Reason (optional, max 255 characters)"
                        className="w-full mt-4 px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary"
                    />
                )}
                <div className="mt-6 flex justify-end gap-3">
                    <button onClick={() => {
                        setReason('');
                        onCancel();
                    }} className={btnOutline}>Cancel
                    </button>
                    <button
                        onClick={() => {
                            onConfirm(reason.trim());
                            setReason('');
                        }}
                        disabled={busy}
                        className={danger ? btnDanger : btnPrimary}
                    >
                        {busy ? 'Please wait...' : confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}

export function Pager({page, totalPages, onChange}) {
    if (!totalPages || totalPages <= 1) return null;
    return (
        <div className="flex justify-between items-center mt-4 text-sm">
            <button onClick={() => onChange(page - 1)} disabled={page === 0}
                    className="font-semibold text-secondary disabled:text-outline">← Previous
            </button>
            <span className="text-on-surface-variant">Page {page + 1} / {totalPages}</span>
            <button onClick={() => onChange(page + 1)} disabled={page + 1 >= totalPages}
                    className="font-semibold text-secondary disabled:text-outline">Next →
            </button>
        </div>
    );
}
