// Thẻ cho 3 phần trang chủ nền tối: Bảo mật, Lưu trữ được gì, Vai trò.
// Mỗi thẻ có một màu nhấn riêng (giống cách phần "Vấn đề" dùng màu theo mốc thời gian), hiệu ứng gắn với ý nghĩa:
//  - Bảo mật: tia quét chạy qua thẻ khi rê chuột (như đang kiểm tra), icon nổi khỏi mặt thẻ (3D).
//  - Lưu trữ: các ô như hộp ký gửi ngân hàng, cửa ô mở ra kiểu 3D để lộ mô tả bên trong.
//  - Vai trò: thẻ như thẻ định danh, có lớp ánh kim (hologram) chạy theo con trỏ.
// Giảm chuyển động: giữ màu, bỏ chuyển động.
import React, { useState } from 'react';
import { useReducedMotion } from 'motion/react';
import { Tilt, TiltContent } from '@animate-ui/react/primitives/effects/tilt';
import './landingCards.css';

function Icon({ name, className = '' }) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

// Ghi vị trí con trỏ (%) vào biến CSS để lớp ánh sáng đi theo chuột
const trackPointer = (e) => {
    const el = e.currentTarget;
    const r = el.getBoundingClientRect();
    el.style.setProperty('--mx', `${((e.clientX - r.left) / r.width) * 100}%`);
    el.style.setProperty('--my', `${((e.clientY - r.top) / r.height) * 100}%`);
};

export function SecurityCard({ icon, title, text, accent }) {
    const reduce = useReducedMotion();
    return (
        <Tilt maxTilt={reduce ? 0 : 6} perspective={1000} className="h-full">
            <TiltContent className="h-full">
                <div className="lv-cy-card lv-acc lv-sec h-full p-7" style={{ '--ac': accent }} onPointerMove={trackPointer}>
                    <span aria-hidden="true" className="lv-acc-clip">
                        <span className="lv-acc-glare" />
                        <span className="lv-sec-scan" />
                    </span>
                    <span aria-hidden="true" className="lv-acc-bar" />
                    <span aria-hidden="true" className="lv-sec-led" />
                    <span className="lv-acc-icon"><Icon name={icon} /></span>
                    <h3 className="lv-acc-title">{title}</h3>
                    <p className="lv-acc-text">{text}</p>
                </div>
            </TiltContent>
        </Tilt>
    );
}

// Hộp ký gửi: cửa ô mở ra kiểu 3D khi rê chuột (máy có chuột) hoặc khi bấm/chạm (giữ mở, bấm lại để đóng).
// Nội dung bên trong luôn có trong DOM nên trình đọc màn hình đọc được kể cả khi cửa đóng.
export function VaultBox({ icon, label, desc, accent }) {
    const [pinned, setPinned] = useState(false);
    return (
        // Bấm ở bất kỳ đâu trong ô để mở/đóng; phím Enter/Space trên nút cửa cũng nổi bọt lên đây
        <div className={`lv-box ${pinned ? 'is-open' : ''}`} style={{ '--ac': accent }} onClick={() => setPinned((v) => !v)}>
            <div className="lv-box-inner">
                <span className="lv-box-icon"><Icon name={icon} /></span>
                <p className="lv-box-label">{label}</p>
                <p className="lv-box-desc">{desc}</p>
            </div>
            <button type="button" className="lv-box-door" aria-expanded={pinned}
                    aria-label={label}>
                <span className="lv-box-front" aria-hidden="true">
                    <span className="lv-box-led" />
                    <span className="lv-box-dial"><i /></span>
                    <span className="lv-box-name">{label}</span>
                </span>
                <span className="lv-box-back" aria-hidden="true" />
            </button>
        </div>
    );
}

export function RoleCard({ icon, title, text, accent }) {
    const reduce = useReducedMotion();
    return (
        <Tilt maxTilt={reduce ? 0 : 8} perspective={900} className="h-full">
            <TiltContent className="h-full">
                <div className="lv-cy-card lv-acc lv-role h-full p-6" style={{ '--ac': accent }} onPointerMove={trackPointer}>
                    <span aria-hidden="true" className="lv-acc-clip">
                        <span className="lv-role-holo" />
                    </span>
                    <span aria-hidden="true" className="lv-acc-bar" />
                    <span className="lv-role-icon"><Icon name={icon} /></span>
                    <h3 className="lv-acc-title">{title}</h3>
                    <p className="lv-acc-text">{text}</p>
                </div>
            </TiltContent>
        </Tilt>
    );
}
