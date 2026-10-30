// API cho Digital Executor — khớp ExecutorController (/api/executor-assignments)
// Mọi API (trừ /me) cần assignmentId và chỉ dùng được khi assignment đã ACTIVE (DMS đã kích hoạt), nếu không BE trả 403.
import http from './http.js';

const BASE = '/executor-assignments';
const unwrap = (res) => res.data?.data;

// Lấy tên file từ header Content-Disposition (hỗ trợ filename*=UTF-8''...)
function fileNameFrom(res, fallback) {
    const cd = res.headers['content-disposition'] || '';
    const star = cd.match(/filename\*=UTF-8''([^;]+)/i);
    if (star) return decodeURIComponent(star[1]);
    const plain = cd.match(/filename="?([^";]+)"?/i);
    return plain ? plain[1] : fallback;
}

// Tải blob về máy. Lỗi trả về dạng Blob JSON -> đọc lại để getErrorMessage lấy được message của BE
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

export const executorApi = {
    // B1: các assignment chưa REVOKED của người đang đăng nhập
    // -> [{ assignmentId, ownerFullName, executorType, status, assignedAt, activatedAt, acknowledgedAt }]
    listMine: () => http.get(`${BASE}/me`).then((r) => unwrap(r) ?? []),

    // FR-07: xác nhận đã nhận thông báo kích hoạt -> { id, executorType, status, activatedAt, acknowledgedAt }
    acknowledge: (assignmentId) => http.post(`${BASE}/${assignmentId}/acknowledge`).then(unwrap),

    // FR-08: vault + tài sản (chỉ metadata)
    // -> { assignmentId, ownerFullName, vaults: [{ vaultId, name, status, handoverInstructions,
    //      beneficiary: { id, fullName } | null, beneficiaryPending, assets: [{ assetId, assetType, assetName }] }] }
    getVaults: (assignmentId) => http.get(`${BASE}/${assignmentId}/vaults`).then(unwrap),

    // FR-10: tiến độ bàn giao từng vault
    // -> [{ vaultId, vaultName, handoverStatus, requestId, requestedAt, decidedAt, rejectionReason, unlockedAt, claimDeadlineAt }]
    getProgress: (assignmentId) => http.get(`${BASE}/${assignmentId}/handover-progress`).then((r) => unwrap(r) ?? []),

    // FR-09: nộp giấy chứng tử (PDF/PNG/JPG thật, ≤10MB) cho 1..50 vault
    submitVerification: (assignmentId, { file, vaultIds, notes }) => {
        const form = new FormData();
        form.append('file', file);
        vaultIds.forEach((id) => form.append('vaultIds', id)); // Spring bind List<UUID> từ tham số lặp
        if (notes) form.append('notes', notes);
        return http.post(`${BASE}/${assignmentId}/verification-requests`, form).then(unwrap);
    },

    // FR-11: mọi hồ sơ xác minh đã gửi (kể cả bị từ chối), mới nhất trước
    // -> Page { content: [{ id, status, vaults: [{ id, name }], rejectionReason, createdAt, decidedAt, unreadMessageCount }], totalElements, ... }
    listVerificationRequests: (assignmentId, { page = 0, size = 20 } = {}) =>
        http.get(`${BASE}/${assignmentId}/verification-requests`, { params: { page, size } }).then(unwrap),

    // FR-11: trao đổi với Legal Verifier (nội dung ≤2000 ký tự và/hoặc 1 tệp)
    // FR-11: BE phân trang mới nhất trước và đánh dấu đã đọc; đảo lại để hiển thị cũ nhất trước như cũ
    // -> { messages (cũ nhất trước), last } — last = false thì còn trang tin cũ hơn
    getMessages: (assignmentId, requestId, { page = 0, size = 50 } = {}) =>
        http.get(`${BASE}/${assignmentId}/verification-requests/${requestId}/messages`, { params: { page, size } })
            .then((r) => {
                const data = unwrap(r);
                return { messages: [...(data?.content ?? [])].reverse(), last: data?.last ?? true };
            }),
    sendMessage: (assignmentId, requestId, { content, file }) => {
        const form = new FormData();
        if (content) form.append('content', content);
        if (file) form.append('file', file);
        return http.post(`${BASE}/${assignmentId}/verification-requests/${requestId}/messages`, form).then(unwrap);
    },
    // B5: tải tệp đính kèm (đã giải mã) của một tin nhắn trong hồ sơ của mình
    downloadAttachment: (assignmentId, requestId, messageId, fileName) =>
        downloadBlob(
            `${BASE}/${assignmentId}/verification-requests/${requestId}/messages/${messageId}/attachment`,
            fileName || 'attachment'),
    // FR-11: tệp đính kèm (đã giải mã) của một tin nhắn, trả về Blob để mở trong tab mới
    getAttachmentBlob: (assignmentId, requestId, messageId) =>
        http.get(`${BASE}/${assignmentId}/verification-requests/${requestId}/messages/${messageId}/attachment`,
            { responseType: 'blob' }),
};

// Bước hiện tại của 1 hồ sơ, dựa trên assignment + tiến độ bàn giao
// 0 = cần acknowledge, 1 = cần nộp giấy tờ, 2 = chờ xác minh, 3 = đã mở khoá / bàn giao
export function caseStep(assignment, progress = []) {
    if (!assignment.acknowledgedAt) return 0;
    const live = progress.filter((p) => p.handoverStatus !== 'REJECTED');
    if (live.length === 0) return 1;
    if (live.some((p) => ['PENDING', 'VERIFIED'].includes(p.handoverStatus))) return 2;
    return 3;
}

export const HANDOVER_LABEL = {
    PENDING: { label: 'Waiting for verifier', cls: 'bg-warning-container text-on-warning-container' },
    VERIFIED: { label: 'Approved, awaiting signature', cls: 'bg-secondary-fixed text-on-secondary-fixed' },
    REJECTED: { label: 'Rejected', cls: 'bg-error-container text-on-error-container' },
    UNLOCKED: { label: 'Unlocked for beneficiary', cls: 'bg-success-container text-on-success-container' },
    CLAIMED: { label: 'Received by beneficiary', cls: 'bg-success-container text-on-success-container' },
    ARCHIVED_LOCKED: { label: 'Expired', cls: 'bg-surface-container text-outline' },
};
