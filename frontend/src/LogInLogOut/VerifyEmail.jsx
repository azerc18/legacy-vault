import AuthShell from '../Common/AuthShell.jsx';
import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '../api/authApi.js';
import { getErrorMessage, getErrorCode } from '../api/http.js';
import LanguageSwitch from '../Common/LanguageSwitch.jsx';
import OtpInput from '../Common/OtpInput.jsx';

const RESEND_SECONDS = 60;
const input = 'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary';

export default function VerifyEmail() {
    const { t } = useTranslation('auth');
    const location = useLocation();
    const navigate = useNavigate();
    // fromLogin: đăng nhập bằng tài khoản chưa xác minh -> BE KHÔNG tự gửi mã, trang này phải gửi.
    // Từ Register sang: BE đã gửi mã lúc đăng ký.
    const fromLogin = !!location.state?.fromLogin;
    const [email, setEmail] = useState(location.state?.email || '');
    const [otp, setOtp] = useState('');
    const [error, setError] = useState('');
    // Lưu khoá thay vì câu đã dịch -> đổi ngôn ngữ thì câu thông báo đổi theo
    const [info, setInfo] = useState(email && !fromLogin ? 'sentTo' : '');
    const [loading, setLoading] = useState(false);
    const [cooldown, setCooldown] = useState(email && !fromLogin ? RESEND_SECONDS : 0);
    const autoSent = useRef(false);

    // Đếm ngược cho nút "Gửi lại mã"
    useEffect(() => {
        if (cooldown <= 0) return;
        const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(timer);
    }, [cooldown]);

    const sendCode = async (infoKey) => {
        if (!email.trim()) return setError(t('verify.errors.emailFirst'));
        setError('');
        try {
            await authApi.sendOtp(email.trim());
            setInfo(infoKey);
            setCooldown(RESEND_SECONDS);
        } catch (err) {
            // BE báo phải chờ bao lâu nữa -> khoá nút đúng khoảng đó
            const wait = err.response?.data?.data?.retryAfterSeconds;
            if (getErrorCode(err) === 'TOO_MANY_OTP_REQUESTS' && wait) setCooldown(Number(wait));
            setError(getErrorMessage(err, t('verify.errors.resendFailed')));
        }
    };

    // Từ Login sang: tự gửi mã 1 lần (ref chặn StrictMode gọi 2 lần)
    useEffect(() => {
        if (fromLogin && email && !autoSent.current) {
            autoSent.current = true;
            sendCode('sentTo');
        }
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

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

    return (
        <AuthShell>
            <LanguageSwitch className="absolute top-4 right-4" />
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                <h2 className="font-headline-md text-on-surface font-bold mb-2">{t('verify.title')}</h2>
                {info && <p role="status" className="text-sm text-on-surface-variant mb-4">{t(`verify.${info}`, { email })}</p>}
                {error && <div role="alert" className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <input type="email" autoComplete="email" aria-label={t('login.email')}
                           placeholder="name@example.com" value={email}
                           onChange={(e) => setEmail(e.target.value)} className={input} />
                    <OtpInput label={t('verify.codePlaceholder')} value={otp} onChange={setOtp} />
                    <button type="submit" disabled={loading}
                            className="w-full h-12 rounded-xl bg-primary text-on-primary font-bold disabled:opacity-60 lv-pill">
                        {loading ? t('verify.submitting') : t('verify.submit')}
                    </button>
                </form>

                <div className="mt-4 flex justify-between text-sm">
                    <button type="button" onClick={() => sendCode('resent')} disabled={cooldown > 0}
                            className="text-secondary font-semibold disabled:text-outline disabled:cursor-not-allowed">
                        {cooldown > 0 ? t('reset.resendIn', { seconds: cooldown }) : t('verify.resend')}
                    </button>
                    <Link to="/login" className="text-secondary font-semibold">{t('backToSignIn')}</Link>
                </div>
            </div>
        </AuthShell>
    );
}
