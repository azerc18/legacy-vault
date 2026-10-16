import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';

const input = 'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary';

export default function VerifyEmail() {
    const location = useLocation();
    const navigate = useNavigate();
    const [email, setEmail] = useState(location.state?.email || '');
    const [otp, setOtp] = useState('');
    const [error, setError] = useState('');
    const [info, setInfo] = useState(email ? `We sent a 6-digit code to ${email}.` : '');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!/^\d{6}$/.test(otp)) return setError('OTP must be exactly 6 digits.');
        setError(''); setLoading(true);
        try {
            await authApi.verifyEmail(email.trim(), otp);
            navigate('/login', { state: { message: 'Email verified. You can sign in now.' } });
        } catch (err) {
            setError(getErrorMessage(err, 'Verification failed.'));
        } finally {
            setLoading(false);
        }
    };

    const handleResend = async () => {
        if (!email) return setError('Please enter your email first.');
        setError('');
        try {
            await authApi.sendOtp(email.trim());
            setInfo('A new code has been sent.');
        } catch (err) {
            setError(getErrorMessage(err, 'Could not resend code.'));
        }
    };

    return (
        <div className="bg-background min-h-screen flex flex-col justify-center items-center px-4">
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                <h2 className="font-headline-md text-on-surface font-bold mb-2">Verify your email</h2>
                {info && <p className="text-sm text-on-surface-variant mb-4">{info}</p>}
                {error && <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <input type="email" placeholder="name@example.com" value={email}
                           onChange={(e) => setEmail(e.target.value)} className={input} />
                    <input inputMode="numeric" maxLength={6} placeholder="6-digit code" value={otp}
                           onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                           className={`${input} tracking-[0.5em] text-center`} />
                    <button type="submit" disabled={loading}
                            className="w-full h-12 rounded-xl bg-primary text-on-primary font-bold disabled:opacity-60">
                        {loading ? 'Verifying...' : 'Verify'}
                    </button>
                </form>

                <div className="mt-4 flex justify-between text-sm">
                    <button onClick={handleResend} className="text-secondary font-semibold">Resend code</button>
                    <Link to="/login" className="text-secondary font-semibold">Back to sign in</Link>
                </div>
            </div>
        </div>
    );
}