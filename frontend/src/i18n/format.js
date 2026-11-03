import i18n from './index.js';

const LOCALE = { en: 'en-GB', vi: 'vi-VN' };
const locale = () => LOCALE[i18n.resolvedLanguage] || 'en-GB';

// LocalDateTime "2026-10-16T10:30:00" hoặc Instant "...Z"
export function formatDate(value, withTime = false) {
    if (!value) return '—';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return '—';
    return withTime
        ? d.toLocaleString(locale(), { dateStyle: 'medium', timeStyle: 'short' })
        : d.toLocaleDateString(locale(), { dateStyle: 'medium' });
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