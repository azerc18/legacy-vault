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
    // Đăng nhập Google: credential = ID token từ Google Identity Services. Trả giống login (có thể mfaRequired)
    loginGoogle: (credential) =>
        http.post('/auth/google', { credential }).then((r) => r.data.data),
    // { enabled, clientId } — BE chưa đặt GOOGLE_CLIENT_ID thì enabled = false
    googleConfig: () => http.get('/auth/google/config').then((r) => r.data.data),
};