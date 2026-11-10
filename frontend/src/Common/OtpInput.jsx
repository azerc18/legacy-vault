// Ô nhập mã OTP: 6 ô (mỗi ô 1 chữ số). Gõ tự nhảy ô, Backspace lùi ô, dán cả mã 6 số vào là điền hết.
// Dùng chung cho mọi chỗ nhập OTP trong dự án. `value` là chuỗi chỉ gồm chữ số (tối đa `length`).
import React, { useLayoutEffect, useRef } from 'react';

export default function OtpInput({ value, onChange, length = 6, autoFocus = false, id, label, disabled = false }) {
    const refs = useRef([]);
    const digits = Array.from({ length }, (_, i) => value[i] || '');

    // Giữ giá trị mới nhất trong ref: gõ nhanh thì onChange + focusAt chạy trước khi React render lại,
    // nếu đọc `value`/`digits` của lần render trước sẽ ghi đè mất số vừa gõ (gõ 123456 ra 246).
    const latest = useRef(value);
    useLayoutEffect(() => { latest.current = value; }, [value]);
    const emit = (next) => { latest.current = next; onChange(next); };

    const focusAt = (i) => {
        const el = refs.current[Math.max(0, Math.min(length - 1, i))];
        if (el) { el.focus(); el.select(); }
    };

    const setAt = (i, ch) => {
        const cur = latest.current;
        const next = Array.from({ length }, (_, k) => cur[k] || '');
        next[i] = ch;
        emit(next.join('').slice(0, length));
    };

    const handleChange = (i, e) => {
        const typed = e.target.value.replace(/\D/g, '');
        if (!typed) { setAt(i, ''); return; }
        // "Dán / tự điền" chỉ chạy khi nhận đủ cả mã; còn lại là gõ tay từng số.
        if (typed.length >= length) {
            const full = typed.slice(0, length);
            emit(full);
            focusAt(full.length - 1);
            return;
        }
        // Ô đang có số mà gõ tiếp: ô chứa 2 ký tự (số cũ + số mới) -> chỉ lấy số vừa gõ, bỏ số cũ.
        let ch = typed[typed.length - 1];
        const old = latest.current[i] || '';
        if (typed.length > 1 && old) {
            const rest = typed.replace(old, '');
            ch = rest ? rest[0] : typed[typed.length - 1];
        }
        setAt(i, ch);
        if (i < length - 1) focusAt(i + 1);
    };

    const handleKeyDown = (i, e) => {
        if (e.key === 'Backspace' && !latest.current[i] && i > 0) {
            e.preventDefault();
            setAt(i - 1, '');
            focusAt(i - 1);
        } else if (e.key === 'ArrowLeft') { e.preventDefault(); focusAt(i - 1); }
        else if (e.key === 'ArrowRight') { e.preventDefault(); focusAt(i + 1); }
    };

    const handlePaste = (i, e) => {
        const pasted = e.clipboardData.getData('text').replace(/\D/g, '');
        if (!pasted) return;
        e.preventDefault();
        // Dán đủ mã -> thay hết; dán một phần -> đặt vào từ ô đang đứng, giữ nguyên các số còn lại.
        const cur = latest.current;
        const from = Math.min(i, cur.length);
        const next = pasted.length >= length
            ? pasted.slice(0, length)
            : (cur.slice(0, from) + pasted + cur.slice(from + pasted.length)).slice(0, length);
        emit(next);
        focusAt(Math.min(from + pasted.length, length - 1));
    };

    // Bấm vào ô trống phía sau: đưa con trỏ về ô trống đầu tiên để số hiện đúng chỗ con trỏ đứng.
    const handleFocus = (i, e) => {
        if (i > latest.current.length) { focusAt(latest.current.length); return; }
        e.target.select();
    };

    return (
        <div role="group" aria-label={label} className="flex w-full justify-center gap-2 sm:gap-3">
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
                    aria-label={label ? `${label} ${i + 1}/${length}` : `${i + 1}/${length}`}
                    // Gõ đúng số đang có trong ô: giá trị input không đổi nên React không gọi onChange,
                    // con trỏ đứng yên và số kế tiếp ghi đè vào ô này. onInput vẫn chạy nên nhảy ô ở đây.
                    onInput={(e) => { if (e.target.value === latest.current[i] && i < length - 1) focusAt(i + 1); }}
                    onChange={(e) => handleChange(i, e)}
                    onKeyDown={(e) => handleKeyDown(i, e)}
                    onPaste={(e) => handlePaste(i, e)}
                    onFocus={(e) => handleFocus(i, e)}
                    className="min-w-0 flex-1 max-w-12 h-14 rounded-xl border border-outline-variant bg-background text-center text-2xl font-semibold text-on-surface focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/30 disabled:opacity-60"
                />
            ))}
        </div>
    );
}
