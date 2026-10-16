import React, { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';
import { saveSession, ROLES, getUser } from '../auth/auth.js';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const navigate = useNavigate();
    const location = useLocation();
    const [loading, setLoading] = useState(false);
    const [needVerify, setNeedVerify] = useState(false);
    const current = getUser();
    if (current) {
        return <Navigate to={current.roles?.includes(ROLES.LEGAL_VERIFIER) ? '/verifier/home' : '/home'} replace />;
    }

    const handleLogin = async (e) => {
        e.preventDefault();
        if (!email || !password) {
            setError('Please enter both your email and password.');
            return;
        }
        setError(''); setNeedVerify(false); setLoading(true);
        try {
            const { accessToken } = await authApi.login(email.trim(), password);
            const user = saveSession(accessToken, email.trim());
            // Legal Verifier vào portal riêng, các role khác vào UserHome
            navigate(user.roles.includes(ROLES.LEGAL_VERIFIER) ? '/verifier/home' : '/home');
        } catch (err) {
            setError(getErrorMessage(err, 'Login failed.'));
            // 403 = chưa xác thực email hoặc tài khoản bị khoá
            if (err.response?.status === 403) setNeedVerify(true);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col justify-center items-center px-4">
            {/* Brand Header */}
            <div className="mb-8 text-center">
                <div className="w-12 h-12 rounded-2xl bg-primary text-on-primary flex items-center justify-center shadow-sm mx-auto mb-3">
                    <span className="material-symbols-outlined text-[28px]">shield_locked</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">LegacyVault</h1>
                <p className="font-body-sm text-on-surface-variant mt-1">A secure platform for managing and transferring digital legacies</p>
            </div>

            {/* Login Card */}
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                <h2 className="font-headline-md text-on-surface font-bold mb-2">Sign in</h2>
                <p className="text-sm text-on-surface-variant mb-6">Enter your account details to access the portal.</p>

                {location.state?.message && !error && (
                    <div className="mb-4 p-3 rounded-lg bg-success-container text-on-success-container text-sm">
                        {location.state.message}
                    </div>
                )}
                {needVerify && (
                    <Link to="/verify-email" state={{ email }} className="block mb-4 text-sm text-secondary font-semibold">
                        Haven't verified your email? Verify now →
                    </Link>
                )}

                {error && (
                    <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">error</span>
                        {error}
                    </div>
                )}

                <form onSubmit={handleLogin} className="flex flex-col gap-4">
                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Account email</label>
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
                            <label className="font-label-sm text-on-surface-variant font-semibold">Password</label>
                            <Link to="/forgot-password" className="text-xs text-secondary hover:text-primary transition-colors font-semibold">Forgot password?</Link>
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
                        disabled={loading}
                        className="w-full h-12 mt-2 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2"
                    >
                        <span className="material-symbols-outlined text-[18px]">login</span>
                        Sign in
                    </button>
                </form>

                <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                    <p className="text-sm text-on-surface-variant">
                        Don't have an account?{' '}
                        <Link to="/register" className="text-secondary font-bold hover:underline">
                            Sign up
                        </Link>
                    </p>
                </div>
            </div>

            {/* Security Footer Note */}
            <div className="mt-8 flex items-center gap-2 text-xs text-outline">
                <span className="material-symbols-outlined text-[16px]">lock</span>
                <span>256-bit encryption • JWT authentication</span>
            </div>
        </div>
    );
}