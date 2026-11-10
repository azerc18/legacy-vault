// API cho Beneficiary — khớp BeneficiaryController (/api/beneficiaries)
import http from './http.js';

const unwrap = (res) => res.data.data;

// Lấy tên file từ header Content-Disposition (hỗ trợ filename*=UTF-8''...)
function fileNameFrom(res, fallback) {
    const cd = res.headers['content-disposition'] || '';
    const star = cd.match(/filename\*=UTF-8''([^;]+)/i);
    if (star) return decodeURIComponent(star[1]);
    const plain = cd.match(/filename="?([^";]+)"?/i);
    return plain ? plain[1] : fallback;
}

// Tải blob về máy. Lỗi trả về dạng Blob JSON -> đọc lại để lấy message
async function downloadBlob(url, fallbackName) {
    try {
        const res = await http.get(url, { responseType: 'blob' });
        const href = URL.createObjectURL(res.data);
        const a = document.createElement('a');
        a.href = href;
        a.download = fileNameFrom(res, fallbackName);
        a.click();
        URL.revokeObjectURL(href);
    } catch (err) {
        const blob = err.response?.data;
        if (blob instanceof Blob) {
            try { err.response.data = JSON.parse(await blob.text()); } catch { /* giữ nguyên */ }
        }
        throw err;
    }
}

export const beneficiaryApi = {
    // FR-16: danh sách vault được bàn giao
    listVaults: () => http.get('/beneficiaries/vaults').then((res) => res.data?.data ?? []),

    // FR-16: khởi tạo yêu cầu nhận (tạo phiên xác thực PENDING, sống 15 phút)
    initClaim: (vaultId, verificationMethod) =>
        http.post('/beneficiaries/claim', { vaultId, verificationMethod }).then(unwrap),

    downloadDossier: (vaultId) =>
        downloadBlob(`/beneficiaries/vaults/${vaultId}/dossier`, 'ho-so-khai-nhan.pdf'),

    // FR-17: gửi OTP (chỉ dùng cho method OTP, cách nhau tối thiểu 60s)
    sendOtp: (vaultId) =>
        http.post('/beneficiaries/verifications/otp', { vaultId }),

    // FR-17: xác thực -> mở phiên xem 30 phút
    verify: (vaultId, verificationMethod, { otp, kycIdNumber } = {}) =>
        http.post('/beneficiaries/verifications/verify', { vaultId, verificationMethod, otp, kycIdNumber }).then(unwrap),

    // FR-17/18: tài sản (cần phiên xem còn hiệu lực, nếu không BE trả 403)
    listAssets: (vaultId) => http.get(`/beneficiaries/vaults/${vaultId}/assets`).then(unwrap),
    getAsset: (vaultId, assetId) => http.get(`/beneficiaries/vaults/${vaultId}/assets/${assetId}`).then(unwrap),
    downloadAsset: (vaultId, assetId, name) =>
        downloadBlob(`/beneficiaries/vaults/${vaultId}/assets/${assetId}/download`, `${name || 'asset'}.txt`),

    // X1: đồng thuận K-of-N cho tài sản JOINT_CONSENSUS
    getConsensus: (vaultId, assetId) => http.get(`/beneficiaries/vaults/${vaultId}/assets/${assetId}/consensus`).then(unwrap),
    approveConsensus: (vaultId, assetId) => http.post(`/beneficiaries/vaults/${vaultId}/assets/${assetId}/consensus/approve`).then(unwrap),

    // FR-18 (phần 2): tài liệu pháp lý
    listDocuments: (vaultId) => http.get(`/beneficiaries/vaults/${vaultId}/documents`).then(unwrap),
    downloadDocument: (vaultId, docId, name) =>
        downloadBlob(`/beneficiaries/vaults/${vaultId}/documents/${docId}/download`, name || 'document'),

    // F4b: lời nhắn di nguyện & hẹn giờ (Time-Capsule) — đọc ngay sau khi đăng nhập khi vault UNLOCKED / CLAIMED
    listLegacyMessages: (vaultId) =>
        http.get(`/beneficiaries/vaults/${vaultId}/messages`).then((r) => r.data?.data ?? []),
    getLegacyMessage: (vaultId, messageId) =>
        http.get(`/beneficiaries/vaults/${vaultId}/messages/${messageId}`).then(unwrap),
    downloadLegacyMessageAttachment: (vaultId, messageId, name) =>
        downloadBlob(`/beneficiaries/vaults/${vaultId}/messages/${messageId}/attachment`, name || 'attachment'),

    // FR-19: đóng hồ sơ
    closePreview: (vaultId) => http.get(`/beneficiaries/vaults/${vaultId}/close-preview`).then(unwrap),
    closeVault: (vaultId, acknowledgeUnviewed) =>
        http.post(`/beneficiaries/vaults/${vaultId}/close`, { confirmed: true, acknowledgeUnviewed }).then(unwrap),

    // N2b: phản hồi phần được chia (cần phiên xem còn hiệu lực). Decline không đảo ngược được
    acceptClaim: (vaultId) => http.post(`/beneficiaries/vaults/${vaultId}/accept`).then(unwrap),
    declineClaim: (vaultId, reason) =>
        http.post(`/beneficiaries/vaults/${vaultId}/decline`, reason ? { reason } : {}).then(unwrap),

    // N2-notify: tiến độ theo từng tài sản của mình (BE trả 409 sau khi đã đóng hồ sơ)
    getProgress: (vaultId) => http.get(`/beneficiaries/vaults/${vaultId}/progress`).then(unwrap),

    // F5: Executor xử lý tài khoản số theo di nguyện (chỉ xem)
    // -> [{ id, platform, accountHandle, wish, status, note, updatedAt, completedAt }]
    listExecutorTasks: (vaultId) =>
        http.get(`/beneficiaries/vaults/${vaultId}/executor-tasks`).then((r) => r.data?.data ?? []),
    // C.6: xác nhận hoặc phản đối kết quả Executor đã báo DONE -> task đã cập nhật
    confirmExecutorTask: (vaultId, taskId) =>
        http.post(`/beneficiaries/vaults/${vaultId}/executor-tasks/${taskId}/confirm`).then(unwrap),
    disputeExecutorTask: (vaultId, taskId, reason) =>
        http.post(`/beneficiaries/vaults/${vaultId}/executor-tasks/${taskId}/dispute`, { reason }).then(unwrap),
};
