import http from './http.js';

export const paymentApi = {
    getMySubscription: () =>
        http.get('/subscriptions/me').then((r) => r.data.data),

    getMyPayments: () =>
        http.get('/payments/me').then((r) => r.data.data),

    checkout: (planCode) =>
        http.post('/payments/checkout', { planCode }).then((r) => r.data.data),
};

