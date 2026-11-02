import React, { useEffect, useState } from 'react';
import { getUser } from '../auth/auth.js';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate, inputClass, btnPrimary } from '../Owner/ownerUi.jsx';
import { AdminLayout, Pager, isAdmin } from './adminUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";

const STATUS = {
    ACTIVE: { label: 'Active', cls: 'bg-secondary-fixed text-on-secondary-fixed' },
    UNLOCKED: { label: 'Unlocked', cls: 'bg-warning-container text-on-warning-container' },
    CLAIMED: { label: 'Claimed', cls: 'bg-success-container text-on-success-container' },
    ARCHIVED_LOCKED: { label: 'Archived', cls: 'bg-surface-container text-outline' },
};

export default function AdminVaults() {
    const me = getUser();
    const [status, setStatus] = useState('');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const [detail, setDetail] = useState(null);
    const MAX_EXTENSIONS = 2;           // khớp AdminClaimServiceImpl.MAX_EXTENSIONS
    const EXTEND_DAYS = [7, 14, 30];

    useEffect(() => {
        if (!isAdmin(me)) return;
        setError('');
        adminApi.listVaults({ status, page, size: 20 })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, 'Could not load vaults.')));
    }, [status, page]); // eslint-disable-line react-hooks/exhaustive-deps

    const open = async (v) => {
        setError('');
        try { setDetail(await adminApi.getVault(v.id)); }
        catch (e) { setError(getErrorMessage(e, 'Could not open this vault.')); }
    };

    const reloadDetail = async () => {
        try { setDetail(await adminApi.getVault(detail.id)); }
        catch (e) { setError(getErrorMessage(e, 'Could not reload this vault.')); }
    };

    const rows = data?.content ?? [];

    return (
        <AdminLayout title="Vaults" subtitle="All vaults in the system. Asset contents are never shown here.">
            <div className="flex flex-wrap items-center gap-3 mb-6">
                <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className={`${inputClass} max-w-xs`}>
                    <option value="">All statuses</option>
                    {Object.entries(STATUS).map(([k, s]) => <option key={k} value={k}>{s.label}</option>)}
                </select>
                {data && <span className="text-sm text-on-surface-variant">{data.totalElements} vault(s)</span>}
            </div>

            <Alert>{error}</Alert>
            {!data && !error && <p className="text-on-surface-variant">Loading...</p>}
            {data && rows.length === 0 && 	<EmptyState bordered icon="inventory_2" title="No vaults found" text="Try another status filter." />}

            {rows.length > 0 && (
                <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <thead className="bg-surface-container-low text-on-surface-variant">
                            <tr><th className="p-4">Vault</th><th className="p-4">Status</th><th className="p-4">Created</th><th className="p-4">Updated</th><th className="p-4" /></tr>
                        </thead>
                        <tbody>
                            {rows.map((v) => {
                                const s = STATUS[v.status] || {};
                                return (
                                    <tr key={v.id} className="border-t border-outline-variant/20 hover:bg-surface-container-low">
                                        <td className="p-4">
                                            <p className="font-semibold">{v.name}</p>
                                            {v.description && <p className="text-xs text-outline line-clamp-1">{v.description}</p>}
                                        </td>
                                        <td className="p-4"><span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${s.cls}`}>{s.label || v.status}</span></td>
                                        <td className="p-4 whitespace-nowrap">{formatDate(v.createdAt)}</td>
                                        <td className="p-4 whitespace-nowrap">{formatDate(v.updatedAt)}</td>
                                        <td className="p-4 text-right">
                                            <button onClick={() => open(v)} className="text-sm font-semibold text-secondary hover:text-primary">Details</button>
                                        </td>
                                    </tr>
                                );
                            })}
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
                                <p className="text-sm text-on-surface-variant">Owner: {detail.ownerEmail}</p>
                            </div>
                            <button onClick={() => setDetail(null)}><Icon name="close" className="text-outline" /></button>
                        </div>
                        {detail.description && <p className="mt-4 text-sm whitespace-pre-wrap">{detail.description}</p>}
                        <dl className="mt-5 grid grid-cols-2 gap-4 text-sm">
                            <Info label="Status" value={STATUS[detail.status]?.label || detail.status} />
                            <Info label="Assets / documents" value={`${detail.totalAssets} / ${detail.totalDocuments}`} />
                            <Info label="Created" value={formatDate(detail.createdAt, true)} />
                            <Info label="Updated" value={formatDate(detail.updatedAt, true)} />
                            <Info label="Claim deadline" value={formatDate(detail.claimDeadlineAt, true)} />
                            <Info label="Extensions used" value={`${detail.claimExtensions ?? 0} / ${MAX_EXTENSIONS}`} />
                        </dl>

                        {detail.status === 'UNLOCKED' && (
                            (detail.claimExtensions ?? 0) < MAX_EXTENSIONS
                                ? <ExtendClaimForm vaultId={detail.id} onDone={reloadDetail} />
                                : <p className="mt-5 text-sm text-on-surface-variant">
                                    The claim period has already been extended {MAX_EXTENSIONS} times.
                                </p>
                        )}
                        <p className="mt-5 text-xs text-outline">Viewing a vault as admin is recorded in the audit log.</p>
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
    // N2-admin: gia hạn thời hạn nhận cho vault UNLOCKED (≤ 30 ngày, tối đa 2 lần)
    function ExtendClaimForm({ vaultId, onDone }) {
        const [days, setDays] = useState(EXTEND_DAYS[0]);
        const [reason, setReason] = useState('');
        const [busy, setBusy] = useState(false);
        const [error, setError] = useState('');
        const [success, setSuccess] = useState('');

        const submit = async (e) => {
            e.preventDefault();
            if (!reason.trim()) return setError('Please enter a reason.');
            setError(''); setSuccess(''); setBusy(true);
            try {
                const res = await adminApi.extendClaim(vaultId, days, reason.trim());
                setSuccess(`Extended to ${formatDate(res.claimDeadlineAt, true)}. ${res.remaining} extension(s) left.`);
                setReason('');
                await onDone();
            } catch (err) {
                setError(getErrorMessage(err, 'Could not extend the claim period.'));
            } finally {
                setBusy(false);
            }
        };

        return (
            <form onSubmit={submit} className="mt-5 pt-5 border-t border-outline-variant/30 space-y-3">
                <h4 className="font-semibold flex items-center gap-2">
                    <Icon name="more_time" className="text-secondary" /> Extend claim period
                </h4>
                <p className="text-xs text-on-surface-variant">
                    Use when a beneficiary needs more time (e.g. slow legal paperwork). Only beneficiaries who have not responded get the new deadline.
                </p>
                <Alert>{error}</Alert>
                <Alert type="success">{success}</Alert>
                <div className="flex gap-3">
                    <select value={days} onChange={(e) => setDays(Number(e.target.value))} className={`${inputClass} max-w-[140px]`}>
                        {EXTEND_DAYS.map((d) => <option key={d} value={d}>+{d} days</option>)}
                    </select>
                    <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255}
                           placeholder="Reason (required)" className={inputClass} />
                </div>
                <div className="flex justify-end">
                    <button type="submit" disabled={busy || !reason.trim()} className={btnPrimary}>
                        {busy ? 'Please wait...' : 'Extend'}
                    </button>
                </div>
            </form>
        );
    }
}
