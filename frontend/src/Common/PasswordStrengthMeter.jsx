import { PASSWORD_RULES, LEVELS, passwordLevel } from './passwordPolicy.js';

const BLOCKS = 10, FROM = [156, 163, 175], TO = [22, 163, 74];
const colorAt = (i) => `rgb(${FROM.map((f, k) => Math.round(f + (TO[k] - f) * i / (BLOCKS - 1))).join(',')})`;

export default function PasswordStrengthMeter({ password }) {
    const level = passwordLevel(password);
    if (level < 0) return null;
    const filled = (level + 1) * 2;
    return (
        <div className="mt-2 space-y-2">
            <div className="flex gap-1">
                {Array.from({ length: BLOCKS }, (_, i) => (
                    <div key={i} className="h-1.5 flex-1 rounded-full transition-colors duration-300"
                         style={{ backgroundColor: i < filled ? colorAt(i) : '#E5E7EB' }} />
                ))}
            </div>
            <p className="text-xs font-semibold" style={{ color: colorAt(filled - 1) }}>{LEVELS[level]}</p>
            <ul className="grid grid-cols-2 gap-x-3 text-xs">
                {PASSWORD_RULES.map((r) => (
                    <li key={r.key} className={r.test(password) ? 'text-green-600' : 'text-on-surface-variant'}>
                        {r.test(password) ? '✓' : '•'} {r.label}
                    </li>
                ))}
            </ul>
        </div>
    );
}