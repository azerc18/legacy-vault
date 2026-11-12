// Mức dùng so với giới hạn gói của Owner (GET /subscriptions/me/usage), để chặn NGAY khi bấm "Thêm"
// thay vì để Owner nhập xong, gửi lên rồi mới bị BE từ chối (PLAN_LIMIT_REACHED).
// BE vẫn là nơi kiểm cuối cùng; hook này chỉ để báo sớm.
import { useEffect, useState } from 'react';
import { getUser } from '../auth/auth.js';
import { paymentApi } from '../api/paymentApi.js';
import { PLAN_LIMIT_EVENT } from '../api/http.js';
import { PROTECTION_CHANGED } from '../api/ownerApi.js';

// null = chưa biết (đang tải, lỗi, không phải Owner): không chặn gì, để BE quyết định
export function usePlanUsage() {
    const user = getUser();
    const isOwner = Array.isArray(user?.roles) && user.roles.includes('OWNER');
    const [usage, setUsage] = useState(null);

    useEffect(() => {
        if (!isOwner) return undefined;
        let alive = true;
        const load = () => paymentApi.getMyUsage()
            .then((u) => { if (alive) setUsage(u || null); })
            .catch(() => {});
        load();
        // Tạo / xoá vault, thêm / bớt người thụ hưởng: tải lại để số đếm đúng
        window.addEventListener(PROTECTION_CHANGED, load);
        return () => {
            alive = false;
            window.removeEventListener(PROTECTION_CHANGED, load);
        };
    }, [isOwner, user?.id]);

    return usage;
}

/** Đã đủ số vault của gói chưa. */
export const atVaultLimit = (usage) =>
    !!usage && usage.maxVaults > 0 && usage.vaultsUsed >= usage.maxVaults;

/** Một vault đang có `count` người thụ hưởng đã đủ giới hạn của gói chưa. */
export const atBeneficiaryLimit = (usage, count) =>
    !!usage && usage.maxBeneficiariesPerVault > 0 && count >= usage.maxBeneficiariesPerVault;

/** Mở hộp nâng cấp dùng chung (PlanLimitUpgradeDialog) với câu riêng cho tính năng. */
export const openPlanLimit = (feature) =>
    window.dispatchEvent(new CustomEvent(PLAN_LIMIT_EVENT, { detail: { data: { feature } } }));
