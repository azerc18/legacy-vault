// Cảnh báo khi Owner đã có vault nhưng kế hoạch bàn giao chưa đủ (thiếu Executor / chưa bật DMS / vault chưa có người thụ hưởng).
// Hiện ở đầu mọi trang Owner (gắn trong OwnerLayout). Tự tải lại khi ownerApi báo dữ liệu bảo vệ vừa thay đổi.
import React, { useCallback, useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ownerApi, PROTECTION_CHANGED } from '../api/ownerApi.js';

// Không import Icon từ ownerUi.jsx để tránh vòng import (ownerUi.jsx import file này)
const Icon = ({ name, className = '' }) => <span className={`material-symbols-outlined ${className}`}>{name}</span>;

async function loadState() {
    const [vaults, executors, dms] = await Promise.all([
        ownerApi.listVaults(),
        ownerApi.listExecutors().catch(() => []),
        // 404 = chưa cấu hình DMS (nếu BE đổi sang trả { configured:false } thì cũng xử lý bên dưới)
        ownerApi.getDms().catch((e) => { if (e.response?.status === 404) return null; throw e; }),
    ]);
    return { vaults, executors: executors ?? [], dms: dms && dms.configured !== false ? dms : null };
}

// Trả về danh sách bước còn thiếu, theo đúng thứ tự BE bắt buộc: Executor -> DMS. Rỗng = không cần banner.
export function computeIssues({ vaults, executors, dms }) {
    if (vaults.length === 0) return [];                  // chưa có gì để bảo vệ
    if (dms?.status === 'TRIGGERED') return [];          // bàn giao đã bắt đầu, banner không còn ý nghĩa

    const issues = [];
    const hasPrimary = executors.some((e) => e.executorType === 'PRIMARY' && e.status !== 'REVOKED');

    if (!hasPrimary) {
        issues.push({ key: 'executor', critical: true, text: 'Assign a primary executor', cta: 'Assign executor', to: '/owner/executors' });
    } else if (dms && dms.hasUsableExecutor === false) {
        issues.push({ key: 'executor', critical: true, text: 'None of your executors can be activated. Assign a new one', cta: 'Fix executors', to: '/owner/executors' });
    }
    if (!dms) {
        issues.push({ key: 'dms', critical: true, text: "Turn on the Dead Man's Switch", cta: 'Turn on', to: '/owner/dms', needs: hasPrimary ? null : 'executor' });
    } else if (dms.status === 'SUSPENDED') {
        issues.push({ key: 'dms', critical: true, text: "Your Dead Man's Switch is paused by an administrator. Contact support to resume it", to: null });
    }
    const noBeneficiary = vaults.filter((v) => !v.beneficiaryEmail);
    if (noBeneficiary.length) {
        issues.push({
            key: 'beneficiary', critical: false,
            text: noBeneficiary.length === 1 ? `"${noBeneficiary[0].name}" has no beneficiary` : `${noBeneficiary.length} vaults have no beneficiary`,
            cta: 'Choose beneficiary', to: `/owner/vaults/${noBeneficiary[0].id}`,
        });
    }
    return issues;
}

export default function ProtectionBanner() {
    const [issues, setIssues] = useState([]);
    const { pathname } = useLocation();

    const refresh = useCallback(() => {
        loadState().then((s) => setIssues(computeIssues(s))).catch(() => setIssues([])); // lỗi tải: im lặng, không chặn trang
    }, []);

    useEffect(() => {
        refresh();
        window.addEventListener(PROTECTION_CHANGED, refresh);
        return () => window.removeEventListener(PROTECTION_CHANGED, refresh);
    }, [refresh]);

    if (issues.length === 0) return null;

    const critical = issues.some((i) => i.critical);
    // Nút chính = bước đầu tiên làm được ngay (bỏ qua bước còn phụ thuộc bước khác)
    const next = issues.find((i) => i.to && !i.needs);

    return (
        <div role="alert"
             className={`mb-8 rounded-2xl border p-5 ${critical
                 ? 'bg-error-container/60 border-error/30 text-on-error-container'
                 : 'bg-warning-container/70 border-warning/30 text-on-warning-container'}`}>
            <div className="flex flex-wrap items-start gap-4">
                <Icon name={critical ? 'gpp_bad' : 'gpp_maybe'} className="text-[28px] shrink-0" />
                <div className="flex-1 min-w-[220px]">
                    <p className="font-bold">
                        {critical ? 'Your vaults are not protected yet' : 'Almost done: finish your hand-over plan'}
                    </p>
                    <p className="mt-0.5 text-sm opacity-90">
                        {critical
                            ? 'If something happens to you, nobody will be notified and your vaults will never be handed over.'
                            : 'Your vaults are protected, but some of them have nobody to receive them.'}
                    </p>
                    <ol className="mt-3 space-y-1.5 text-sm">
                        {issues.map((i, idx) => (
                            <li key={i.key} className="flex items-start gap-2">
                                <span className="mt-0.5 w-5 h-5 shrink-0 rounded-full bg-surface-container-lowest/80 text-xs font-bold flex items-center justify-center">
                                    {idx + 1}
                                </span>
                                <span className="flex flex-wrap items-baseline gap-x-2">
                                    <span className={i.needs ? 'opacity-60' : ''}>{i.text}{i.needs ? ' (after step 1)' : ''}</span>
                                    {i.to && !i.needs && i !== next && pathname !== i.to && (
                                        <Link to={i.to} className="font-semibold underline underline-offset-2 whitespace-nowrap">{i.cta}</Link>
                                    )}
                                </span>
                            </li>
                        ))}
                    </ol>
                </div>
                {next && pathname !== next.to && (
                    <Link to={next.to}
                          className="shrink-0 h-10 px-4 rounded-xl bg-primary text-on-primary text-sm font-bold inline-flex items-center gap-2 hover:opacity-90">
                        {next.cta} <Icon name="arrow_forward" className="text-[18px]" />
                    </Link>
                )}
            </div>
        </div>
    );
}
