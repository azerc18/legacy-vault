import axios from 'axios';
import { getToken, setToken, clearAuth } from '../auth/auth.js';

const http = axios.create({ baseURL: '/api', withCredentials: true });

// Gắn Bearer token vào mọi request
http.interceptors.request.use((config) => {
    const token = getToken();
    const isAuthCall = config.url?.startsWith('/auth/');
    if (token && !isAuthCall) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

// Gặp 401 -> gọi /auth/refresh 1 lần -> gửi lại request cũ
let refreshing = null;
http.interceptors.response.use(
    (res) => res,
    async (error) => {
        const original = error.config;
        const isAuthCall = original?.url?.startsWith('/auth/');
        if (error.response?.status === 401 && !original._retry && !isAuthCall) {
            original._retry = true;
            try {
                refreshing ??= axios
                    .post('/api/auth/refresh', null, { withCredentials: true })
                    .finally(() => { refreshing = null; });
                const res = await refreshing;
                setToken(res.data.data.accessToken);
                return http(original);
            } catch {
                clearAuth();
                window.location.href = '/login';
            }
        }
        return Promise.reject(error);
    }
);

// Lấy message lỗi từ ApiResponse của BE
// Mã lỗi BE trả về (VALIDATION_FAILED, LAST_BENEFICIARY, ...).
// 'NETWORK_ERROR' khi không gọi được server; null nếu response không có mã.
export function getErrorCode(error) {
    if (!error?.response) return 'NETWORK_ERROR';
    return error.response.data?.errorCode ?? null;
}

// translate (tuỳ chọn, dùng từ H2): (code) => chuỗi đã dịch hoặc undefined
export function getErrorMessage(error, fallback = 'Something went wrong.', translate) {
    const body = error?.response?.data;
    const code = getErrorCode(error);

    // Lỗi validation: data = { field: message } -> hiện lỗi của field đầu tiên
    if (code === 'VALIDATION_FAILED' && body?.data && typeof body.data === 'object') {
        const first = Object.values(body.data)[0];
        if (typeof first === 'string') return first;
    }

    const translated = translate && code ? translate(code) : undefined;
    if (translated) return translated;
    if (body?.message) return body.message;
    if (code === 'NETWORK_ERROR') return 'Cannot reach the server. Check your connection.';
    return fallback;
}

export default http;