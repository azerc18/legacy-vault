// Nút chuyển nền sáng / tối, cùng kích thước với LanguageSwitch.
// variant="dark": đặt trên nền thương hiệu tối (sidebar, navbar trang chủ).
import React from 'react';
import { useTranslation } from 'react-i18next';
import { useTheme } from './theme.js';

export default function ThemeToggle({ className = '', variant = 'light' }) {
    const { t } = useTranslation();
    const [theme, setTheme] = useTheme();
    const isDark = theme === 'dark';
    const label = isDark ? t('theme.toLight') : t('theme.toDark');
    const tone = variant === 'dark'
        ? 'border-white/25 text-white/80 hover:text-white hover:bg-white/10'
        : 'border-outline-variant text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface';

    return (
        <button
            type="button"
            onClick={() => setTheme(isDark ? 'light' : 'dark')}
            title={label}
            aria-label={label}
            className={`h-10 w-10 shrink-0 rounded-xl border flex items-center justify-center transition-colors ${tone} ${className}`}
        >
            <span className="material-symbols-outlined text-[20px]">{isDark ? 'light_mode' : 'dark_mode'}</span>
        </button>
    );
}
