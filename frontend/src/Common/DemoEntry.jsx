import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { saveSession } from '../auth/auth.js';
import { DEMO_USER, DEMO_TOKEN } from './demoMockData.js';

export default function DemoEntry() {
    const { t } = useTranslation('auth');
    const navigate = useNavigate();

    useEffect(() => {
        try {
            saveSession(DEMO_TOKEN, DEMO_USER.email);
            localStorage.setItem('lv_demo_mode', 'true');
        } catch (e) {
            console.error('Demo login error:', e);
        }
        navigate('/owner/vaults', { replace: true });
    }, [navigate]);

    return (
        <div className="min-h-screen bg-[#070b14] text-white flex flex-col items-center justify-center p-4">
            <div className="w-12 h-12 border-4 border-amber-400 border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-sm font-semibold tracking-wide text-amber-300">
                {t('demo.loading', 'Đang khởi tạo phiên dùng thử và chuyển tới Dashboard...')}
            </p>
        </div>
    );
}
