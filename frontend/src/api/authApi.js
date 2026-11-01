import http from './http.js';

export const authApi = {
    register: (body) => http.post('/auth/register', body),
    sendOtp: (email) => http.post('/auth/send-otp', { email }),
    verifyEmail: (email, otp) => http.post('/auth/verify-email', { email, otp }),
    // trả về { accessToken, tokenType, expiresAt }
    login: (email, password) =>
        http.post('/auth/login', { email, password }).then((r) => r.data.data),
    forgotPassword: (email) => http.post('/auth/forgot-password', { email }),
    resetPassword: (email, otp, newPassword, passwordConfirm) =>
        http.post('/auth/reset-password', { email, otp, newPassword, passwordConfirm }),
    // H4: bước 2 khi login trả { mfaRequired: true, mfaToken }
    loginMfa: (mfaToken, code) =>
        http.post('/auth/login/2fa', { mfaToken, code }).then((r) => r.data.data),
};