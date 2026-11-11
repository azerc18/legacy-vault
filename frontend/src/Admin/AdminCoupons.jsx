// Việc 4: Admin tạo và bật / tắt mã giảm giá (nằm dưới các gói ở trang Gói).
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnPrimary, formatDate, Icon, inputClass } from '../Owner/ownerUi.jsx';
import { Dossier, Stamp } from './adminUi.jsx';

const EMPTY = { code: '', percentOff: '20', planCode: '', maxRedemptions: '', validUntil: '', note: '' };

export default function AdminCoupons({ plans }) {
    const { t } = useTranslation('admin');
    const [coupons, setCoupons] = useState(null);
    const [form, setForm] = useState(EMPTY);
    const [busy, setBusy] = useState(null);       // 'create' | id đang bật/tắt
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const load = () => adminApi.listCoupons().then(setCoupons).catch((e) => setError(getErrorMessage(e, t('coupons.loadError'))));
    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const set = (k) => (e) => setForm((f) => ({ ...f, [k]: k === 'code' ? e.target.value.toUpperCase() : e.target.value }));
    const percent = Number(form.percentOff);
    const valid = /^[A-Z0-9_-]{3,40}$/.test(form.code) && Number.isInteger(percent) && percent >= 1 && percent <= 90;

    const create = async (e) => {
        e.preventDefault();
        setBusy('create'); setError(''); setSuccess('');
        try {
            const c = await adminApi.createCoupon({
                code: form.code,
                percentOff: percent,
                planCode: form.planCode || null,
                maxRedemptions: form.maxRedemptions ? Number(form.maxRedemptions) : null,
                validUntil: form.validUntil || null,
                note: form.note.trim() || null,
            });
            setSuccess(t('coupons.created', { code: c.code }));
            setForm(EMPTY);
            load();
        } catch (err) {
            setError(getErrorMessage(err, t('coupons.saveError')));
        } finally {
            setBusy(null);
        }
    };

    const toggle = async (c) => {
        setBusy(c.id); setError(''); setSuccess('');
        try {
            await adminApi.setCouponActive(c.id, !c.active);
            load();
        } catch (err) {
            setError(getErrorMessage(err, t('coupons.saveError')));
        } finally {
            setBusy(null);
        }
    };

    const vipPlans = (plans ?? []).filter((p) => p.code !== 'FREE');
    const stateOf = (c) => (!c.active ? 'off' : c.expired ? 'expired'
        : c.maxRedemptions != null && c.redeemedCount >= c.maxRedemptions ? 'usedUp' : 'on');
    const tone = { on: 'ok', off: 'muted', expired: 'warn', usedUp: 'warn' };

    return (
        <Dossier title={t('coupons.title')} tab={coupons ? t('coupons.count', { count: coupons.length }) : ''} className="mt-8">
            <p className="text-sm text-on-surface-variant mb-4">{t('coupons.subtitle')}</p>

            <form onSubmit={create} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.2fr_100px_1fr_120px_160px] items-end mb-3">
                <label className="text-sm font-semibold">{t('coupons.form.code')}
                    <input value={form.code} onChange={set('code')} maxLength={40} placeholder="TET2027" className={`${inputClass} mt-1 lv-mono`} />
                </label>
                <label className="text-sm font-semibold">{t('coupons.form.percent')}
                    <input type="number" min={1} max={90} value={form.percentOff} onChange={set('percentOff')} className={`${inputClass} mt-1 lv-mono`} />
                </label>
                <label className="text-sm font-semibold">{t('coupons.form.plan')}
                    <select value={form.planCode} onChange={set('planCode')} className={`${inputClass} mt-1`}>
                        <option value="">{t('coupons.form.allPlans')}</option>
                        {vipPlans.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
                    </select>
                </label>
                <label className="text-sm font-semibold">{t('coupons.form.max')}
                    <input type="number" min={1} value={form.maxRedemptions} onChange={set('maxRedemptions')} placeholder="∞" className={`${inputClass} mt-1 lv-mono`} />
                </label>
                <label className="text-sm font-semibold">{t('coupons.form.until')}
                    <input type="date" value={form.validUntil} onChange={set('validUntil')} className={`${inputClass} mt-1`} />
                </label>
                <label className="text-sm font-semibold sm:col-span-2 lg:col-span-4">{t('coupons.form.note')}
                    <input value={form.note} onChange={set('note')} maxLength={255} placeholder={t('coupons.form.notePlaceholder')} className={`${inputClass} mt-1`} />
                </label>
                <button type="submit" disabled={!valid || busy === 'create'} className={btnPrimary}>
                    <Icon name="add" className="text-[18px]" /> {busy === 'create' ? t('wait') : t('coupons.form.create')}
                </button>
            </form>
            <p className="text-xs text-on-surface-variant flex gap-1.5 mb-4"><Icon name="info" className="text-[16px]" />{t('coupons.form.hint')}</p>

            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>

            {coupons && coupons.length === 0 && <p className="text-sm text-on-surface-variant">{t('coupons.empty')}</p>}
            {coupons && coupons.length > 0 && (
                <div className="overflow-x-auto">
                    <table className="lv-ledger is-stack">
                        <thead>
                        <tr>
                            <th>{t('coupons.cols.code')}</th>
                            <th className="text-right">{t('coupons.cols.percent')}</th>
                            <th>{t('coupons.cols.plan')}</th>
                            <th className="text-right">{t('coupons.cols.used')}</th>
                            <th>{t('coupons.cols.until')}</th>
                            <th>{t('coupons.cols.status')}</th>
                            <th />
                        </tr>
                        </thead>
                        <tbody>
                        {coupons.map((c) => {
                            const st = stateOf(c);
                            return (
                                <tr key={c.id} className="is-row">
                                    <td data-label={t('coupons.cols.code')}>
                                        <p className="lv-mono font-semibold">{c.code}</p>
                                        {c.note && <p className="text-[11px] text-outline">{c.note}</p>}
                                    </td>
                                    <td data-label={t('coupons.cols.percent')} className="lv-num font-semibold">-{c.percentOff}%</td>
                                    <td data-label={t('coupons.cols.plan')}>{c.planCode ? (vipPlans.find((p) => p.code === c.planCode)?.name ?? c.planCode) : t('coupons.form.allPlans')}</td>
                                    <td data-label={t('coupons.cols.used')} className="lv-num">{c.redeemedCount}{c.maxRedemptions != null ? ` / ${c.maxRedemptions}` : ''}</td>
                                    <td data-label={t('coupons.cols.until')} className="whitespace-nowrap">{c.validUntil ? formatDate(`${c.validUntil}T00:00:00`) : t('coupons.noEnd')}</td>
                                    <td data-label={t('coupons.cols.status')}><Stamp tone={tone[st]}>{t(`coupons.state.${st}`)}</Stamp></td>
                                    <td className="text-right">
                                        <button onClick={() => toggle(c)} disabled={busy === c.id}
                                                className={`text-xs font-semibold whitespace-nowrap hover:underline disabled:opacity-60 ${c.active ? 'text-error' : 'text-secondary'}`}>
                                            {c.active ? t('coupons.disable') : t('coupons.enable')}
                                        </button>
                                    </td>
                                </tr>
                            );
                        })}
                        </tbody>
                    </table>
                </div>
            )}
        </Dossier>
    );
}
