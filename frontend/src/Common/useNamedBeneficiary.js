import { useEffect, useState } from 'react';
import { beneficiaryApi } from '../api/beneficiaryApi.js';

// Mọi tài khoản đều có role BENEFICIARY (thiết kế: quyền thật do dữ liệu quyết định).
// Hook này cho biết user đã THẬT SỰ được ai đó chỉ định chưa, để UI chỉ hiện phần Beneficiary khi có ý nghĩa.
let cache = null; // Promise<boolean>, dùng chung cho cả phiên

export function resetNamedBeneficiaryCache() {
    cache = null;
}

export default function useNamedBeneficiary(enabled = true) {
    const [named, setNamed] = useState(null); // null = đang kiểm tra
    useEffect(() => {
        if (!enabled) { setNamed(false); return; }
        cache ??= beneficiaryApi.listVaults()
            .then((list) => list.length > 0)
            .catch(() => false); // 403 (không có role) hoặc lỗi mạng -> coi như chưa được chỉ định
        let alive = true;
        cache.then((v) => { if (alive) setNamed(v); });
        return () => { alive = false; };
    }, [enabled]);
    return named;
}