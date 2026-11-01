import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { getHistory } from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import { statusLabel, STATUS_CLASS, formatDate } from './verifierUi.js';
import VerifierHeader from './VerifierHeader.jsx';
import {PAGE_WIDTH} from "../Common/AppHeader.jsx";
import EmptyState from '../Common/EmptyState.jsx';

const PAGE_SIZE = 20;

export default function VerificationHistory() {
    const { t } = useTranslation('verifier');
    const navigate = useNavigate();
    const [status, setStatus] = useState('');   // '' = tất cả
    const [from, setFrom] = useState('');       // 'YYYY-MM-DD'
    const [to, setTo] = useState('');
    const [page, setPage] = useState(0);
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Tải lại khi bộ lọc hoặc trang thay đổi
    useEffect(() => {
        let cancelled = false;   // bỏ kết quả cũ nếu bộ lọc đã đổi trong lúc chờ
        setLoading(true);
        setError('');
        getHistory({ status: status || undefined, from: from || undefined, to: to || undefined, page, size: PAGE_SIZE })
            .then((data) => { if (!cancelled) setItems(data); })
            .catch((e) => {
                if (cancelled) return;
                setItems([]);   // lỗi thì không hiện kết quả cũ dưới thông báo lỗi
                setError(getErrorMessage(e, t('history.loadError')));
            })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [status, from, to, page]); // eslint-disable-line react-hooks/exhaustive-deps -- t chỉ dùng cho thông báo lỗi

    // Đổi bộ lọc thì quay về trang đầu
    const onFilter = (setter) => (e) => { setter(e.target.value); setPage(0); };

    const inputCls = 'h-10 px-3 rounded-lg border border-outline-variant bg-surface-container-lowest';

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased">
            <VerifierHeader title={t('history.title')} backTo="/verifier/home" />

            <main className={`${PAGE_WIDTH} py-8`}>
                <div className="flex flex-wrap items-end gap-4 mb-6">
                    <label className="flex flex-col gap-1 text-sm text-on-surface-variant">
                        {t('history.result')}
                        <select value={status} onChange={onFilter(setStatus)} className={inputCls}>
                            <option value="">{t('history.all')}</option>
                            <option value="APPROVED">{t('history.awaitingSignature')}</option>
                            <option value="SIGNED">{statusLabel('SIGNED')}</option>
                            <option value="REJECTED">{statusLabel('REJECTED')}</option>
                        </select>
                    </label>
                    <label className="flex flex-col gap-1 text-sm text-on-surface-variant">
                        {t('history.from')}
                        <input type="date" value={from} max={to || undefined} onChange={onFilter(setFrom)} className={inputCls} />
                    </label>
                    <label className="flex flex-col gap-1 text-sm text-on-surface-variant">
                        {t('history.to')}
                        <input type="date" value={to} min={from || undefined} onChange={onFilter(setTo)} className={inputCls} />
                    </label>
                    {(status || from || to) && (
                        <button
                            onClick={() => { setStatus(''); setFrom(''); setTo(''); setPage(0); }}
                            className="h-10 px-3 text-sm font-semibold text-secondary"
                        >
                            {t('history.clear')}
                        </button>
                    )}
                </div>

                {loading && items.length === 0 && <p>{t('loading')}</p>}
                {error && <div className="p-4 rounded-xl bg-error-container text-on-error-container">{error}</div>}
                {!loading && !error && items.length === 0 && ((status || from || to) ? (
                    <EmptyState bordered icon="filter_alt_off" title={t('history.noResultsTitle')}
                                text={t('history.noResultsText')}
                                action={{ label: t('history.clear'), onClick: () => { setStatus(''); setFrom(''); setTo(''); setPage(0); } }} />
                ) : (
                    <EmptyState bordered icon="history" title={t('history.emptyTitle')}
                                text={t('history.emptyText')}
                                action={{ to: '/verifier', label: t('history.openPending'), icon: 'gavel' }} />
                ))}

                {items.length > 0 && (
                    <div className={`bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 overflow-x-auto transition-opacity ${loading ? 'opacity-50' : ''}`}>
                        <table className="w-full text-left">
                            <thead className="bg-surface-container-low text-sm text-on-surface-variant">
                            <tr>
                                <th className="p-4">{t('history.cols.decided')}</th>
                                <th className="p-4">{t('history.cols.owner')}</th>
                                <th className="p-4">{t('history.cols.executor')}</th>
                                <th className="p-4">{t('history.cols.result')}</th>
                                <th className="p-4">{t('history.cols.note')}</th>
                            </tr>
                            </thead>
                            <tbody>
                            {items.map((h) => (
                                <tr
                                    key={h.id}
                                    onClick={() => navigate(`/verifier/request/${h.id}`)}
                                    className="border-t border-outline-variant/20 hover:bg-surface-container-low cursor-pointer"
                                >
                                    <td className="p-4 whitespace-nowrap">{formatDate(h.decidedAt, true)}</td>
                                    <td className="p-4 font-bold">
                                        <Link to={`/verifier/request/${h.id}`} className="hover:text-primary">{h.ownerName}</Link>
                                    </td>
                                    <td className="p-4">{h.executorName}</td>
                                    <td className="p-4">
                                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold whitespace-nowrap ${STATUS_CLASS[h.status]}`}>
                                            {statusLabel(h.status)}
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
                            {t('history.prev')}
                        </button>
                        <span className="text-on-surface-variant">{t('history.page', { n: page + 1 })}</span>
                        <button onClick={() => setPage((p) => p + 1)} disabled={items.length < PAGE_SIZE} className="font-semibold text-secondary disabled:text-outline">
                            {t('history.next')}
                        </button>
                    </div>
                )}
            </main>
        </div>
    );
}
