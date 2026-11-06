// Header dùng chung cho mọi trang sau đăng nhập (Owner, Admin, Beneficiary, Executor, Verifier, /me, /home).
// Luôn có: logo (+ tên khu vực) bên trái, avatar + đăng xuất bên phải. Thanh tab (nav) là tuỳ chọn.
// Tiêu đề trang + nút quay lại KHÔNG nằm trong header nữa mà dùng <PageTitle> trong nội dung.
import React, { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { getUser, logout } from '../auth/auth.js';
import { paymentApi } from '../api/paymentApi.js';
import UserAvatar from './UserAvatar.jsx';
import { useTranslation } from 'react-i18next';
import LanguageSwitch from './LanguageSwitch.jsx';
import ThemeToggle from './ThemeToggle.jsx';


// Cùng một độ rộng cho header và nội dung mọi trang
// Menu mặc định khi trang không tự truyền nav: theo khu vực của URL, còn /me và /home thì theo vai trò chính của user
const ROLE_ORDER = ['ADMIN', 'OWNER', 'EXECUTOR', 'LEGAL_VERIFIER', 'BENEFICIARY'];
function roleItems(role) {
    switch (role) {
        case 'ADMIN': return [{ to: '/admin/users', key: 'admin', icon: 'admin_panel_settings' }];
        case 'OWNER': return [
            { to: '/owner/vaults', key: 'myVaults', icon: 'inventory_2' },
            { to: '/owner/dms', key: 'dms', icon: 'favorite' },
            { to: '/owner/executors', key: 'executors', icon: 'badge' },
            { to: '/owner/activity', key: 'activity', icon: 'history' },
            { to: '/billing', key: 'billing', icon: 'workspace_premium' },
        ];
        case 'EXECUTOR': return [{ to: '/executor/home', key: 'estates', icon: 'badge' }];
        case 'LEGAL_VERIFIER': return [
            { to: '/verifier', key: 'requests', icon: 'gavel', end: true },
            { to: '/verifier/history', key: 'history', icon: 'history' },
        ];
        case 'BENEFICIARY': return [{ to: '/beneficiary', key: 'myVaults', icon: 'inventory_2', end: true }];
        default: return [];
    }
}
function defaultNav(pathname, roles) {
    const home = { to: '/home', key: 'home', icon: 'home', end: true };
    const profile = { to: '/me', key: 'profile', icon: 'person' };
    if (pathname.startsWith('/executor')) return [{ to: '/executor/home', key: 'estates', icon: 'badge' }, profile];
    if (pathname.startsWith('/verifier')) {
        return [{ to: '/verifier/home', key: 'home', icon: 'home', end: true }, ...roleItems('LEGAL_VERIFIER'), profile];
    }
    if (pathname.startsWith('/beneficiary')) {
        return [{ to: '/beneficiary', key: 'myVaults', icon: 'inventory_2', end: true }, { to: '/beneficiary/guide', key: 'guide', icon: 'menu_book' }, profile];
    }
    const main = ROLE_ORDER.find((r) => roles.includes(r));
    return [home, ...roleItems(main), profile];
}

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
export default function AppHeader({ icon = 'shield_locked', workspace, home = '/home', nav: navProp, extra, userHome = false }) {
    const { pathname } = useLocation();
    const { t } = useTranslation();
    const user = getUser();
    const nav = navProp && navProp.length > 0
        ? navProp
        : defaultNav(pathname, Array.isArray(user?.roles) ? user.roles : []).map(({ key, ...n }) => ({ ...n, label: t(`nav.${key}`) }));
    const { t: tPayment } = useTranslation('payment');
    const navigate = useNavigate();
    const isOwner = Array.isArray(user?.roles) && user.roles.includes('OWNER');
    const [vipStatus, setVipStatus] = useState(null);

    useEffect(() => {
        if (!isOwner) return;
        let mounted = true;

        // n3: cache /subscriptions/me trong sessionStorage 5 phút (try/catch)
        try {
            const cached = sessionStorage.getItem('lv_sub_cache');
            if (cached) {
                const { data, timestamp } = JSON.parse(cached);
                if (Date.now() - timestamp < 5 * 60 * 1000) {
                    setVipStatus(data);
                    return;
                }
            }
        } catch {
            // ignore sessionStorage error
        }

        paymentApi
            .getMySubscription()
            .then((data) => {
                if (mounted && data) {
                    setVipStatus(data);
                    try {
                        sessionStorage.setItem('lv_sub_cache', JSON.stringify({ data, timestamp: Date.now() }));
                    } catch {
                        // ignore
                    }
                }
            })
            .catch(() => {});
        return () => {
            mounted = false;
        };
    }, [isOwner]);

    const handleLogout = () => { logout(); navigate('/login'); };

    // Thanh bên trái (desktop) / thanh trên cùng (điện thoại). Nội dung trang được đẩy sang phải nhờ class lv-has-side (xem index.css).
    useEffect(() => {
        document.body.classList.add('lv-has-side');
        return () => document.body.classList.remove('lv-has-side');
    }, []);

    const linkBase = 'flex items-center gap-3 px-4 py-3 rounded-xl text-[15px] font-semibold whitespace-nowrap transition-colors';
    return (
        <header className="lv-side bg-brand text-white">
            <div className="lv-side-top">
                <Link to={home} className="flex items-center gap-2 min-w-0 text-white">
                    <Icon name={icon === 'shield_locked' ? 'lock' : icon} className="text-[26px]" />
                    <span className="min-w-0">
                        <span className="font-bold text-sm tracking-[0.18em] uppercase block leading-tight">LegacyVault</span>
                        {workspace && (
                            <span className="text-[11px] text-white/60 font-semibold tracking-wide uppercase block truncate">{workspace}</span>
                        )}
                    </span>
                </Link>
            </div>

            {nav?.length > 0 && (
                <nav className="lv-side-nav">
                    {nav.map((n) => (
                        <NavLink key={n.to} to={n.to} end={n.end}
                                 className={({ isActive }) =>
                                     `${linkBase} ${isActive ? 'bg-brand-container text-white' : 'text-white/70 hover:text-white hover:bg-white/5'}`}>
                            <Icon name={n.icon} className="text-[20px]" /> {n.label}
                            {n.badge > 0 && (
                                <span className="ml-auto min-w-5 h-5 px-1.5 rounded-full bg-error text-on-error text-[11px] font-bold inline-flex items-center justify-center">
                                    {n.badge}
                                </span>
                            )}
                        </NavLink>
                    ))}
                </nav>
            )}

            <div className="lv-side-bottom">
                {isOwner && (
                    <Link
                        to={vipStatus?.vip ? '/billing' : '/pricing'}
                        title={vipStatus?.vip ? tPayment('header.vipActive') : tPayment('header.upgradeToVip')}
                        className={`h-9 px-3 rounded-full text-xs font-bold inline-flex items-center gap-1.5 ${
                            vipStatus?.vip ? 'bg-white text-brand' : 'bg-white/10 text-white hover:bg-white hover:text-brand'
                        }`}
                    >
                        <Icon name="workspace_premium" className="text-[16px]" />
                        <span>{vipStatus?.vip ? tPayment('header.vip') : tPayment('header.upgrade')}</span>
                    </Link>
                )}
                {userHome && (
                    <Link to="/home" title={t('header.userHome')}
                          className="h-9 px-3 rounded-full inline-flex items-center gap-1.5 text-xs font-semibold text-white/75 hover:text-white hover:bg-white/10">
                        <Icon name="swap_horiz" className="text-[16px]" /><span>{t('header.userHome')}</span>
                    </Link>
                )}
                <div className="flex items-center gap-2">
                    <LanguageSwitch variant="dark" />
                    <ThemeToggle variant="dark" />
                    {extra}
                </div>
                <div className="flex items-center gap-3 pt-3 border-t border-white/10 min-w-0">
                    <Link to="/me" title={user?.email} className="flex items-center gap-3 min-w-0 flex-1">
                        <UserAvatar name={user?.name} hasAvatar={user?.hasAvatar} version={user?.avatarVersion} />
                        <span className="text-sm font-semibold text-white truncate">{user?.name}</span>
                    </Link>
                    <button type="button" onClick={handleLogout} title={t('header.logout')} aria-label={t('header.logout')}
                            className="h-9 w-9 rounded-full flex items-center justify-center text-white/75 hover:text-white hover:bg-white/10">
                        <Icon name="logout" className="text-[18px]" />
                    </button>
                </div>
            </div>
        </header>
    );
}

/**
 * Tiêu đề trang đặt trong <main>: link quay lại (tuỳ chọn) + h1 + mô tả + nút bên phải.
 * @param back { to, label }  vd. { to: '/executor/home', label: 'All estates' }
 */
export function PageTitle({ back, title, subtitle, right, className = 'mb-8' ,}) {
    const { t } = useTranslation();
    return (
        <div className={className}>
            {back && (
                <Link to={back.to} className="inline-flex items-center gap-1 text-sm font-semibold text-secondary hover:text-primary mb-3">
                    <Icon name="arrow_back" className="text-[18px]" /> {back.label || t('actions.back')}
                </Link>
            )}
            <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0">
                    <h1 className="font-headline-md text-[30px] md:text-[34px] leading-tight font-bold tracking-tight break-words">{title}</h1>
                    {subtitle && <p className="mt-1 text-on-surface-variant">{subtitle}</p>}
                </div>
                {right && <div className="flex flex-wrap items-center gap-3">{right}</div>}
            </div>
        </div>
    );
}
