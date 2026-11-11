import axios from 'axios';
import { getToken, setToken, clearAuth } from '../auth/auth.js';
import i18n from '../i18n/index.js';
import {
    DEMO_USER,
    DEMO_VAULTS,
    DEMO_ASSETS,
    DEMO_DOCUMENTS,
    DEMO_MESSAGES,
    DEMO_LOGS,
    DEMO_DMS,
    DEMO_EXECUTORS,
    DEMO_READINESS,
    DEMO_BENEFICIARY_VAULTS,
    DEMO_EXECUTOR_CASES,
    DEMO_VERIFIER_REQUESTS,
    DEMO_ACCESS_LOGS,
    DEMO_SUBSCRIPTION,
    demoNotifications
} from '../Common/demoMockData.js';

const http = axios.create({ baseURL: '/api', withCredentials: true });

function handleDemoMock(config) {
    const url = config.url || '';
    const method = (config.method || 'get').toLowerCase();
    let data = { success: true, data: [] };

    if (url.includes('/dms/check-in') || (url.endsWith('/dms') && (method === 'put' || method === 'post'))) {
        data = { success: true, data: DEMO_DMS };
    } else if (url.includes('/dms/confirmers') && method === 'post') {
        data = { success: true, data: { id: 'c-' + Date.now(), name: 'Người xác nhận mới', email: 'confirmer@example.com', relationship: 'Bạn bè', status: 'CONFIRMED' } };
    } else if (url.includes('/notifications/me/unread-count')) {
        data = { success: true, data: demoNotifications().filter((n) => !n.read).length };
    } else if (url.endsWith('/notifications/me')) {
        const all = demoNotifications();
        data = { success: true, data: { content: config.params?.unreadOnly ? all.filter((n) => !n.read) : all, last: true } };
    } else if (method === 'post' || method === 'put' || method === 'patch') {
        data = { success: true, data: { id: 'demo-' + Date.now(), success: true, status: 'SUCCESS' } };
    } else if (url.includes('/users/me')) {
        data = { success: true, data: DEMO_USER };
    } else if (url.includes('/plans/my-subscription')) {
        data = { success: true, data: { vip: true, planName: 'VIP_UNLIMITED' } };
    } else if (url.endsWith('/subscriptions/me')) {
        data = { success: true, data: DEMO_SUBSCRIPTION };
    } else if (url.includes('/owner/activity-logs/access')) {
        data = { success: true, data: DEMO_ACCESS_LOGS };
    } else if (url.includes('/owner/readiness')) {
        data = { success: true, data: DEMO_READINESS };
    } else if (url.includes('/documents')) {
        data = { success: true, data: DEMO_DOCUMENTS };
    } else if (url.includes('/messages')) {
        data = { success: true, data: DEMO_MESSAGES };
    } else if (url.includes('/dms/confirmers')) {
        data = { success: true, data: DEMO_DMS.confirmers };
    } else if (url.includes('/dms')) {
        data = { success: true, data: DEMO_DMS };
    } else if (url.includes('/owner/executors')) {
        data = { success: true, data: DEMO_EXECUTORS };
    } else if (url.includes('/vaults/v-demo-1/assets') || url.includes('/assets')) {
        data = { success: true, data: DEMO_ASSETS };
    } else if (url.includes('/vaults/v-demo-1')) {
        data = { success: true, data: DEMO_VAULTS[0] };
    } else if (url.includes('/vaults')) {
        data = { success: true, data: DEMO_VAULTS };
    } else if (url.includes('/owner/activity-logs') || url.includes('/activity')) {
        data = { success: true, data: DEMO_LOGS };
    } else if (url.includes('/beneficiaries/vaults')) {
        data = { success: true, data: DEMO_BENEFICIARY_VAULTS };
    } else if (url.includes('/executor/assignments/me') || url.includes('/executor')) {
        data = { success: true, data: DEMO_EXECUTOR_CASES };
    } else if (url.includes('/verifier')) {
        data = { success: true, data: DEMO_VERIFIER_REQUESTS };
    }

    return {
        data,
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
    };
}

// Gắn Bearer token vào mọi request
http.interceptors.request.use((config) => {
    const isAuthCall = config.url?.startsWith('/auth/') || config.url?.includes('/auth/');
    if (!isAuthCall && localStorage.getItem('lv_demo_mode') === 'true') {
        config.adapter = async (cfg) => handleDemoMock(cfg);
    }
    const token = getToken();

    if (token && !isAuthCall) config.headers.Authorization = `Bearer ${token}`;
    config.headers['Accept-Language'] = i18n.resolvedLanguage || 'en';   // ← thêm
    return config;
});

// T1 (m6): 403 PLAN_LIMIT_REACHED -> phát sự kiện để App mở UpgradeDialog dùng chung (detail.data.feature cho biết tính năng nào)
export const PLAN_LIMIT_EVENT = 'lv:plan-limit-reached';

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
                // Báo cho trang Login biết phiên đã hết hạn (hiện thông báo thay vì chuyển trang im lặng)
                if (window.location.pathname !== '/login') window.location.href = '/login?expired=1';
            }
        }
        if (error.response?.data?.errorCode === 'PLAN_LIMIT_REACHED') {
            window.dispatchEvent(new CustomEvent(PLAN_LIMIT_EVENT, { detail: error.response.data }));
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
