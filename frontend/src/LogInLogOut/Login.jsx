import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleLogin = (e) => {
        e.preventDefault();
        if (!email || !password) {
            setError('Vui lòng nhập đầy đủ email và mật khẩu.');
            return;
        }
        setError('');

        // TODO: Người số 1 sẽ thay thế phần gọi API đăng nhập thực tế ở đây
        console.log('Logging in with:', { email, password });

        // Giả lập đăng nhập thành công, chuyển hướng về Dashboard
        navigate('/');
    };

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col justify-center items-center px-4">
            {/* Brand Header */}
            <div className="mb-8 text-center">
                <div className="w-12 h-12 rounded-2xl bg-primary text-on-primary flex items-center justify-center shadow-sm mx-auto mb-3">
                    <span className="material-symbols-outlined text-[28px]">shield_locked</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">LegacyVault</h1>
                <p className="font-body-sm text-on-surface-variant mt-1">Nền tảng quản lý và chuyển giao di sản số an toàn</p>
            </div>

            {/* Login Card */}
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                <h2 className="font-headline-md text-on-surface font-bold mb-2">Đăng nhập hệ thống</h2>
                <p className="text-sm text-on-surface-variant mb-6">Nhập thông tin tài khoản để truy cập cổng dịch vụ.</p>

                {error && (
                    <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">error</span>
                        {error}
                    </div>
                )}

                <form onSubmit={handleLogin} className="flex flex-col gap-4">
                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Email tài khoản</label>
                        <input
                            type="email"
                            placeholder="name@example.com"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <div className="flex justify-between items-center">
                            <label className="font-label-sm text-on-surface-variant font-semibold">Mật khẩu</label>
                            <a href="#" className="text-xs text-secondary hover:text-primary transition-colors font-semibold">Quên mật khẩu?</a>
                        </div>
                        <input
                            type="password"
                            placeholder="••••••••"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <button
                        type="submit"
                        className="w-full h-12 mt-2 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2"
                    >
                        <span className="material-symbols-outlined text-[18px]">login</span>
                        Đăng nhập
                    </button>
                </form>

                <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                    <p className="text-sm text-on-surface-variant">
                        Chưa có tài khoản?{' '}
                        <Link to="/register" className="text-secondary font-bold hover:underline">
                            Đăng ký ngay
                        </Link>
                    </p>
                </div>
            </div>

            {/* Security Footer Note */}
            <div className="mt-8 flex items-center gap-2 text-xs text-outline">
                <span className="material-symbols-outlined text-[16px]">lock</span>
                <span>Bảo mật mã hóa 256-Bit • Xác thực chuẩn JWT</span>
            </div>
        </div>
    );
}