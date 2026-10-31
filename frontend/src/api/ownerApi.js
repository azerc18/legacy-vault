// API cho Vault Owner — khớp VaultController, DigitalAssetController, DocumentController, VaultActivityLogController,
// DmsController, OwnerExecutorController. Dùng http.js chung (tự gắn token + refresh).
import http from './http.js';

const unwrap = (res) => res.data?.data;

// Báo ProtectionBanner tải lại sau khi Owner đổi vault / người thụ hưởng / Executor / DMS
export const PROTECTION_CHANGED = 'lv:owner-protection-changed';
const changed = (result) => { window.dispatchEvent(new Event(PROTECTION_CHANGED)); return result; };

// Lấy tên file từ Content-Disposition rồi cho trình duyệt tải về
async function saveBlob(url, fallbackName) {
    try {
        const res = await http.get(url, { responseType: 'blob' });
        const cd = res.headers['content-disposition'] || '';
        const star = cd.match(/filename\*=UTF-8''([^;]+)/i);
        const plain = cd.match(/filename="?([^";]+)"?/i);
        const name = star ? decodeURIComponent(star[1]) : plain ? plain[1] : fallbackName;
        const href = URL.createObjectURL(res.data);
        const a = document.createElement('a');
        a.href = href; a.download = name; a.click();
        URL.revokeObjectURL(href);
    } catch (err) {
        // Lỗi trả về dạng Blob JSON -> đọc lại để lấy message của BE
        const blob = err.response?.data;
        if (blob instanceof Blob) {
            try { err.response.data = JSON.parse(await blob.text()); } catch { /* giữ nguyên */ }
        }
        throw err;
    }
}

