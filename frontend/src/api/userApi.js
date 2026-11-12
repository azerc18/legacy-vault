import http from './http.js';

// Hồ sơ của người dùng đang đăng nhập (BE: UserController /api/users/me)
export const userApi = {
    // { id, email, fullName, phoneNumber, preferredLanguage, status, roles[], createdAt, lastLoginAt, hasAvatar, avatarUpdatedAt }
    getMe: () => http.get('/users/me').then((r) => r.data.data),
    // preferredLanguage: 'vi' | 'en' (ngôn ngữ email/hóa đơn gửi cho người dùng); bỏ trống = giữ nguyên
    updateMe: (fullName, phoneNumber, preferredLanguage) =>
        http.put('/users/me', { fullName, phoneNumber, preferredLanguage }).then((r) => r.data.data),
    changePassword: (currentPassword, newPassword, passwordConfirm) =>
        http.post('/users/me/change-password', { currentPassword, newPassword, passwordConfirm }),
    // Tài khoản tạo bằng Google chưa có mật khẩu (profile.passwordSet = false): cài đặt, không hỏi mật khẩu hiện tại
    // nhưng phải có OTP gửi tới email (sendSetPasswordOtp), và mã 2FA nếu đã bật.
    // Trả lại hồ sơ đã cập nhật (passwordSet = true); không đăng xuất.
    sendSetPasswordOtp: () => http.post('/users/me/set-password/otp'),
    setPassword: ({ newPassword, passwordConfirm, otp, code }) =>
        http.post('/users/me/set-password', { newPassword, passwordConfirm, otp, code: code || undefined })
            .then((r) => r.data.data),

    // ---- Avatar ----
    // Ảnh cần Bearer token nên không dùng <img src="/api/..."> trực tiếp được -> tải blob rồi tạo object URL
    getAvatarUrl: () =>
        http.get('/users/me/avatar', { responseType: 'blob' }).then((r) => URL.createObjectURL(r.data)),
    uploadAvatar: (file) => {
        const form = new FormData();
        form.append('file', file);
        return http.put('/users/me/avatar', form).then((r) => r.data.data);
    },
    deleteAvatar: () => http.delete('/users/me/avatar').then((r) => r.data.data),
    // ---- 2FA (H4) ----
    // { secret, otpauthUri, qrDataUri }
    setup2fa: () => http.post('/users/me/2fa/setup').then((r) => r.data.data),
    // { backupCodes: [...] } — chỉ trả 1 lần
    enable2fa: (code) => http.post('/users/me/2fa/enable', { code }).then((r) => r.data.data),
    disable2fa: (code) => http.post('/users/me/2fa/disable', { code }),
};

// Phần thông tin hồ sơ lưu vào localStorage (lv_user) để header hiển thị
export const toStoredUser = (me) => ({
    name: me.fullName,
    hasAvatar: !!me.hasAvatar,
    avatarVersion: me.avatarUpdatedAt || null,
});
