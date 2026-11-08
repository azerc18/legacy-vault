// "Di sản của tôi" trên trang Hồ sơ (chỉ Owner): số liệu nhanh + những người trong di sản
// (ý từ "Connected Network" của EstateOS và "Emergency access" của Bitwarden). Chỉ đọc API có sẵn.
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ownerApi } from '../api/ownerApi.js';
import { paymentApi } from '../api/paymentApi.js';
import { Card, Icon } from '../Owner/ownerUi.jsx';

const initials = (name = '') =>
    name.trim().split(/\s+/).slice(-2).map((w) => w[0]?.toUpperCase() || '').join('') || '?';

// Trạng thái -> màu chấm nhỏ
const TONE = {
    good: 'bg-success-container text-on-success-container',
    wait: 'bg-warning-container text-on-warning-container',
    bad: 'bg-error-container text-on-error-container',
    idle: 'bg-surface-container text-on-surface-variant',
};
const BENEFICIARY_TONE = { ACCEPTED: 'good', VERIFIED: 'good', INVITED: 'wait', PENDING_REGISTRATION: 'wait', DECLINED: 'bad', WAIVED: 'idle' };
const EXECUTOR_TONE = { ACTIVE: 'good', INACTIVE: 'good' };
const CONFIRMER_TONE = { ACTIVE: 'good', CONFIRMED_ALIVE: 'good', CONFIRMED_UNREACHABLE: 'bad' };
const DMS_TONE = { ACTIVE: 'good', PENDING_CONFIRMATION: 'wait', TRIGGERED: 'bad', SUSPENDED: 'idle' };

function Stat({ icon, label, value, to }) {
    return (
        <Link to={to} className="p-3 rounded-xl bg-surface-container-low hover:bg-surface-container transition-colors min-w-0">
            <Icon name={icon} className="text-[18px] text-secondary" />
            <span className="block mt-1 font-title-lg text-title-lg truncate">{value}</span>
            <span className="block text-xs text-on-surface-variant truncate">{label}</span>
        </Link>
    );
}

function Person({ name, email, role, status, tone }) {
    return (
        <li className="flex items-center gap-3 py-2.5">
            <span className="w-9 h-9 shrink-0 rounded-full bg-secondary-fixed text-on-secondary-fixed text-xs font-bold flex items-center justify-center">
                {initials(name || email)}
            </span>
            <span className="flex-1 min-w-0">
                <span className="block text-sm font-semibold truncate">{name || email}</span>
                <span className="block text-xs text-on-surface-variant truncate">{role}{name && email ? ` · ${email}` : ''}</span>
            </span>
            {status && (
                <span className={`shrink-0 text-[11px] font-semibold px-2.5 py-0.5 rounded-full ${TONE[tone] || TONE.idle}`}>{status}</span>
            )}
        </li>
    );
}

