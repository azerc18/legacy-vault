import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';

export default function ForgotPassword() {
    const [email, setEmail] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [sent, setSent] = useState(false);
    const navigate = useNavigate();
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!email.trim()) {
            setError('Please enter your email address.');
            return;
        }
        setError('');
        setLoading(true);
        try {
            await authApi.forgotPassword(email.trim());
            navigate('/reset-password', { state: { email: email.trim() } });
        } catch (err) {
            setError(getErrorMessage(err, 'Something went wrong. Please try again.'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col justify-center items-center px-4">
            {/* Brand Header */}
            <div className="mb-8 text-center">
                <div className="w-12 h-12 rounded-2xl bg-primary text-on-primary flex items-center justify-center shadow-sm mx-auto mb-3">
                    <span className="material-symbols-outlined text-[28px]">lock_reset</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">LegacyVault</h1>
            </div>

            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[420px] w-full">
                {sent ? (
                    /* Success state */
                    <div className="text-center">
                        <div className="w-14 h-14 rounded-full bg-success-container text-on-success-container flex items-center justify-center mx-auto mb-4">
                            <span className="material-symbols-outlined text-[30px]">mark_email_read</span>
                        </div>
                        <h2 className="font-headline-md text-on-surface font-bold mb-2">Check your email</h2>
                        <p className="text-sm text-on-surface-variant mb-6">
                            If an account exists for <strong>{email}</strong>, we have sent a link to reset your password.
                        </p>
                        <Link
                            to="/login"
                            className="w-full h-12 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center"
                        >
                            Back to sign in
                        </Link>
                    </div>
                ) : (
                    /* Form state */
                    <>
                        <h2 className="font-headline-md text-on-surface font-bold mb-2">Forgot your password?</h2>
                        <p className="text-sm text-on-surface-variant mb-6">
                            Enter your account email and we will send you a link to reset your password.
                        </p>

                        {error && (
                            <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                                <span className="material-symbols-outlined text-[18px]">error</span>
                                {error}
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
                                    className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                                />
                            </div>

                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full h-12 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2 disabled:opacity-50"
                            >
                                <span className="material-symbols-outlined text-[18px]">send</span>
                                {loading ? 'Sending...' : 'Send reset link'}
                            </button>
                        </form>

                        <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                            <Link to="/login" className="text-sm text-secondary font-bold hover:underline">
                                Back to sign in
                            </Link>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}