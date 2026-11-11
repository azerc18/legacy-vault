// A2: Trang Thanh toán của Admin — danh sách giao dịch, tổng doanh thu, tải biên lai, xuất CSV.
// Việc 3: hoàn tiền giao dịch SUCCESS (qua cổng hoặc ghi nhận hoàn tay).
import React, { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate, inputClass, LoadingSkeleton } from '../Owner/ownerUi.jsx';
import EmptyState from '../Common/EmptyState.jsx';
import { AdminLayout, Dossier, Pager, Stamp, StatCard, formatVnd, toneOf } from './adminUi.jsx';
import UserDrawer from './UserDrawer.jsx';
import { RefundDialog } from './BillingDialogs.jsx';

const STATUSES = ['SUCCESS', 'PENDING', 'FAILED', 'EXPIRED', 'REFUNDED'];
const EMPTY = { status: '', email: '', from: '', to: '' };

export default function AdminPayments() {
    const { t } = useTranslation('admin');
    const [params] = useSearchParams();
    const initial = { ...EMPTY, status: STATUSES.includes(params.get('status')) ? params.get('status') : '', email: params.get('email') || '' };
    const [openUserId, setOpenUserId] = useState(null);

    const [filters, setFilters] = useState(initial);   // ô đang nhập
    const [applied, setApplied] = useState(initial);   // bộ lọc đã áp dụng
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [summary, setSummary] = useState(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(null);             // id giao dịch đang tải biên lai | 'csv'
    const [refunding, setRefunding] = useState(null);   // dòng giao dịch đang mở hộp thoại hoàn tiền
    const [refundBusy, setRefundBusy] = useState(false);
    const [success, setSuccess] = useState('');

    const load = useCallback(() => {
        setError('');
        adminApi.listPayments({ ...applied, page })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, t('payments.loadError'))));
    }, [applied, page]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    const confirmRefund = async ({ reason, viaGateway }) => {
        const p = refunding;
        setRefundBusy(true); setError(''); setSuccess('');
        try {
            await adminApi.refundPayment(p.id, reason, viaGateway);
            setSuccess(t('payments.refund.success', { email: p.userEmail }));
            setRefunding(null);
            load();
            adminApi.paymentSummary(applied).then(setSummary).catch(() => {});
        } catch (e) {
            setError(getErrorMessage(e, t('payments.refund.error')));
            setRefunding(null);
        } finally {
            setRefundBusy(false);
        }
    };
    useEffect(() => {
        adminApi.paymentSummary(applied).then(setSummary).catch(() => setSummary(null));
    }, [applied]);

    const set = (k) => (e) => setFilters((f) => ({ ...f, [k]: e.target.value }));
    const submit = (e) => { e.preventDefault(); setPage(0); setData(null); setApplied({ ...filters }); };
    const reset = () => { setFilters(EMPTY); setPage(0); setData(null); setApplied(EMPTY); };

    const receipt = async (p) => {
        setBusy(p.id); setError('');
        try { await adminApi.downloadReceipt(p.id); } catch (e) { setError(getErrorMessage(e, t('payments.receiptError'))); } finally { setBusy(null); }
    };
    const exportCsv = async () => {
        setBusy('csv'); setError('');
        try { await adminApi.exportPayments(applied); } catch (e) { setError(getErrorMessage(e, t('exportError'))); } finally { setBusy(null); }
    };

    const rows = data?.content ?? [];
    const filtered = Object.values(applied).some(Boolean);

    return (
        <AdminLayout
            title={t('payments.title')}
            subtitle={t('payments.subtitle')}
            actions={(
                <button onClick={exportCsv} disabled={busy === 'csv'}
                        className="h-10 px-4 rounded-xl border border-outline-variant text-sm font-semibold inline-flex items-center gap-2 hover:bg-surface-container-low disabled:opacity-60">
                    <Icon name="download" className="text-[18px]" /> {busy === 'csv' ? t('wait') : t('exportCsv')}
                </button>
            )}
        >
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-6">
                <StatCard icon="payments" tone="ok" label={t('payments.summary.revenue')} value={summary ? formatVnd(summary.totalRevenueVnd) : '—'}
                          foot={filtered ? t('payments.summary.filtered') : t('payments.summary.allTime')} />
                <StatCard icon="check_circle" tone="ok" label={t('payments.status.SUCCESS')} value={summary?.successCount ?? '—'}
                          foot={summary?.refundedCount ? t('payments.summary.refundedFoot', { count: summary.refundedCount }) : undefined} />
                <StatCard icon="schedule" tone="warn" label={t('payments.status.PENDING')} value={summary?.pendingCount ?? '—'}
                          foot={summary ? t('payments.summary.expiredFoot', { count: summary.expiredCount ?? 0 }) : undefined} />
                <StatCard icon="cancel" tone="danger" label={t('payments.status.FAILED')} value={summary?.failedCount ?? '—'} />
            </div>

            <form onSubmit={submit} className="grid gap-3 mb-6 sm:grid-cols-2 lg:grid-cols-[1fr_170px_160px_160px_auto]">
                <input value={filters.email} onChange={set('email')} placeholder={t('payments.emailPlaceholder')} className={inputClass} />
                <select value={filters.status} onChange={set('status')} className={inputClass}>
                    <option value="">{t('allStatuses')}</option>
                    {STATUSES.map((s) => <option key={s} value={s}>{t(`payments.status.${s}`)}</option>)}
                </select>
                <input type="date" value={filters.from} onChange={set('from')} className={inputClass} title={t('audit.from')} aria-label={t('audit.from')} />
                <input type="date" value={filters.to} onChange={set('to')} className={inputClass} title={t('audit.to')} aria-label={t('audit.to')} />
                <div className="flex gap-2">
                    <button type="submit" className="h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center gap-2 lv-pill">
                        <Icon name="search" className="text-[18px]" /> {t('search')}
                    </button>
                    <button type="button" onClick={reset} className="h-11 px-3 rounded-xl border border-outline-variant text-sm font-semibold">{t('reset')}</button>
                </div>
            </form>

            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            {!data && !error && <LoadingSkeleton label={t('loading')}/>}
            {data && rows.length === 0 && (
                <EmptyState bordered icon="receipt_long" title={t('payments.empty.title')} text={filtered ? t('payments.empty.filtered') : t('payments.empty.text')} />
            )}

            {rows.length > 0 && (
                <Dossier title={t('payments.ledger')} tab={t('payments.total', { count: data.totalElements })} bodyClass="overflow-x-auto">
                    <table className="lv-ledger is-stack">
                        <thead>
                        <tr>
                            <th>{t('payments.cols.time')}</th>
                            <th>{t('payments.cols.user')}</th>
                            <th>{t('payments.cols.plan')}</th>
                            <th className="text-right">{t('payments.cols.amount')}</th>
                            <th>{t('payments.cols.status')}</th>
                            <th>{t('payments.cols.provider')}</th>
                            <th />
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((p) => (
                            <tr key={p.id} className="is-row">
                                <td data-label={t('payments.cols.time')} className="whitespace-nowrap">
                                    {formatDate(p.createdAt, true)}
                                    {p.paidAt && <p className="text-[11px] text-outline">{t('payments.paidAt', { date: formatDate(p.paidAt, true) })}</p>}
                                </td>
                                <td data-label={t('payments.cols.user')}>
                                    <button onClick={() => setOpenUserId(p.userId)} className="font-semibold text-left hover:text-secondary hover:underline" title={t('drawer.open')}>{p.userEmail}</button>
                                    {p.userFullName && <p className="text-xs text-on-surface-variant">{p.userFullName}</p>}
                                </td>
                                <td data-label={t('payments.cols.plan')}>
                                    <p className="font-semibold">{p.planName || p.planCode}</p>
                                    <p className="text-[11px] text-outline">{t('payments.currentPlan', { plan: p.currentPlanCode })}</p>
                                </td>
                                <td data-label={t('payments.cols.amount')} className="lv-num font-semibold">{formatVnd(p.amountVnd)}</td>
                                <td data-label={t('payments.cols.status')}>
                                    <Stamp tone={toneOf(p.status)}>{t(`payments.status.${p.status}`, { defaultValue: p.status })}</Stamp>
                                    {p.refundedAt && (
                                        <p className="text-[11px] text-outline mt-1" title={p.refundReason || ''}>
                                            {t('payments.refund.on', { date: formatDate(p.refundedAt, true) })}
                                        </p>
                                    )}
                                </td>
                                <td data-label={t('payments.cols.provider')}>
                                    <p className="text-xs font-semibold">{p.provider}</p>
                                    {p.providerTransactionNo && <p className="lv-mono text-[11px] text-outline">#{p.providerTransactionNo}</p>}
                                </td>
                                <td className="text-right">
                                    {p.status === 'SUCCESS' && (
                                        <div className="inline-flex flex-col items-end gap-1.5">
                                            <button onClick={() => receipt(p)} disabled={busy === p.id}
                                                    className="text-xs font-semibold text-secondary hover:text-primary inline-flex items-center gap-1 disabled:opacity-60">
                                                <Icon name="picture_as_pdf" className="text-[16px]" /> {busy === p.id ? t('wait') : t('payments.receipt')}
                                            </button>
                                            <button onClick={() => setRefunding(p)}
                                                    className="text-xs font-semibold text-error hover:underline inline-flex items-center gap-1 whitespace-nowrap">
                                                <Icon name="undo" className="text-[16px]" /> {t('payments.refund.action')}
                                            </button>
                                        </div>
                                    )}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </Dossier>
            )}
            <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />
            <UserDrawer userId={openUserId} onClose={() => setOpenUserId(null)} />
            <RefundDialog payment={refunding} busy={refundBusy} onConfirm={confirmRefund} onCancel={() => setRefunding(null)} />
        </AdminLayout>
    );
}
