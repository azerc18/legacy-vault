// C1: Quản lý gói — xem giới hạn của từng gói, đổi giá/tên hiển thị cho lần mua SAU (không đụng gói đang chạy)
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { formatStorage } from '../i18n/format.js';
import { Icon, Alert, inputClass, btnPrimary, btnOutline } from '../Owner/ownerUi.jsx';
import { AdminLayout, Dossier, Stamp, formatVnd } from './adminUi.jsx';
import AdminCoupons from './AdminCoupons.jsx';

const MIN_PRICE = 10000;
const MAX_PRICE = 100000000;

function PlanCard({ plan, onSaved }) {
    const { t } = useTranslation('admin');
    const [editing, setEditing] = useState(false);
    const [price, setPrice] = useState(String(plan.priceVnd));
    const [name, setName] = useState(plan.name);
    const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const isFree = plan.code === 'FREE';

    const n = Number(price);
    const valid = Number.isInteger(n) && n >= MIN_PRICE && n <= MAX_PRICE && name.trim().length > 0;

    const save = async (e) => {
        e.preventDefault();
        setBusy(true); setError('');
        try {
            await adminApi.updatePlan(plan.code, { priceVnd: n, name: name.trim(), reason: reason.trim() || null });
            setEditing(false); setReason('');
            onSaved(t('plans.saved', { code: plan.code, price: formatVnd(n) }));
        } catch (err) {
            setError(getErrorMessage(err, t('plans.saveError')));
        } finally {
            setBusy(false);
        }
    };

    const limit = (label, value) => (
        <div className="flex justify-between gap-3 py-1.5 border-b border-dashed border-outline-variant/70 last:border-0 text-sm">
            <span className="text-on-surface-variant">{label}</span><span className="font-semibold lv-mono">{value}</span>
        </div>
    );

    return (
        <Dossier title={plan.name} tab={plan.code}
                 right={isFree ? null : <Stamp tone="info" flat>{t('plans.subscribers', { count: plan.activeSubscribers })}</Stamp>}>
            <p className="lv-stat-value">{isFree ? t('plans.free') : formatVnd(plan.priceVnd)}</p>
            <p className="lv-stat-foot">{plan.durationDays ? t('plans.duration', { count: plan.durationDays }) : t('plans.noExpiry')}</p>

            <div className="mt-4">
                {limit(t('plans.limits.vaults'), plan.maxVaults)}
                {limit(t('plans.limits.beneficiaries'), plan.maxBeneficiaries)}
                {limit(t('plans.limits.storage'), formatStorage(plan.storageQuotaBytes))}
                {limit(t('plans.limits.messages'), plan.legacyMessagesEnabled ? t('drawer.on') : t('drawer.off'))}
            </div>

            {!isFree && !editing && (
                <button onClick={() => setEditing(true)} className={`${btnOutline} mt-4 inline-flex items-center gap-2`}>
                    <Icon name="edit" className="text-[18px]" /> {t('plans.edit')}
                </button>
            )}

            {editing && (
                <form onSubmit={save} className="mt-4 pt-4 border-t border-outline-variant space-y-3">
                    <label className="block text-sm font-semibold">{t('plans.form.name')}
                        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} className={`${inputClass} mt-1`} />
                    </label>
                    <label className="block text-sm font-semibold">{t('plans.form.price')}
                        <input type="number" inputMode="numeric" min={MIN_PRICE} max={MAX_PRICE} step={1000} value={price}
                               onChange={(e) => setPrice(e.target.value)} className={`${inputClass} mt-1 lv-mono`} />
                        <span className="block mt-1 text-xs font-normal text-on-surface-variant">{t('plans.form.priceHint')}</span>
                    </label>
                    <label className="block text-sm font-semibold">{t('plans.form.reason')}
                        <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} placeholder={t('plans.form.reasonPlaceholder')} className={`${inputClass} mt-1`} />
                    </label>
                    <p className="text-xs text-on-surface-variant flex gap-1.5"><Icon name="info" className="text-[16px]" />{t('plans.form.futureOnly')}</p>
                    <Alert>{error}</Alert>
                    <div className="flex justify-end gap-2">
                        <button type="button" onClick={() => { setEditing(false); setPrice(String(plan.priceVnd)); setName(plan.name); setError(''); }} className={btnOutline}>{t('common:actions.cancel')}</button>
                        <button type="submit" disabled={busy || !valid} className={btnPrimary}>{busy ? t('wait') : t('plans.form.save')}</button>
                    </div>
                </form>
            )}
        </Dossier>
    );
}

export default function AdminPlans() {
    const { t } = useTranslation('admin');
    const [plans, setPlans] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = () => adminApi.listPlans().then(setPlans).catch((e) => setError(getErrorMessage(e, t('plans.loadError'))));
    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

    return (
        <AdminLayout title={t('plans.title')} subtitle={t('plans.subtitle')}>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            {!plans && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
            {plans && (
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                    {plans.map((p) => <PlanCard key={`${p.code}-${p.priceVnd}-${p.name}`} plan={p} onSaved={(m) => { setSuccess(m); load(); }} />)}
                </div>
            )}
            <p className="mt-6 text-xs text-on-surface-variant flex gap-1.5"><Icon name="lock" className="text-[16px]" />{t('plans.limitsLocked')}</p>
            <AdminCoupons plans={plans} />
        </AdminLayout>
    );
}
