// Khung chung cho các trang đăng nhập / đăng ký / quên mật khẩu / OTP:
// bên trái ảnh chìa khóa + lời giới thiệu, bên phải là form (children).
import React from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import ThemeToggle from './ThemeToggle.jsx';

export default function AuthShell({ children, className = '' }) {
    const { t } = useTranslation('auth');
    return (
        <div className="min-h-screen font-body-md text-on-surface antialiased lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
            <aside className="relative hidden lg:flex flex-col justify-between overflow-hidden bg-brand text-white p-12">
                <img src="/key-photo.jpg" alt="" aria-hidden="true"
                     className="absolute inset-0 w-full h-full object-cover" style={{ objectPosition: '60% 50%' }} />
                <div aria-hidden="true" className="absolute inset-0"
                     style={{ background: 'linear-gradient(180deg,rgba(11,16,32,.72) 0%,rgba(11,16,32,.35) 38%,rgba(11,16,32,.86) 100%)' }} />
                <div aria-hidden="true" className="absolute inset-0"
                     style={{ background: 'linear-gradient(90deg,rgba(11,16,32,.7) 0%,rgba(11,16,32,0) 75%)' }} />
                <Link to="/" className="relative flex items-center gap-2 text-white font-bold tracking-[0.18em] text-sm">
                    <span className="material-symbols-outlined text-[26px]">lock</span>LEGACYVAULT
                </Link>
                <div className="relative max-w-[520px] flex flex-col gap-5">
                    <h1 className="font-headline-lg text-[44px] leading-[1.1] font-semibold tracking-tight lv-rise">
                        {t('brand.tagline')}
                    </h1>
                    <p className="text-lg text-white/80">{t('brand.footer')}</p>
                </div>
                <p className="relative text-sm text-white/60">SWP391, FPT University</p>
            </aside>
            <div className={`lv-auth-form relative flex flex-col justify-center items-center px-4 py-8 min-h-screen ${className}`}>
                {/* Đặt ngay bên trái nút đổi ngôn ngữ (LanguageSwitch nằm ở top-4 right-4 trong từng trang) */}
                <ThemeToggle className="absolute top-4 right-[6.25rem]" />
                {children}
            </div>
        </div>
    );
}
