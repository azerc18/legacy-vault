import AuthShell from '../Common/AuthShell.jsx';
import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';
import LanguageSwitch from '../Common/LanguageSwitch.jsx';

const input = 'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary';

export default function VerifyEmail() {
    const { t } = useTranslation('auth');
    const location = useLocation();
    const navigate = useNavigate();
    const [email, setEmail] = useState(location.state?.email || '');
    const [otp, setOtp] = useState('');
    const [error, setError] = useState('');
    // Lưu khoá thay vì câu đã dịch -> đổi ngôn ngữ thì câu thông báo đổi theo
    const [info, setInfo] = useState(email ? 'sentTo' : '');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!/^\d{6}$/.test(otp)) return setError(t('verify.errors.otpFormat'));
        setError(''); setLoading(true);
        try {
            await authApi.verifyEmail(email.trim(), otp);
            navigate('/login', { state: { message: t('verify.verified') } });
        } catch (err) {
            setError(getErrorMessage(err, t('verify.errors.failed')));
        } finally {
            setLoading(false);
        }
    };

    const handleResend = async () => {
        if (!email) return setError(t('verify.errors.emailFirst'));
        setError('');
        try {
            await authApi.sendOtp(email.trim());
            setInfo('resent');
        } catch (err) {
            setError(getErrorMessage(err, t('verify.errors.resendFailed')));
        }
    };

    return (
        <AuthShell>
            <LanguageSwitch className="absolute top-4 right-4" />
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                <h2 className="font-headline-md text-on-surface font-bold mb-2">{t('verify.title')}</h2>
                {info && <p className="text-sm text-on-surface-variant mb-4">{t(`verify.${info}`, { email })}</p>}
                {error && <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <input type="email" placeholder="name@example.com" value={email}
                           onChange={(e) => setEmail(e.target.value)} className={input} />
                    <input inputMode="numeric" maxLength={6} placeholder={t('verify.codePlaceholder')} value={otp}
                           onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                           className={`${input} tracking-[0.5em] text-center`} />
                    <button type="submit" disabled={loading}
                            className="w-full h-12 rounded-xl bg-primary text-on-primary font-bold disabled:opacity-60 lv-pill">
                        {loading ? t('verify.submitting') : t('verify.submit')}
                    </button>
                </form>

                <div className="mt-4 flex justify-between text-sm">
                    <button type="button" onClick={handleResend} className="text-secondary font-semibold">{t('verify.resend')}</button>
                    <Link to="/login" className="text-secondary font-semibold">{t('backToSignIn')}</Link>
                </div>
            </div>
        </AuthShell>
    );
}