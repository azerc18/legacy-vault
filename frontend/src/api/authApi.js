import http from './http.js';

export const authApi = {
    register: (body) => http.post('/auth/register', body),   // body có thể kèm captchaToken
    sendOtp: (email) => http.post('/auth/send-otp', { email }),
    verifyEmail: (email, otp) => http.post('/auth/verify-email', { email, otp }),
    // trả về { accessToken, tokenType, expiresAt }
    // captchaToken: chỉ cần khi BE đã trả captchaRequired (tài khoản sai mật khẩu nhiều lần)
    login: (email, password, captchaToken) =>
        http.post('/auth/login', { email, password, captchaToken: captchaToken || undefined }).then((r) => r.data.data),
    forgotPassword: (email, captchaToken) =>
        http.post('/auth/forgot-password', { email, captchaToken: captchaToken || undefined }),
    resetPassword: (email, otp, newPassword, passwordConfirm) =>
        http.post('/auth/reset-password', { email, otp, newPassword, passwordConfirm }),
    // H4: bước 2 khi login trả { mfaRequired: true, mfaToken }
    loginMfa: (mfaToken, code) =>
        http.post('/auth/login/2fa', { mfaToken, code }).then((r) => r.data.data),
    // Đăng nhập Google: credential = ID token từ Google Identity Services. Trả giống login (có thể mfaRequired)
    // language ('vi' | 'en'): ngôn ngữ giao diện, BE chỉ dùng khi lần này tạo tài khoản mới (làm ngôn ngữ email/hóa đơn)
    loginGoogle: (credential, language) =>
        http.post('/auth/google', { credential, language }).then((r) => r.data.data),
    // { enabled, clientId } — BE chưa đặt GOOGLE_CLIENT_ID thì enabled = false
    googleConfig: () => http.get('/auth/google/config').then((r) => r.data.data),
    // { enabled, siteKey, loginAfterFailures } — BE chưa bật APP_CAPTCHA_ENABLED thì enabled = false
    captchaConfig: () => http.get('/auth/captcha/config').then((r) => r.data.data),
};