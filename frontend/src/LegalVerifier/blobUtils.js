import { getErrorMessage } from '../api/http.js';

// Nạp blob vào tab đã mở sẵn, rồi thu hồi URL sau 1 phút để khỏi rò bộ nhớ
export function showBlobInTab(tab, blob) {
    const url = URL.createObjectURL(blob);
    tab.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

// Với responseType 'blob', JSON lỗi của BE cũng bị bọc trong Blob -> đọc ra để lấy đúng message
export async function getBlobErrorMessage(error, fallback) {
    const data = error.response?.data;
    if (data instanceof Blob) {
        try {
            const body = JSON.parse(await data.text());
            return getErrorMessage({ response: { data: body } }, fallback);
        } catch {
            return fallback;
        }
    }
    return getErrorMessage(error, fallback);
}
