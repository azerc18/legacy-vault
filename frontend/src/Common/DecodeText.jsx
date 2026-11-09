// Hiệu ứng chữ "giải mã" dùng chung (trang đăng nhập, trang chủ).
import React, { useEffect, useState } from 'react';
import './decodeText.css';
import { randGlyph } from './decodeGlyphs.js';

/* ---------------- Tiêu đề "giải mã" từng dòng rồi lặp lại ----------------
 * Mỗi dòng: ký tự ngẫu nhiên (chỉ chữ cái và số) nhảy liên tục rồi lần lượt "khoá" thành chữ thật
 * từ trái sang phải; dòng sau bắt đầu trễ LINE_GAP. Giải mã xong giữ HOLD giây, rồi cả tiêu đề
 * mờ đi cùng lúc (FADE) và bắt đầu vòng mới. Hợp với ý "mã hoá đầu cuối" của sản phẩm.
 * Cập nhật ~30 khung/giây bằng requestAnimationFrame (tab ẩn thì trình duyệt tự dừng). */
const DECODE = 1.1;      // giây để giải mã xong một dòng
const LINE_GAP = 0.35;   // dòng sau bắt đầu trễ
const HOLD = 3;          // giữ nguyên để đọc (tính từ lúc dòng cuối giải mã xong)
const FADE = 0.45;       // cả tiêu đề mờ đi cùng lúc
const PAUSE = 0.35;      // nghỉ trống trước vòng mới

const easeOut = (x) => 1 - Math.pow(1 - x, 3);

/** Một dòng tại thời điểm u (giây, tính từ lúc dòng này bắt đầu); trả về [phần đã giải mã, phần đang nhảy]. */
function frameOf(text, u) {
    const n = text.length;
    if (u < 0) return ['', ''];
    if (u >= DECODE) return [text, ''];
    const scramble = (from, to) => [...text.slice(from, to)].map((c) => (c === ' ' ? ' ' : randGlyph())).join('');
    // Ký tự hiện dần (đang nhảy) và khoá dần, khoá chậm hơn hiện một nhịp
    const shown = Math.ceil(n * easeOut(Math.min(1, u / (DECODE * 0.55))));
    const locked = Math.floor(n * easeOut(Math.max(0, (u - DECODE * 0.25) / (DECODE * 0.75))));
    return [text.slice(0, locked), scramble(locked, shown)];
}

export function DecodeLoop({ lines, reduce = false, startDelay = 0, hold = HOLD, noiseClassName = 'lv-decode-noise--gold' }) {
    const [state, setState] = useState(() => ({ frames: lines.map(() => ['', '']), opacity: 1 }));

    useEffect(() => {
        if (reduce) return undefined;
        const decoded = DECODE + LINE_GAP * (lines.length - 1);   // lúc dòng cuối giải mã xong
        const fadeAt = decoded + hold;
        const cycle = fadeAt + FADE + PAUSE;
        const t0 = performance.now() + startDelay * 1000;
        let raf = 0;
        let last = 0;
        const tick = (now) => {
            raf = requestAnimationFrame(tick);
            if (now - last < 33) return;   // ~30 khung/giây là đủ cho hiệu ứng nhảy chữ
            last = now;
            const elapsed = (now - t0) / 1000;
            if (elapsed < 0) return;
            const tc = elapsed % cycle;
            if (tc >= fadeAt + FADE) {   // đã mờ hết: để trống chờ vòng mới
                setState({ frames: lines.map(() => ['', '']), opacity: 1 });
                return;
            }
            setState({
                frames: lines.map((text, k) => frameOf(text, tc - k * LINE_GAP)),
                opacity: tc < fadeAt ? 1 : 1 - (tc - fadeAt) / FADE,
            });
        };
        raf = requestAnimationFrame(tick);
        return () => cancelAnimationFrame(raf);
    }, [lines, reduce, startDelay, hold]);

    if (reduce) {
        return lines.map((l) => <span key={l} className="block">{l}</span>);
    }
    return (
        <span className="block" style={{ opacity: state.opacity }} aria-hidden="true">
            {lines.map((line, k) => {
                const [done, noise] = state.frames[k] || ['', ''];
                return (
                    <span key={line} className="lv-decode-line">
                        {done}
                        <span className={`lv-decode-noise ${noiseClassName}`}>{noise}</span>
                        {!done && !noise && '\u00a0'}
                    </span>
                );
            })}
        </span>
    );
}
