// Icon nét theo loại tài sản (24px, nét 1.75, currentColor) - bộ icon riêng của nhận diện "Công chứng".
// Tên khớp ASSET_TYPES trong ownerUi.jsx. Khi có bộ SVG chính thức (lv-asset-*.svg) thì thay path ở đây.
import React from 'react';

const PATHS = {
    BANK_ACCOUNT: (
        <>
            <path d="M3 9.5 12 4l9 5.5"/>
            <path d="M4.5 10h15M6.5 10v7M10.2 10v7M13.8 10v7M17.5 10v7M4.5 17.5h15M3.5 20h17"/>
        </>
    ),
    CRYPTO_WALLET: (
        <>
            <rect x="3.5" y="7" width="17" height="10" rx="2"/>
            <rect x="6.5" y="9.75" width="7" height="4.5" rx="0.75"/>
            <circle cx="16.75" cy="12" r="1.6"/>
        </>
    ),
    SOCIAL_MEDIA: (
        <>
            <path d="M4 5h16v11H10l-4.5 3.5V16H4z"/>
            <circle cx="12" cy="9" r="1.9"/>
            <path d="M8.6 13.6c.8-1.4 2-2.1 3.4-2.1s2.6.7 3.4 2.1"/>
        </>
    ),
    DOCUMENT: (
        <>
            <path d="M6 3h8.5L18 6.5V21H6z"/>
            <path d="M14 3v4h4M9 9.5h3M9 13h6M9 16.5h6"/>
        </>
    ),
    EXCHANGE_ACCOUNT: (
        <>
            <path d="M4 8.5h14M14.5 5 18 8.5 14.5 12"/>
            <path d="M20 15.5H6M9.5 12 6 15.5 9.5 19"/>
        </>
    ),
    E_WALLET: (
        <>
            <rect x="6.5" y="2.75" width="11" height="18.5" rx="2.25"/>
            <rect x="9" y="6.5" width="6" height="4" rx="0.75"/>
            <path d="M10.5 17.75h3"/>
        </>
    ),
    EMAIL: (
        <>
            <rect x="3.5" y="5.75" width="17" height="12.5" rx="1.75"/>
            <path d="m4.25 7 7.75 6 7.75-6"/>
        </>
    ),
    OTHER: (
        <>
            <path d="M3.75 12.4V4.5h7.9l8.6 8.6-7.9 7.9z"/>
            <circle cx="8.25" cy="9" r="1.5"/>
        </>
    ),
};

export function AssetTypeIcon({type, className = 'w-5 h-5'}) {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
             strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
            {PATHS[type] || PATHS.OTHER}
        </svg>
    );
}
