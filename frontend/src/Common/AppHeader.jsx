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
import BrandMark from './BrandMark.jsx';
import OnboardingGuideModal from './OnboardingGuideModal.jsx';


// Cùng một độ rộng cho header và nội dung mọi trang
const SIDE_KEY = 'lv_side_collapsed';
const SEC_PREFIX = 'lv_nav_sec_';
const ALERT_CACHE_KEY = 'lv_admin_alerts_cache';

// Thứ tự ưu tiên vai trò để hiển thị các nhóm điều hướng nhất quán trên MỌI trang
const ROLE_ORDER = ['ADMIN', 'OWNER', 'EXECUTOR', 'LEGAL_VERIFIER', 'BENEFICIARY'];

const ADMIN_NAV_ITEMS = [
    { to: '/admin', key: 'overview', icon: 'dashboard', end: true },
    { to: '/admin/users', key: 'users', icon: 'group' },
    { to: '/admin/roles', key: 'roles', icon: 'shield_person' },
    { to: '/admin/vaults', key: 'vaults', icon: 'inventory_2' },
    { to: '/admin/audit-logs', key: 'auditLogs', icon: 'history' },
    { to: '/admin/alerts', key: 'alerts', icon: 'notifications_active' },
    { to: '/admin/payments', key: 'payments', icon: 'receipt_long' },
    { to: '/admin/plans', key: 'plans', icon: 'sell' },
    { to: '/admin/notifications', key: 'notifications', icon: 'unsubscribe' },
    { to: '/admin/beneficiary-locks', key: 'beneficiaryLocks', icon: 'lock_open' },
];

function buildRoleSections(roles, pathname, tCommon, tAdmin, openAlerts = 0) {
    const activeRoles = ROLE_ORDER.filter((r) => roles.includes(r));
    // Chỉ hiện nhóm Beneficiary khi user đang ở trang /beneficiary hoặc chỉ có đúng 1 role BENEFICIARY
    const filteredRoles = activeRoles.filter(
        (r) => r !== 'BENEFICIARY' || pathname.startsWith('/beneficiary') || activeRoles.length === 1
    );

    return filteredRoles.map((role) => {
        switch (role) {
            case 'ADMIN':
                return {
                    role: 'ADMIN',
                    label: tAdmin('workspace', { defaultValue: tCommon('roles.ADMIN') }),
                    matchPrefix: '/admin',
                    items: ADMIN_NAV_ITEMS.map(({ key, ...n }) => ({
                        ...n,
                        label: tAdmin(`nav.${key}`),
                        ...(n.to === '/admin/alerts' && openAlerts > 0 ? { badge: openAlerts } : {}),
                    })),
                };
            case 'OWNER':
                return {
                    role: 'OWNER',
                    label: tCommon('roles.OWNER'),
                    matchPrefix: '/owner',
                    items: [
                        { to: '/owner/vaults', label: tCommon('nav.myVaults'), icon: 'inventory_2' },
                        { to: '/owner/dms', label: tCommon('nav.dms'), icon: 'favorite' },
                        { to: '/owner/executors', label: tCommon('nav.executors'), icon: 'badge' },
                        { to: '/owner/activity', label: tCommon('nav.activity'), icon: 'history' },
                        { to: '/billing', label: tCommon('nav.billing'), icon: 'workspace_premium' },
                    ],
                };
            case 'EXECUTOR':
                return {
                    role: 'EXECUTOR',
                    label: tCommon('roles.EXECUTOR'),
                    matchPrefix: '/executor',
                    items: [
                        { to: '/executor/home', label: tCommon('nav.estates'), icon: 'badge' },
                    ],
                };
            case 'LEGAL_VERIFIER':
                return {
                    role: 'LEGAL_VERIFIER',
                    label: tCommon('roles.LEGAL_VERIFIER'),
                    matchPrefix: '/verifier',
                    items: [
                        { to: '/verifier/home', label: tCommon('nav.home'), icon: 'dashboard', end: true },
                        { to: '/verifier', label: tCommon('nav.requests'), icon: 'gavel', end: true },
                        { to: '/verifier/history', label: tCommon('nav.history'), icon: 'history' },
                    ],
                };
            case 'BENEFICIARY':
                return {
                    role: 'BENEFICIARY',
                    label: tCommon('roles.BENEFICIARY'),
                    matchPrefix: '/beneficiary',
                    items: [
                        { to: '/beneficiary', label: tCommon('nav.myVaults'), icon: 'inventory_2', end: true },
                        { to: '/beneficiary/guide', label: tCommon('nav.guide'), icon: 'menu_book' },
                    ],
                };
            default:
                return null;
        }
    }).filter(Boolean);
}

