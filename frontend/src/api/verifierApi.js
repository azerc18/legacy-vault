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

// UC13: kiểm tra giấy chứng tử (mock) -> { requestId, valid, message }
export const verifyDocument = (id) => http.get(`${BASE}/${id}/document/verify`).then(unwrap);

// UC12: duyệt / từ chối (lý do bắt buộc, tối đa 1000 ký tự)
export const approveRequest = (id) => http.post(`${BASE}/${id}/approve`).then(unwrap);
export const rejectRequest = (id, reason) => http.post(`${BASE}/${id}/reject`, { reason }).then(unwrap);

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
