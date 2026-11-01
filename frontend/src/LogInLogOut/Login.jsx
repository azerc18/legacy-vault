import React, { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi.js';
import { getErrorMessage, getErrorCode } from '../api/http.js';
import { saveSession, ROLES, getUser, updateStoredUser } from '../auth/auth.js';
import { userApi, toStoredUser } from '../api/userApi.js';
import LanguageSwitch from '../Common/LanguageSwitch.jsx';

// Trang đích sau khi đăng nhập theo role: Admin > Legal Verifier > còn lại
const homeFor = (u) =>
    u.roles?.includes('ADMIN') ? '/admin/users'
        : u.roles?.includes(ROLES.LEGAL_VERIFIER) ? '/verifier/home'
            : '/home';

const inputCls =
    'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const navigate = useNavigate();
    const location = useLocation();
    const [loading, setLoading] = useState(false);
    const [needVerify, setNeedVerify] = useState(false);
    const [mfaToken, setMfaToken] = useState(null);   // != null -> đang ở bước 2
    const [code, setCode] = useState('');
    const [useBackup, setUseBackup] = useState(false);

    // Đã đăng nhập -> không cho vào lại trang login
    const current = getUser();
    if (current) {
        return <Navigate to={homeFor(current)} replace />;
    }

    // Lưu token TRƯỚC rồi mới gọi /users/me (để request có Bearer token)
    const finishLogin = async (accessToken) => {
        const user = saveSession(accessToken, email.trim());
        try {
            updateStoredUser(toStoredUser(await userApi.getMe()));
        } catch { /* header vẫn hiển thị được tên mặc định */ }
        navigate(homeFor(user));
    };

    const backToPassword = () => {
        setMfaToken(null); setPassword(''); setCode(''); setError('');
    };

    const handleLogin = async (e) => {
        e.preventDefault();
        if (!email || !password) {
            setError('Please enter both your email and password.');
            return;
        }
        setError(''); setNeedVerify(false); setLoading(true);
        try {
            const res = await authApi.login(email.trim(), password);
            if (res.mfaRequired) {          // H4: chuyển sang bước nhập mã
                setMfaToken(res.mfaToken);
                setCode(''); setUseBackup(false);
                return;
            }
            await finishLogin(res.accessToken);
        } catch (err) {
            setError(getErrorMessage(err, 'Login failed.'));
            // 403 = chưa xác thực email hoặc tài khoản bị khoá
            if (err.response?.status === 403) setNeedVerify(true);
        } finally {
            setLoading(false);
        }
    };

    const handleMfa = async (e) => {
        e.preventDefault();
        if (!code.trim()) return setError('Please enter the code.');
        setError(''); setLoading(true);
        try {
            const { accessToken } = await authApi.loginMfa(mfaToken, code.trim());
            await finishLogin(accessToken);
        } catch (err) {
            const msg = getErrorMessage(err, 'Verification failed.');
            const ec = getErrorCode(err);
            // Phiếu hết hạn / tài khoản bị khoá -> quay lại nhập mật khẩu
            if (ec === 'MFA_CHALLENGE_EXPIRED' || ec === 'ACCOUNT_LOCKED') backToPassword();
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="relative bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col justify-center items-center px-4">
            <LanguageSwitch className="absolute top-4 right-4" />
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
                <h2 className="font-headline-md text-on-surface font-bold mb-2">
                    {mfaToken ? 'Two-factor verification' : 'Sign in'}
                </h2>
                <p className="text-sm text-on-surface-variant mb-6">
                    {mfaToken ? `Signing in as ${email.trim()}` : 'Enter your account details to access the portal.'}
                </p>

                {location.state?.message && !error && !mfaToken && (
                    <div className="mb-4 p-3 rounded-lg bg-success-container text-on-success-container text-sm">
                        {location.state.message}
                    </div>
                )}
                {needVerify && !mfaToken && (
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

                {!mfaToken ? (
                    /* ---------- Bước 1: email + mật khẩu ---------- */
                    <form onSubmit={handleLogin} className="flex flex-col gap-4">
                        <div className="flex flex-col gap-1.5">
                            <label className="font-label-sm text-on-surface-variant font-semibold">Account email</label>
                            <input
                                type="email"
                                placeholder="name@example.com"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                className={inputCls}
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
                                className={inputCls}
                            />
                        </div>

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full h-12 mt-2 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2"
                        >
                            <span className="material-symbols-outlined text-[18px]">login</span>
                            {loading ? 'Signing in...' : 'Sign in'}
                        </button>
                    </form>
                ) : (
                    /* ---------- Bước 2: mã 2FA (H4) ---------- */
                    <form onSubmit={handleMfa} className="flex flex-col gap-4">
                        <div className="flex items-center gap-3 p-3 rounded-xl bg-secondary-fixed text-on-secondary-fixed text-sm">
                            <span className="material-symbols-outlined">phonelink_lock</span>
                            {useBackup
                                ? 'Enter one of your backup codes (each code works once).'
                                : 'Open your authenticator app and enter the 6-digit code.'}
                        </div>
                        <input
                            autoFocus
                            value={code}
                            onChange={(e) => setCode(useBackup
                                ? e.target.value.toUpperCase()
                                : e.target.value.replace(/\D/g, ''))}
                            inputMode={useBackup ? 'text' : 'numeric'}
                            autoComplete="one-time-code"
                            maxLength={useBackup ? 9 : 6}
                            placeholder={useBackup ? 'XXXX-XXXX' : '123456'}
                            className={`${inputCls} text-center text-xl tracking-[0.3em] font-semibold`}
                        />
                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full h-12 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2"
                        >
                            <span className="material-symbols-outlined text-[18px]">verified_user</span>
                            {loading ? 'Verifying...' : 'Verify'}
                        </button>
                        <div className="flex justify-between text-sm">
                            <button type="button"
                                    onClick={() => { setUseBackup(!useBackup); setCode(''); setError(''); }}
                                    className="text-secondary font-semibold hover:text-primary">
                                {useBackup ? 'Use authenticator app' : 'Use a backup code'}
                            </button>
                            <button type="button" onClick={backToPassword}
                                    className="text-on-surface-variant hover:text-primary">
                                ← Back
                            </button>
                        </div>
                    </form>
                )}

                {!mfaToken && (
                    <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                        <p className="text-sm text-on-surface-variant">
                            Don't have an account?{' '}
                            <Link to="/register" className="text-secondary font-bold hover:underline">
                                Sign up
                            </Link>
                        </p>
                    </div>
                )}
            </div>

            {/* Security Footer Note */}
            <div className="mt-8 flex items-center gap-2 text-xs text-outline">
                <span className="material-symbols-outlined text-[16px]">lock</span>
                <span>End-to-end encrypted • Released only after legal verification</span>
            </div>
        </div>
    );
}