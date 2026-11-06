import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate, inputClass, btnPrimary } from '../Owner/ownerUi.jsx';
import { AdminLayout, Pager, isAdmin } from './adminUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";

// Nhãn ở admin.json > vaults.status.<code>
const STATUS_CLS = {
    ACTIVE: 'bg-secondary-fixed text-on-secondary-fixed',
    UNLOCKED: 'bg-warning-container text-on-warning-container',
    CLAIMED: 'bg-success-container text-on-success-container',
    ARCHIVED_LOCKED: 'bg-surface-container text-outline',
};
const MAX_EXTENSIONS = 2;           // khớp AdminClaimServiceImpl.MAX_EXTENSIONS
const EXTEND_DAYS = [7, 14, 30];

export default function AdminVaults() {
    const { t } = useTranslation('admin');
    const me = getUser();
    const [status, setStatus] = useState('');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const [detail, setDetail] = useState(null);

    const statusLabel = (s) => t(`vaults.status.${s}`, { defaultValue: s });

    useEffect(() => {
        if (!isAdmin(me)) return;
        setError('');
        adminApi.listVaults({ status, page, size: 20 })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, t('vaults.loadError'))));
    }, [status, page]); // eslint-disable-line react-hooks/exhaustive-deps

    const open = async (v) => {
        setError('');
        try { setDetail(await adminApi.getVault(v.id)); }
        catch (e) { setError(getErrorMessage(e, t('vaults.openError'))); }
    };

    const reloadDetail = async () => {
        try { setDetail(await adminApi.getVault(detail.id)); }
        catch (e) { setError(getErrorMessage(e, t('vaults.openError'))); }
    };

    const rows = data?.content ?? [];

    return (
        <AdminLayout title={t('vaults.title')} subtitle={t('vaults.subtitle')}>
            <div className="flex flex-wrap items-center gap-3 mb-6">
                <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className={`${inputClass} max-w-xs`}>
                    <option value="">{t('allStatuses')}</option>
                    {Object.keys(STATUS_CLS).map((k) => <option key={k} value={k}>{statusLabel(k)}</option>)}
                </select>
                {data && <span className="text-sm text-on-surface-variant">{t('vaults.total', { count: data.totalElements })}</span>}
            </div>

            <Alert>{error}</Alert>
            {!data && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
            {data && rows.length === 0 && <EmptyState bordered icon="inventory_2" title={t('vaults.empty.title')} text={t('vaults.empty.text')} />}

            {rows.length > 0 && (
                <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <thead className="bg-surface-container-low text-on-surface-variant">
                        <tr>
                            <th className="p-4">{t('vaults.cols.vault')}</th>
                            <th className="p-4">{t('vaults.cols.status')}</th>
                            <th className="p-4">{t('vaults.cols.created')}</th>
                            <th className="p-4">{t('vaults.cols.updated')}</th>
                            <th className="p-4" />
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((v) => (
                            <tr key={v.id} className="border-t border-outline-variant/20 hover:bg-surface-container-low">
                                <td className="p-4">
                                    <p className="font-semibold">{v.name}</p>
                                    {v.description && <p className="text-xs text-outline line-clamp-1">{v.description}</p>}
                                </td>
                                <td className="p-4"><span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${STATUS_CLS[v.status] || ''}`}>{statusLabel(v.status)}</span></td>
                                <td className="p-4 whitespace-nowrap">{formatDate(v.createdAt)}</td>
                                <td className="p-4 whitespace-nowrap">{formatDate(v.updatedAt)}</td>
                                <td className="p-4 text-right">
                                    <button onClick={() => open(v)} className="text-sm font-semibold text-secondary hover:text-primary">{t('vaults.details')}</button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />

            {detail && (
                <div className="fixed inset-0 z-40 bg-black/40 flex items-center justify-center px-4" onClick={() => setDetail(null)}>
                    <div className="bg-surface-container-lowest rounded-2xl p-6 w-full max-w-lg" onClick={(e) => e.stopPropagation()}>
                        <div className="flex justify-between items-start">
                            <div>
                                <h3 className="font-title-lg text-title-lg">{detail.name}</h3>
                                <p className="text-sm text-on-surface-variant">{t('vaults.detail.owner', { email: detail.ownerEmail })}</p>
                            </div>
                            <button onClick={() => setDetail(null)}><Icon name="close" className="text-outline" /></button>
                        </div>
                        {detail.description && <p className="mt-4 text-sm whitespace-pre-wrap">{detail.description}</p>}
                        <dl className="mt-5 grid grid-cols-2 gap-4 text-sm">
                            <Info label={t('vaults.cols.status')} value={statusLabel(detail.status)} />
                            <Info label={t('vaults.detail.assetsDocs')} value={`${detail.totalAssets} / ${detail.totalDocuments}`} />
                            <Info label={t('vaults.cols.created')} value={formatDate(detail.createdAt, true)} />
                            <Info label={t('vaults.cols.updated')} value={formatDate(detail.updatedAt, true)} />
                            <Info label={t('vaults.detail.unlockedAt')} value={formatDate(detail.unlockedAt, true)} />
                            <Info label={t('vaults.detail.deadline')} value={formatDate(detail.claimDeadlineAt, true)} />
                            <Info label={t('vaults.detail.extensionsUsed')} value={`${detail.claimExtensions ?? 0} / ${MAX_EXTENSIONS}`} />
                        </dl>

                        {detail.status === 'UNLOCKED' && (
                            (detail.claimExtensions ?? 0) < MAX_EXTENSIONS
                                ? <ExtendClaimForm vaultId={detail.id} onDone={reloadDetail} />
                                : <p className="mt-5 text-sm text-on-surface-variant">{t('vaults.detail.maxReached', { max: MAX_EXTENSIONS })}</p>
                        )}
                        <p className="mt-5 text-xs text-outline">{t('vaults.detail.auditNote')}</p>
                    </div>
                </div>
            )}
        </AdminLayout>
    );
}

function Info({ label, value }) {
    return (
        <div>
            <dt className="text-outline">{label}</dt>
            <dd className="font-semibold">{value}</dd>
        </div>
    );
}

// N2-admin: gia hạn thời hạn nhận cho vault UNLOCKED (≤ 30 ngày, tối đa 2 lần)
function ExtendClaimForm({ vaultId, onDone }) {
    const { t } = useTranslation('admin');
    const [days, setDays] = useState(EXTEND_DAYS[0]);
    const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const submit = async (e) => {
        e.preventDefault();
        if (!reason.trim()) return setError(t('vaults.extend.reasonRequired'));
        setError(''); setSuccess(''); setBusy(true);
        try {
            const res = await adminApi.extendClaim(vaultId, days, reason.trim());
            setSuccess(t('vaults.extend.success', { date: formatDate(res.claimDeadlineAt, true), count: res.remaining }));
            setReason('');
            await onDone();
        } catch (err) {
            setError(getErrorMessage(err, t('vaults.extend.error')));
        } finally {
            setBusy(false);
        }
    };

    return (
        <form onSubmit={submit} className="mt-5 pt-5 border-t border-outline-variant/30 space-y-3">
            <h4 className="font-semibold flex items-center gap-2">
                <Icon name="more_time" className="text-secondary" /> {t('vaults.extend.title')}
            </h4>
            <p className="text-xs text-on-surface-variant">{t('vaults.extend.hint')}</p>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            <div className="flex gap-3">
                <select value={days} onChange={(e) => setDays(Number(e.target.value))} className={`${inputClass} max-w-[140px]`}>
                    {EXTEND_DAYS.map((d) => <option key={d} value={d}>{t('vaults.extend.days', { count: d })}</option>)}
                </select>
                <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255}
                       placeholder={t('vaults.extend.reasonPlaceholder')} className={inputClass} />
            </div>
            <div className="flex justify-end">
                <button type="submit" disabled={busy || !reason.trim()} className={btnPrimary}>
                    {busy ? t('wait') : t('vaults.extend.submit')}
                </button>
            </div>
        </form>
    );
}
