import http from './http.js';

export const paymentApi = {
    // config tuỳ chọn, vd. { skipLoginRedirect: true } cho trang công khai /payment/result
    getMySubscription: (config) =>
        http.get('/subscriptions/me', config).then((r) => r.data.data),

    getMyPayments: () =>
        http.get('/payments/me').then((r) => r.data.data),

    checkout: (planCode) =>
        http.post('/payments/checkout', { planCode }).then((r) => r.data.data),
};

