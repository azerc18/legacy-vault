// Giải mã QR ngay trên trình duyệt bằng jsQR (chạy được cả Safari iOS, nơi chưa có BarcodeDetector).
// Ảnh CCCD không rời khỏi máy khi đọc được QR.
import jsQR from 'jsqr';

const MAX_SIDE = 1600;

function decodeCanvas(canvas, ctx) {
    const { width, height } = canvas;
    const img = ctx.getImageData(0, 0, width, height);
    const found = jsQR(img.data, width, height, { inversionAttempts: 'attemptBoth' });
    return found?.data || null;
}

/** Đọc QR trong một file ảnh; trả chuỗi QR hoặc null nếu không thấy. */
export async function decodeQrFromFile(file) {
    if (!file || !file.type?.startsWith('image/')) return null;
    let bitmap;
    try {
        bitmap = await createImageBitmap(file);
    } catch {
        return null;   // định dạng trình duyệt không đọc được (vd. HEIC trên máy tính)
    }
    // Thử cả ảnh thu nhỏ lẫn ảnh gốc: QR nhỏ trên thẻ có thể mất chi tiết khi thu quá nhỏ
    const scales = [Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height)), 1];
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    try {
        for (const scale of [...new Set(scales)]) {
            canvas.width = Math.round(bitmap.width * scale);
            canvas.height = Math.round(bitmap.height * scale);
            ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
            const text = decodeCanvas(canvas, ctx);
            if (text) return text;
        }
        return null;
    } finally {
        bitmap.close?.();
    }
}

/** Đọc QR trong một khung hình video. */
export function decodeQrFromVideo(video, canvas) {
    if (!video.videoWidth) return null;
    const scale = Math.min(1, 960 / Math.max(video.videoWidth, video.videoHeight));
    canvas.width = Math.round(video.videoWidth * scale);
    canvas.height = Math.round(video.videoHeight * scale);
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    return decodeCanvas(canvas, ctx);
}

/** Chuỗi QR có dạng QR CCCD không (12 số rồi dấu |). Kiểm sơ bộ ở FE, BE kiểm lại đầy đủ. */
export const looksLikeCitizenQr = (text) => /^\d{12}\|/.test(text || '');
