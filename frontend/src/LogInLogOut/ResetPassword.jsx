import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';

const RESEND_SECONDS = 60;

export default function ResetPassword() {
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

    // Countdown for the "Resend code" button
    useEffect(() => {
        if (cooldown <= 0) return;
        const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(timer);
    }, [cooldown]);

    const handleResend = async () => {
        if (!email.trim()) {
            setError('Please enter your email address.');
            return;
        }
        setError('');
        try {
            await authApi.forgotPassword(email.trim());
            setInfo('If this email is registered, a new code has been sent.');
            setCooldown(RESEND_SECONDS);
        } catch (err) {
            setError(getErrorMessage(err, 'Could not resend code.'));
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setInfo('');

        if (!email.trim() || !otp || !password || !passwordConfirm) {
            setError('Please fill in all fields.');
            return;
        }
        if (!/^\d{6}$/.test(otp)) {
            setError('The code must be exactly 6 digits.');
            return;
        }
        if (password.length < 6) {
            setError('Password must be at least 6 characters.');
            return;
        }
        if (password !== passwordConfirm) {
            setError('Passwords do not match.');
            return;
        }

        setError('');
        setLoading(true);
        try {
            await authApi.resetPassword(email.trim(), otp, password, passwordConfirm);
            setDone(true);
        } catch (err) {
            setError(getErrorMessage(err, 'Invalid or expired code.'));
        } finally {
            setLoading(false);
        }
    };

    const inputClass =
        'w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all';

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col justify-center items-center px-4 py-8">
            {/* Brand Header */}
            <div className="mb-6 text-center">
                <div className="w-12 h-12 rounded-2xl bg-primary text-on-primary flex items-center justify-center shadow-sm mx-auto mb-3">
                    <span className="material-symbols-outlined text-[28px]">password</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">LegacyVault</h1>
            </div>

            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                {done ? (
                    /* Success state */
                    <div className="text-center">
                        <div className="w-14 h-14 rounded-full bg-success-container text-on-success-container flex items-center justify-center mx-auto mb-4">
                            <span className="material-symbols-outlined text-[30px]">check_circle</span>
                        </div>
                        <h2 className="font-headline-md text-on-surface font-bold mb-2">Password updated</h2>
                        <p className="text-sm text-on-surface-variant mb-6">
                            Your password has been reset successfully. You can now sign in with your new password.
                        </p>
                        <Link
                            to="/login"
                            className="w-full h-12 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center"
                        >
                            Go to sign in
                        </Link>
                    </div>
                ) : (
                    /* Form state */
                    <>
                        <h2 className="font-headline-md text-on-surface font-bold mb-2">Reset your password</h2>
                        <p className="text-sm text-on-surface-variant mb-6">
                            Enter the 6-digit code we sent to your email, then choose a new password.
                        </p>

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
                                <label className="font-label-sm text-on-surface-variant font-semibold">Account email</label>
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
                                    <label className="font-label-sm text-on-surface-variant font-semibold">Verification code (OTP)</label>
                                    <button
                                        type="button"
                                        onClick={handleResend}
                                        disabled={cooldown > 0}
                                        className="text-xs text-secondary font-semibold hover:text-primary disabled:text-outline disabled:cursor-not-allowed"
                                    >
                                        {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
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
                                <label className="font-label-sm text-on-surface-variant font-semibold">New password</label>
                                <input
                                    type="password"
                                    placeholder="At least 6 characters"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    className={inputClass}
                                />
                            </div>

                            <div className="flex flex-col gap-1.5">
                                <label className="font-label-sm text-on-surface-variant font-semibold">Confirm new password</label>
                                <input
                                    type="password"
                                    placeholder="Re-enter new password"
                                    value={passwordConfirm}
                                    onChange={(e) => setPasswordConfirm(e.target.value)}
                                    className={inputClass}
                                />
                            </div>

                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full h-12 mt-2 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2 disabled:opacity-50"
                            >
                                <span className="material-symbols-outlined text-[18px]">lock_reset</span>
                                {loading ? 'Updating...' : 'Reset password'}
                            </button>
                        </form>

                        <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                            <Link to="/login" className="text-sm text-secondary font-bold hover:underline">
                                Back to sign in
                            </Link>
                        </div>

                        {/* Demo hint (remove when the real API is integrated) */}
                        <p className="mt-4 text-xs text-outline text-center">
                            Demo: the valid code is <strong>123456</strong>.
                        </p>
                    </>
                )}
            </div>
        </div>
    );
}