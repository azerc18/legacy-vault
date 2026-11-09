// Nền hero trang chủ: "mưa ký tự" kiểu Matrix (chữ cái + số), thỉnh thoảng một cột rơi thành
// từ khoá bảo mật (AES-256, VAULT, eKYC...). Vẽ bằng canvas, màu xanh thương hiệu, mờ dần ở giữa
// để tiêu đề và nút vẫn nổi bật (xem mặt nạ trong cipherField.css).
// Chỉ trang trí: aria-hidden, không nhận chuột; dừng khi tab ẩn; giảm chuyển động thì không vẽ.
import React, { useEffect, useRef } from 'react';
import { useReducedMotion } from 'motion/react';
import { randGlyph } from './decodeGlyphs.js';
import './cipherField.css';

const WORDS = ['AES256', 'SHA256', 'RSA4096', 'E2EE', 'TOTP', 'VAULT', 'EKYC', 'LEGACY', 'SEALED', 'TRUST', 'KEY', 'HMAC'];
const FONT_PX = 15;
const COL_GAP = 20;          // khoảng cách giữa các cột (px)
const FPS = 22;              // đủ mượt cho mưa ký tự, nhẹ máy
const WORD_CHANCE = 0.12;    // tỉ lệ một giọt mới rơi thành từ khoá

export default function CipherField() {
    const reduce = useReducedMotion();
    const ref = useRef(null);

    useEffect(() => {
        if (reduce) return undefined;
        const canvas = ref.current;
        const ctx = canvas.getContext('2d');
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        let w = 0;
        let h = 0;
        let drops = [];
        let raf = 0;
        let last = 0;

        const newDrop = (startAbove) => {
            const word = Math.random() < WORD_CHANCE ? WORDS[Math.floor(Math.random() * WORDS.length)] : null;
            return {
                y: startAbove ? -Math.random() * h : Math.random() * h,   // vị trí (px) của đầu giọt
                speed: FONT_PX * (0.45 + Math.random() * 0.75),           // px mỗi khung
                word,
                idx: 0,
            };
        };

        const resize = () => {
            const r = canvas.getBoundingClientRect();
            w = r.width; h = r.height;
            canvas.width = Math.floor(w * dpr); canvas.height = Math.floor(h * dpr);
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            const cols = Math.ceil(w / COL_GAP);
            drops = Array.from({ length: cols }, (_, i) => drops[i] || newDrop(false));
            ctx.fillStyle = '#0b1020';
            ctx.fillRect(0, 0, w, h);
        };
        resize();
        const ro = new ResizeObserver(resize);
        ro.observe(canvas);

        const draw = () => {
            // Phủ một lớp nền mờ để các ký tự cũ phai dần thành vệt
            ctx.fillStyle = 'rgba(11, 16, 32, 0.16)';
            ctx.fillRect(0, 0, w, h);
            ctx.font = `600 ${FONT_PX}px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace`;
            ctx.textAlign = 'center';
            for (let i = 0; i < drops.length; i++) {
                const d = drops[i];
                const x = i * COL_GAP + COL_GAP / 2;
                let ch;
                if (d.word) {
                    ch = d.word[d.idx % d.word.length];
                    d.idx++;
                    if (d.idx >= d.word.length) d.word = null;   // viết xong từ khoá thì quay lại ký tự ngẫu nhiên
                    ctx.fillStyle = 'rgba(233, 214, 160, 0.95)';  // từ khoá ánh vàng
                } else {
                    ch = randGlyph();
                    ctx.fillStyle = 'rgba(214, 224, 255, 0.9)';   // đầu giọt sáng
                }
                ctx.fillText(ch, x, d.y);
                // Ký tự ngay phía trên đầu giọt đổi màu xanh thương hiệu, tạo thân vệt
                ctx.fillStyle = 'rgba(111, 134, 255, 0.55)';
                ctx.fillText(randGlyph(), x, d.y - FONT_PX);
                d.y += d.speed;
                if (d.y - FONT_PX * 2 > h) drops[i] = newDrop(true);
            }
        };

        const tick = (now) => {
            raf = requestAnimationFrame(tick);
            if (now - last < 1000 / FPS) return;
            last = now;
            draw();
        };
        const start = () => { last = 0; raf = requestAnimationFrame(tick); };
        const stop = () => cancelAnimationFrame(raf);
        const onVis = () => (document.hidden ? stop() : start());
        document.addEventListener('visibilitychange', onVis);
        start();
        return () => { stop(); ro.disconnect(); document.removeEventListener('visibilitychange', onVis); };
    }, [reduce]);

    if (reduce) return null;
    return <canvas ref={ref} aria-hidden="true" className="lv-cipher-rain" />;
}
