import http from './http.js';

// Xoá tài khoản (BE: AccountDeletionController /api/users/me/account)
export const accountApi = {
    // { deletable, blockers: ['VAULTS_OWNED' | 'EXECUTOR' | 'BENEFICIARY' | 'STAFF_ROLE'], vaultCount, mfaRequired }
    deletionCheck: () => http.get('/users/me/account/deletion-check').then((r) => r.data.data),
    // code: mã 2FA hoặc mã dự phòng, chỉ cần khi đã bật 2FA
    deleteAccount: ({ password, confirmEmail, code }) =>
        http.post('/users/me/account/delete', { password, confirmEmail, code: code || null }),
};
