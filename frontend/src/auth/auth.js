const USER_KEY = 'lv_user';
const TOKEN_KEY = 'token';

export const ROLES = {
    OWNER: 'OWNER',
    LEGAL_VERIFIER: 'LEGAL_VERIFIER',
};

function decodeJwt(token) {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload));
}

// Gọi sau khi login thành công
export function saveSession(accessToken, email) {
    const { sub, roles = [] } = decodeJwt(accessToken);
    const user = {
        id: sub,
        email,
        name: email.split('@')[0],
        roles,
        role: roles.includes(ROLES.LEGAL_VERIFIER) ? ROLES.LEGAL_VERIFIER : ROLES.OWNER,
    };
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    return user;
}

// Trang đích sau khi đăng nhập theo role: Admin > Legal Verifier > còn lại
export const homeFor = (u) =>
    u?.roles?.includes('ADMIN') ? '/admin'
        : u?.roles?.includes(ROLES.LEGAL_VERIFIER) ? '/verifier/home'
            : '/home';

// Chỉ nhận đường dẫn nội bộ ("/owner/vaults/1"), chặn open redirect: "//evil.com", "https://...",
// và "/\\evil.com" (trình duyệt đổi "\\" thành "/" nên thành "//evil.com"). Kiểm lại bằng URL: phải cùng origin.
export const safeRedirect = (path) => {
    if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//') || path.includes('\\')) return null;
    try {
        const url = new URL(path, window.location.origin);
        if (url.origin !== window.location.origin || url.pathname === '/login') return null;
        return url.pathname + url.search + url.hash;
    } catch {
        return null;
    }
};

// Đích sau khi đăng nhập: lần đầu (BE trả firstLogin) thì qua trang hồ sơ, nút "Tiếp tục" ở đó đưa về target
export const afterLoginPath = (firstLogin, target) =>
    firstLogin ? `/me?${new URLSearchParams({ welcome: '1', next: target }).toString()}` : target;

// Email trong ID token của Google, chỉ để hiển thị / lưu tên mặc định (BE mới là nơi kiểm tra chữ ký)
export function emailFromCredential(credential) {
    try {
        return decodeJwt(credential).email || '';
    } catch {
        return '';
    }
}

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (t) => localStorage.setItem(TOKEN_KEY, t);

export function getUser() {
    try {
        return JSON.parse(localStorage.getItem(USER_KEY));
    } catch {
        return null;
    }
}

export function clearAuth() {
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem('lv_demo_mode');
}

// Xoá local + báo BE thu hồi refresh token (không cần chờ)
import { resetNamedBeneficiaryCache } from '../Common/useNamedBeneficiary.js';
export function logout() {
    fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }).catch(() => {});
    resetNamedBeneficiaryCache();
    try { sessionStorage.removeItem('lv_sub_cache'); } catch { /* T1: xoá cache badge VIP; sessionStorage có thể bị chặn */ }
    clearAuth();
}

// Cập nhật một phần thông tin user đã lưu (vd: tên sau khi sửa hồ sơ)
export function updateStoredUser(patch) {
    const user = getUser();
    if (user) localStorage.setItem(USER_KEY, JSON.stringify({ ...user, ...patch }));
}
