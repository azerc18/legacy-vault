import React, { useEffect, useState } from 'react';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Alert, formatDate, humanize, inputClass } from './ownerUi.jsx';

// Nhật ký cấp Owner (FR-05): sự kiện DMS và Executor — không gắn với vault nào nên không có trong nhật ký từng vault.
// BE mặc định 30 ngày gần nhất, tối đa 1 năm.
const RANGES = [
    { value: 30, label: 'Last 30 days' },
    { value: 90, label: 'Last 90 days' },
    { value: 365, label: 'Last 12 months' },
];

export default function OwnerActivity() {
    const [days, setDays] = useState(30);
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        setError('');
        const end = new Date();
        const start = new Date(end.getTime() - days * 24 * 60 * 60 * 1000);
        ownerApi.getOwnerActivityLogs({ startDate: start.toISOString(), endDate: end.toISOString(), page, size: 20 })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, 'Could not load activity.')));
    }, [days, page]);

    const rows = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;

    return (
        <OwnerLayout
            title="Activity log"
            subtitle="Dead Man's Switch and executor events. Vault-specific events are in each vault's page."
            actions={
                <select value={days} onChange={(e) => { setDays(Number(e.target.value)); setPage(0); }} className={`${inputClass} w-48`}>
                    {RANGES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                </select>
            }
        >
            <Alert>{error}</Alert>
            <Card>
                {!data && !error && <p className="text-sm text-on-surface-variant">Loading...</p>}
                {data && rows.length === 0 && <p className="text-sm text-on-surface-variant">No activity in this period.</p>}
                {rows.length > 0 && (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                            <thead className="text-on-surface-variant">
                                <tr><th className="py-2 pr-4">Time</th><th className="py-2 pr-4">Action</th><th className="py-2 pr-4">By</th><th className="py-2">Details</th></tr>
                            </thead>
                            <tbody>
                                {rows.map((r) => (
                                    <tr key={r.id} className="border-t border-outline-variant/20">
                                        <td className="py-2 pr-4 whitespace-nowrap">{formatDate(r.createdAt, true)}</td>
                                        <td className="py-2 pr-4 font-semibold">{humanize(r.action)}</td>
                                        <td className="py-2 pr-4">{r.actorName || 'System'}</td>
                                        <td className="py-2 text-on-surface-variant">{r.description || '—'}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
                {totalPages > 1 && (
                    <div className="flex justify-between items-center mt-4 text-sm">
                        <button onClick={() => setPage((p) => p - 1)} disabled={page === 0} className="font-semibold text-secondary disabled:text-outline">← Previous</button>
                        <span className="text-on-surface-variant">Page {page + 1} / {totalPages}</span>
                        <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= totalPages} className="font-semibold text-secondary disabled:text-outline">Next →</button>
                    </div>
                )}
            </Card>
        </OwnerLayout>
    );
}
