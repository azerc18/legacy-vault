import { useEffect, useState } from 'react';
import { getUser } from '../auth/auth.js';
import { paymentApi } from '../api/paymentApi.js';

const CACHE_KEY = 'lv_sub_cache';
const CACHE_MS = 5 * 60 * 1000;
const SUB_EVENT = 'lv:sub-updated';   // InteractiveVaultLock cũng phát và nghe sự kiện này

function cachedVip() {
    try {
        const { data, timestamp } = JSON.parse(sessionStorage.getItem(CACHE_KEY));
        return data && Date.now() - timestamp < CACHE_MS ? Boolean(data.vip) : null;
    } catch {
        return null;   // chưa có cache, cache hỏng hoặc sessionStorage bị chặn
    }
}

let pending = null;   // Header và widget cú cùng hỏi một lúc thì chỉ gọi API một lần
function refreshVip() {
    pending ??= paymentApi.getMySubscription()
        .then((data) => {
            if (!data) return;
            try {
                sessionStorage.setItem(CACHE_KEY, JSON.stringify({ data, timestamp: Date.now() }));
            } catch {
                // sessionStorage có thể bị chặn
            }
            window.dispatchEvent(new CustomEvent(SUB_EVENT, { detail: Boolean(data.vip) }));
        })
        .catch(() => {})
        .finally(() => { pending = null; });
}

// true/false = đã biết gói của Owner; null = chưa biết hoặc tài khoản không phải Owner (không mua được VIP)
export function useVipStatus() {
    const user = getUser();
    const userId = user?.id;
    const isOwner = Array.isArray(user?.roles) && user.roles.includes('OWNER');
    const [vip, setVip] = useState(() => (isOwner ? cachedVip() : null));

    useEffect(() => {
        if (!isOwner) {
            setVip(null);
            return undefined;
        }
        const cached = cachedVip();
        setVip(cached);
        if (cached === null) refreshVip();
        const onUpdate = (e) => {
            if (typeof e.detail === 'boolean') setVip(e.detail);
        };
        window.addEventListener(SUB_EVENT, onUpdate);
        return () => window.removeEventListener(SUB_EVENT, onUpdate);
    }, [userId, isOwner]);

    return vip;
}
