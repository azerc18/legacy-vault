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
export function getErrorMessage(error, fallback = 'Something went wrong.') {
    const body = error.response?.data;
    // Lỗi validation: data = { field: message }
    if (body?.data && typeof body.data === 'object') {
        const first = Object.values(body.data)[0];
        if (typeof first === 'string') return first;
    }
    return body?.message || fallback;
}

export default http;