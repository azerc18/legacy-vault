import React from 'react';

// Logo cú-ổ-khoá (nét trắng, nền trong suốt) — dùng trên nền tối/màu brand
export default function BrandMark({ className = 'h-[26px] w-[26px]' }) {
    return <img src="/brand/logo-mark.png" alt="" aria-hidden="true" draggable="false" className={`${className} object-contain shrink-0`} />;
}
