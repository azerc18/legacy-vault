import React from 'react';
import {useTranslation} from "react-i18next";

// N2: thanh tiến độ "X/N người đã chấp nhận". Dùng chung cho Beneficiary, Executor, Admin.
// Props: accepted (số người đã Accept), required (N), status (VaultStatus)
const STYLE = {
    UNLOCKED: { bar: 'bg-primary', text: 'text-primary' },
    CLAIM_STALLED: { bar: 'bg-warning', text: 'text-on-warning-container' },
    CLAIMED: {  bar: 'bg-success', text: 'text-on-success-container' },
    EXPIRED: {  bar: 'bg-outline', text: 'text-outline' },
};

export default function ClaimProgress({ accepted = 0, required = 0, status = 'UNLOCKED', className = '' }) {
    const { t } = useTranslation();
    const key = STYLE[status] ? status : 'UNLOCKED';
    const s = STYLE[key];
    const pct = required > 0 ? Math.min(100, Math.round((accepted / required) * 100)) : 0;
    return (
        <div className={className} role="group" aria-label={t('claim.progress')}>
            <div className="flex items-center justify-between text-sm mb-1">
                <span className={`font-semibold ${s.text}`}>{t(`claim.status.${key}`)}</span>
                <span className="text-on-surface-variant">{t('claim.accepted', { accepted, required })}</span>
            </div>
            <div
                className="h-2 rounded-full bg-surface-container overflow-hidden"
                role="progressbar" aria-valuemin={0} aria-valuemax={required} aria-valuenow={accepted}
            >
                <div className={`h-full ${s.bar} transition-all`} style={{ width: `${pct}%` }} />
            </div>
        </div>
    );
}