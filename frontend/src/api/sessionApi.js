import http from './http.js';
import { getToken } from '../auth/auth.js';

// Thiết bị đang đăng nhập (BE: UserSessionController /api/auth/sessions).
// API nằm dưới /auth để BE đọc được cookie refresh_token (biết "thiết bị này"), nhưng interceptor của http
// không gắn Bearer cho /auth/* và không tự làm mới token -> gắn tay, gặp 401 thì gọi /users/me (tự làm mới) rồi thử lại 1 lần.
const authed = async (fn) => {
    const run = () => fn({ headers: { Authorization: `Bearer ${getToken()}` } });
    try {
        return await run();
    } catch (e) {
        if (e.response?.status !== 401) throw e;
        await http.get('/users/me');
        return run();
    }
};

export const sessionApi = {
    // [{ id, userAgent, ipAddress, lastActiveAt, expiresAt, current }]
    list: () => authed((cfg) => http.get('/auth/sessions', cfg)).then((r) => r.data.data ?? []),
    revoke: (id) => authed((cfg) => http.delete(`/auth/sessions/${id}`, cfg)),
    // Trả số thiết bị đã đăng xuất
    revokeOthers: () => authed((cfg) => http.post('/auth/sessions/revoke-others', null, cfg)).then((r) => r.data.data ?? 0),
};

// "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ... Chrome/130..." -> { browser: 'Chrome', os: 'Windows', mobile: false }
export function parseUserAgent(ua = '') {
    const s = ua || '';
    const browser = /Edg\//.test(s) ? 'Edge'
        : /OPR\/|Opera/.test(s) ? 'Opera'
            : /Firefox\//.test(s) ? 'Firefox'
                : /Chrome\/|CriOS\//.test(s) ? 'Chrome'
                    : /Safari\//.test(s) ? 'Safari'
                        : /PostmanRuntime|curl|okhttp|axios|python|Java/i.test(s) ? 'API'
                            : null;
    const os = /Windows/.test(s) ? 'Windows'
        : /Android/.test(s) ? 'Android'
            : /iPhone|iPad|iPod/.test(s) ? 'iOS'
                : /Mac OS X|Macintosh/.test(s) ? 'macOS'
                    : /Linux/.test(s) ? 'Linux'
                        : null;
    const mobile = /Mobi|Android|iPhone|iPod/.test(s);
    return { browser, os, mobile };
}
