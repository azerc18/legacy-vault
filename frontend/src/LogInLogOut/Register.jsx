import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';

export default function Register() {
    const [formData, setFormData] = useState({
        fullName: '',
        email: '',
        password: '',
        passwordConfirm: ''
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    const handleRegister = async (e) => {
        e.preventDefault();
        const { fullName, email, password, passwordConfirm } = formData;

        if (!fullName || !email || !password || !passwordConfirm) {
            setError('Please fill in all fields.');
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
        await authApi.register({ fullName, email: email.trim(), password, passwordConfirm });
        // BE đã tự gửi OTP -> sang trang xác thực
        navigate('/verify-email', { state: { email: email.trim() } });
    } catch (err) {
        setError(getErrorMessage(err, 'Registration failed.'));
    } finally {
        setLoading(false);
    }
};

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col justify-center items-center px-4 py-8">
            {/* Brand Header */}
            <div className="mb-6 text-center">
                <div className="w-12 h-12 rounded-2xl bg-primary text-on-primary flex items-center justify-center shadow-sm mx-auto mb-3">
                    <span className="material-symbols-outlined text-[28px]">person_add</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">Create a LegacyVault account</h1>
                <p className="font-body-sm text-on-surface-variant mt-1">Sign up for a new account with Owner permissions</p>
            </div>

            {/* Register Card */}
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[440px] w-full">
                {error && (
                    <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">error</span>
                        {error}
                    </div>
                )}

                <form onSubmit={handleRegister} className="flex flex-col gap-4">
                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Full name</label>
                        <input
                            type="text"
                            name="fullName"
                            placeholder="John Doe"
                            value={formData.fullName}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Email address</label>
                        <input
                            type="email"
                            name="email"
                            placeholder="name@example.com"
                            value={formData.email}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Password</label>
                        <input
                            type="password"
                            name="password"
                            placeholder="At least 6 characters"
                            value={formData.password}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Confirm password</label>
                        <input
                            type="password"
                            name="passwordConfirm"
                            placeholder="Re-enter password"
                            value={formData.passwordConfirm}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full h-12 mt-3 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                        <span className="material-symbols-outlined text-[18px]">how_to_reg</span>
                        {loading ? 'Signing up...' : 'Sign up'}
                    </button>
                </form>

                <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                    <p className="text-sm text-on-surface-variant">
                        Already have an account?{' '}
                        <Link to="/login" className="text-secondary font-bold hover:underline">
                            Sign in now
                        </Link>
                    </p>
                </div>
            </div>
        </div>
    );
}