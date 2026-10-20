import http from './http.js';

// Hồ sơ của người dùng đang đăng nhập (BE: UserController /api/users/me)
export const userApi = {
    // { id, email, fullName, phoneNumber, status, roles[], createdAt, lastLoginAt, hasAvatar, avatarUpdatedAt }
    getMe: () => http.get('/users/me').then((r) => r.data.data),
    updateMe: (fullName, phoneNumber) =>
        http.put('/users/me', { fullName, phoneNumber }).then((r) => r.data.data),
    changePassword: (currentPassword, newPassword, passwordConfirm) =>
        http.post('/users/me/change-password', { currentPassword, newPassword, passwordConfirm }),

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
};

// Phần thông tin hồ sơ lưu vào localStorage (lv_user) để header hiển thị
export const toStoredUser = (me) => ({
    name: me.fullName,
    hasAvatar: !!me.hasAvatar,
    avatarVersion: me.avatarUpdatedAt || null,
});
