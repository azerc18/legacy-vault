// Khung chung cho các trang đăng nhập / đăng ký / quên mật khẩu / OTP:
// bên trái ảnh chìa khóa + lời giới thiệu, bên phải là form (children).
import React from 'react';
import ThemeToggle from './ThemeToggle.jsx';
import AuthHero from './AuthHero.jsx';

export default function AuthShell({ children, className = '' }) {
    return (
        <div className="min-h-screen font-body-md text-on-surface antialiased lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
            <AuthHero />
            <div className={`lv-auth-form relative flex flex-col justify-center items-center px-4 py-8 min-h-screen ${className}`}>
                {/* Đặt ngay bên trái nút đổi ngôn ngữ (LanguageSwitch nằm ở top-4 right-4 trong từng trang) */}
                <ThemeToggle className="absolute top-4 right-[6.25rem]" />
                {children}
            </div>
        </div>
    );
}
