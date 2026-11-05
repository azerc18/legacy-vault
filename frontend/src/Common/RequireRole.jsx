// Bảo vệ route theo role (đọc từ JWT đã lưu khi đăng nhập).
// Chưa đăng nhập -> /login; thiếu role -> trang "không có quyền" thay vì để trang tự gặp 403 từ BE.
import React from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { useTranslation } from 'react-i18next';

export default function RequireRole({ roles, children }) {
    const user = getUser();
    const location = useLocation();
    const { t } = useTranslation('common');

    if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;

    const mine = user.roles?.length ? user.roles : [user.role];
    if (roles && !roles.some((r) => mine.includes(r))) {
        return (
            <div className="bg-background min-h-screen font-body-md text-on-surface flex flex-col items-center justify-center px-5 text-center">
                <span className="material-symbols-outlined text-[48px] text-outline">lock</span>
                <h1 className="mt-4 font-headline-md text-headline-md">{t('forbidden.title')}</h1>
                <p className="mt-2 text-on-surface-variant max-w-md">
                    {t('forbidden.message', {
                        roles: roles.map((r) => t(`roles.${r}`, { defaultValue: r.replace('_', ' ').toLowerCase() })).join(' / '),
                    })}
                </p>
                <Link to="/home" className="mt-6 h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center lv-pill">
                    {t('forbidden.backHome')}
                </Link>
            </div>
        );
    }
    return children;
}
