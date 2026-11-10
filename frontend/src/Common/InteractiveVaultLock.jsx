import React, { useEffect, useId, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';
import { paymentApi } from '../api/paymentApi.js';
import { publishSubscription } from './useVipStatus.js';

const PUBLIC_ROUTES = [
    '/',
    '/login',
    '/register',
    '/verify-email',
    '/forgot-password',
    '/reset-password',
];

// Trang đăng nhập / đăng ký / quên mật khẩu / OTP (AuthShell chia 2 cột): không vẽ cú nền,
// vì cú nằm giữa màn hình nên bị đường chia 2 cột cắt đôi.
// Lấy từ PUBLIC_ROUTES để thêm trang auth mới chỉ phải sửa một chỗ.
const AUTH_ROUTES = PUBLIC_ROUTES.filter((p) => p !== '/');

const DIAL_TICKS_24 = Array.from({ length: 24 }, (_, i) => i * 15);

const ARCHETYPE_ORDER = ['AUTO', 'ADMIN', 'VIP', 'FREE', 'VERIFIER', 'EXECUTOR', 'BENEFICIARY'];

/**
 * Tất cả các phân hạng ổ khóa đều giữ chung bảng màu Xanh Navy / Sapphire / Băng Trắng sang trọng
 * của thương hiệu LegacyVault, chỉ khác nhau ở thiết kế hình học "Cú - Ổ Khóa" (lấy cảm hứng từ
 * Logo Cú-Ổ-Khóa và các phân hạng nhân vật Cú Vaulty).
 */
const ARCHETYPE_META = {
    ADMIN: {
        vi: 'Cú Tối Cao · Quản Trị',
        en: 'Grandmaster Owl · Admin',
        badgeVi: 'HẠNG ADMIN · TỐI CAO',
        badgeEn: 'ADMIN TIER · SOVEREIGN',
        accentRgb: '91 120 246',
        secondaryRgb: '143 164 255',
    },
    VIP: {
        vi: 'Cú Vương Miện · Gói VIP',
        en: 'Royal Monocle Owl · VIP',
        badgeVi: 'HẠNG VIP · VƯƠNG MIỆN',
        badgeEn: 'VIP TIER · ROYAL OWL',
        accentRgb: '79 108 240',
        secondaryRgb: '165 184 255',
    },
    FREE: {
        vi: 'Cú Gác Đêm · Tiêu Chuẩn',
        en: 'Sentinel Owl · Free Plan',
        badgeVi: 'HẠNG FREE · NGUYÊN BẢN',
        badgeEn: 'FREE TIER · SENTINEL',
        accentRgb: '61 90 224',
        secondaryRgb: '143 164 255',
    },
    VERIFIER: {
        vi: 'Cú Học Giả · Công Chứng',
        en: 'Scholar Owl · Verifier',
        badgeVi: 'CÔNG CHỨNG · HỌC GIẢ',
        badgeEn: 'VERIFIER · SCHOLAR OWL',
        accentRgb: '72 104 236',
        secondaryRgb: '150 172 255',
    },
    EXECUTOR: {
        vi: 'Cú Hiệp Sĩ · Thi Hành',
        en: 'Knight Owl · Executor',
        badgeVi: 'THI HÀNH · HIỆP SĨ',
        badgeEn: 'EXECUTOR · KNIGHT OWL',
        accentRgb: '68 98 232',
        secondaryRgb: '143 164 255',
    },
    BENEFICIARY: {
        vi: 'Cú Tinh Cầu · Thụ Hưởng',
        en: 'Astral Owl · Beneficiary',
        badgeVi: 'THỤ HƯỞNG · TINH CẦU',
        badgeEn: 'BENEFICIARY · ASTRAL OWL',
        accentRgb: '82 114 242',
        secondaryRgb: '160 180 255',
    },
};

const OVERRIDE_EVENT = 'lv:lock-archetype-override';
const SUB_EVENT = 'lv:sub-updated';

function detectAutoArchetype(pathname, user, isVip) {
    if (pathname.startsWith('/admin')) return 'ADMIN';
    if (pathname.startsWith('/verifier')) return 'VERIFIER';
    if (pathname.startsWith('/executor')) return 'EXECUTOR';
    if (pathname.startsWith('/beneficiary')) return 'BENEFICIARY';
    if (
        pathname.startsWith('/owner') ||
        pathname.startsWith('/billing') ||
        pathname.startsWith('/pricing')
    ) {
        return isVip ? 'VIP' : 'FREE';
    }

    const roles = Array.isArray(user?.roles)
        ? user.roles
        : user?.role
          ? [user.role]
          : [];
    if (roles.includes('ADMIN')) return 'ADMIN';
    if (roles.includes('LEGAL_VERIFIER')) return 'VERIFIER';
    if (roles.includes('EXECUTOR')) return 'EXECUTOR';
    if (roles.includes('OWNER')) return isVip ? 'VIP' : 'FREE';
    if (roles.includes('BENEFICIARY')) return 'BENEFICIARY';
    return isVip ? 'VIP' : 'FREE';
}

export default function InteractiveVaultLock({
    variant = 'hero',
    to = '/register',
    label,
    className = '',
}) {
    const uid = useId().replace(/:/g, '');
    const navigate = useNavigate();
    const location = useLocation();
    const { i18n } = useTranslation();
    const isVi = (i18n.language || 'vi').startsWith('vi');

    const lockRef = useRef(null);
    const rafRef = useRef(null);
    const prevPathRef = useRef(location.pathname);

    const [status, setStatus] = useState('idle');
    const [routePulse, setRoutePulse] = useState(false);
    const [overrideMode, setOverrideMode] = useState(() => {
        try {
            return sessionStorage.getItem('lv_lock_override') || 'AUTO';
        } catch {
            return 'AUTO';
        }
    });

    const user = getUser();
    const isOwner = Array.isArray(user?.roles) && user.roles.includes('OWNER');

    const [isVip, setIsVip] = useState(() => {
        try {
            const cached = sessionStorage.getItem('lv_sub_cache');
            if (cached) {
                const parsed = JSON.parse(cached);
                return Boolean(parsed?.data?.vip);
            }
        } catch {
            // ignore
        }
        return false;
    });

    useEffect(() => {
        const syncOverride = (e) => {
            if (e?.detail) setOverrideMode(e.detail);
        };
        const syncSub = (e) => {
            if (typeof e?.detail === 'boolean') setIsVip(e.detail);
        };
        window.addEventListener(OVERRIDE_EVENT, syncOverride);
        window.addEventListener(SUB_EVENT, syncSub);
        return () => {
            window.removeEventListener(OVERRIDE_EVENT, syncOverride);
            window.removeEventListener(SUB_EVENT, syncSub);
        };
    }, []);

    useEffect(() => {
        if (!user || !isOwner) return undefined;
        try {
            const cached = sessionStorage.getItem('lv_sub_cache');
            if (cached) {
                const { data, timestamp } = JSON.parse(cached);
                if (Date.now() - timestamp < 5 * 60 * 1000 && data) {
                    setIsVip(Boolean(data.vip));
                    return undefined;
                }
            }
        } catch {
            // ignore
        }
        let alive = true;
        paymentApi
            .getMySubscription()
            .then((data) => {
                if (!alive || !data) return;
                setIsVip(Boolean(data.vip));
                publishSubscription(data);
            })
            .catch(() => {});
        return () => {
            alive = false;
        };
    }, [user, isOwner, location.pathname]);

    const autoArchetype = detectAutoArchetype(location.pathname, user, isVip);
    const activeArchetype =
        overrideMode !== 'AUTO' && ARCHETYPE_META[overrideMode]
            ? overrideMode
            : autoArchetype;
    const meta = ARCHETYPE_META[activeArchetype] || ARCHETYPE_META.FREE;

    useEffect(() => {
        if (variant !== 'sentinel') return;
        document.documentElement.style.setProperty('--lock-accent-rgb', meta.accentRgb);
        document.documentElement.style.setProperty('--lock-secondary-rgb', meta.secondaryRgb);
    }, [variant, meta.accentRgb, meta.secondaryRgb]);

    const [cursor, setCursor] = useState({
        tiltX: 0,
        tiltY: 0,
        pullX: 0,
        pullY: 0,
        dialAngle: 0,
        distance: 999,
        speed: 0,
    });

    const isPublic = PUBLIC_ROUTES.includes(location.pathname);

    useEffect(() => {
        const reduced =
            typeof window !== 'undefined' &&
            window.matchMedia &&
            window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduced) return undefined;

        let lastX = window.innerWidth / 2;
        let lastY = window.innerHeight / 2;

        const handlePointerMove = (e) => {
            const clientX = e.clientX;
            const clientY = e.clientY;

            if (rafRef.current) cancelAnimationFrame(rafRef.current);
            rafRef.current = requestAnimationFrame(() => {
                if (variant === 'sentinel') {
                    document.documentElement.style.setProperty('--cursor-x', `${clientX}px`);
                    document.documentElement.style.setProperty('--cursor-y', `${clientY}px`);

                    const glassNodes = document.querySelectorAll(
                        '.lv-side, .lv-dossier, .lv-page-hero, .lv-glass, .lv-glass-clear, .lv-glass-regular, .lv-glass-thick, .lv-glass-tinted, .lv-glass-secure, .lv-surface-standard, .lv-surface-secure, .bg-surface-container-lowest.rounded-2xl, .bg-surface-container-lowest.rounded-3xl'
                    );
                    for (let i = 0; i < glassNodes.length; i += 1) {
                        const node = glassNodes[i];
                        const r = node.getBoundingClientRect();
                        if (r.bottom < -80 || r.top > window.innerHeight + 80) continue;

                        const gx = Math.round(clientX - r.left);
                        const gy = Math.round(clientY - r.top);
                        const dxEdge = Math.max(r.left - clientX, 0, clientX - r.right);
                        const dyEdge = Math.max(r.top - clientY, 0, clientY - r.bottom);
                        const distToCard = Math.hypot(dxEdge, dyEdge);
                        const prox = Math.max(0, Math.min(1, 1 - distToCard / 42));

                        node.style.setProperty('--gx', `${gx}px`);
                        node.style.setProperty('--gy', `${gy}px`);
                        node.style.setProperty('--g-prox', prox.toFixed(3));
                    }
                }

                const el = lockRef.current;
                if (!el) return;
                const rect = el.getBoundingClientRect();
                const cx = rect.left + rect.width / 2;
                const cy = rect.top + rect.height / 2;

                const dx = clientX - cx;
                const dy = clientY - cy;
                const dist = Math.hypot(dx, dy);
                const vx = clientX - lastX;
                const vy = clientY - lastY;
                const speed = Math.min(100, Math.round(Math.hypot(vx, vy) * 2.2));
                lastX = clientX;
                lastY = clientY;

                const rawAngle = (Math.atan2(dy, dx) * 180) / Math.PI + 90;
                const normalizedAngle = ((Math.round(rawAngle) % 360) + 360) % 360;

                const maxRadius = variant === 'hero' ? 220 : 520;
                const factor = Math.max(0, 1 - dist / maxRadius);
                const clampDx = Math.max(-1, Math.min(1, dx / 240));
                const clampDy = Math.max(-1, Math.min(1, dy / 240));

                const tiltScale = variant === 'hero' ? 12 : variant === 'sentinel' ? 10 : 0;
                const pullScale = variant === 'hero' ? 6 : variant === 'sentinel' ? 12 : 0;

                const tiltX = Number((-clampDy * factor * tiltScale).toFixed(2));
                const tiltY = Number((clampDx * factor * tiltScale).toFixed(2));
                const pullX = Number((clampDx * factor * pullScale).toFixed(2));
                const pullY = Number((clampDy * factor * pullScale).toFixed(2));

                setCursor({
                    tiltX,
                    tiltY,
                    pullX,
                    pullY,
                    dialAngle: normalizedAngle,
                    distance: Math.round(dist),
                    speed,
                });
            });
        };

        window.addEventListener('pointermove', handlePointerMove, { passive: true });
        return () => {
            window.removeEventListener('pointermove', handlePointerMove);
            if (rafRef.current) cancelAnimationFrame(rafRef.current);
        };
    }, [variant]);

    useEffect(() => {
        if (prevPathRef.current === location.pathname) return undefined;
        prevPathRef.current = location.pathname;
        setRoutePulse(true);
        setStatus('route-spin');

        const t1 = setTimeout(() => {
            setStatus('idle');
        }, 540);
        const t2 = setTimeout(() => {
            setRoutePulse(false);
        }, 700);

        return () => {
            clearTimeout(t1);
            clearTimeout(t2);
        };
    }, [location.pathname]);

    const triggerUnlockSequence = (targetPath) => {
        if (status !== 'idle') return;

        const reduced =
            typeof window !== 'undefined' &&
            window.matchMedia &&
            window.matchMedia('(prefers-reduced-motion: reduce)').matches;

        if (reduced) {
            if (targetPath) navigate(targetPath);
            return;
        }

        setStatus('rotating');
        setTimeout(() => {
            setStatus('unlocked');
        }, 280);

        setTimeout(() => {
            if (targetPath && targetPath !== location.pathname) {
                setStatus('transitioning');
                setTimeout(() => {
                    navigate(targetPath);
                    setStatus('idle');
                }, 200);
            } else {
                setStatus('idle');
            }
        }, 540);
    };

    const cycleArchetypeOverride = (e) => {
        e.stopPropagation();
        const idx = ARCHETYPE_ORDER.indexOf(overrideMode);
        const next = ARCHETYPE_ORDER[(idx + 1) % ARCHETYPE_ORDER.length];
        setOverrideMode(next);
        try {
            sessionStorage.setItem('lv_lock_override', next);
        } catch {
            // ignore
        }
        window.dispatchEvent(new CustomEvent(OVERRIDE_EVENT, { detail: next }));
        triggerUnlockSequence(null);
    };

    const effectiveDialRotation =
        status === 'rotating' || status === 'route-spin'
            ? cursor.dialAngle + 270
            : status === 'unlocked' || status === 'transitioning'
              ? cursor.dialAngle + 360
              : cursor.dialAngle;

    const isShackleOpen =
        status === 'unlocked' ||
        status === 'transitioning' ||
        (isPublic && cursor.distance < 85 && status === 'idle');

    /**
     * Vẽ SVG Biểu Tượng "Cú - Ổ Khóa" (Owl-Padlock Crest) đồng bộ 100% với phong cách Logo gốc (/brand/logo-mark.png)
     * và biến hóa phụ kiện phân hạng lấy cảm hứng từ nhân vật Cú Vaulty (Vương miện, Kính một mắt, Mũ Học Giả, Giáp Hiệp Sĩ, Đại Pháp Sư).
     * Toàn bộ dùng bảng màu đơn sắc Xanh Navy / Sapphire / Trắng Băng sang trọng, không pha màu lòe loẹt.
     */
    const renderOwlLockSvg = (sizeClass = 'w-32 h-32', isBackdrop = false) => {
        // Bảng màu đơn sắc đồng nhất: tránh pha màu xanh/xám chồng chéo gây vết cắt lạ trên hình Cú nền
        const primaryStroke = isBackdrop ? 'currentColor' : '#ffffff';
        const ringRotation = isBackdrop ? 0 : effectiveDialRotation;

        return (
            <svg
                className={`${sizeClass} select-none overflow-visible ${isBackdrop ? 'lv-owl-backdrop-svg' : ''}`}
                viewBox="0 0 200 200"
                aria-hidden="true"
            >
                <defs>
                    <radialGradient id={`owlCoreGlow-${uid}`} cx="50%" cy="50%" r="55%">
                        <stop offset="0%" stopColor="#5b78f6" stopOpacity={isBackdrop ? '0.18' : '0.38'} />
                        <stop offset="65%" stopColor="#1e2d66" stopOpacity={isBackdrop ? '0.06' : '0.16'} />
                        <stop offset="100%" stopColor="#0b1020" stopOpacity="0" />
                    </radialGradient>
                </defs>

                {/* Lớp hào quang nền nhẹ đồng điệu tông Sapphire */}
                <circle cx="100" cy="104" r="90" fill={`url(#owlCoreGlow-${uid})`} />

                {/* ==================== TẦNG 1: VÀNH LA BÀN NGOÀI CÙNG (TĨNH KHI LÀ BACKGROUND, KHÔNG CẮT QUA CÀNG KHÓA) ==================== */}
                <g
                    style={{
                        transform: isBackdrop ? 'none' : `rotate(${ringRotation}deg)`,
                        transformOrigin: '100px 104px',
                        transition: isBackdrop
                            ? 'none'
                            : status === 'idle'
                              ? 'transform 140ms cubic-bezier(0.22, 1, 0.36, 1)'
                              : 'transform 440ms cubic-bezier(0.16, 1, 0.3, 1)',
                    }}
                >
                    {/* Vành tròn bao ngoài sạch sẽ, r=94 nằm hoàn toàn bên ngoài càng khóa và mũ/vương miện */}
                    <circle
                        cx="100"
                        cy="104"
                        r="94"
                        fill="none"
                        stroke={primaryStroke}
                        strokeWidth="1.5"
                        strokeOpacity="0.38"
                    />

                    {/* Vành tròn mảnh thứ hai r=88 tạo viền kép sang trọng, không dùng nét đứt cắt ngang thân cú */}
                    <circle
                        cx="100"
                        cy="104"
                        r="88"
                        fill="none"
                        stroke={primaryStroke}
                        strokeWidth="1"
                        strokeOpacity="0.22"
                    />

                    {/* Các vạch chia độ nằm gọn giữa r=88 và r=94, tuyệt đối không đâm vào càng khóa */}
                    {DIAL_TICKS_24.map((deg) => {
                        const major = deg % 45 === 0;
                        return (
                            <line
                                key={deg}
                                x1="100"
                                y1="6.5"
                                x2="100"
                                y2={major ? '11.5' : '9.5'}
                                stroke={primaryStroke}
                                strokeWidth={major ? '1.6' : '1'}
                                strokeOpacity={major ? '0.48' : '0.24'}
                                strokeLinecap="round"
                                transform={`rotate(${deg} 100 104)`}
                            />
                        );
                    })}
                </g>

                {/* ==================== TẦNG 2: CÀNG KHÓA (SHACKLE) & PHỤ KIỆN ĐẦU CÚ THEO PHÂN HẠNG ==================== */}
                <g
                    style={{
                        transform:
                            !isBackdrop && isShackleOpen
                                ? 'translateY(-14px) rotate(12deg)'
                                : !isBackdrop && status === 'route-spin'
                                  ? 'translateY(-5px) rotate(0deg)'
                                  : 'translateY(0px) rotate(0deg)',
                        transformOrigin: '76px 68px',
                        transition: 'transform 320ms cubic-bezier(0.16, 1, 0.3, 1)',
                    }}
                >
                    {/* Càng khóa chữ U dày nguyên bản từ Logo LegacyVault */}
                    <path
                        d="M 76 68 V 52 C 76 36, 86 27, 100 27 C 114 27, 124 36, 124 52 V 68"
                        fill="none"
                        stroke={primaryStroke}
                        strokeWidth="11"
                        strokeLinecap="round"
                    />

                    {/* HẠNG VIP: Vương Miện Vua Cú (King Owl Crown) */}
                    {activeArchetype === 'VIP' && (
                        <g fill={primaryStroke}>
                            <path d="M 84 22 L 86 12 L 94 18 L 100 9 L 106 18 L 114 12 L 116 22 Z" />
                        </g>
                    )}

                    {/* HẠNG ADMIN: Ấn Vương Quyền Tối Cao (Grandmaster Crest) */}
                    {activeArchetype === 'ADMIN' && (
                        <g fill={primaryStroke}>
                            <polygon points="100,9 105,18 100,22 95,18" />
                        </g>
                    )}

                    {/* HẠNG VERIFIER: Mũ Học Giả / Thẩm Phán (Graduate Mortarboard) */}
                    {activeArchetype === 'VERIFIER' && (
                        <g fill={primaryStroke}>
                            <polygon points="100,11 124,19 100,26 76,19" />
                        </g>
                    )}

                    {/* HẠNG EXECUTOR: Mào Hiệp Sĩ Hộ Vệ (Knight Crest) */}
                    {activeArchetype === 'EXECUTOR' && (
                        <g fill={primaryStroke}>
                            <path d="M 95 22 L 100 10 L 105 22 Z" />
                        </g>
                    )}
                </g>

                {/* ==================== TẦNG 3: THÂN "CÚ - Ổ KHÓA" (CHUẨN ĐƯỜNG NÉT LOGO LEGACYVAULT) ==================== */}
                <g fill={primaryStroke}>
                    {/* 1. Đôi mày & sừng cú vút cong chữ V đặc trưng của Logo */}
                    <path d="M 48 56 C 58 68, 79 70, 100 83 C 121 70, 142 68, 152 56 C 149 74, 135 83, 117 87 C 108 89, 103 94, 100 99 C 97 94, 92 89, 83 87 C 65 83, 51 74, 48 56 Z" />

                    {/* ADMIN có thêm cặp sừng phụ vút cao hai bên thái dương */}
                    {activeArchetype === 'ADMIN' && (
                        <>
                            <path d="M 46 66 C 36 58, 34 46, 38 38 C 42 48, 48 54, 55 60 Z" />
                            <path d="M 154 66 C 164 58, 166 46, 162 38 C 158 48, 152 54, 145 60 Z" />
                        </>
                    )}

                    {/* 2. Đôi mắt cú bán nguyệt sắc sảo */}
                    <path d="M 65 85 A 13.5 13.5 0 1 0 90 92 L 84 89 A 7.5 7.5 0 1 1 71 86 Z" />
                    <path d="M 110 92 A 13.5 13.5 0 1 0 135 85 L 129 86 A 7.5 7.5 0 1 1 116 89 Z" />

                    {/* VIP & VERIFIER: Kính một mắt (Monocle) đặc trưng của Cú Vaulty */}
                    {(activeArchetype === 'VIP' || activeArchetype === 'VERIFIER') && (
                        <g fill="none" stroke={primaryStroke}>
                            <circle cx="122.5" cy="90" r="16.5" strokeWidth="2.4" />
                        </g>
                    )}

                    {/* 3. Mỏ cú hình thoi sắc gọn ở chính giữa (đồng thời là tâm ổ khóa) */}
                    <polygon points="100,101 107,110 100,121 93,110" />

                    {/* 4. Cánh trái: 3 nan lông vũ uốn cong tạo nửa thân khiên bên trái (chuẩn logo-mark.png) */}
                    <path d="M 60 80 C 51 80, 47 86, 47 96 C 47 128, 63 153, 88 166 L 83 145 C 78 142, 74 136, 73 125 L 79 129 L 83 155 C 88 158, 92 160, 94 161 L 94 140 C 96 135, 99 130, 102 126 C 85 113, 68 103, 60 91 C 57 86, 58 82, 60 80 Z" />

                    {/* 5. Cánh phải: đường lượn chiếc lá ôm trọn đáy khiên ổ khóa (chuẩn logo-mark.png) */}
                    <path
                        fillRule="evenodd"
                        d="M 140 80 C 149 80, 153 86, 153 96 C 153 132, 131 157, 100 174 C 95 165, 94 152, 99 139 C 105 124, 121 112, 136 101 C 142 96, 143 85, 140 80 Z M 139 112 C 126 121, 113 132, 107 146 C 105 151, 105 156, 106 160 C 125 149, 137 133, 139 112 Z"
                    />
                </g>
            </svg>
        );
    };

    // ==================== CHẾ ĐỘ 1: HERO CENTERPIECE ====================
    if (variant === 'hero') {
        const heroLabel =
            label ||
            (status === 'idle'
                ? isVi
                    ? 'Chạm để mở khóa Két Di Sản'
                    : 'Click to unlock Legacy Vault'
                : status === 'rotating'
                  ? isVi
                      ? 'Đang xoay mã khóa AES-256...'
                      : 'Rotating AES-256 combination...'
                  : isVi
                    ? 'Đã mở khóa — Đang vào két!'
                    : 'Vault unlocked — Entering!');

        return (
            <div className={`relative inline-flex flex-col items-center ${className}`}>
                <div
                    aria-hidden="true"
                    className={`pointer-events-none absolute -inset-4 rounded-full blur-2xl transition-all duration-500 ${
                        isShackleOpen
                            ? 'bg-[#8fa4ff]/35 scale-125'
                            : 'bg-secondary/35 scale-100'
                    }`}
                />

                <button
                    ref={lockRef}
                    type="button"
                    onClick={() => triggerUnlockSequence(to)}
                    disabled={status !== 'idle'}
                    aria-label={heroLabel}
                    style={{
                        transform: `perspective(700px) translate3d(${cursor.pullX}px, ${cursor.pullY}px, 0) rotateX(${cursor.tiltX}deg) rotateY(${cursor.tiltY}deg)`,
                        transition: 'transform 140ms cubic-bezier(0.22, 1, 0.36, 1)',
                    }}
                    className="group relative cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-[#b9c9ff] rounded-3xl p-2 text-white flex flex-col items-center"
                >
                    {renderOwlLockSvg('w-36 h-36 md:w-40 md:h-40', false)}

                    <span className="mt-2 inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-white/10 border border-white/20 backdrop-blur text-xs font-semibold text-white tracking-wide group-hover:bg-white/20 transition-colors">
                        <span className="w-2 h-2 rounded-full bg-[#8fa4ff]" />
                        <span>{heroLabel}</span>
                    </span>
                </button>
            </div>
        );
    }

    // ==================== CHẾ ĐỘ 2: EMBLEM TRONG TIÊU ĐỀ TRANG ====================
    if (variant === 'emblem') {
        const badgeTitle = isVi ? meta.badgeVi : meta.badgeEn;
        const lockName = isVi ? meta.vi : meta.en;

        return (
            <div className={`inline-flex items-center gap-1.5 shrink-0 ${className}`}>
                <button
                    ref={lockRef}
                    type="button"
                    onClick={() => triggerUnlockSequence(null)}
                    title={
                        isVi
                            ? `${lockName} · Góc ${String(cursor.dialAngle).padStart(3, '0')}° (Bấm để xoay kiểm tra cơ khóa)`
                            : `${lockName} · Dial ${String(cursor.dialAngle).padStart(3, '0')}° (Click to spin lock)`
                    }
                    aria-label={lockName}
                    className="group relative inline-flex items-center gap-2.5 rounded-2xl bg-white/[0.08] hover:bg-white/[0.15] border border-white/15 hover:border-[#8fa4ff]/60 p-1.5 pr-3.5 transition-all backdrop-blur-md text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-[#b9c9ff]"
                >
                    <span className="w-10 h-10 flex items-center justify-center shrink-0 text-white">
                        {renderOwlLockSvg('w-10 h-10', false)}
                    </span>
                    <span className="hidden sm:flex flex-col items-start text-left leading-tight">
                        <span className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-[#b9c9ff] tabular-nums">
                            <span className="w-1.5 h-1.5 rounded-full bg-[#8fa4ff]" />
                            {badgeTitle} · {String(cursor.dialAngle).padStart(3, '0')}°
                        </span>
                        <span className="text-[11px] font-semibold text-white/95 mt-0.5">
                            {lockName}
                        </span>
                    </span>
                </button>

                <button
                    type="button"
                    onClick={cycleArchetypeOverride}
                    title={
                        isVi
                            ? `Đổi phân hạng Cú-Ổ-Khóa nền (Đang chọn: ${overrideMode === 'AUTO' ? 'Tự động theo Role/Plan' : activeArchetype})`
                            : `Switch Owl-Lock rank (Current: ${overrideMode === 'AUTO' ? 'Auto by Role/Plan' : activeArchetype})`
                    }
                    aria-label="Switch owl lock archetype"
                    className="h-10 px-2.5 rounded-xl bg-white/[0.08] hover:bg-white/[0.16] border border-white/15 text-[11px] font-bold text-[#b9c9ff] hover:text-white inline-flex items-center gap-1 backdrop-blur-md transition-all"
                >
                    <span className="material-symbols-outlined text-[16px]">tune</span>
                    <span className="hidden md:inline uppercase tracking-wider">
                        {overrideMode === 'AUTO' ? 'AUTO' : overrideMode}
                    </span>
                </button>
            </div>
        );
    }

    // ==================== CHẾ ĐỘ 3: BIỂU TƯỢNG CÚ - Ổ KHÓA XOAY NỀN PHÍA SAU LỚP KÍNH LIQUID GLASS ====================
    if (location.pathname === '/' || AUTH_ROUTES.includes(location.pathname)) {
        return (
            <div
                aria-hidden="true"
                className={`fixed top-0 left-0 right-0 z-50 h-[3px] pointer-events-none transition-opacity duration-300 ${
                    routePulse ? 'opacity-100' : 'opacity-0'
                }`}
            >
                <div className="h-full w-full bg-secondary lv-route-bar" />
            </div>
        );
    }

    return (
        <>
            <div
                aria-hidden="true"
                className={`fixed top-0 left-0 right-0 z-50 h-[3px] pointer-events-none transition-opacity duration-300 ${
                    routePulse ? 'opacity-100' : 'opacity-0'
                }`}
            >
                <div className="h-full w-full bg-secondary lv-route-bar" />
            </div>

            <div
                aria-hidden="true"
                className="lv-backdrop-vault fixed inset-0 pointer-events-none flex items-center justify-center overflow-hidden"
            >
                <div
                    ref={lockRef}
                    className="lv-backdrop-lock-stage relative flex flex-col items-center justify-center"
                    style={{
                        transform: `perspective(900px) translate3d(${cursor.pullX}px, ${cursor.pullY}px, 0) rotateX(${cursor.tiltX}deg) rotateY(${cursor.tiltY}deg)`,
                        transition: 'transform 160ms cubic-bezier(0.22, 1, 0.36, 1)',
                    }}
                >
                    {renderOwlLockSvg(
                        'w-[460px] h-[460px] md:w-[600px] md:h-[600px] lg:w-[700px] lg:h-[700px]',
                        true
                    )}
                </div>
            </div>
        </>
    );
}
