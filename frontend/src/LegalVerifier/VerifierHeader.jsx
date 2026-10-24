import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getUser, logout } from '../auth/auth.js';
import UserAvatar from '../Common/UserAvatar.jsx';

// Header dùng chung cho các trang con của Legal Verifier (danh sách, chi tiết, lịch sử).
// backTo: nút mũi tên quay lại; right: phần tử thêm bên phải (vd. trạng thái hồ sơ).
export default function VerifierHeader({ title, subtitle, backTo = '/verifier/home', right = null }) {
    const navigate = useNavigate();
    const user = getUser();

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <header className="sticky top-0 z-10 bg-background/95 backdrop-blur-sm border-b border-outline-variant/30">
            <div className="max-w-[1600px] mx-auto px-5 h-20 flex items-center justify-between gap-4">
                <div className="flex items-center gap-3 min-w-0">
                    <Link to={backTo} aria-label="Go back"
                          className="p-2 rounded-full hover:bg-surface-container-high text-on-surface-variant">
                        <span className="material-symbols-outlined">arrow_back</span>
                    </Link>
                    <div className="min-w-0">
                        <h1 className="font-headline-sm text-primary font-bold tracking-tight truncate">{title}</h1>
                        {subtitle && <p className="text-sm text-on-surface-variant mt-0.5 truncate">{subtitle}</p>}
                    </div>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                    {right}
                    <Link to="/verifier/home"
                          className="hidden sm:flex items-center gap-1 text-sm font-semibold text-on-surface-variant hover:text-primary">
                        <span className="material-symbols-outlined text-[18px]">home</span> Home
                    </Link>
                    <Link to="/me" title={user?.email}
                          className="flex items-center gap-2 h-10 pl-1 pr-3 rounded-xl hover:bg-surface-container-low">
                        <UserAvatar name={user?.name} hasAvatar={user?.hasAvatar} version={user?.avatarVersion} />
                        <span className="hidden md:block text-sm font-semibold text-on-surface">{user?.name}</span>
                    </Link>
                    <button
                        type="button"
                        onClick={handleLogout}
                        className="h-10 px-3 rounded-xl border border-outline-variant text-sm font-semibold flex items-center gap-1 hover:bg-surface-container-low"
                    >
                        <span className="material-symbols-outlined text-[18px]">logout</span>
                        <span className="hidden sm:inline">Log out</span>
                    </button>
                </div>
            </div>
        </header>
    );
}
