// Ô kiểm tra bảo mật Cloudflare Turnstile ở Đăng ký, Quên mật khẩu, Đăng nhập (sau nhiều lần sai).
// Site key lấy từ BE (/api/auth/captcha/config) lúc chạy; BE tắt CAPTCHA thì component không hiện gì.
// Token chỉ dùng được một lần: sau mỗi lần gửi form, tăng resetSignal để lấy token mới.
import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { authApi } from '../api/authApi.js';
import { useTheme } from './theme.js';

const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
let configPromise = null;   // hỏi BE 1 lần cho cả phiên (hỏi lại khi BE báo lỗi CAPTCHA)
let scriptPromise = null;
const listeners = new Set();

let currentConfig = null;   // kết quả lần hỏi gần nhất
const loadConfig = () => {
    configPromise ??= authApi.captchaConfig()
        .catch(() => ({ enabled: false }))
        .then((cfg) => { currentConfig = cfg; return cfg; });
    return configPromise;
};

const CAPTCHA_ERROR_CODES = new Set(['CAPTCHA_REQUIRED', 'CAPTCHA_INVALID', 'CAPTCHA_UNAVAILABLE']);

/**
 * BE vừa đòi CAPTCHA mà trang có thể đang giữ cấu hình cũ (tab mở trước khi Admin bật APP_CAPTCHA_ENABLED):
 * hỏi lại BE và vẽ widget ngay, không bắt người dùng tải lại trang.
 */
export function refreshCaptchaConfigOnError(err) {
    const body = err?.response?.data;
    if (!CAPTCHA_ERROR_CODES.has(body?.errorCode) && !body?.data?.captchaRequired) return;
    if (currentConfig?.enabled && currentConfig.siteKey) return;   // đã có cấu hình bật: widget đang hiện, khỏi hỏi lại
    configPromise = null;
    loadConfig().then((cfg) => listeners.forEach((fn) => fn(cfg)));
}

const loadScript = () => {
    if (window.turnstile) return Promise.resolve();
    scriptPromise ??= new Promise((resolve, reject) => {
        const s = document.createElement('script');
        s.src = SCRIPT_SRC;
        s.async = true;
        s.defer = true;
        s.onload = () => resolve();
        s.onerror = () => { scriptPromise = null; reject(new Error('Turnstile script failed to load')); };
        document.head.appendChild(s);
    });
    return scriptPromise;
};

/** { enabled, siteKey, loginAfterFailures } hoặc null khi chưa hỏi xong. */
export function useCaptchaConfig() {
    const [config, setConfig] = useState(null);
    useEffect(() => {
        let alive = true;
        const apply = (cfg) => { if (alive) setConfig(cfg?.enabled && cfg.siteKey ? cfg : { enabled: false }); };
        loadConfig().then(apply);
        listeners.add(apply);
        return () => { alive = false; listeners.delete(apply); };
    }, []);
    return config;
}

/**
 * @param onToken      (token | null) => void; null khi token hết hạn / lỗi / vừa reset
 * @param resetSignal  đổi giá trị -> xoá token cũ, widget tự lấy token mới
 * @param show         false thì không vẽ (vd. Đăng nhập chỉ hiện sau nhiều lần sai)
 */
export default function TurnstileWidget({ onToken, resetSignal = 0, show = true }) {
    const { t, i18n } = useTranslation('auth');
    const lang = (i18n.resolvedLanguage || 'vi').slice(0, 2) === 'en' ? 'en' : 'vi';
    const [theme] = useTheme();
    const config = useCaptchaConfig();
    const boxRef = useRef(null);
    const widgetRef = useRef(null);
    const tokenRef = useRef(onToken);
    const [failed, setFailed] = useState(false);

    useEffect(() => { tokenRef.current = onToken; }, [onToken]);

    const enabled = !!config?.enabled && show;

    // Vẽ widget (vẽ lại khi đổi ngôn ngữ / nền sáng-tối)
    useEffect(() => {
        if (!enabled) return;
        let alive = true;
        loadScript()
            .then(() => {
                if (!alive || !boxRef.current || !window.turnstile) return;
                setFailed(false);
                widgetRef.current = window.turnstile.render(boxRef.current, {
                    sitekey: config.siteKey,
                    theme: theme === 'dark' ? 'dark' : 'light',
                    language: lang,
                    size: 'flexible',
                    callback: (token) => tokenRef.current?.(token),
                    'expired-callback': () => tokenRef.current?.(null),
                    'error-callback': () => { tokenRef.current?.(null); },
                });
            })
            .catch(() => { if (alive) setFailed(true); });
        return () => {
            alive = false;
            if (widgetRef.current != null && window.turnstile) {
                try { window.turnstile.remove(widgetRef.current); } catch { /* widget đã bị gỡ */ }
            }
            widgetRef.current = null;
            tokenRef.current?.(null);
        };
    }, [enabled, config?.siteKey, theme, lang]);

    // Sau mỗi lần gửi: token cũ đã dùng, xin token mới
    useEffect(() => {
        if (!resetSignal || widgetRef.current == null || !window.turnstile) return;
        tokenRef.current?.(null);
        try { window.turnstile.reset(widgetRef.current); } catch { /* bỏ qua */ }
    }, [resetSignal]);

    if (!enabled) return null;

    return (
        <div className="flex flex-col gap-1">
            <div ref={boxRef} className="w-full min-h-[65px]" aria-label={t('captcha.label')} />
            {failed && <p role="alert" className="text-xs text-error">{t('captcha.loadFailed')}</p>}
            <p className="text-[11px] leading-snug text-outline">{t('captcha.notice')}</p>
        </div>
    );
}
