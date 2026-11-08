// Tự đăng xuất khi không thao tác (trang Hồ sơ > Tuỳ chọn). Lưu ở trình duyệt, theo từng máy.
// lv_idle_minutes: 0 = tắt; lv_last_activity: mốc thao tác gần nhất, dùng chung giữa các tab.
const KEY = 'lv_idle_minutes';
const ACTIVITY_KEY = 'lv_last_activity';
export const IDLE_EVENT = 'lv:idle-change';
export const IDLE_OPTIONS = [0, 15, 30, 60];

export function getIdleMinutes() {
    try {
        const v = Number(localStorage.getItem(KEY));
        return IDLE_OPTIONS.includes(v) ? v : 0;
    } catch {
        return 0;
    }
}

export function setIdleMinutes(minutes) {
    try { localStorage.setItem(KEY, String(minutes)); } catch { /* bỏ qua */ }
    touchActivity(true);
    window.dispatchEvent(new CustomEvent(IDLE_EVENT, { detail: minutes }));
}

let lastWrite = 0;
// Ghi mốc thao tác, tối đa 1 lần / 5 giây để không ghi localStorage liên tục khi rê chuột
export function touchActivity(force = false) {
    const now = Date.now();
    if (!force && now - lastWrite < 5000) return;
    lastWrite = now;
    try { localStorage.setItem(ACTIVITY_KEY, String(now)); } catch { /* bỏ qua */ }
}

export function getLastActivity() {
    try {
        return Number(localStorage.getItem(ACTIVITY_KEY)) || Date.now();
    } catch {
        return Date.now();
    }
}
