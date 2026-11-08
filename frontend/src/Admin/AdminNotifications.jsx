// B1: Email thông báo gửi lỗi + gửi lại thủ công (GET /api/admin/notifications, POST .../{id}/retry)
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate } from '../Owner/ownerUi.jsx';
import EmptyState from '../Common/EmptyState.jsx';
import { AdminLayout, Dossier, Pager, Stamp, toneOf } from './adminUi.jsx';
import UserDrawer from './UserDrawer.jsx';

// Loại email BE cho phép gửi lại (khớp AdminNotificationService)
const RETRYABLE = ['EXECUTOR_ACTIVATED'];

export default function AdminNotifications() {
    const { t } = useTranslation('admin');
    const [status, setStatus] = useState('FAILED');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busyId, setBusyId] = useState(null);
    const [openUserId, setOpenUserId] = useState(null);

    const load = useCallback(() => {
        setError('');
        adminApi.listNotifications({ status, page })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, t('notifications.loadError'))));
    }, [status, page]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    const retry = async (n) => {
        setBusyId(n.id); setError(''); setSuccess('');
        try {
            await adminApi.retryNotification(n.id);
            setData((d) => d && { ...d, content: d.content.filter((x) => x.id !== n.id), totalElements: d.totalElements - 1 });
            setSuccess(t('notifications.retried', { email: n.recipientEmail }));
        } catch (e) {
            setError(getErrorMessage(e, t('notifications.retryError')));
            load();
        } finally {
            setBusyId(null);
        }
    };

    const rows = data?.content ?? [];
    const tab = (s) => (
        <button key={s} onClick={() => { setStatus(s); setPage(0); setData(null); setSuccess(''); }}
                className={`px-4 h-10 rounded-xl text-sm font-semibold ${status === s ? 'bg-primary text-on-primary' : 'border border-outline-variant text-on-surface-variant'} lv-pill`}>
            {t(`notifications.status.${s}`)}
        </button>
    );

    return (
        <AdminLayout title={t('notifications.title')} subtitle={t('notifications.subtitle')}>
            <div className="flex flex-wrap gap-2 mb-6">{['FAILED', 'QUEUED', 'SENT'].map(tab)}</div>

            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            {!data && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
            {data && rows.length === 0 && (
                <EmptyState bordered icon={status === 'FAILED' ? 'mark_email_read' : 'inbox'}
                            title={status === 'FAILED' ? t('notifications.empty.failedTitle') : t('notifications.empty.title')}
                            text={status === 'FAILED' ? t('notifications.empty.failedText') : ''} />
            )}

            {rows.length > 0 && (
                <Dossier title={t('notifications.ledger')} tab={t('notifications.total', { count: data.totalElements })} bodyClass="overflow-x-auto">
                    <table className="lv-ledger is-stack">
                        <thead>
                        <tr>
                            <th>{t('notifications.cols.created')}</th>
                            <th>{t('notifications.cols.recipient')}</th>
                            <th>{t('notifications.cols.type')}</th>
                            <th>{t('notifications.cols.attempts')}</th>
                            <th>{t('notifications.cols.status')}</th>
                            <th />
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((n) => (
                            <tr key={n.id} className="is-row">
                                <td data-label={t('notifications.cols.created')} className="whitespace-nowrap">{formatDate(n.createdAt, true)}</td>
                                <td data-label={t('notifications.cols.recipient')}>
                                    <button onClick={() => setOpenUserId(n.recipientId)} className="font-semibold text-left hover:text-secondary hover:underline">{n.recipientEmail}</button>
                                </td>
                                <td data-label={t('notifications.cols.type')}>{t(`notifications.types.${n.type}`, { defaultValue: n.type })}</td>
                                <td data-label={t('notifications.cols.attempts')}>
                                    <span className="lv-mono">{n.attemptCount}</span>
                                    {n.lastAttemptAt && <p className="text-[11px] text-outline">{t('notifications.lastAttempt', { date: formatDate(n.lastAttemptAt, true) })}</p>}
                                </td>
                                <td data-label={t('notifications.cols.status')}><Stamp tone={toneOf(n.status)}>{t(`notifications.status.${n.status}`, { defaultValue: n.status })}</Stamp></td>
                                <td className="text-right">
                                    {n.status === 'FAILED' && (RETRYABLE.includes(n.type)
                                        ? (
                                            <button onClick={() => retry(n)} disabled={busyId === n.id}
                                                    className="h-9 px-3 rounded-xl border border-outline-variant text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-surface-container-low disabled:opacity-60">
                                                <Icon name="forward_to_inbox" className="text-[16px]" /> {busyId === n.id ? t('wait') : t('notifications.retry')}
                                            </button>
                                        )
                                        : <span className="text-[11px] text-outline">{t('notifications.notRetryable')}</span>)}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </Dossier>
            )}
            <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />
            <UserDrawer userId={openUserId} onClose={() => setOpenUserId(null)} />
        </AdminLayout>
    );
}
