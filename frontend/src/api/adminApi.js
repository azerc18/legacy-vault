// API cho System Admin — khớp AdminController (/api/admin) và VaultController (/api/vaults/admin/*)
// Mọi API cần authority ADMIN_MANAGE.
import http from './http.js';

const unwrap = (res) => res.data?.data;

export const adminApi = {
    /* ---------- Người dùng ---------- */
    // q: tìm theo email/tên; role: OWNER | BENEFICIARY | EXECUTOR | LEGAL_VERIFIER | ADMIN; status: PENDING | ACTIVE | LOCKED
    // -> Page { content: [{ id, email, fullName, status, roles[], createdAt, lastLoginAt }], totalPages, totalElements, number }
    listUsers: ({ q, role, status, page = 0, size = 20 } = {}) => {
        const params = { page, size };
        if (q) params.q = q;
        if (role) params.role = role;
        if (status) params.status = status;
        return http.get('/admin/users', { params }).then(unwrap);
    },

    /* ---------- Role & quyền ---------- */
    // -> [{ code, description }]
    listAuthorities: () => http.get('/admin/authorities').then(unwrap),
    // -> [{ id, code, name, description, system, authorities[] }]
    listRoles: () => http.get('/admin/roles').then(unwrap),
    // body: { code, name, description, authorities[] }; 409 nếu trùng code, 400 nếu quyền không tồn tại
    createRole: (body) => http.post('/admin/roles', body).then(unwrap),
    // body: { name, description, authorities[] }; role ADMIN phải giữ ADMIN_MANAGE (409)
    updateRole: (code, body) => http.put(`/admin/roles/${encodeURIComponent(code)}`, body).then(unwrap),
    // 409 nếu là role hệ thống hoặc còn user đang giữ role
    deleteRole: (code) => http.delete(`/admin/roles/${encodeURIComponent(code)}`).then(unwrap),

    // Chỉ chuyển ACTIVE <-> LOCKED, không tự đổi chính mình, user PENDING (chưa xác thực email) bị chặn
    updateStatus: (userId, status, reason) =>
        http.patch(`/admin/users/${userId}/status`, { status, reason: reason || null }).then(unwrap),

    // Chỉ quản lý được LEGAL_VERIFIER và ADMIN; user phải ACTIVE; không tự gỡ ADMIN của mình
    grantRole: (userId, roleCode) => http.post(`/admin/users/${userId}/roles/${roleCode}`).then(unwrap),
    revokeRole: (userId, roleCode) => http.delete(`/admin/users/${userId}/roles/${roleCode}`).then(unwrap),

    /* ---------- Dead Man's Switch của 1 Owner ---------- */
    // 409 nếu DMS đã TRIGGERED / đã SUSPENDED; 404 nếu Owner chưa cấu hình DMS
    suspendDms: (ownerId, reason) => http.put(`/admin/dms/${ownerId}/suspend`, { reason: reason || null }).then(unwrap),
    resumeDms: (ownerId) => http.put(`/admin/dms/${ownerId}/resume`).then(unwrap),
    // B-01b: chỉ dùng khi user.executorRescueNeeded = true. 409 nếu Owner vẫn còn Executor dùng được.
    rescueExecutor: (ownerId, email) => http.post(`/admin/dms/${ownerId}/executor`, { email }).then(unwrap),

    /* ---------- Vault ---------- */
    // status: ACTIVE | UNLOCKED | CLAIMED | ARCHIVED_LOCKED; mặc định sắp xếp mới nhất trước
    // -> Page { content: [{ id, ownerId, name, description, status, createdAt, updatedAt }], ... }
    listVaults: ({ status, page = 0, size = 20 } = {}) => {
        const params = { page, size };
        if (status) params.status = status;
        return http.get('/vaults/admin/all', { params }).then(unwrap);
    },
    // -> { id, name, description, status, ownerId, ownerEmail, unlockedAt, claimDeadlineAt, createdAt, updatedAt, totalDocuments, totalAssets }
    getVault: (vaultId) => http.get(`/vaults/admin/${vaultId}`).then(unwrap),

    /* ---------- Audit log & cảnh báo bảo mật ---------- */
    // from/to là ISO string; mặc định BE lấy 30 ngày gần nhất, tối đa 365 ngày
    // -> Page { content: [{ id, action, result, actorId, actorEmail, entityType, entityId, vaultId, ipAddress, detail, userAgent, createdAt }], ... }
    searchAuditLogs: ({ action, actorEmail, ip, from, to, page = 0, size = 50 } = {}) => {
        const params = { page, size };
        if (action) params.action = action;
        if (actorEmail) params.actorEmail = actorEmail;
        if (ip) params.ip = ip;
        if (from) params.from = from;
        if (to) params.to = to;
        return http.get('/admin/audit-logs', { params }).then(unwrap);
    },
    listAuditActions: () => http.get('/admin/audit-logs/actions').then(unwrap),

    // status: OPEN | ACKNOWLEDGED
    listAlerts: ({ status = 'OPEN', page = 0, size = 20 } = {}) =>
        http.get('/admin/security-alerts', { params: { status, page, size } }).then(unwrap),
    // -> { open: number }
    countOpenAlerts: () => http.get('/admin/security-alerts/count').then(unwrap),
    // 409 nếu đã được xác nhận
    acknowledgeAlert: (alertId) => http.patch(`/admin/security-alerts/${alertId}/acknowledge`).then(unwrap),
};
