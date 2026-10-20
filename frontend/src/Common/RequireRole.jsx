// Bảo vệ route theo role (đọc từ JWT đã lưu khi đăng nhập).
// Chưa đăng nhập -> /login; thiếu role -> trang "không có quyền" thay vì để trang tự gặp 403 từ BE.
import React from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { getUser } from '../auth/auth.js';

export default function RequireRole({ roles, children }) {
    const user = getUser();
    const location = useLocation();

    if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;

    const mine = user.roles?.length ? user.roles : [user.role];
    if (roles && !roles.some((r) => mine.includes(r))) {
        return (
            <div className="bg-background min-h-screen font-body-md text-on-surface flex flex-col items-center justify-center px-5 text-center">
                <span className="material-symbols-outlined text-[48px] text-outline">lock</span>
                <h1 className="mt-4 font-headline-md text-headline-md">You don't have access to this page</h1>
                <p className="mt-2 text-on-surface-variant max-w-md">
                    This page is for {roles.map((r) => r.replace('_', ' ').toLowerCase()).join(' / ')} accounts.
                    If you were just given this role, sign out and sign in again.
                </p>
                <Link to="/home" className="mt-6 h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center">
                    Back to home
                </Link>
            </div>
        );
    }
    return children;
}