export default function LegacyOverviewCard() {
    const { t } = useTranslation('profile');
    const [data, setData] = useState(null);

    useEffect(() => {
        Promise.all([
            ownerApi.listVaults().catch(() => []),
            ownerApi.listExecutors().catch(() => []),
            ownerApi.getDms().catch(() => null),
            ownerApi.listDmsConfirmers().catch(() => []),
            paymentApi.getMySubscription().catch(() => null),
        ]).then(([vaults, executors, dms, confirmers, sub]) => setData({ vaults, executors, dms, confirmers, sub }));
    }, []);

    if (!data) {
        return <Card title={t('legacy.title')} icon="family_restroom"><p className="text-sm text-on-surface-variant">{t('loading')}</p></Card>;
    }

    const { vaults, executors, dms, confirmers, sub } = data;
    const assets = vaults.reduce((n, v) => n + (v.totalAssets || 0), 0);

    // Người thụ hưởng: gộp theo email qua các két, đếm số két
    const byEmail = new Map();
    vaults.forEach((v) => (v.beneficiaries || []).forEach((b) => {
        const key = (b.email || '').toLowerCase();
        const cur = byEmail.get(key);
        byEmail.set(key, cur ? { ...cur, vaults: cur.vaults + 1 } : { ...b, vaults: 1 });
    }));
    const beneficiaries = [...byEmail.values()];
    const activeExecutors = (executors || []).filter((e) => e.status !== 'REVOKED')
        .sort((a, b) => (a.executorType === 'PRIMARY' ? -1 : 1) - (b.executorType === 'PRIMARY' ? -1 : 1));
    const dmsStatus = dms?.configured === false || !dms?.status ? null : dms.status;
    const people = beneficiaries.length + activeExecutors.length + (confirmers || []).length;

    return (
        <Card title={t('legacy.title')} icon="family_restroom">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <Stat icon="inventory_2" label={t('legacy.vaults')} value={vaults.length} to="/owner/vaults" />
                <Stat icon="key" label={t('legacy.assets')} value={assets} to="/owner/vaults" />
                <Stat icon="timer" label={t('legacy.dms')} to="/owner/dms"
                      value={dmsStatus ? t(`legacy.dmsStatus.${dmsStatus}`, { defaultValue: dmsStatus }) : t('legacy.dmsOff')} />
                <Stat icon="workspace_premium" label={t('legacy.plan')} to="/billing"
                      value={sub?.planName || (sub?.vip ? 'VIP' : t('legacy.planFree'))} />
            </div>

            <div className="mt-5 flex items-center justify-between">
                <h3 className="text-sm font-semibold">{t('legacy.peopleTitle', { count: people })}</h3>
                {dmsStatus && DMS_TONE[dmsStatus] === 'bad' && (
                    <span className="text-xs font-semibold text-error">{t('legacy.dmsWarning')}</span>
                )}
            </div>

            {people === 0 ? (
                <div className="mt-3 p-4 rounded-xl border border-dashed border-outline-variant text-sm text-on-surface-variant">
                    {t('legacy.empty')}{' '}
                    <Link to="/owner/vaults/new" className="font-semibold text-secondary hover:underline">{t('legacy.createVault')}</Link>
                </div>
            ) : (
                <ul className="mt-1 divide-y divide-outline-variant/30">
                    {beneficiaries.map((b) => (
                        <Person key={`b-${b.email}`} name={b.fullName} email={b.email}
                                role={[b.relationship, t('legacy.roleBeneficiary', { count: b.vaults })].filter(Boolean).join(' · ')}
                                status={t(`legacy.beneficiaryStatus.${b.status}`, { defaultValue: b.status })}
                                tone={BENEFICIARY_TONE[b.status]} />
                    ))}
                    {activeExecutors.map((e) => (
                        <Person key={`e-${e.executorType}`} name={e.fullName} email={e.email}
                                role={t(`legacy.executor.${e.executorType}`)}
                                status={t(`legacy.executorStatus.${e.status}`, { defaultValue: e.status })}
                                tone={EXECUTOR_TONE[e.status]} />
                    ))}
                    {(confirmers || []).map((c) => (
                        <Person key={`c-${c.id}`} name={c.name} email={c.email}
                                role={[c.relationship, t('legacy.roleConfirmer')].filter(Boolean).join(' · ')}
                                status={t(`legacy.confirmerStatus.${c.status}`, { defaultValue: c.status })}
                                tone={CONFIRMER_TONE[c.status]} />
                    ))}
                </ul>
            )}

            <div className="mt-4 flex flex-wrap gap-x-4 gap-y-2 text-xs font-semibold">
                <Link to="/owner/executors" className="text-secondary hover:underline">{t('legacy.manageExecutors')}</Link>
                <Link to="/owner/dms" className="text-secondary hover:underline">{t('legacy.manageDms')}</Link>
                <Link to="/owner/activity" className="text-secondary hover:underline">{t('legacy.viewActivity')}</Link>
            </div>
        </Card>
    );
}
