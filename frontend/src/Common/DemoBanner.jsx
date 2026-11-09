import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { clearAuth } from '../auth/auth.js';

export default function DemoBanner() {
    const { t } = useTranslation('auth');
    const location = useLocation();
    const navigate = useNavigate();
    const [isDemo, setIsDemo] = useState(false);

    useEffect(() => {
        const check = () => {
            const hasDemoFlag = localStorage.getItem('lv_demo_mode') === 'true';
            const isPublicAuthPage = ['/', '/login', '/register', '/demo', '/forgot-password', '/reset-password', '/verify-email'].includes(location.pathname);
            const active = hasDemoFlag && !isPublicAuthPage;
            setIsDemo(active);
            if (active) {
                document.body.classList.add('lv-has-demo');
            } else {
                document.body.classList.remove('lv-has-demo');
            }
        };

        check();
        window.addEventListener('storage', check);
        return () => {
            window.removeEventListener('storage', check);
            document.body.classList.remove('lv-has-demo');
        };
    }, [location.pathname]);

    if (!isDemo) return null;

    const handleExit = () => {
        clearAuth();
        document.body.classList.remove('lv-has-demo');
        setIsDemo(false);
        navigate('/login', { replace: true });
    };

    return (
        <aside
            aria-label="Demo mode notice"
            className="fixed top-0 inset-x-0 h-10 z-[100] bg-gradient-to-r from-amber-500 via-amber-400 to-amber-500 text-amber-950 px-4 text-xs sm:text-sm font-semibold shadow-md flex items-center justify-between border-b border-amber-600/30 select-none"
        >
            <div className="flex items-center gap-2 min-w-0 truncate">
                <span className="material-symbols-outlined text-[18px] text-amber-950 shrink-0">science</span>
                <span className="truncate">{t('demo.bannerText', 'Bạn đang ở chế độ dùng thử, dữ liệu không được lưu')}</span>
            </div>
            <button
                type="button"
                onClick={handleExit}
                className="shrink-0 ml-3 px-3 py-1 rounded-full bg-amber-950 hover:bg-black text-amber-200 text-xs font-bold transition-all flex items-center gap-1 shadow-sm cursor-pointer"
            >
                <span className="material-symbols-outlined text-[14px]">logout</span>
                <span>{t('demo.exitBtn', 'Thoát')}</span>
            </button>
        </aside>
    );
}
