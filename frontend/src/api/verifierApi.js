// API cho Legal Verifier — khớp LegalVerificationController (/api/verifier/requests)
// Dùng http.js chung: tự gắn token + tự refresh khi 401.
// Mọi hàm trả về đúng VerificationRequestResponseDto của BE:
// { id, ownerName, executorName, status, hasDeathCertificate, vaultIds[], rejectionReason, decidedAt, createdAt }

import http from './http.js';

const BASE = '/verifier/requests';
const unwrap = (res) => res.data?.data;

// UC12: hồ sơ cần xử lý = PENDING (chờ duyệt) + APPROVED do mình duyệt (chờ ký)
// BE: GET /requests chỉ trả PENDING, nên lấy thêm APPROVED từ /history
export async function getRequests() {
    const [pending, approved] = await Promise.all([
        http.get(BASE).then(unwrap),
        http.get(`${BASE}/history`, { params: { status: 'APPROVED', size: 100 } }).then(unwrap),
    ]);
    return [...(approved ?? []), ...(pending ?? [])];
}

// UC12: chi tiết (404 nếu không tồn tại hoặc xung đột lợi ích)
export const getRequestById = (id) => http.get(`${BASE}/${id}`).then(unwrap);

// UC13 + FR-13: kiểm tra giấy chứng tử (POST, phải đã View trước, nếu không BE trả 409) -> { requestId, valid, message }
export const verifyDocument = (id) => http.post(`${BASE}/${id}/document/verify`).then(unwrap);

// D1: tải giấy chứng tử (trả về file, nên cần responseType blob)
export const getDocumentBlob = (id) =>
    http.get(`${BASE}/${id}/document`, { responseType: 'blob' });

// FR-12: danh sách lý do từ chối cho dropdown -> [{ code, label }]
export const getRejectionReasons = () => http.get(`${BASE}/rejection-reasons`).then(unwrap);

// UC12: duyệt / từ chối. reason = code của lý do; note bắt buộc khi reason = OTHER (tối đa 500 ký tự)
export const approveRequest = (id) => http.post(`${BASE}/${id}/approve`).then(unwrap);
export const rejectRequest = (id, reason, note) =>
    http.post(`${BASE}/${id}/reject`, { reason, note: note || null }).then(unwrap);

// UC14 bước 1: gửi mã ký 6 số qua email (hết hạn 5 phút, gửi lại sau 60s)
export const sendSignOtp = (id) => http.post(`${BASE}/${id}/sign/otp`);

// UC14 bước 2: ký và mở khoá vault. BE hiện chỉ chấp nhận method MOCK_OTP
export const signRequest = (id, code) =>
    http.post(`${BASE}/${id}/sign`, { method: 'MOCK_OTP', code }).then(unwrap);

// UC15: lịch sử hồ sơ mình đã xử lý. status: APPROVED | REJECTED | SIGNED; from/to: 'YYYY-MM-DD'
export async function getHistory({ status, from, to, page = 0, size = 20 } = {}) {
    const params = { page, size };
    if (status) params.status = status;
    if (from) params.from = from;
    if (to) params.to = to;
    return (await http.get(`${BASE}/history`, { params }).then(unwrap)) ?? [];
}

// Số lượng hồ sơ theo trạng thái (đếm trong DB): { pending, awaitingSignature, signed, rejected }
export const getSummary = () => http.get(`${BASE}/summary`).then(unwrap);

// D2: trao đổi tin nhắn với Executor
export const getMessages = (id) => http.get(`${BASE}/${id}/messages`).then(unwrap);

export const sendMessage = (id, content, file) => {
    const form = new FormData();
    if (content) form.append('content', content);
    if (file) form.append('file', file);
    return http.post(`${BASE}/${id}/messages`, form).then(unwrap);
};

export const getAttachmentBlob = (id, messageId) =>
    http.get(`${BASE}/${id}/messages/${messageId}/attachment`, { responseType: 'blob' });
