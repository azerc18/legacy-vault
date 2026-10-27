export const PASSWORD_RULES = [
    { key: 'length',  label: '8+ characters',     test: (p) => p.length >= 8 && p.length <= 72 },
    { key: 'lower',   label: 'Lowercase letter',  test: (p) => /[a-z]/.test(p) },
    { key: 'upper',   label: 'Uppercase letter',  test: (p) => /[A-Z]/.test(p) },
    { key: 'digit',   label: 'Number',            test: (p) => /\d/.test(p) },
    { key: 'special', label: 'Special character', test: (p) => /[^A-Za-z0-9]/.test(p) },
];
export const LEVELS = ['Very weak', 'Weak', 'Medium', 'Strong', 'Very strong'];

// 0..4 (-1 = rỗng). Đạt hết 5 rule = Strong; thêm >= 12 ký tự = Very strong
export function passwordLevel(p) {
    if (!p) return -1;
    const passed = PASSWORD_RULES.filter((r) => r.test(p)).length;
    if (passed === 5) return p.length >= 12 ? 4 : 3;
    return passed >= 4 ? 2 : passed === 3 ? 1 : 0;
}
export const isPasswordValid = (p) => PASSWORD_RULES.every((r) => r.test(p));