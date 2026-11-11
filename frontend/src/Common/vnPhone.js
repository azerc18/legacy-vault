// Số di động Việt Nam: cùng quy tắc với VnPhoneNumber.java ở BE (đổi một bên thì đổi cả bên kia).
// Nhận 0912345678, +84912345678, 84912345678, +84 0912 345 678, có thể kèm khoảng trắng, dấu chấm, gạch, ngoặc.

export const MOBILE_PREFIXES = new Set([
    '032', '033', '034', '035', '036', '037', '038', '039',
    '052', '055', '056', '058', '059',
    '070', '076', '077', '078', '079',
    '081', '082', '083', '084', '085', '086', '087', '088', '089',
    '090', '091', '092', '093', '094', '096', '097', '098', '099',
]);

// Đầu số 11 chữ số cũ -> 10 chữ số (đợt chuyển đổi 2018)
const LEGACY_PREFIX = {
    '0162': '032', '0163': '033', '0164': '034', '0165': '035', '0166': '036', '0167': '037', '0168': '038', '0169': '039',
    '0120': '070', '0121': '079', '0122': '077', '0126': '076', '0128': '078',
    '0123': '083', '0124': '084', '0125': '085', '0127': '081', '0129': '082',
    '0186': '056', '0188': '058', '0199': '059',
};

const fail = (problem, suggestion = null) => ({ ok: false, problem, suggestion });

/**
 * -> { ok: true, national: '0912345678', e164: '+84912345678', display: '0912 345 678' }
 *  | { ok: false, problem: 'EMPTY'|'INVALID_CHARACTERS'|'FOREIGN'|'LANDLINE'|'LEGACY_11_DIGIT'|'INVALID_LENGTH'|'INVALID_PREFIX', suggestion }
 */
export function parseVnPhone(raw) {
    if (raw == null || !String(raw).trim()) return fail('EMPTY');
    const compact = String(raw).trim().replace(/[\s.\-()]/g, '');
    if (!/^\+?\d+$/.test(compact)) return fail('INVALID_CHARACTERS');

    const toNational = (rest) => (rest.startsWith('0') ? rest : `0${rest}`);
    let national;
    if (compact.startsWith('+')) {
        if (!compact.startsWith('+84')) return fail('FOREIGN');
        national = toNational(compact.slice(3));
    } else if (compact.startsWith('84') && (compact.length === 11 || compact.length === 12)) {
        national = toNational(compact.slice(2));
    } else if (compact.startsWith('0')) {
        national = compact;
    } else if (compact.length === 9) {
        national = `0${compact}`;
    } else {
        return fail('FOREIGN');
    }

    if (national.startsWith('02')) return fail('LANDLINE');
    if (national.length === 11 && national.startsWith('01')) {
        const p = LEGACY_PREFIX[national.slice(0, 4)];
        return fail('LEGACY_11_DIGIT', p ? p + national.slice(4) : null);
    }
    if (national.length !== 10) return fail('INVALID_LENGTH');
    if (!MOBILE_PREFIXES.has(national.slice(0, 3))) return fail('INVALID_PREFIX');
    return {
        ok: true,
        national,
        e164: `+84${national.slice(1)}`,
        display: `${national.slice(0, 4)} ${national.slice(4, 7)} ${national.slice(7)}`,
    };
}

/** Hiển thị số đã lưu (thường là E.164) theo dạng 0912 345 678; số cũ không đúng chuẩn thì giữ nguyên. */
export function displayVnPhone(stored) {
    if (!stored) return '';
    const p = parseVnPhone(stored);
    return p.ok ? p.display : stored;
}

/** Tự chèn khoảng trắng khi gõ số bắt đầu bằng 0: 0912345678 -> 0912 345 678. Cách viết khác giữ nguyên. */
export function formatPhoneInput(value) {
    const v = String(value ?? '');
    if (!/^0[\d\s]*$/.test(v)) return v;
    const d = v.replace(/\s/g, '').slice(0, 11);
    if (d.length <= 4) return d;
    if (d.length <= 7) return `${d.slice(0, 4)} ${d.slice(4)}`;
    return `${d.slice(0, 4)} ${d.slice(4, 7)} ${d.slice(7)}`;
}

/** Cùng một số (khác cách viết) thì coi như không đổi. */
export function samePhone(a, b) {
    const pa = parseVnPhone(a);
    const pb = parseVnPhone(b);
    if (pa.ok && pb.ok) return pa.e164 === pb.e164;
    return String(a ?? '').trim() === String(b ?? '').trim();
}
