import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Alert, formatDate, humanize, inputClass } from './ownerUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";

// Nhật ký cấp Owner (FR-05 & F6): sự kiện DMS/Executor và nhật ký minh bạch "Ai đã xem di sản của bạn".
// Không gửi startDate thì BE trả toàn bộ lịch sử, không giới hạn độ dài khoảng ngày. value 0 = "All time".
const RANGES = [
    { value: 0, label: 'All time' },
    { value: 30, label: 'Last 30 days' },
    { value: 90, label: 'Last 90 days' },
    { value: 365, label: 'Last 12 months' },
];

export default function OwnerActivity() {
    const { t } = useTranslation('accessLog');
    const [tab, setTab] = useState('dms');
    const [days, setDays] = useState(0);
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        setError('');
        setData(null);
        const range = {};
        if (days > 0) {
            const end = new Date();
            range.startDate = new Date(end.getTime() - days * 24 * 60 * 60 * 1000).toISOString();
            range.endDate = end.toISOString();
        }
        const fetcher = tab === 'access' ? ownerApi.getEstateAccessLogs : ownerApi.getOwnerActivityLogs;
        fetcher({ ...range, page, size: 20 })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, 'Could not load activity.')));
    }, [tab, days, page]);

    const rows = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;

    const tabs = [
        { id: 'dms', label: 'DMS & Executor Events' },
        { id: 'access', label: t('tab', 'Who Accessed My Estate') },
    ];

    return (
        <OwnerLayout
            title="Activity log"
            subtitle={tab === 'access'
                ? t('subtitle', 'Transparency log showing every time an Executor, Beneficiary, Admin, or Emergency Access viewed or downloaded your vaults, assets, or legal documents.')
                : "Dead Man's Switch and executor events. Vault-specific events are in each vault's page."}
            actions={
                <select value={days} onChange={(e) => { setDays(Number(e.target.value)); setPage(0); }} className={`${inputClass} w-48`}>
                    {RANGES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                </select>
            }
        >
            <div className="flex gap-2 mb-4 border-b border-outline-variant/20 pb-2">
                {tabs.map((tabItem) => (
                    <button
                        key={tabItem.id}
                        type="button"
                        onClick={() => { setTab(tabItem.id); setPage(0); }}
                        className={`px-4 py-2 rounded-lg text-sm font-semibold transition-colors ${
                            tab === tabItem.id
                                ? 'bg-secondary text-on-secondary'
                                : 'text-on-surface-variant hover:bg-surface-container-high'
                        }`}
                    >
                        {tabItem.label}
                    </button>
                ))}
            </div>
            <Alert>{error}</Alert>
            <Card>
                {!data && !error && <p className="text-sm text-on-surface-variant">Loading...</p>}
                {data && rows.length === 0 && (
                    <EmptyState compact icon={tab === 'access' ? 'visibility' : 'history'}
                                title={days > 0 ? 'No activity in this period' : (tab === 'access' ? t('emptyTitle', 'No external access recorded yet') : 'No activity yet')}
                                text={days > 0
                                    ? 'Choose a longer range above to see older events.'
                                    : (tab === 'access'
                                        ? t('emptyText', 'When an Executor, Beneficiary, Admin, or Emergency Access views your vault, assets, or documents, it will be recorded here.')
                                        : "Check-ins, Dead Man's Switch changes and executor events will appear here.")} />
                )}
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
