import axios from 'axios';
import { getToken, setToken, clearAuth } from '../auth/auth.js';
import i18n from '../i18n/index.js';

const http = axios.create({ baseURL: '/api', withCredentials: true });

// Gắn Bearer token vào mọi request
http.interceptors.request.use((config) => {
    const token = getToken();
    const isAuthCall = config.url?.startsWith('/auth/');
    if (token && !isAuthCall) config.headers.Authorization = `Bearer ${token}`;
    config.headers['Accept-Language'] = i18n.resolvedLanguage || 'en';   // ← thêm
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
                if (original.skipLoginRedirect) return Promise.reject(error);   // T1: trang công khai tự xử lý 401
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
    const res = error?.response;
    if (!res) return 'NETWORK_ERROR';
    if (res.data?.errorCode) return res.data.errorCode;
    // Proxy trả lỗi khi BE không chạy: 502/503/504 hoặc 500 không có body
    if ([502, 503, 504].includes(res.status) || (res.status === 500 && !res.data)) return 'NETWORK_ERROR';
    return null;
}

// translate (tuỳ chọn, dùng từ H2): (code) => chuỗi đã dịch hoặc undefined
export function getErrorMessage(error, fallback) {
    const body = error?.response?.data;
    const code = getErrorCode(error);

    // Lỗi validation: hiện lỗi của field đầu tiên (BE đã trả câu cụ thể)
    if (code === 'VALIDATION_FAILED' && body?.data && typeof body.data === 'object') {
        const first = Object.values(body.data)[0];
        if (typeof first === 'string') return first;
    }

    // Có bản dịch cho mã này -> dùng; data của BE làm tham số (vd. retryAfterSeconds)
    if (code && i18n.exists(code, { ns: 'errors' })) {
        const params = body?.data && typeof body.data === 'object' ? body.data : {};
        return i18n.t(code, { ns: 'errors', ...params });
    }

    if (body?.message) return body.message;
    return fallback ?? i18n.t('UNKNOWN', { ns: 'errors' });
}

export default http;