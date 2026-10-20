import React, { useEffect, useState } from 'react';
import { getUser } from '../auth/auth.js';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate, inputClass } from '../Owner/ownerUi.jsx';
import { AdminLayout, Pager, isAdmin } from './adminUi.jsx';

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
            {data && rows.length === 0 && <p className="text-on-surface-variant">No vaults found.</p>}

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
                            <Info label="Unlocked" value={formatDate(detail.unlockedAt, true)} />
                            <Info label="Claim deadline" value={formatDate(detail.claimDeadlineAt)} />
                        </dl>
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
}
