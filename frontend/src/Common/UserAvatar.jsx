// Ảnh đại diện dùng chung (header các role + trang /me).
// Chưa có ảnh -> hiện chữ cái đầu của tên. Có ảnh -> tải 1 lần, các header dùng lại cache.
import React, { useEffect, useState } from 'react';
import { userApi } from '../api/userApi.js';

// Cache theo phiên bản ảnh (avatarUpdatedAt) để chuyển trang không phải tải lại
let cache = { version: null, url: null, promise: null };

function loadAvatar(version) {
    if (cache.version === version && (cache.url || cache.promise)) {
        return cache.url ? Promise.resolve(cache.url) : cache.promise;
    }
    if (cache.url) URL.revokeObjectURL(cache.url);
    const promise = userApi.getAvatarUrl().then((url) => {
        if (cache.promise === promise) cache.url = url;
        return url;
    });
    cache = { version, url: null, promise };
    return promise;
}

// Gọi sau khi đăng xuất / xoá ảnh để không hiện ảnh cũ
export function clearAvatarCache() {
    if (cache.url) URL.revokeObjectURL(cache.url);
    cache = { version: null, url: null, promise: null };
}

const SIZES = {
    sm: 'w-8 h-8 text-sm',
    lg: 'w-24 h-24 text-3xl',
};

const initials = (name = '') =>
    name.trim().split(/\s+/).filter(Boolean).slice(-2).map((w) => w[0].toUpperCase()).join('') || '?';

export default function UserAvatar({ name, hasAvatar, version, size = 'sm', previewUrl }) {
    const [url, setUrl] = useState(hasAvatar && cache.version === version ? cache.url : null);

    useEffect(() => {
        if (previewUrl || !hasAvatar) { setUrl(null); return; }
        let alive = true;
        loadAvatar(version)
            .then((u) => alive && setUrl(u))
            .catch(() => alive && setUrl(null)); // lỗi tải ảnh -> quay về chữ cái
        return () => { alive = false; };
    }, [hasAvatar, version, previewUrl]);

    const src = previewUrl || url;
    const cls = `${SIZES[size] || SIZES.sm} rounded-full shrink-0 overflow-hidden`;

    return src ? (
        <img src={src} alt={name || 'Avatar'} className={`${cls} object-cover`} />
    ) : (
        <span className={`${cls} bg-primary-fixed text-primary flex items-center justify-center font-bold`}>
            {initials(name)}
        </span>
    );
}
