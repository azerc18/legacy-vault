// Nút "Đăng nhập bằng Google" (Google Identity Services).
// Client ID lấy từ BE (/api/auth/google/config) lúc chạy; BE chưa cấu hình thì component không hiện gì.
// onCredential(idToken) nhận ID token để gửi lên POST /api/auth/google.
import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { authApi } from '../api/authApi.js';
import { useTheme } from './theme.js';

const GIS_SRC = 'https://accounts.google.com/gsi/client';
let configPromise = null;                    // hỏi BE 1 lần cho cả phiên
let loaded = { lang: null, promise: null };  // script Google đang nạp theo ngôn ngữ nào

const loadConfig = () => {
    configPromise ??= authApi.googleConfig().catch(() => ({ enabled: false }));
    return configPromise;
};

// Chữ trên nút do Google vẽ trong iframe và lấy ngôn ngữ theo tham số ?hl= của script
// (tuỳ chọn locale của renderButton thường bị bỏ qua). Đổi ngôn ngữ -> gỡ script cũ, nạp lại với hl mới.
const loadScript = (lang) => {
    if (loaded.lang === lang && loaded.promise) return loaded.promise;

    document.querySelectorAll('script[data-lv-gis]').forEach((s) => s.remove());
    try { delete window.google; } catch { window.google = undefined; }

    const promise = new Promise((resolve, reject) => {
        const s = document.createElement('script');
        s.src = `${GIS_SRC}?hl=${encodeURIComponent(lang)}`;
        s.async = true;
        s.defer = true;
        s.dataset.lvGis = '1';
        s.onload = () => resolve();
        s.onerror = () => {
            if (loaded.promise === promise) loaded = { lang: null, promise: null };
            reject(new Error('Google script failed to load'));
        };
        document.head.appendChild(s);
    });
    loaded = { lang, promise };
    return promise;
};

/**
 * @param text  'signin_with' (trang Đăng nhập) | 'signup_with' (trang Đăng ký)
 */
export default function GoogleSignInButton({ onCredential, text = 'signin_with', disabled = false }) {
    const { t, i18n } = useTranslation('auth');
    const lang = i18n.resolvedLanguage || 'en';
    const [theme] = useTheme();
    const boxRef = useRef(null);
    const callbackRef = useRef(onCredential);
    const [clientId, setClientId] = useState(null);
    const [readyLang, setReadyLang] = useState(null);   // script đã nạp xong cho ngôn ngữ nào
    const [failed, setFailed] = useState(false);

    // Luôn gọi bản onCredential mới nhất (Google chỉ nhận callback lúc initialize)
    useEffect(() => { callbackRef.current = onCredential; }, [onCredential]);

    // 1) Hỏi BE có bật Google không
    useEffect(() => {
        let alive = true;
        loadConfig().then((cfg) => {
            if (alive && cfg?.enabled && cfg.clientId) setClientId(cfg.clientId);
        });
        return () => { alive = false; };
    }, []);

    // 2) Nạp script Google theo ngôn ngữ đang chọn (đổi EN/VI thì nạp lại)
    useEffect(() => {
        if (!clientId) return;
        let alive = true;
        loadScript(lang)
            .then(() => { if (alive) { setFailed(false); setReadyLang(lang); } })
            .catch(() => { if (alive) setFailed(true); });
        return () => { alive = false; };
    }, [clientId, lang]);

    // 3) Vẽ nút (vẽ lại khi đổi ngôn ngữ / nền sáng-tối)
    useEffect(() => {
        const gid = window.google?.accounts?.id;
        if (!clientId || readyLang !== lang || !boxRef.current || !gid) return;
        gid.initialize({
            client_id: clientId,
            callback: (res) => res?.credential && callbackRef.current?.(res.credential),
            ux_mode: 'popup',
            auto_select: false,
            cancel_on_tap_outside: true,
        });
        boxRef.current.innerHTML = '';
        gid.renderButton(boxRef.current, {
            type: 'standard',
            theme: theme === 'dark' ? 'filled_black' : 'outline',
            size: 'large',
            shape: 'pill',
            text,
            logo_alignment: 'center',
            width: Math.min(400, Math.max(200, boxRef.current.offsetWidth || 356)),
            locale: lang,
        });
    }, [clientId, readyLang, lang, theme, text]);

    if (!clientId) return null;

    return (
        <div className="flex flex-col gap-4">
            <div className="flex items-center gap-3 text-xs text-outline">
                <span className="h-px flex-1 bg-outline-variant/60" />
                {t('google.or')}
                <span className="h-px flex-1 bg-outline-variant/60" />
            </div>
            {failed
                ? <p className="text-xs text-center text-outline">{t('google.unavailable')}</p>
                : <div ref={boxRef}
                    // Iframe của Google luôn là "light"; trang đang "dark" thì Chrome tô nền iframe màu trắng.
                    // Đặt vùng chứa về light để khớp -> nền iframe trong suốt.
                       style={{ colorScheme: 'light' }}
                       className={`w-full flex justify-center min-h-[44px] ${disabled ? 'pointer-events-none opacity-60' : ''}`}
                       aria-busy={disabled} />}
        </div>
    );
}
