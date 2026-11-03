import React, { useEffect, useState } from 'react';
import { PLAN_LIMIT_EVENT } from '../api/http.js';
import UpgradeDialog from './UpgradeDialog.jsx';

// T1 (m6): một UpgradeDialog dùng chung cho cả app. http.js phát PLAN_LIMIT_EVENT khi BE trả 403 PLAN_LIMIT_REACHED;
// trang gọi API vẫn tự hiện câu lỗi như cũ, dialog chỉ là lời mời nâng cấp.
export default function PlanLimitUpgradeDialog() {
    const [limit, setLimit] = useState(null);

    useEffect(() => {
        const onLimit = (e) => setLimit(e.detail || {});
        window.addEventListener(PLAN_LIMIT_EVENT, onLimit);
        return () => window.removeEventListener(PLAN_LIMIT_EVENT, onLimit);
    }, []);

    return (
        <UpgradeDialog
            open={!!limit}
            feature={limit?.data?.feature}
            onClose={() => setLimit(null)}
        />
    );
}
