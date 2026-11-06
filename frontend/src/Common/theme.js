// Nền sáng / tối. Lưu lựa chọn ở localStorage (lv_theme = light | dark).
// Chưa chọn thì theo hệ điều hành. Script trong index.html đã đặt class "dark" trước khi React chạy.
import { useEffect, useState } from 'react';

const KEY = 'lv_theme';
const EVENT = 'lv:theme-change';
const systemDark = () => window.matchMedia?.('(prefers-color-scheme: dark)');

export function getStoredTheme() {
    try {
        const v = localStorage.getItem(KEY);
        return v === 'light' || v === 'dark' ? v : null;
    } catch {
        return null;   // localStorage bị chặn (chế độ riêng tư...)
    }
}

export const resolveTheme = () => getStoredTheme() ?? (systemDark()?.matches ? 'dark' : 'light');

export function applyTheme(theme) {
    const root = document.documentElement;
    root.classList.toggle('dark', theme === 'dark');
    root.style.colorScheme = theme;
}

export function setTheme(theme) {
    try { localStorage.setItem(KEY, theme); } catch { /* vẫn đổi được trong phiên này */ }
    applyTheme(theme);
    window.dispatchEvent(new CustomEvent(EVENT, { detail: theme }));
}

// [theme, setTheme]. Đồng bộ giữa các nút trên cùng trang, giữa các tab, và theo hệ điều hành khi chưa chọn.
export function useTheme() {
    const [theme, setState] = useState(resolveTheme);

    useEffect(() => {
        const sync = () => {
            const next = resolveTheme();
            applyTheme(next);
            setState(next);
        };
        const onStorage = (e) => { if (e.key === KEY) sync(); };
        const onSystem = () => { if (!getStoredTheme()) sync(); };
        const media = systemDark();

        window.addEventListener(EVENT, sync);
        window.addEventListener('storage', onStorage);
        media?.addEventListener?.('change', onSystem);
        return () => {
            window.removeEventListener(EVENT, sync);
            window.removeEventListener('storage', onStorage);
            media?.removeEventListener?.('change', onSystem);
        };
    }, []);

    return [theme, setTheme];
}
