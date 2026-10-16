import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getHistory } from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import { STATUS_LABEL, STATUS_CLASS, formatDate } from './verifierUi.js';

const PAGE_SIZE = 20;

export default function VerificationHistory() {
    const [status, setStatus] = useState('');   // '' = tất cả
    const [from, setFrom] = useState('');       // 'YYYY-MM-DD'
    const [to, setTo] = useState('');
    const [page, setPage] = useState(0);
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Tải lại khi bộ lọc hoặc trang thay đổi
    useEffect(() => {
        setLoading(true);
        setError('');
        getHistory({ status: status || undefined, from: from || undefined, to: to || undefined, page, size: PAGE_SIZE })
            .then(setItems)
            .catch((e) => setError(getErrorMessage(e, 'Could not load history.')))
            .finally(() => setLoading(false));
    }, [status, from, to, page]);

    // Đổi bộ lọc thì quay về trang đầu
    const onFilter = (setter) => (e) => { setter(e.target.value); setPage(0); };

    const inputCls = 'h-10 px-3 rounded-lg border border-outline-variant bg-surface-container-lowest';

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased">
            <header className="sticky top-0 z-10 bg-background/95 backdrop-blur-sm border-b border-outline-variant/30">
                <div className="max-w-[1200px] mx-auto px-5 h-20 flex items-center gap-3">
                    <Link to="/verifier" className="p-2 rounded-full hover:bg-surface-container-high text-on-surface-variant">
                        <span className="material-symbols-outlined">arrow_back</span>
                    </Link>
                    <h1 className="font-headline-sm text-primary font-bold tracking-tight">Verification History</h1>
                </div>
            </header>

            <main className="max-w-[1200px] mx-auto p-5 mt-6">
                <div className="flex flex-wrap items-end gap-4 mb-6">
                    <label className="flex flex-col gap-1 text-sm text-on-surface-variant">
                        Result
                        <select value={status} onChange={onFilter(setStatus)} className={inputCls}>
                            <option value="">All</option>
                            <option value="APPROVED">Approved (awaiting signature)</option>
                            <option value="SIGNED">Signed</option>
                            <option value="REJECTED">Rejected</option>
                        </select>
                    </label>
                    <label className="flex flex-col gap-1 text-sm text-on-surface-variant">
                        From
                        <input type="date" value={from} max={to || undefined} onChange={onFilter(setFrom)} className={inputCls} />
                    </label>
                    <label className="flex flex-col gap-1 text-sm text-on-surface-variant">
                        To
                        <input type="date" value={to} min={from || undefined} onChange={onFilter(setTo)} className={inputCls} />
                    </label>
                    {(status || from || to) && (
                        <button
                            onClick={() => { setStatus(''); setFrom(''); setTo(''); setPage(0); }}
                            className="h-10 px-3 text-sm font-semibold text-secondary"
                        >
                            Clear filters
                        </button>
                    )}
                </div>

                {loading && <p>Loading...</p>}
                {error && <div className="p-4 rounded-xl bg-error-container text-on-error-container">{error}</div>}
                {!loading && !error && items.length === 0 && <p>No history found.</p>}

                {!loading && items.length > 0 && (
                    <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 overflow-x-auto">
                        <table className="w-full text-left">
                            <thead className="bg-surface-container-low text-sm text-on-surface-variant">
                            <tr>
                                <th className="p-4">Decided</th>
                                <th className="p-4">Vault Owner</th>
                                <th className="p-4">Executor</th>
                                <th className="p-4">Result</th>
                                <th className="p-4">Note</th>
                            </tr>
                            </thead>
                            <tbody>
                            {items.map((h) => (
                                <tr key={h.id} className="border-t border-outline-variant/20 hover:bg-surface-container-low">
                                    <td className="p-4 whitespace-nowrap">{formatDate(h.decidedAt, true)}</td>
                                    <td className="p-4 font-bold">
                                        <Link to={`/verifier/request/${h.id}`} className="hover:text-primary">{h.ownerName}</Link>
                                    </td>
                                    <td className="p-4">{h.executorName}</td>
                                    <td className="p-4">
                                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold whitespace-nowrap ${STATUS_CLASS[h.status]}`}>
                                            {STATUS_LABEL[h.status]}
                                        </span>
                                    </td>
                                    <td className="p-4 text-on-surface-variant">{h.rejectionReason || '—'}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Phân trang: BE không trả tổng số, nên "Next" tắt khi trang hiện tại chưa đầy */}
                {!loading && (page > 0 || items.length === PAGE_SIZE) && (
                    <div className="flex justify-between items-center mt-4 text-sm">
                        <button onClick={() => setPage((p) => p - 1)} disabled={page === 0} className="font-semibold text-secondary disabled:text-outline">
                            ← Previous
                        </button>
                        <span className="text-on-surface-variant">Page {page + 1}</span>
                        <button onClick={() => setPage((p) => p + 1)} disabled={items.length < PAGE_SIZE} className="font-semibold text-secondary disabled:text-outline">
                            Next →
                        </button>
                    </div>
                )}
            </main>
        </div>
    );
}
