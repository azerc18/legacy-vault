// Ô nhập mã OTP: 6 ô (mỗi ô 1 chữ số). Gõ tự nhảy ô, Backspace lùi ô, dán cả mã 6 số vào là điền hết.
// Dùng chung cho mọi chỗ nhập OTP trong dự án. `value` là chuỗi chỉ gồm chữ số (tối đa `length`).
import React, { useRef } from 'react';

export default function OtpInput({ value, onChange, length = 6, autoFocus = false, id, label, disabled = false }) {
    const refs = useRef([]);
    const digits = Array.from({ length }, (_, i) => value[i] || '');

    const focusAt = (i) => {
        const el = refs.current[Math.max(0, Math.min(length - 1, i))];
        if (el) { el.focus(); el.select(); }
    };

    const setAt = (i, ch) => {
        const next = digits.slice();
        next[i] = ch;
        onChange(next.join('').slice(0, length));
    };

    const handleChange = (i, e) => {
        const typed = e.target.value.replace(/\D/g, '');
        if (!typed) { setAt(i, ''); return; }
        if (typed.length > 1) {                        // trình duyệt tự điền / dán nhiều số vào 1 ô
            const merged = (value.slice(0, i) + typed).slice(0, length);
            onChange(merged);
            focusAt(Math.min(merged.length, length - 1));
            return;
        }
        setAt(i, typed);
        if (i < length - 1) focusAt(i + 1);
    };

    const handleKeyDown = (i, e) => {
        if (e.key === 'Backspace' && !digits[i] && i > 0) {
            e.preventDefault();
            setAt(i - 1, '');
            focusAt(i - 1);
        } else if (e.key === 'ArrowLeft') { e.preventDefault(); focusAt(i - 1); }
        else if (e.key === 'ArrowRight') { e.preventDefault(); focusAt(i + 1); }
    };

    const handlePaste = (e) => {
        const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length);
        if (!pasted) return;
        e.preventDefault();
        onChange(pasted);
        focusAt(Math.min(pasted.length, length - 1));
    };

    return (
        <div role="group" aria-label={label} className="flex justify-center gap-2 sm:gap-3">
            {digits.map((d, i) => (
                <input
                    key={i}
                    id={i === 0 ? id : undefined}
                    ref={(el) => { refs.current[i] = el; }}
                    value={d}
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    autoComplete={i === 0 ? 'one-time-code' : 'off'}
                    autoFocus={autoFocus && i === 0}
                    disabled={disabled}
                    maxLength={length}
                    aria-label={label ? `${label} ${i + 1}/${length}` : `${i + 1}/${length}`}
                    onChange={(e) => handleChange(i, e)}
                    onKeyDown={(e) => handleKeyDown(i, e)}
                    onPaste={handlePaste}
                    onFocus={(e) => e.target.select()}
                    className="w-11 h-14 sm:w-12 sm:h-14 rounded-xl border border-outline-variant bg-background text-center text-2xl font-semibold text-on-surface focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/30 disabled:opacity-60"
                />
            ))}
        </div>
    );
}
