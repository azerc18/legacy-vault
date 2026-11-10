// Danh sách trang phục cho bảng chọn (ChatPanel dùng) - tách riêng để không kéo `three` vào bundle chính
// vip: true = Owner gói thường phải nâng cấp mới mặc được
export const COSTUMES = [
    { id: 'none', label: 'Original', emoji: '🦉' },
    { id: 'graduate', label: 'Graduate', emoji: '🎓' },
    { id: 'panda', label: 'Panda', emoji: '🐼' },
    { id: 'fpt', label: 'FPT', emoji: '🏫' },
    { id: 'wizard', label: 'Wizard', emoji: '🧙', vip: true },
    { id: 'king', label: 'King', emoji: '👑', vip: true },
    { id: 'detective', label: 'Detective', emoji: '🕵️', vip: true },
    { id: 'astronaut', label: 'Astronaut', emoji: '🚀', vip: true },
    { id: 'ninja', label: 'Ninja', emoji: '🥷', vip: true },
    { id: 'santa', label: 'Santa', emoji: '🎅', vip: true },
    { id: 'chef', label: 'Chef', emoji: '👨‍🍳', vip: true },
    { id: 'knight', label: 'Knight', emoji: '🛡️', vip: true },
    { id: 'devil', label: 'Devil', emoji: '😈', vip: true },
    { id: 'spy', label: 'Spy', emoji: '🕶️', vip: true },
    { id: 'whitemage', label: 'White Mage', emoji: '🪄', vip: true },
    { id: 'beach', label: 'Beach', emoji: '🏖️', vip: true },
    { id: 'fox', label: 'Fox', emoji: '🦊', vip: true },
    { id: 'soccer', label: 'Soccer', emoji: '⚽', vip: true },
    { id: 'cyber', label: 'Cyber', emoji: '🎧', vip: true },
];

export const isVipCostume = (id) => COSTUMES.some((c) => c.id === id && c.vip);
