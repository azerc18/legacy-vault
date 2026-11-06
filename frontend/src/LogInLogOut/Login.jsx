import AuthShell from '../Common/AuthShell.jsx';
import React, { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '../api/authApi.js';
import { getErrorMessage, getErrorCode } from '../api/http.js';
import { saveSession, getUser, updateStoredUser, homeFor, safeRedirect, emailFromCredential } from '../auth/auth.js';
import { userApi, toStoredUser } from '../api/userApi.js';
import LanguageSwitch from '../Common/LanguageSwitch.jsx';
import PasswordInput from '../Common/PasswordInput.jsx';
import GoogleSignInButton from '../Common/GoogleSignInButton.jsx';

const inputCls =
    'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all';

export default function Login() {
    const { t } = useTranslation('auth');
    const navigate = useNavigate();
    const location = useLocation();
    // Trang Đăng ký chuyển sang khi tài khoản Google đã bật 2FA: { email, mfaToken }
    const [email, setEmail] = useState(location.state?.email || '');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [needVerify, setNeedVerify] = useState(false);
    const [mfaToken, setMfaToken] = useState(location.state?.mfaToken || null);   // != null -> đang ở bước 2
    const [code, setCode] = useState('');
    const [useBackup, setUseBackup] = useState(false);

    // Trang cần đăng nhập mà người dùng đang mở dở (RequireRole truyền sang)
    const from = safeRedirect(location.state?.from);
    // http.js chuyển về /login?expired=1 khi refresh token hết hạn
    const expired = new URLSearchParams(location.search).has('expired');

    // Đã đăng nhập -> không cho vào lại trang login
    const current = getUser();
    if (current) {
        return <Navigate to={from || homeFor(current)} replace />;
    }

    // Lưu token TRƯỚC rồi mới gọi /users/me (để request có Bearer token)
    // mail: truyền thẳng khi vừa setEmail (state chưa kịp cập nhật), vd. đăng nhập Google
    const finishLogin = async (accessToken, mail = email) => {
        const user = saveSession(accessToken, mail.trim());
        try {
            updateStoredUser(toStoredUser(await userApi.getMe()));
        } catch { /* header vẫn hiển thị được tên mặc định */ }
        navigate(from || homeFor(user), { replace: true });
    };

    const backToPassword = () => {
        setMfaToken(null); setPassword(''); setCode(''); setError('');
    };

    const handleLogin = async (e) => {
        e.preventDefault();
        if (!email || !password) {
            setError(t('login.errors.required'));
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
            setError(getErrorMessage(err, t('login.errors.failed')));
            // Chỉ tài khoản chưa xác minh email mới cần link xác minh (bị khoá cũng là 403 nhưng khác mã)
            setNeedVerify(getErrorCode(err) === 'ACCOUNT_NOT_ACTIVE');
        } finally {
            setLoading(false);
        }
    };

    // Google trả ID token -> BE kiểm tra và cấp phiên như đăng nhập thường (vẫn hỏi mã nếu đã bật 2FA)
    const handleGoogle = async (credential) => {
        const googleEmail = emailFromCredential(credential);
        setError(''); setNeedVerify(false); setEmail(googleEmail); setLoading(true);
        try {
            const res = await authApi.loginGoogle(credential);
            if (res.mfaRequired) {
                setMfaToken(res.mfaToken);
                setCode(''); setUseBackup(false);
                return;
            }
            await finishLogin(res.accessToken, googleEmail);
        } catch (err) {
            setError(getErrorMessage(err, t('google.failed')));
        } finally {
            setLoading(false);
        }
    };

    const handleMfa = async (e) => {
        e.preventDefault();
        if (!code.trim()) return setError(t('mfa.errors.required'));
        setError(''); setLoading(true);
        try {
            const { accessToken } = await authApi.loginMfa(mfaToken, code.trim());
            await finishLogin(accessToken);
        } catch (err) {
            const msg = getErrorMessage(err, t('mfa.errors.failed'));
            const ec = getErrorCode(err);
            // Phiếu hết hạn / tài khoản bị khoá -> quay lại nhập mật khẩu
            if (ec === 'MFA_CHALLENGE_EXPIRED' || ec === 'ACCOUNT_LOCKED') backToPassword();
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    // Thông báo xanh: từ trang khác chuyển sang (đã xác minh email, đã đổi mật khẩu...) hoặc hết phiên
    const notice = location.state?.message || (expired ? t('login.expired') : '');

    return (
        <AuthShell>
            <LanguageSwitch className="absolute top-4 right-4" />
            {/* Brand Header */}
            <div className="mb-8 text-center lv-auth-logo">
                <div className="w-12 h-12 rounded-2xl bg-primary text-on-primary flex items-center justify-center shadow-sm mx-auto mb-3">
                    <span className="material-symbols-outlined text-[28px]">shield_locked</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">LegacyVault</h1>
                <p className="font-body-sm text-on-surface-variant mt-1">{t('brand.tagline')}</p>
            </div>

            {/* Login Card */}
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                <h2 className="font-headline-md text-on-surface font-bold mb-2">
                    {mfaToken ? t('mfa.title') : t('login.title')}
                </h2>
                <p className="text-sm text-on-surface-variant mb-6">
                    {mfaToken ? t('mfa.signingInAs', { email: email.trim() }) : t('login.subtitle')}
                </p>

                {notice && !error && !mfaToken && (
                    <div role="status" className="mb-4 p-3 rounded-lg bg-success-container text-on-success-container text-sm">
                        {notice}
                    </div>
                )}
                {needVerify && !mfaToken && (
                    <Link to="/verify-email" state={{ email: email.trim(), fromLogin: true }}
                          className="block mb-4 text-sm text-secondary font-semibold">
                        {t('login.notVerified')}
                    </Link>
                )}

                {error && (
                    <div role="alert" className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">error</span>
                        {error}
                    </div>
                )}

                {!mfaToken ? (
                    /* ---------- Bước 1: email + mật khẩu, hoặc Google ---------- */
                    <div className="flex flex-col gap-4">
                    <form onSubmit={handleLogin} className="flex flex-col gap-4">
                        <div className="flex flex-col gap-1.5">
                            <label htmlFor="login-email" className="font-label-sm text-on-surface-variant font-semibold">{t('login.email')}</label>
                            <input
                                id="login-email"
                                type="email"
                                autoComplete="username"
                                placeholder="name@example.com"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                className={inputCls}
                            />
                        </div>

                        <div className="flex flex-col gap-1.5">
                            <div className="flex justify-between items-center">
                                <label htmlFor="login-password" className="font-label-sm text-on-surface-variant font-semibold">{t('login.password')}</label>
                                <Link to="/forgot-password" className="text-xs text-secondary hover:text-primary transition-colors font-semibold">{t('login.forgot')}</Link>
                            </div>
                            <PasswordInput
                                id="login-password"
                                autoComplete="current-password"
                                placeholder="••••••••"
                                value={password}
                                onChange={setPassword}
                                className={inputCls}
                            />
                        </div>

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full h-12 mt-2 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2 disabled:opacity-60 lv-pill"
                        >
                            <span className="material-symbols-outlined text-[18px]">login</span>
                            {loading ? t('login.submitting') : t('login.submit')}
                        </button>
                    </form>
                    <GoogleSignInButton onCredential={handleGoogle} disabled={loading} />
                    </div>
                ) : (
                    /* ---------- Bước 2: mã 2FA (H4) ---------- */
                    <form onSubmit={handleMfa} className="flex flex-col gap-4">
                        <div className="flex items-center gap-3 p-3 rounded-xl bg-secondary-fixed text-on-secondary-fixed text-sm">
                            <span className="material-symbols-outlined">phonelink_lock</span>
                            {useBackup ? t('mfa.hintBackup') : t('mfa.hintApp')}
                        </div>
                        <input
                            autoFocus
                            aria-label={useBackup ? t('mfa.hintBackup') : t('mfa.hintApp')}
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
                            className="w-full h-12 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2 disabled:opacity-60 lv-pill"
                        >
                            <span className="material-symbols-outlined text-[18px]">verified_user</span>
                            {loading ? t('mfa.verifying') : t('mfa.verify')}
                        </button>
                        <div className="flex justify-between text-sm">
                            <button type="button"
                                    onClick={() => { setUseBackup(!useBackup); setCode(''); setError(''); }}
                                    className="text-secondary font-semibold hover:text-primary">
                                {useBackup ? t('mfa.useApp') : t('mfa.useBackup')}
                            </button>
                            <button type="button" onClick={backToPassword}
                                    className="text-on-surface-variant hover:text-primary">
                                {t('mfa.back')}
                            </button>
                        </div>
                    </form>
                )}

                {!mfaToken && (
                    <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                        <p className="text-sm text-on-surface-variant">
                            {t('login.noAccount')}{' '}
                            <Link to="/register" className="text-secondary font-bold hover:underline">
                                {t('login.signUp')}
                            </Link>
                        </p>
                    </div>
                )}
            </div>

            {/* Security Footer Note */}
            <div className="mt-8 flex items-center gap-2 text-xs text-outline">
                <span className="material-symbols-outlined text-[16px]">lock</span>
                <span>{t('brand.footer')}</span>
            </div>
        </AuthShell>
    );
}
