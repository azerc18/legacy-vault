// Header dùng chung cho mọi trang sau đăng nhập (Owner, Admin, Beneficiary, Executor, Verifier, /me, /home).
// Luôn có: logo (+ tên khu vực) bên trái, avatar + đăng xuất bên phải. Thanh tab (nav) là tuỳ chọn.
// Tiêu đề trang + nút quay lại KHÔNG nằm trong header nữa mà dùng <PageTitle> trong nội dung.
import React from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { getUser, logout } from '../auth/auth.js';
import UserAvatar from './UserAvatar.jsx';

// Cùng một độ rộng cho header và nội dung mọi trang
export const PAGE_WIDTH = 'max-w-6xl mx-auto px-5';

const Icon = ({ name, className = '' }) => <span className={`material-symbols-outlined ${className}`}>{name}</span>;

/**
 * @param icon       icon trong ô logo (mặc định khiên)
 * @param workspace  dòng nhỏ dưới chữ LegacyVault, vd. "Administration"
 * @param home       logo dẫn về đâu
 * @param nav        [{ to, label, icon, badge }] — badge là số, > 0 thì hiện chấm đỏ
 * @param extra      phần tử thêm bên phải, trước avatar (vd. NotificationBell)
 * @param userHome   true = hiện link "User home" (cho người có nhiều vai trò)
 */
export default function AppHeader({ icon = 'shield_locked', workspace, home = '/home', nav, extra, userHome = false }) {
    const navigate = useNavigate();
    const user = getUser();
    const handleLogout = () => { logout(); navigate('/login'); };

    return (
        <header className="sticky top-0 z-20 bg-surface-container-lowest/95 backdrop-blur-sm border-b border-outline-variant/30">
            <div className={`${PAGE_WIDTH} h-16 flex items-center justify-between gap-3`}>
                <Link to={home} className="flex items-center gap-2 min-w-0">
                    <span className="w-9 h-9 shrink-0 rounded-xl bg-primary text-on-primary flex items-center justify-center">
                        <Icon name={icon} className="text-[20px]" />
                    </span>
                    <span className="min-w-0">
                        <span className="font-headline-sm text-primary font-bold text-xl block leading-tight">LegacyVault</span>
                        {workspace && (
                            <span className="text-[11px] text-secondary font-semibold tracking-wide uppercase block truncate">{workspace}</span>
                        )}
                    </span>
                </Link>

                <div className="flex items-center gap-2 sm:gap-3 shrink-0">
                    {userHome && (
                        <Link to="/home" title="User home"
                              className="h-10 px-2 sm:px-3 rounded-xl flex items-center gap-1 text-sm font-semibold text-on-surface-variant hover:text-primary hover:bg-surface-container-low">
                            <Icon name="swap_horiz" className="text-[18px]" /><span className="hidden md:inline">User home</span>
                        </Link>
                    )}
                    {extra}
                    <Link to="/me" title={user?.email}
                          className="flex items-center gap-2 h-10 pl-1 pr-1 md:pr-3 rounded-xl hover:bg-surface-container-low">
                        <UserAvatar name={user?.name} hasAvatar={user?.hasAvatar} version={user?.avatarVersion} />
                        <span className="hidden md:block text-sm font-semibold text-on-surface max-w-[160px] truncate">{user?.name}</span>
                    </Link>
                    <button type="button" onClick={handleLogout} title="Log out"
                            className="h-10 px-3 rounded-xl border border-outline-variant text-sm font-semibold flex items-center gap-1.5 hover:bg-surface-container-low">
                        <Icon name="logout" className="text-[18px]" /><span className="hidden sm:inline">Log out</span>
                    </button>
                </div>
            </div>

            {nav?.length > 0 && (
                <nav className={`${PAGE_WIDTH} flex gap-1 overflow-x-auto`}>
                    {nav.map((n) => (
                        <NavLink key={n.to} to={n.to}
                                 className={({ isActive }) =>
                                     `flex items-center gap-1.5 px-3 py-3 text-sm font-semibold border-b-2 whitespace-nowrap ${
                                         isActive ? 'border-primary text-primary' : 'border-transparent text-on-surface-variant hover:text-primary'}`}>
                            <Icon name={n.icon} className="text-[18px]" /> {n.label}
                            {n.badge > 0 && (
                                <span className="ml-1 min-w-5 h-5 px-1.5 rounded-full bg-error text-on-error text-[11px] font-bold inline-flex items-center justify-center">
                                    {n.badge}
                                </span>
                            )}
                        </NavLink>
                    ))}
                </nav>
            )}
        </header>
    );
}

/**
 * Tiêu đề trang đặt trong <main>: link quay lại (tuỳ chọn) + h1 + mô tả + nút bên phải.
 * @param back { to, label }  vd. { to: '/executor/home', label: 'All estates' }
 */
export function PageTitle({ back, title, subtitle, right, className = 'mb-8' }) {
    return (
        <div className={className}>
            {back && (
                <Link to={back.to} className="inline-flex items-center gap-1 text-sm font-semibold text-secondary hover:text-primary mb-3">
                    <Icon name="arrow_back" className="text-[18px]" /> {back.label || 'Back'}
                </Link>
            )}
            <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0">
                    <h1 className="font-headline-md text-headline-md break-words">{title}</h1>
                    {subtitle && <p className="mt-1 text-on-surface-variant">{subtitle}</p>}
                </div>
                {right && <div className="flex flex-wrap items-center gap-3">{right}</div>}
            </div>
        </div>
    );
}
