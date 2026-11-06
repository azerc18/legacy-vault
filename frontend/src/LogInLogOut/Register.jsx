import AuthShell from '../Common/AuthShell.jsx';
import React, { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { getUser, homeFor, saveSession, updateStoredUser, emailFromCredential } from '../auth/auth.js';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';
import PasswordStrengthMeter from '../Common/PasswordStrengthMeter.jsx';
import { isPasswordValid } from '../Common/passwordPolicy.js';
import PasswordInput from '../Common/PasswordInput.jsx';
import LanguageSwitch from '../Common/LanguageSwitch.jsx';
import GoogleSignInButton from '../Common/GoogleSignInButton.jsx';
import { userApi, toStoredUser } from '../api/userApi.js';
import { useTranslation } from 'react-i18next';

const inputCls =
    'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all';

export default function Register() {
    const { t } = useTranslation('auth');
    const [formData, setFormData] = useState({
        fullName: '',
        email: '',
        password: '',
        passwordConfirm: ''
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    // Đã đăng nhập -> về trang chủ theo role (giống Login)
    const current = getUser();
    if (current) return <Navigate to={homeFor(current)} replace />;


    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };
    // PasswordInput trả thẳng giá trị chứ không trả event
    const setField = (name) => (value) => setFormData((f) => ({ ...f, [name]: value }));

    // Đăng ký bằng Google: BE tạo tài khoản (đã xác minh email) rồi đăng nhập luôn, không cần OTP
    const handleGoogle = async (credential) => {
        const googleEmail = emailFromCredential(credential);
        setError(''); setLoading(true);
        try {
            const res = await authApi.loginGoogle(credential);
            if (res.mfaRequired) {
                // Email này đã có tài khoản bật 2FA -> sang trang Đăng nhập nhập mã
                navigate('/login', { state: { email: googleEmail, mfaToken: res.mfaToken } });
                return;
            }
            const user = saveSession(res.accessToken, googleEmail);
            try { updateStoredUser(toStoredUser(await userApi.getMe())); } catch { /* giữ tên mặc định */ }
            navigate(homeFor(user), { replace: true });
        } catch (err) {
            setError(getErrorMessage(err, t('google.failed')));
        } finally {
            setLoading(false);
        }
    };

    const handleRegister = async (e) => {
        e.preventDefault();
        const { fullName, email, password, passwordConfirm } = formData;

        if (!fullName || !email || !password || !passwordConfirm) {
            setError(t('register.errors.required'));
            return;
        }

        if (!isPasswordValid(password)) { setError(t('register.errors.weakPassword')); return; }

        if (password !== passwordConfirm) {
            setError(t('register.errors.mismatch'));
            return;
        }

        setError('');
        setLoading(true);
        try {
            await authApi.register({ fullName, email: email.trim(), password, passwordConfirm });
            // BE đã tự gửi OTP -> sang trang xác thực
            navigate('/verify-email', { state: { email: email.trim() } });
        } catch (err) {
            setError(getErrorMessage(err, t('register.errors.failed')));
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthShell>
            <LanguageSwitch className="absolute top-4 right-4" />
            {/* Brand Header */}
            <div className="mb-6 text-center">
                <div className="w-12 h-12 rounded-2xl bg-primary text-on-primary flex items-center justify-center shadow-sm mx-auto mb-3">
                    <span className="material-symbols-outlined text-[28px]">person_add</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">{t('register.title')}</h1>
                <p className="font-body-sm text-on-surface-variant mt-1">{t('register.subtitle')}</p>
            </div>

            {/* Register Card */}
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[440px] w-full">
                {error && (
                    <div role="alert" className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">error</span>
                        {error}
                    </div>
                )}

                <form onSubmit={handleRegister} className="flex flex-col gap-4">
                    <div className="flex flex-col gap-1.5">
                        <label htmlFor="reg-name" className="font-label-sm text-on-surface-variant font-semibold">{t('register.fullName')}</label>
                        <input
                            id="reg-name"
                            type="text"
                            autoComplete="name"
                            name="fullName"
                            placeholder={t('register.fullNamePlaceholder')}
                            value={formData.fullName}
                            onChange={handleChange}
                            className={inputCls}
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label htmlFor="reg-email" className="font-label-sm text-on-surface-variant font-semibold">{t('register.email')}</label>
                        <input
                            id="reg-email"
                            type="email"
                            autoComplete="email"
                            name="email"
                            placeholder="name@example.com"
                            value={formData.email}
                            onChange={handleChange}
                            className={inputCls}
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label htmlFor="reg-password" className="font-label-sm text-on-surface-variant font-semibold">{t('register.password')}</label>
                        <PasswordInput
                            id="reg-password"
                            name="password"
                            autoComplete="new-password"
                            placeholder={t('register.passwordPlaceholder')}
                            value={formData.password}
                            onChange={setField('password')}
                            className={inputCls}
                        />
                        <PasswordStrengthMeter password={formData.password} />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label htmlFor="reg-confirm" className="font-label-sm text-on-surface-variant font-semibold">{t('register.confirm')}</label>
                        <PasswordInput
                            id="reg-confirm"
                            name="passwordConfirm"
                            autoComplete="new-password"
                            placeholder={t('register.confirmPlaceholder')}
                            value={formData.passwordConfirm}
                            onChange={setField('passwordConfirm')}
                            className={inputCls}
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full h-12 mt-3 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed lv-pill"
                    >
                        <span className="material-symbols-outlined text-[18px]">how_to_reg</span>
                        {loading ? t('register.submitting') : t('register.submit')}
                    </button>
                </form>

                <div className="mt-4">
                    <GoogleSignInButton text="signup_with" onCredential={handleGoogle} disabled={loading} />
                </div>

                <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                    <p className="text-sm text-on-surface-variant">
                        {t('register.haveAccount')}{' '}
                        <Link to="/login" className="text-secondary font-bold hover:underline">
                            {t('register.signIn')}
                        </Link>
                    </p>
                </div>
            </div>
        </AuthShell>
    );
}