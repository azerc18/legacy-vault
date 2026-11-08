// Đăng xuất khi người dùng không thao tác quá thời gian đã chọn ở trang Hồ sơ (giống "vault timeout" của trình quản lý mật khẩu).
// Không hiển thị gì; gắn 1 lần trong App.
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getToken, logout } from '../auth/auth.js';
import { getIdleMinutes, getLastActivity, touchActivity, IDLE_EVENT } from './idleTimeout.js';

const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'];

export default function IdleLogout() {
    const navigate = useNavigate();
    const { t } = useTranslation('profile');

    useEffect(() => {
        let minutes = getIdleMinutes();
        const onActivity = () => touchActivity();
        const onChange = () => { minutes = getIdleMinutes(); };

        touchActivity(true);
        ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, onActivity, { passive: true }));
        window.addEventListener(IDLE_EVENT, onChange);
        window.addEventListener('storage', onChange);

        const timer = setInterval(() => {
            if (!minutes || !getToken()) return;
            if (Date.now() - getLastActivity() >= minutes * 60_000) {
                logout();
                navigate('/login', { replace: true, state: { message: t('prefs.idleSignedOut') } });
            }
        }, 15_000);

        return () => {
            clearInterval(timer);
            ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, onActivity));
            window.removeEventListener(IDLE_EVENT, onChange);
            window.removeEventListener('storage', onChange);
        };
    }, [navigate, t]);

    return null;
}
