import React, { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { authApi } from '../api/authApi.js';
import { getErrorMessage } from '../api/http.js';
import PasswordStrengthMeter from '../Common/PasswordStrengthMeter.jsx';
import { isPasswordValid } from '../Common/passwordPolicy.js';
import {useTranslation} from "react-i18next";

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

    // Đã đăng nhập -> về trang chủ người dùng
    if (getUser()) return <Navigate to="/home" replace />;


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
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col justify-center items-center px-4 py-8">
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
                    <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">error</span>
                        {error}
                    </div>
                )}

                <form onSubmit={handleRegister} className="flex flex-col gap-4">
                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">{t('register.fullName')}</label>
                        <input
                            type="text"
                            name="fullName"
                            placeholder={t('register.fullNamePlaceholder')}
                            value={formData.fullName}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">{t('register.email')}</label>
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
                        <label className="font-label-sm text-on-surface-variant font-semibold">{t('register.password')}</label>
                        <input
                            type="password"
                            name="password"
                            placeholder={t('register.passwordPlaceholder')}
                            value={formData.password}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                        <PasswordStrengthMeter password={formData.password} />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">{t('register.confirm')}</label>
                        <input
                            type="password"
                            name="passwordConfirm"
                            placeholder={t('register.confirmPlaceholder')}
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
                        {loading ? t('register.submitting') : t('register.submit')}
                    </button>
                </form>

                <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                    <p className="text-sm text-on-surface-variant">
                        {t('register.haveAccount')}{' '}
                        <Link to="/login" className="text-secondary font-bold hover:underline">
                            {t('register.signIn')}
                        </Link>
                    </p>
                </div>
            </div>
        </div>
    );
}