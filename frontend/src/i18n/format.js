import i18n from './index.js';

const LOCALE = { en: 'en-GB', vi: 'vi-VN' };
const locale = () => LOCALE[i18n.resolvedLanguage] || 'en-GB';

// Ứng dụng luôn hiển thị giờ Việt Nam, kể cả khi trình duyệt đặt múi giờ khác.
export const APP_TIME_ZONE = 'Asia/Ho_Chi_Minh';

// Ngày giờ có giờ nhưng không kèm múi giờ ("2026-10-16T10:30:00", LocalDateTime từ BE hoặc ô datetime-local).
const NO_OFFSET = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/;

// Đọc mốc giờ từ BE thành Date. LocalDateTime của BE là giờ VN (UTC+7) nên gắn +07:00 thay vì để trình duyệt
// tự hiểu theo múi giờ máy; Instant "...Z" giữ nguyên. Trả null nếu trống/không hợp lệ.
export function parseServerDate(value) {
    if (value == null || value === '') return null;
    if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
    const text = typeof value === 'string' && NO_OFFSET.test(value) ? `${value.replace(' ', 'T')}+07:00` : value;
    const d = new Date(text);
    return Number.isNaN(d.getTime()) ? null : d;
}

// Ngày "YYYY-MM-DD" theo giờ VN (không theo UTC như toISOString, không theo múi giờ máy khách)
const DATE_KEY = new Intl.DateTimeFormat('en-CA', { timeZone: APP_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' });
export const appDateKey = (date = new Date()) => DATE_KEY.format(date);

// Giờ trong ngày (0-23) theo giờ VN
const HOUR = new Intl.DateTimeFormat('en-GB', { timeZone: APP_TIME_ZONE, hour: '2-digit', hourCycle: 'h23' });
export const appHour = (date = new Date()) => Number(HOUR.format(date));

// Mili giây của mốc giờ BE (để so sánh/sắp xếp); 0 nếu trống.
export const serverTime = (value) => parseServerDate(value)?.getTime() ?? 0;

// LocalDateTime "2026-10-16T10:30:00" (giờ VN) hoặc Instant "...Z" -> chuỗi theo giờ VN
export function formatDate(value, withTime = false) {
    const d = parseServerDate(value);
    if (!d) return '—';
    return withTime
        ? d.toLocaleString(locale(), { dateStyle: 'medium', timeStyle: 'short', timeZone: APP_TIME_ZONE })
        : d.toLocaleDateString(locale(), { dateStyle: 'medium', timeZone: APP_TIME_ZONE });
}

// "VAULT_CREATED" -> "Vault created" (dùng làm chữ dự phòng khi chưa có bản dịch)
export const humanize = (s) => (s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : '—');

// T1: dung lượng (byte) -> "5 GB" / "50 MB" (đơn vị nhị phân, làm tròn), dùng chung cho các trang gói cước
export function formatStorage(bytes) {
    if (typeof bytes !== 'number' || bytes <= 0) return '—';
    const gb = bytes / (1024 * 1024 * 1024);
    if (gb >= 1) return `${Math.round(gb)} GB`;
    return `${Math.round(bytes / (1024 * 1024))} MB`;
}

// Nhãn enum từ enums.json: tEnum('assetType', 'BANK_ACCOUNT'). Chưa dịch thì hiện humanize.
export const tEnum = (group, value) =>
    value ? i18n.t(`${group}.${value}`, { ns: 'enums', defaultValue: humanize(value) }) : '—';