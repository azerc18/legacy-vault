// Kiểm CCCD 12 số trước khi gửi; cùng quy tắc với CitizenId.java ở BE (BE kiểm lại).
// 3 số đầu: mã tỉnh nơi đăng ký khai sinh; số thứ 4: giới tính + thế kỷ; số 5-6: 2 số cuối năm sinh.

const BIRTH_PROVINCE_CODES = new Set([
    '001', '002', '004', '006', '008', '010', '011', '012', '014', '015', '017', '019', '020', '022',
    '024', '025', '026', '027', '030', '031', '033', '034', '035', '036', '037', '038', '040', '042',
    '044', '045', '046', '048', '049', '051', '052', '054', '056', '058', '060', '062', '064', '066',
    '067', '068', '070', '072', '074', '075', '077', '079', '080', '082', '083', '084', '086', '087',
    '089', '091', '092', '093', '094', '095', '096',
]);

const CENTURY = [1900, 2000, 2100, 2200, 1800];

export const digitsOnly = (s) => String(s ?? '').replace(/\D/g, '');

/**
 * Trả mã lỗi (khoá i18n identity:review.errors.*) hoặc null nếu hợp lệ.
 * form: { citizenId, oldIdNumber, fullName, dateOfBirth (yyyy-MM-dd), gender, residenceProvince }
 */
export function validateIdentity(form, today = new Date()) {
    const id = digitsOnly(form.citizenId);
    if (id.length !== 12) return 'citizenIdLength';
    if (!BIRTH_PROVINCE_CODES.has(id.slice(0, 3))) return 'citizenIdProvince';

    const name = (form.fullName || '').trim();
    if (name.length < 2 || name.length > 100) return 'nameLength';

    if (!form.dateOfBirth) return 'dobRequired';
    const [y, m, d] = form.dateOfBirth.split('-').map(Number);
    const dob = new Date(y, m - 1, d);
    if (Number.isNaN(dob.getTime()) || y < 1900 || dob >= today) return 'dobInvalid';
    const eighteen = new Date(y + 18, m - 1, d);
    if (eighteen > today) return 'underage';

    const genderDigit = Number(id[3]);
    if (CENTURY[Math.floor(genderDigit / 2)] + Number(id.slice(4, 6)) !== y) return 'citizenIdYear';
    if (!form.gender) return 'genderRequired';
    if ((genderDigit % 2 === 0 ? 'MALE' : 'FEMALE') !== form.gender) return 'citizenIdGender';

    const old = digitsOnly(form.oldIdNumber);
    if (old && old !== id && old.length !== 9) return 'oldIdLength';

    if (!form.residenceProvince) return 'provinceRequired';
    return null;
}

/** So họ tên không phân biệt dấu, hoa thường, khoảng trắng (giống VnText.sameName ở BE). */
export function sameName(a, b) {
    const fold = (s) => String(s ?? '').normalize('NFD').replace(/\p{M}+/gu, '')
        .replace(/đ/g, 'd').replace(/Đ/g, 'D').trim().replace(/\s+/g, ' ').toUpperCase();
    const fa = fold(a);
    return fa !== '' && fa === fold(b);
}
