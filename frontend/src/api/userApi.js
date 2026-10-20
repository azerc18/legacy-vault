import http from './http.js';

// Hồ sơ của người dùng đang đăng nhập (BE: UserController /api/users/me)
export const userApi = {
    // { id, email, fullName, phoneNumber, status, roles[], createdAt, lastLoginAt }
    getMe: () => http.get('/users/me').then((r) => r.data.data),
    updateMe: (fullName, phoneNumber) =>
        http.put('/users/me', { fullName, phoneNumber }).then((r) => r.data.data),
    changePassword: (currentPassword, newPassword, passwordConfirm) =>
        http.post('/users/me/change-password', { currentPassword, newPassword, passwordConfirm }),
};
