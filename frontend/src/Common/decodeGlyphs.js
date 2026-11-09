// Bộ ký tự ngẫu nhiên cho hiệu ứng giải mã: chỉ chữ cái và số (không ký tự đặc biệt)
const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

export const randGlyph = () => GLYPHS[Math.floor(Math.random() * GLYPHS.length)];
