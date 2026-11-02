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
}

// Xoá local + báo BE thu hồi refresh token (không cần chờ)
import { resetNamedBeneficiaryCache } from '../Common/useNamedBeneficiary.js';
export function logout() {
    fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }).catch(() => {});
    resetNamedBeneficiaryCache();
    clearAuth();
}

// Cập nhật một phần thông tin user đã lưu (vd: tên sau khi sửa hồ sơ)
export function updateStoredUser(patch) {
    const user = getUser();
    if (user) localStorage.setItem(USER_KEY, JSON.stringify({ ...user, ...patch }));
}