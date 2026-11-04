// F2: Owner check-in một chạm từ trang công khai /dms/checkin (mở từ link trong email nhắc).
// Không dùng http.js: Owner bấm từ hộp thư nên có thể chưa đăng nhập; không gắn Bearer, không tự chuyển /login khi 401.
import axios from 'axios';

export const dmsCheckinApi = {
    // -> { checkinDueAt, triggerAt }
    checkInWithToken: (token) =>
        axios.post('/api/dms/checkin-link', { token }).then((res) => res.data?.data),
};
