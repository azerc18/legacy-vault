// Phần "Vấn đề" trang chủ: dòng thời gian 3D (Ngày 1 -> Mãi mãi) chạy lùi vào chiều sâu.
// Màu nhấn đi từ vàng cam sang đỏ theo mốc; mốc cuối chập chờn như dữ liệu đang mất.
// Chỉ dùng CSS 3D (perspective) + Tilt sẵn có, không thêm thư viện. Giảm chuyển động: thẻ phẳng.
import React, { useEffect, useRef, useState } from 'react';
import { useReducedMotion } from 'motion/react';
import { Tilt, TiltContent } from '@animate-ui/react/primitives/effects/tilt';
import './problemTimeline.css';

export default function ProblemTimeline({ items }) {
    const reduce = useReducedMotion();
    const ref = useRef(null);
    const noIO = typeof window === 'undefined' || !('IntersectionObserver' in window);
    const [shown, setShown] = useState(noIO);    // đã cuộn tới -> thẻ bay ra

    useEffect(() => {
        const el = ref.current;
        if (!el || noIO) return undefined;
        const io = new IntersectionObserver(([e]) => {
            if (e.isIntersecting) { setShown(true); io.disconnect(); }
        }, { threshold: 0.2 });
        io.observe(el);
        return () => io.disconnect();
    }, [noIO]);

    return (
        <div ref={ref} className={`lv-pt ${shown || reduce ? 'is-in' : ''} ${reduce ? 'is-still' : ''}`}>
            <div className="lv-pt-stage">
            <span aria-hidden="true" className="lv-pt-rail"><span className="lv-pt-pulse" /></span>
            <ol className="lv-pt-grid">
                {items.map((p, i) => (
                    <li key={p.key} className={`lv-pt-item lv-pt-s${i}`} style={{ '--i': i }}>
                        <span aria-hidden="true" className="lv-pt-dot" />
                        <Tilt maxTilt={reduce ? 0 : 4} perspective={900} className="h-full">
                            <TiltContent className="h-full">
                                <div className="lv-pt-card">
                                    <span className="lv-pt-icon"><span className="material-symbols-outlined">{p.icon}</span></span>
                                    <p className="lv-pt-when">{p.when}</p>
                                    <p className="lv-pt-text">{p.text}</p>
                                </div>
                            </TiltContent>
                        </Tilt>
                    </li>
                ))}
            </ol>
            </div>
        </div>
    );
}
