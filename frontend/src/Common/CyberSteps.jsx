// Phần "Cách hoạt động" trang chủ, phong cách cyber: 4 nút nối bằng đường mạch nét đứt, gói dữ liệu
// chạy dọc đường và lần lượt kích sáng từng nút,
// thẻ dạng bảng HUD có góc khung, thanh tiến độ nạp đầy khi cuộn tới rồi có vệt sáng chạy liên tục.
// Chỉ CSS (+1 IntersectionObserver để biết lúc cuộn tới). Giảm chuyển động: tĩnh hoàn toàn.
import React, { useEffect, useRef, useState } from 'react';
import './cyber.css';

// Nhãn trạng thái mang tính trang trí (mã kỹ thuật), giống nhau ở mọi ngôn ngữ
const STATUS = ['ENCRYPTED', 'ASSIGNED', 'MONITORING', 'VERIFIED'];

export default function CyberSteps({ items }) {
    const ref = useRef(null);
    const noIO = typeof window === 'undefined' || !('IntersectionObserver' in window);
    const [shown, setShown] = useState(noIO);

    useEffect(() => {
        const el = ref.current;
        if (!el || noIO) return undefined;
        const io = new IntersectionObserver(([e]) => {
            if (e.isIntersecting) { setShown(true); io.disconnect(); }
        }, { threshold: 0.25 });
        io.observe(el);
        return () => io.disconnect();
    }, [noIO]);

    return (
        <div ref={ref} className={`lv-cs ${shown ? 'is-in' : ''}`}>
            <span aria-hidden="true" className="lv-cs-bus"><span className="lv-cs-packet" /></span>
            <ol className="lv-cs-grid">
                {items.map((s, i) => (
                    <li key={s.key} className="lv-cs-step" style={{ '--i': i }}>
                        <span className="lv-cs-node" aria-hidden="true">
                            <span className="material-symbols-outlined">{s.icon}</span>
                        </span>
                        <div className="lv-cy-card lv-cs-card">
                            <div className="lv-cs-meta" aria-hidden="true">
                                <span className="lv-cs-status">[{STATUS[i] || 'OK'}]</span>
                                <span className="lv-cs-pct">{Math.round(((i + 1) / items.length) * 100)}%</span>
                            </div>
                            <p className="sr-only">{s.stepLabel}</p>
                            <h3 className="lv-cs-title">{s.title}</h3>
                            <p className="lv-cs-text">{s.text}</p>
                            <span aria-hidden="true" className="lv-cs-bar"><span style={{ width: `${((i + 1) / items.length) * 100}%` }} /></span>
                        </div>
                    </li>
                ))}
            </ol>
        </div>
    );
}
