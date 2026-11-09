// Màu nhấn (bộ số rgb) cho thẻ trang chủ, theo từng mục — dùng với LandingCards.jsx
const C = {
    cyan: '92 225 255',
    amber: '255 190 92',
    violet: '170 140 255',
    green: '92 224 160',
    pink: '255 128 184',
    blue: '128 150 255',
};
export const ACCENT = {
    security: { encrypted: C.cyan, noFalseAlarm: C.amber, human: C.violet },
    assets: { passwords: C.cyan, banks: C.green, crypto: C.amber, legal: C.violet, notes: C.pink, files: C.blue },
    roles: { OWNER: C.blue, EXECUTOR: C.amber, BENEFICIARY: C.green, LEGAL_VERIFIER: C.violet },
};