export const PAGE_WIDTH = 'max-w-6xl mx-auto px-5';

const Icon = ({ name, className = '' }) => <span className={`material-symbols-outlined ${className}`}>{name}</span>;

/**
 * @param workspace   dòng nhỏ dưới chữ LegacyVault, vd. "Administration"
 * @param home        logo dẫn về đâu
 * @param nav         (tuỳ chọn) ghi đè danh sách phẳng nếu trang đặc biệt cần
 * @param alertBadge  số cảnh báo mở của Admin (đồng bộ sang sessionStorage để giữ nguyên khi chuyển trang)
 * @param extra       phần tử thêm bên phải, trước avatar (vd. NotificationBell)
 * @param userHome    true = hiện link "User home" (cho người có nhiều vai trò)
 */
export default function AppHeader({ workspace, home = '/home', nav: navProp, alertBadge, extra, userHome = false }) {
    const { pathname } = useLocation();
    const { t } = useTranslation('common');
    const { t: tAdmin } = useTranslation('admin');
    const user = getUser();
    const roles = Array.isArray(user?.roles) ? user.roles : [user?.role].filter(Boolean);
    const mainRole = ROLE_ORDER.find((r) => roles.includes(r));

    // Lưu / đọc số badge cảnh báo của Admin để khi chuyển qua /home hay /me vẫn giữ nguyên hiển thị
    const [storedAlerts] = useState(() => {
        try {
            const raw = sessionStorage.getItem(ALERT_CACHE_KEY);
            return raw ? Number(raw) || 0 : 0;
        } catch {
            return 0;
        }
    });
    const effectiveAlerts = typeof alertBadge === 'number' ? alertBadge : storedAlerts;

    useEffect(() => {
        if (typeof alertBadge === 'number') {
            try { sessionStorage.setItem(ALERT_CACHE_KEY, String(alertBadge)); } catch { /* ignore */ }
        }
    }, [alertBadge]);

    const generalItems = [
        { to: '/home', label: t('nav.home'), icon: 'home', end: true },
        { to: '/me', label: t('nav.profile'), icon: 'person' },
    ];
    const roleSections = buildRoleSections(roles, pathname, t, tAdmin, effectiveAlerts);

    // Cho phép đóng/mở từng nhóm vai trò. Chưa chọn gì (null) thì chỉ mở nhóm của trang đang xem
    // (ở /home, /me thì mở nhóm đầu tiên) để menu nhiều vai trò không dài quá màn hình.
    const [openSections, setOpenSections] = useState(() => {
        const init = {};
        for (const r of ROLE_ORDER) {
            try {
                const saved = sessionStorage.getItem(SEC_PREFIX + r);
                init[r] = saved === null ? null : saved === '1';
            } catch {
                init[r] = null;
            }
        }
        return init;
    });
    const sectionMatches = (sec) =>
        pathname.startsWith(sec.matchPrefix) || sec.items.some((n) => pathname === n.to || pathname.startsWith(`${n.to}/`));
    const activeRole = (roleSections.find(sectionMatches) || roleSections[0])?.role;
    const isSectionOpen = (sec) => openSections[sec.role] ?? sec.role === activeRole;

    const toggleSection = (role) => {
        setOpenSections((prev) => {
            const sec = roleSections.find((s) => s.role === role);
            const next = !(prev[role] ?? (sec ? sec.role === activeRole : true));
            try { sessionStorage.setItem(SEC_PREFIX + role, next ? '1' : '0'); } catch { /* ignore */ }
            return { ...prev, [role]: next };
        });
    };

    const effectiveWorkspace = workspace || (mainRole ? t(`roles.${mainRole}`) : '');
    const { t: tPayment } = useTranslation('payment');

    const navigate = useNavigate();
    const isOwner = Array.isArray(user?.roles) && user.roles.includes('OWNER');
    const [vipStatus, setVipStatus] = useState(() => {
        if (!isOwner) return null;
        try {
            const cached = sessionStorage.getItem('lv_sub_cache');
            if (cached) {
                const { data, timestamp } = JSON.parse(cached);
                if (Date.now() - timestamp < 5 * 60 * 1000) return data;
            }
        } catch {
            // ignore sessionStorage error
        }
        return null;
    });

    useEffect(() => {
        if (!isOwner) return;
        let mounted = true;

        try {
            const cached = sessionStorage.getItem('lv_sub_cache');
            if (cached) {
                const { timestamp } = JSON.parse(cached);
                if (Date.now() - timestamp < 5 * 60 * 1000) return undefined;
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

    // Thu gọn thanh bên (chỉ desktop): còn icon. Nhớ trạng thái để đổi trang / tải lại không bị mở ra lại
    const [collapsed, setCollapsed] = useState(() => {
        try { return localStorage.getItem(SIDE_KEY) === '1'; } catch { return false; }
    });
    useEffect(() => {
        try { localStorage.setItem(SIDE_KEY, collapsed ? '1' : '0'); } catch { /* bỏ qua */ }
        document.body.classList.toggle('lv-side-collapsed', collapsed);
        return () => document.body.classList.remove('lv-side-collapsed');
    }, [collapsed]);

    const linkBase = 'lv-nav-item flex items-center gap-3 px-3 py-2 rounded-xl text-[14px] font-semibold whitespace-nowrap transition-all';
    const renderNavLink = (n) => (
        <NavLink key={n.to} to={n.to} end={n.end} title={n.label}
                 className={({ isActive }) =>
                     `${linkBase} ${isActive ? 'lv-nav-capsule text-white' : 'text-white/75 hover:text-white'}`}>
            <Icon name={n.icon} className="text-[19px] shrink-0" />
            <span className="lv-label truncate">{n.label}</span>
            {n.badge > 0 && (
                <span className="lv-badge ml-auto min-w-5 h-5 px-1.5 rounded-full bg-error text-on-error text-[11px] font-bold inline-flex items-center justify-center shadow-sm">
                    {n.badge}
                </span>
            )}
        </NavLink>
    );

    return (
        <header className="lv-side bg-brand text-white">
            <div className="lv-side-top lg:flex lg:items-center lg:justify-between lg:gap-2">
                <Link to={home} className="flex items-center gap-2.5 min-w-0 flex-1 text-white">
                    <BrandMark className="h-[28px] w-[28px] shrink-0" />
                    <span className="min-w-0 lv-label">
                        <span className="font-bold text-[13px] tracking-[0.14em] uppercase block leading-tight truncate">LegacyVault</span>
                        {effectiveWorkspace && (
                            <span className="text-[10.5px] text-white/60 font-semibold tracking-wide uppercase block truncate">{effectiveWorkspace}</span>
                        )}
                    </span>
                </Link>
                <button type="button" onClick={() => setCollapsed((c) => !c)}
                        title={collapsed ? t('header.expandSidebar', 'Expand sidebar') : t('header.collapseSidebar', 'Collapse sidebar')}
                        aria-label={collapsed ? t('header.expandSidebar', 'Expand sidebar') : t('header.collapseSidebar', 'Collapse sidebar')}
                        aria-expanded={!collapsed}
                        className="lv-side-toggle shrink-0 hidden lg:flex h-8 w-8 rounded-full items-center justify-center text-white/75 hover:text-white bg-white/[0.06] hover:bg-white/[0.14] border border-white/10 transition-all">
                    <Icon name={collapsed ? 'left_panel_open' : 'left_panel_close'} className="text-[19px]" />
                </button>
            </div>

            {navProp?.length > 0 ? (
                <nav className="lv-side-nav">
                    {navProp.map(renderNavLink)}
                </nav>
            ) : (
                <nav className="lv-side-nav">
                    {generalItems.map(renderNavLink)}

                    {roleSections.map((sec) => {
                        const isSecOpen = collapsed || isSectionOpen(sec);
                        return (
                            <div key={sec.role} className="lv-nav-section mt-1.5 pt-1.5 border-t border-white/10">
                                <button
                                    type="button"
                                    onClick={() => toggleSection(sec.role)}
                                    aria-expanded={isSecOpen}
                                    className="lv-nav-section-header lv-label w-full flex items-center justify-between px-3 py-1 text-[11px] font-bold uppercase tracking-[0.12em] text-white/50 hover:text-white/80 transition-colors"
                                >
                                    <span className="truncate">{sec.label}</span>
                                    <Icon
                                        name={isSecOpen ? 'expand_more' : 'chevron_right'}
                                        className="text-[16px] opacity-75"
                                    />
                                </button>
                                {/* Luôn render link: dưới 1024px không có nút mở nhóm, nên chỉ ẩn bằng CSS trên desktop */}
                                <div className={`lv-nav-section-items flex flex-col gap-0.5 mt-0.5${isSecOpen ? '' : ' lv-nav-section-items--closed'}`}>
                                    {sec.items.map(renderNavLink)}
                                </div>
                            </div>
                        );
                    })}
                </nav>
            )}

            <div className="lv-side-bottom">
                {isOwner && (
                    <Link
                        to={vipStatus?.vip ? '/billing' : '/pricing'}
                        title={vipStatus?.vip ? tPayment('header.vipActive') : tPayment('header.upgradeToVip')}
                        className={`h-9 px-3.5 rounded-full text-xs font-bold inline-flex items-center gap-1.5 border transition-all ${
                            vipStatus?.vip
                                ? 'bg-white/90 text-brand border-white shadow-sm'
                                : 'bg-white/10 text-white border-white/20 hover:bg-white hover:text-brand'
                        }`}
                    >
                        <Icon name="workspace_premium" className="text-[16px]" />
                        <span className="lv-label">{vipStatus?.vip ? tPayment('header.vip') : tPayment('header.upgrade')}</span>
                    </Link>
                )}
                {userHome && (
                    <Link to="/home" title={t('header.userHome')}
                          className="h-9 px-3 rounded-full inline-flex items-center gap-1.5 text-xs font-semibold text-white/80 hover:text-white bg-white/[0.06] hover:bg-white/[0.14] border border-white/10 transition-all">
                        <Icon name="swap_horiz" className="text-[16px]" /><span className="lv-label">{t('header.userHome')}</span>
                    </Link>
                )}
                <div className="lv-side-tools flex items-center justify-between gap-2 w-full">
                    <LanguageSwitch variant="dark" className="lv-lang shrink-0" />
                    <div className="lv-side-tools-actions flex items-center gap-2 shrink-0">
                        <ThemeToggle variant="dark" />
                        {extra}
                    </div>
                </div>
                <div className="lv-side-user flex items-center gap-3 pt-3 border-t border-white/15 min-w-0">
                    <Link to="/me" title={user?.email} className="flex items-center gap-3 min-w-0 flex-1 group">
                        <UserAvatar name={user?.name} hasAvatar={user?.hasAvatar} version={user?.avatarVersion} />
                        <span className="lv-label text-sm font-semibold text-white/90 group-hover:text-white truncate transition-colors">{user?.name}</span>
                    </Link>
                    <button type="button" onClick={handleLogout} title={t('header.logout')} aria-label={t('header.logout')}
                            className="h-9 w-9 rounded-full flex items-center justify-center text-white/75 hover:text-white bg-white/[0.06] hover:bg-white/[0.15] border border-white/10 transition-all">
                        <Icon name="logout" className="text-[18px]" />
                    </button>
                </div>
            </div>
            <OnboardingGuideModal />
        </header>
    );
}

/**
 * Tiêu đề trang đặt trong <main>: khung Hero tương phản cao tách biệt rõ với phần nội dung bên dưới.
 * @param back { to, label }  vd. { to: '/executor/home', label: 'All estates' }
 * @param kicker dòng nhãn nhỏ phía trên tiêu đề (vd. "HỒ SƠ QUẢN TRỊ · LEGACYVAULT")
 */
export function PageTitle({ back, kicker, title, subtitle, right, className = 'mb-8' }) {
    const { t } = useTranslation();
    return (
        <div className={`lv-page-hero lv-rise ${className}`.trim()}>
            {back && (
                <Link to={back.to} className="lv-page-hero-back inline-flex items-center gap-1.5 text-sm font-semibold text-[#b9c9ff] hover:text-white mb-3 transition-colors">
                    <Icon name="arrow_back" className="text-[18px]" /> {back.label || t('actions.back')}
                </Link>
            )}
            {kicker && <p className="lv-page-hero-kicker text-[11px] font-bold uppercase tracking-[0.12em] text-[#b9c9ff] mb-1.5">{kicker}</p>}
            <div className="flex flex-wrap items-center justify-between gap-4">
                <div className="min-w-0 flex-1">
                    <h1 className="font-headline-md text-[28px] md:text-[32px] leading-tight font-bold tracking-tight text-white break-words">{title}</h1>
                    {subtitle && <p className="lv-page-hero-sub mt-1.5 text-sm md:text-[15px] max-w-3xl">{subtitle}</p>}
                </div>
                {right && <div className="flex flex-wrap items-center gap-3 shrink-0">{right}</div>}
            </div>
        </div>
    );
}
