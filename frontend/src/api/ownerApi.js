// API cho Vault Owner — khớp VaultController, DocumentController, VaultActivityLogController,
// DmsController, OwnerExecutorController. Dùng http.js chung (tự gắn token + refresh).
import http from './http.js';
import { getUser } from '../auth/auth.js';

const unwrap = (res) => res.data?.data;

/* ------------------------------------------------------------------
   Danh sách vault: BE CHƯA có GET /api/vaults.
   Tạm thời FE tự nhớ các vault do chính trình duyệt này tạo (localStorage, theo user).
   Khi BE thêm API: chỉ cần sửa listVaults() thành
       listVaults: () => http.get('/vaults').then(unwrap)
   ------------------------------------------------------------------ */
const storeKey = () => `lv_owner_vaults_${getUser()?.id || 'anon'}`;

function readLocalVaults() {
    try { return JSON.parse(localStorage.getItem(storeKey())) || []; } catch { return []; }
}
function writeLocalVaults(list) {
    try { localStorage.setItem(storeKey(), JSON.stringify(list)); } catch { /* bỏ qua */ }
}
function rememberVault(v) {
    writeLocalVaults([v, ...readLocalVaults().filter((x) => x.id !== v.id)]);
}
function forgetVault(id) {
    writeLocalVaults(readLocalVaults().filter((x) => x.id !== id));
}

export const ownerApi = {
    /* ---------- Vault ---------- */
    listVaults: async () => readLocalVaults(),
    getLocalVault: (id) => readLocalVaults().find((v) => v.id === id) || null,

    // FR-01: tạo vault kèm tài sản. assets: [{ assetType, assetName, secret, notes }]
    // -> VaultResponse { id, name, description, status, createdAt, updatedAt }
    createVault: async ({ name, description, assets }) => {
        const vault = await http.post('/vaults', { name, description, assets }).then(unwrap);
        rememberVault(vault);
        return vault;
    },

    // Chỉ xoá được khi vault ACTIVE
    deleteVault: async (vaultId) => {
        await http.delete(`/vaults/${vaultId}`);
        forgetVault(vaultId);
    },

    // FR-02: chỉ định / thay người thụ hưởng. 409 + "confirmReplace" => gửi lại với confirmReplace=true
    // -> { vaultId, beneficiaryEmail, beneficiaryName, pendingRegistration, replaced }
    assignBeneficiary: (vaultId, email, confirmReplace = false) =>
        http.put(`/vaults/${vaultId}/beneficiary`, { email, confirmReplace }).then(unwrap),

    // FR-04: upload tài liệu pháp lý (PDF, DOC/DOCX, JPG/PNG, tối đa 10MB)
    // type: WILL | OWNERSHIP_PAPER | IDENTITY_CARD | OTHER
    uploadDocument: (vaultId, type, file) => {
        const form = new FormData();
        form.append('vaultId', vaultId);
        form.append('type', type);
        form.append('file', file);
        return http.post('/documents/upload', form).then(unwrap);
    },

    // Nhật ký hoạt động: trả Page { content[], totalPages, totalElements, number, size }
    // startDate/endDate: ISO date-time (vd 2026-10-01T00:00:00Z), tối đa 1 năm
    getActivityLogs: (vaultId, { startDate, endDate, page = 0, size = 20 } = {}) => {
        const params = { page, size };
        if (startDate) params.startDate = startDate;
        if (endDate) params.endDate = endDate;
        return http.get(`/vaults/${vaultId}/activity-logs`, { params }).then(unwrap);
    },

    /* ---------- Dead Man's Switch (FR-03) ---------- */
    // 404 = chưa cấu hình
    getDms: () => http.get('/dms').then(unwrap),
    // { checkinIntervalDays 1-365, gracePeriodDays 1-90, reminderCount 1-10 }
    saveDms: (config) => http.put('/dms', config).then(unwrap),
    checkIn: () => http.post('/dms/check-in').then(unwrap),

    /* ---------- Executor (FR-06) ---------- */
    // Danh sách vị trí (gồm cả đã thu hồi)
    listExecutors: () => http.get('/owner/executors').then(unwrap),
    // type: PRIMARY | BACKUP
    assignExecutor: (type, email, confirmReplace = false) =>
        http.put(`/owner/executors/${type}`, { email, confirmReplace }).then(unwrap),
    revokeExecutor: (type) => http.delete(`/owner/executors/${type}`).then(unwrap),
};

// BE báo "đã có người, gửi lại với confirmReplace=true" bằng 409 + message chứa "confirmReplace"
export const needsReplaceConfirm = (err) =>
    err.response?.status === 409 && /confirmReplace/i.test(err.response?.data?.message || '');
