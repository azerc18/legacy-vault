import AuthShell from '../Common/AuthShell.jsx';
import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';
import PasswordStrengthMeter from '../Common/PasswordStrengthMeter.jsx';
import { isPasswordValid } from '../Common/passwordPolicy.js';
import LanguageSwitch from '../Common/LanguageSwitch.jsx';

const RESEND_SECONDS = 60;

const inputClass =
    'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all';

export default function ResetPassword() {
    const { t } = useTranslation('auth');
    const location = useLocation();
    const [email, setEmail] = useState(location.state?.email || '');
    const [otp, setOtp] = useState('');
    const [password, setPassword] = useState('');
    const [passwordConfirm, setPasswordConfirm] = useState('');
    const [error, setError] = useState('');
    const [info, setInfo] = useState('');
    const [loading, setLoading] = useState(false);
    const [done, setDone] = useState(false);
    const [cooldown, setCooldown] = useState(RESEND_SECONDS);

    // Đếm ngược cho nút "Gửi lại mã"
    useEffect(() => {
        if (cooldown <= 0) return;
        const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(timer);
    }, [cooldown]);

    const handleResend = async () => {
        if (!email.trim()) {
            setError(t('reset.errors.emailRequired'));
            return;
        }
        setError('');
        try {
            await authApi.forgotPassword(email.trim());
            setInfo(t('reset.resent'));
            setCooldown(RESEND_SECONDS);
        } catch (err) {
            setError(getErrorMessage(err, t('reset.errors.resendFailed')));
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setInfo('');

        if (!email.trim() || !otp || !password || !passwordConfirm) {
            setError(t('reset.errors.required'));
            return;
        }
        if (!/^\d{6}$/.test(otp)) {
            setError(t('reset.errors.otpFormat'));
            return;
        }
        if (!isPasswordValid(password)) {
            setError(t('reset.errors.weakPassword'));
            return;
        }
        if (password !== passwordConfirm) {
            setError(t('reset.errors.mismatch'));
            return;
        }

        setError('');
        setLoading(true);
        try {
            await authApi.resetPassword(email.trim(), otp, password, passwordConfirm);
            setDone(true);
        } catch (err) {
            setError(getErrorMessage(err, t('reset.errors.failed')));
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
                    <span className="material-symbols-outlined text-[28px]">password</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">LegacyVault</h1>
            </div>

            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                {done ? (
                    /* Thành công */
                    <div className="text-center">
                        <div className="w-14 h-14 rounded-full bg-success-container text-on-success-container flex items-center justify-center mx-auto mb-4">
                            <span className="material-symbols-outlined text-[30px]">check_circle</span>
                        </div>
                        <h2 className="font-headline-md text-on-surface font-bold mb-2">{t('reset.doneTitle')}</h2>
                        <p className="text-sm text-on-surface-variant mb-6">{t('reset.doneText')}</p>
                        <Link
                            to="/login"
                            className="w-full h-12 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center lv-pill"
                        >
                            {t('reset.goToSignIn')}
                        </Link>
                    </div>
                ) : (
                    /* Form */
                    <>
                        <h2 className="font-headline-md text-on-surface font-bold mb-2">{t('reset.title')}</h2>
                        <p className="text-sm text-on-surface-variant mb-6">{t('reset.subtitle')}</p>

                        {error && (
                            <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                                <span className="material-symbols-outlined text-[18px]">error</span>
                                {error}
                            </div>
                        )}
                        {info && (
                            <div className="mb-4 p-3 rounded-lg bg-success-container text-on-success-container text-sm flex items-center gap-2">
                                <span className="material-symbols-outlined text-[18px]">mark_email_read</span>
                                {info}
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                            <div className="flex flex-col gap-1.5">
                                <label className="font-label-sm text-on-surface-variant font-semibold">{t('reset.email')}</label>
                                <input
                                    type="email"
                                    placeholder="name@example.com"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    className={inputClass}
                                />
                            </div>

                            <div className="flex flex-col gap-1.5">
                                <div className="flex justify-between items-center">
                                    <label className="font-label-sm text-on-surface-variant font-semibold">{t('reset.otp')}</label>
                                    <button
                                        type="button"
                                        onClick={handleResend}
                                        disabled={cooldown > 0}
                                        className="text-xs text-secondary font-semibold hover:text-primary disabled:text-outline disabled:cursor-not-allowed"
                                    >
                                        {cooldown > 0 ? t('reset.resendIn', { seconds: cooldown }) : t('reset.resend')}
                                    </button>
                                </div>
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    maxLength={6}
                                    placeholder="______"
                                    value={otp}
                                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                    className={`${inputClass} text-center text-lg tracking-[0.5em]`}
                                />
                            </div>

                            <div className="flex flex-col gap-1.5">
                                <label className="font-label-sm text-on-surface-variant font-semibold">{t('reset.newPassword')}</label>
                                <input
                                    type="password"
                                    placeholder={t('reset.newPasswordPlaceholder')}
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    className={inputClass}
                                />
                                <PasswordStrengthMeter password={password} />
                            </div>

                            <div className="flex flex-col gap-1.5">
                                <label className="font-label-sm text-on-surface-variant font-semibold">{t('reset.confirm')}</label>
                                <input
                                    type="password"
                                    placeholder={t('reset.confirmPlaceholder')}
                                    value={passwordConfirm}
                                    onChange={(e) => setPasswordConfirm(e.target.value)}
                                    className={inputClass}
                                />
                            </div>

                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full h-12 mt-2 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2 disabled:opacity-50 lv-pill"
                            >
                                <span className="material-symbols-outlined text-[18px]">lock_reset</span>
                                {loading ? t('reset.submitting') : t('reset.submit')}
                            </button>
                        </form>

                        <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                            <Link to="/login" className="text-sm text-secondary font-bold hover:underline">
                                {t('backToSignIn')}
                            </Link>
                        </div>
                    </>
                )}
            </div>
        </AuthShell>
    );
}