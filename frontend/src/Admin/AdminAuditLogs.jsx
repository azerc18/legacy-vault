import React, { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate, inputClass } from '../Owner/ownerUi.jsx';
import { AdminLayout, Pager } from './adminUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";

// <input type="datetime-local"> -> ISO string cho BE
const toIso = (local) => (local ? new Date(local).toISOString() : undefined);

const RESULT_CLS = {
    SUCCESS: 'bg-success-container text-on-success-container',
    FAILURE: 'bg-error-container text-on-error-container',
};

export default function AdminAuditLogs() {
    const [params] = useSearchParams();
    // Cho phép mở sẵn bộ lọc từ trang Alerts: /admin/audit-logs?ip=...&action=...
    const initial = {
        action: params.get('action') || '',
        actorEmail: params.get('actorEmail') || '',
        ip: params.get('ip') || '',
        from: '',
        to: '',
    };
    const [filters, setFilters] = useState(initial);
    const [query, setQuery] = useState(initial);
    const [page, setPage] = useState(0);
    const [actions, setActions] = useState([]);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const [expanded, setExpanded] = useState(null);

    useEffect(() => { adminApi.listAuditActions().then(setActions).catch(() => {}); }, []);

    const load = useCallback(() => {
        setError('');
        adminApi.searchAuditLogs({ ...query, from: toIso(query.from), to: toIso(query.to), page, size: 50 })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, 'Could not load audit logs.')));
    }, [query, page]);

    useEffect(() => { load(); }, [load]);

    const set = (field) => (e) => setFilters((f) => ({ ...f, [field]: e.target.value }));
    const search = (e) => { e.preventDefault(); setPage(0); setQuery(filters); };
    const reset = () => {
        const empty = { action: '', actorEmail: '', ip: '', from: '', to: '' };
        setFilters(empty); setQuery(empty); setPage(0);
    };
    const rows = data?.content ?? [];

    return (
        <AdminLayout title="Audit log" subtitle="Every security-relevant action in the system. Defaults to the last 30 days.">
            <form onSubmit={search} className="grid gap-3 mb-6 sm:grid-cols-3 lg:grid-cols-[200px_1fr_160px_190px_190px_auto]">
                <select value={filters.action} onChange={set('action')} className={inputClass}>
                    <option value="">All actions</option>
                    {actions.map((a) => <option key={a} value={a}>{a}</option>)}
                </select>
                <input value={filters.actorEmail} onChange={set('actorEmail')} placeholder="Actor email" className={inputClass} />
                <input value={filters.ip} onChange={set('ip')} placeholder="IP address" className={inputClass} />
                <input type="datetime-local" value={filters.from} onChange={set('from')} className={inputClass} title="From" />
                <input type="datetime-local" value={filters.to} onChange={set('to')} className={inputClass} title="To" />
                <div className="flex gap-2">
                    <button type="submit" className="h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center gap-2">
                        <Icon name="search" className="text-[18px]" /> Search
                    </button>
                    <button type="button" onClick={reset} className="h-11 px-3 rounded-xl border border-outline-variant text-sm font-semibold">Reset</button>
                </div>
            </form>

            <Alert>{error}</Alert>

            {!data && !error && <p className="text-on-surface-variant">Loading...</p>}
            {data && rows.length === 0 && <EmptyState bordered icon={status === 'OPEN' ? 'verified' : 'notifications_off'}
                                                      title={status === 'OPEN' ? 'All clear' : 'No acknowledged alerts yet'}
                                                      text={status === 'OPEN' ? 'Failed notifications and stuck hand-overs will show up here.' : 'Alerts you acknowledge will be listed here.'} />}

            {rows.length > 0 && (
                <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <thead className="bg-surface-container-low text-on-surface-variant">
                        <tr>
                            <th className="p-4">Time</th>
                            <th className="p-4">Action</th>
                            <th className="p-4">Result</th>
                            <th className="p-4">Actor</th>
                            <th className="p-4">IP</th>
                            <th className="p-4">Detail</th>
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((l) => (
                            <React.Fragment key={l.id}>
                                <tr className="border-t border-outline-variant/20 align-top cursor-pointer hover:bg-surface-container-low"
                                    onClick={() => setExpanded(expanded === l.id ? null : l.id)}>
                                    <td className="p-4 whitespace-nowrap">{formatDate(l.createdAt, true)}</td>
                                    <td className="p-4 font-mono text-xs font-semibold">{l.action}</td>
                                    <td className="p-4">
                                        {l.result && (
                                            <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${RESULT_CLS[l.result] || ''}`}>{l.result}</span>
                                        )}
                                    </td>
                                    <td className="p-4">{l.actorEmail || <span className="text-outline">System</span>}</td>
                                    <td className="p-4 font-mono text-xs">{l.ipAddress || '—'}</td>
                                    <td className="p-4 text-on-surface-variant max-w-xs truncate">{l.detail || '—'}</td>
                                </tr>
                                {expanded === l.id && (
                                    <tr className="bg-surface-container-low">
                                        <td colSpan={6} className="px-4 pb-4 text-xs text-on-surface-variant">
                                            <div className="grid sm:grid-cols-2 gap-x-6 gap-y-1">
                                                <span><b>Detail:</b> {l.detail || '—'}</span>
                                                <span><b>Entity:</b> {l.entityType ? `${l.entityType} ${l.entityId || ''}` : '—'}</span>
                                                <span><b>Vault:</b> {l.vaultId || '—'}</span>
                                                <span><b>Actor ID:</b> {l.actorId || '—'}</span>
                                                <span className="sm:col-span-2 break-all"><b>User agent:</b> {l.userAgent || '—'}</span>
                                            </div>
                                        </td>
                                    </tr>
                                )}
                            </React.Fragment>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />
        </AdminLayout>
    );
}