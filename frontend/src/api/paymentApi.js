import http from './http.js';

// T2: lấy tên file từ Content-Disposition (hỗ trợ filename*=UTF-8''...)
function fileNameFrom(res, fallback) {
    const cd = res.headers['content-disposition'] || '';
    const star = cd.match(/filename\*=UTF-8''([^;]+)/i);
    if (star) return decodeURIComponent(star[1]);
    const plain = cd.match(/filename="?([^";]+)"?/i);
    return plain ? plain[1] : fallback;
}

export const paymentApi = {
    // config tuỳ chọn, vd. { skipLoginRedirect: true } cho trang công khai /payment/result
    getMySubscription: (config) =>
        http.get('/subscriptions/me', config).then((r) => r.data.data),

    // Việc 6: hủy gia hạn cuối kỳ / bật lại; trả về gói hiện tại mới (giống getMySubscription)
    cancelRenewal: () =>
        http.post('/subscriptions/me/cancel').then((r) => r.data.data),

    resumeRenewal: () =>
        http.post('/subscriptions/me/resume').then((r) => r.data.data),

    // Việc 2: đã dùng / hạn mức và mục đang vượt hạn mức (sau khi VIP hết hạn)
    getMyUsage: () =>
        http.get('/subscriptions/me/usage').then((r) => r.data.data),

    getMyPayments: () =>
        http.get('/payments/me').then((r) => r.data.data),

    checkout: (planCode, provider) =>
        http.post('/payments/checkout', { planCode, provider }).then((r) => r.data.data),

    getProviders: () =>
        http.get('/payments/providers').then((r) => r.data.data),

    // T2: tải hoá đơn mô phỏng (PDF) của giao dịch SUCCESS. Lỗi trả về dạng Blob JSON -> đọc lại để lấy message
    downloadReceipt: async (transactionId) => {
        try {
            const res = await http.get(`/payments/${transactionId}/invoice`, { responseType: 'blob' });
            const href = URL.createObjectURL(res.data);
            const a = document.createElement('a');
            a.href = href;
            a.download = fileNameFrom(res, 'hoa-don.pdf');
            a.click();
            // Safari có thể huỷ tải nếu thu hồi URL ngay sau click
            setTimeout(() => URL.revokeObjectURL(href), 1000);
        } catch (err) {
            const blob = err.response?.data;
            if (blob instanceof Blob) {
                try { err.response.data = JSON.parse(await blob.text()); } catch { /* giữ nguyên */ }
            }
            throw err;
        }
    },
};

