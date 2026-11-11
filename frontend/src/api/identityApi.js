// Xác minh danh tính Owner (KYC) — khớp OwnerIdentityController (/api/owner/identity).
import http from './http.js';
import { PROTECTION_CHANGED } from './ownerApi.js';

const unwrap = (res) => res.data?.data;
const changed = (result) => {
    window.dispatchEvent(new Event(PROTECTION_CHANGED));
    return result;
};

export const identityApi = {
    // -> { kycLevel, verified, source, citizenIdMasked, hasOldIdNumber, fullName, dateOfBirth, gender,
    //      residenceProvince, verifiedAt, accountFullName, phoneNumber, phoneVerified, locked, provinces: [{code,name}] }
    getStatus: () => http.get('/owner/identity').then(unwrap),

    // Đọc chuỗi QR CCCD (không lưu) -> { source, citizenId, oldIdNumber, fullName, dateOfBirth, gender, residenceProvince, warnings }
    extractFromQr: (qrText) => http.post('/owner/identity/extract/qr', { qrText }).then(unwrap),

    // Đọc ảnh CCCD bằng AI (không lưu ảnh); cần aiOcrConsent = true
    extractFromImage: (file, aiOcrConsent) => {
        const form = new FormData();
        form.append('file', file);
        form.append('aiOcrConsent', String(Boolean(aiOcrConsent)));
        return http.post('/owner/identity/extract/image', form).then(unwrap);
    },

    // -> { maskedPhone, carrier, expiresAt, resendAfterSeconds, sandboxCode? }
    sendPhoneOtp: (phoneNumber) => http.post('/owner/identity/phone/otp', { phoneNumber }).then(unwrap),
    verifyPhoneOtp: (code) => http.post('/owner/identity/phone/verify', { code }).then(unwrap),

    // { citizenId, oldIdNumber, fullName, dateOfBirth (yyyy-MM-dd), gender, residenceProvince, source,
    //   dataConsent, aiOcrConsent, updateAccountName } -> trạng thái mới
    submit: (payload) => http.put('/owner/identity', payload).then(unwrap).then(changed),
};
