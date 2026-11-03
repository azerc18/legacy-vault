// X2 (C1): người xác nhận chéo gửi quyết định từ trang công khai /dms/confirm.
// Không dùng http.js: người nhận mail không đăng nhập, nên không gắn Bearer và không tự chuyển /login khi 401.
import axios from 'axios';

export const dmsConfirmApi = {
    // decision: 'ALIVE' | 'UNREACHABLE' -> { dmsStatus, decision, ownerName, postponedUntil, triggeredAt, message }
    submitDecision: (token, decision) =>
        axios.post('/api/dms/confirm', { token, decision }).then((res) => res.data?.data),
};