export const ownerApi = {
    /* ---------- Vault (FR-01, FR-27) ---------- */
    // -> [VaultResponse { id, ownerId, name, description, status, createdAt, updatedAt,
    //      beneficiaryEmail, pendingRegistration, totalAssets, totalDocuments, modifiable, lockReason }]
    listVaults: () => http.get('/vaults').then((r) => unwrap(r) ?? []),

    // -> VaultDetailResponse { id, name, description, status, ownerEmail, beneficiaryId, beneficiaryEmail,
    //      unlockedAt, claimDeadlineAt, createdAt, updatedAt, totalDocuments, totalAssets, modifiable, lockReason }
    getVault: (vaultId) => http.get(`/vaults/${vaultId}`).then(unwrap),

    // name ≤100 (bắt buộc), description ≤500, assets ≤50: [{ assetType, assetName ≤200, secret ≤5000, notes ≤2000 }]
    createVault: ({ name, description, assets }) => http.post('/vaults', { name, description, assets }).then(unwrap).then(changed),

    // Bị chặn khi DMS đã kích hoạt hoặc đang có hồ sơ xác minh
    updateVault: (vaultId, { name, description }) => http.put(`/vaults/${vaultId}`, { name, description }).then(unwrap),

    // Chỉ xoá được khi vault ACTIVE
    deleteVault: (vaultId) => http.delete(`/vaults/${vaultId}`).then(changed),

    /* ---------- Tài sản số (FR-27) ---------- */
    // Danh sách KHÔNG có secret -> [{ id, assetType, assetName, status, createdAt, updatedAt }]
    listAssets: (vaultId) => http.get(`/vaults/${vaultId}/assets`).then((r) => unwrap(r) ?? []),
    // Chi tiết CÓ secret + notes đã giải mã (BE ghi audit ASSET_VIEWED mỗi lần xem)
    getAsset: (vaultId, assetId) => http.get(`/vaults/${vaultId}/assets/${assetId}`).then(unwrap),
    // { assetType, assetName, secret (bắt buộc khi thêm), notes }
    addAsset: (vaultId, asset) => http.post(`/vaults/${vaultId}/assets`, asset).then(unwrap),
    // Cập nhật: secret null/rỗng = giữ nguyên; notes null = giữ nguyên, "" = xoá ghi chú
    updateAsset: (vaultId, assetId, asset) => http.put(`/vaults/${vaultId}/assets/${assetId}`, asset).then(unwrap),
    deleteAsset: (vaultId, assetId) => http.delete(`/vaults/${vaultId}/assets/${assetId}`),

    // FR-02: chỉ định / thay người thụ hưởng. 409 + "confirmReplace" => gửi lại với confirmReplace=true
    // -> { vaultId, beneficiaryEmail, beneficiaryName, pendingRegistration, replaced }
    assignBeneficiary: (vaultId, email, confirmReplace = false) =>
        http.put(`/vaults/${vaultId}/beneficiary`, { email, confirmReplace }).then(unwrap).then(changed),

    // FR-02 (V8): gỡ người thụ hưởng hoặc hủy lời mời đang chờ đăng ký (idempotent)
    removeBeneficiary: (vaultId) => http.delete(`/vaults/${vaultId}/beneficiary`).then(changed),

    // FR-04: upload tài liệu pháp lý (PDF, DOC/DOCX, JPG/PNG, tối đa 10MB)
    // type: WILL | OWNERSHIP_PAPER | IDENTITY_CARD | OTHER
    uploadDocument: (vaultId, type, file) => {
        const form = new FormData();
        form.append('vaultId', vaultId);
        form.append('type', type);
        form.append('file', file);
        return http.post('/documents/upload', form).then(unwrap);
    },
    // -> [{ id, vaultId, documentType, fileName, uploadedAt }]
    listDocuments: (vaultId) => http.get(`/documents/vault/${vaultId}`).then((r) => unwrap(r) ?? []),
    downloadDocument: (docId, fileName) => saveBlob(`/documents/${docId}/download`, fileName || 'document'),
    // 409 nếu tài liệu đang nằm trong hồ sơ xác minh
    deleteDocument: (docId) => http.delete(`/documents/${docId}`),

    // Nhật ký hoạt động: trả Page { content[], totalPages, totalElements, number, size }
    // startDate/endDate: ISO date-time (vd 2026-10-01T00:00:00Z); không gửi startDate thì xem toàn bộ lịch sử
    getActivityLogs: (vaultId, { startDate, endDate, page = 0, size = 20 } = {}) => {
        const params = { page, size };
        if (startDate) params.startDate = startDate;
        if (endDate) params.endDate = endDate;
        return http.get(`/vaults/${vaultId}/activity-logs`, { params }).then(unwrap);
    },

    // Nhật ký cấp Owner (sự kiện DMS / Executor, không gắn vault). Cùng định dạng Page như nhật ký vault
    getOwnerActivityLogs: ({ startDate, endDate, page = 0, size = 20 } = {}) => {
        const params = { page, size };
        if (startDate) params.startDate = startDate;
        if (endDate) params.endDate = endDate;
        return http.get('/owner/activity-logs', { params }).then(unwrap);
    },

    /* ---------- Dead Man's Switch (FR-03) ---------- */
    // configured=false = chưa cấu hình (chỉ có hasUsableExecutor, các trường khác null)
    getDms: () => http.get('/dms').then(unwrap),
    // { checkinIntervalDays 1-365, gracePeriodDays 1-90, reminderCount 1-10 }
    saveDms: (config) => http.put('/dms', config).then(unwrap).then(changed),
    checkIn: () => http.post('/dms/check-in').then(unwrap),
    // Lịch sử check-in, mới nhất trước: Page { content[{ checkinType, channel, occurredAt }], totalPages, totalElements, number, size }
    getDmsCheckins: ({ page = 0, size = 20 } = {}) =>
        http.get('/dms/check-ins', { params: { page, size } }).then(unwrap),

    /* ---------- Executor (FR-06) ---------- */
    // Danh sách vị trí (gồm cả đã thu hồi)
    listExecutors: () => http.get('/owner/executors').then(unwrap),
    // type: PRIMARY | BACKUP
    assignExecutor: (type, email, confirmReplace = false) =>
        http.put(`/owner/executors/${type}`, { email, confirmReplace }).then(unwrap).then(changed),
    revokeExecutor: (type) => http.delete(`/owner/executors/${type}`).then(unwrap).then(changed),
};

// BE báo "đã có người, gửi lại với confirmReplace=true" bằng 409 + message chứa "confirmReplace"
export const needsReplaceConfirm = (err) =>
    err.response?.status === 409 && /confirmReplace/i.test(err.response?.data?.message || '');
