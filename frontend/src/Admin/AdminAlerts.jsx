import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { getUser } from '../auth/auth.js';
import { Icon, Alert, formatDate, inputClass, LoadingSkeleton } from '../Owner/ownerUi.jsx';
import { AdminLayout, Pager, ReasonDialog, Stamp, toneOf } from './adminUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";
import UserDrawer from './UserDrawer.jsx';

const SEVERITIES = ['HIGH', 'MEDIUM'];

export default function AdminAlerts() {
    const { t } = useTranslation('admin');
    const me = getUser();
    const [status, setStatus] = useState('OPEN');
    const [severity, setSeverity] = useState('');       // B3: lọc theo mức độ
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busy, setBusy] = useState(false);
    const [dialog, setDialog] = useState(null);         // { kind: 'ack' | 'lock', alert }
    const [openUserId, setOpenUserId] = useState(null);

    const load = useCallback(() => {
        setError('');
        adminApi.listAlerts({ status, severity, page })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, t('alerts.loadError'))));
    }, [status, severity, page]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    const switchTab = (s) => { setStatus(s); setPage(0); setData(null); setSuccess(''); };

    const confirm = async (text) => {
        const { kind, alert } = dialog;
        setBusy(true); setError(''); setSuccess('');
        try {
            if (kind === 'ack') {
                await adminApi.acknowledgeAlert(alert.id, text);
                setData((d) => d && { ...d, content: d.content.filter((a) => a.id !== alert.id) });
                setSuccess(t('alerts.acknowledgedMsg'));
            } else {
                // Khoá người dùng liên quan (cùng API với trang Người dùng; BE chặn tự khoá mình)
                await adminApi.updateStatus(alert.relatedUserId, 'LOCKED', text || t('alerts.lockReason', { rule: alert.rule }));
                setData((d) => d && {
                    ...d,
                    content: d.content.map((a) => (a.relatedUserId === alert.relatedUserId ? { ...a, relatedUserStatus: 'LOCKED' } : a)),
                });
                setSuccess(t('users.success.lock', { email: alert.relatedUserEmail }));
            }
        } catch (e) {
            setError(getErrorMessage(e, kind === 'ack' ? t('alerts.ackError') : t('actionFailed')));
            load();
        } finally {
            setBusy(false);
            setDialog(null);
        }
    };

    const rows = data?.content ?? [];
    const tab = (s, label) => (
        <button onClick={() => switchTab(s)}
                className={`px-4 h-10 rounded-xl text-sm font-semibold ${status === s ? 'bg-primary text-on-primary' : 'border border-outline-variant text-on-surface-variant'} lv-pill`}>
            {label}
        </button>
    );
    const linkCls = 'text-xs font-semibold text-secondary hover:text-primary inline-flex items-center gap-1';

    return (
        <AdminLayout title={t('alerts.title')} subtitle={t('alerts.subtitle')}>
            <div className="flex flex-wrap items-center gap-2 mb-6">
                {tab('OPEN', t('alerts.tabs.open'))}
                {tab('ACKNOWLEDGED', t('alerts.tabs.acknowledged'))}
                <select value={severity} onChange={(e) => { setSeverity(e.target.value); setPage(0); setData(null); }}
                        className={`${inputClass} !h-10 max-w-[200px] sm:ml-auto`} aria-label={t('alerts.severityFilter')}>
                    <option value="">{t('alerts.allSeverities')}</option>
                    {SEVERITIES.map((s) => <option key={s} value={s}>{t(`alerts.severity.${s}`)}</option>)}
                </select>
            </div>

            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>

            {!data && !error && <LoadingSkeleton label={t('loading')}/>}
            {data && rows.length === 0 && (
                <EmptyState bordered icon={status === 'OPEN' ? 'verified' : 'notifications_off'}
                            title={status === 'OPEN' ? t('alerts.empty.openTitle') : t('alerts.empty.ackTitle')}
                            text={status === 'OPEN' ? t('alerts.empty.openText') : t('alerts.empty.ackText')} />
            )}

            <div className="space-y-3">
                {rows.map((a) => (
                    <div key={a.id} className="lv-dossier p-5 pt-7">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                            <div className="min-w-0 flex-1">
                                <div className="flex flex-wrap items-center gap-2">
                                    <Stamp tone={toneOf(a.severity)}>{t(`alerts.severity.${a.severity}`, { defaultValue: a.severity })}</Stamp>
                                    <span className="font-semibold">{t(`alerts.rules.${a.rule}`, { defaultValue: a.rule })}</span>
                                </div>
                                <p className="mt-2 text-sm">{a.message}</p>
                                <p className="mt-2 text-xs text-outline">
                                    {t('alerts.meta', { count: a.eventCount, first: formatDate(a.firstSeenAt, true), last: formatDate(a.lastSeenAt, true) })}
                                    {a.acknowledgedAt && <> · {t('alerts.acknowledgedAt', { date: formatDate(a.acknowledgedAt, true) })}</>}
                                </p>
                                {a.acknowledgeNote && (
                                    <p className="mt-2 text-sm border-l-2 border-outline-variant pl-3 text-on-surface-variant italic">
                                        <Icon name="sticky_note_2" className="text-[16px] align-[-3px] mr-1 not-italic" />{a.acknowledgeNote}
                                    </p>
                                )}
                                {a.relatedUserId && (
                                    <p className="mt-2 text-xs flex flex-wrap items-center gap-2">
                                        <span className="text-outline">{t('alerts.relatedUser')}</span>
                                        <button onClick={() => setOpenUserId(a.relatedUserId)} className="font-semibold hover:text-secondary hover:underline">{a.relatedUserEmail}</button>
                                        {a.relatedUserStatus && <Stamp tone={toneOf(a.relatedUserStatus)} flat>{t(`users.status.${a.relatedUserStatus}`, { defaultValue: a.relatedUserStatus })}</Stamp>}
                                    </p>
                                )}
                            </div>
                            <div className="flex flex-wrap items-center gap-3">
                                {a.rule === 'LOGIN_FAILURES_FROM_IP' && (
                                    <Link to={`/admin/audit-logs?action=LOGIN_FAILED&ip=${encodeURIComponent(a.subject)}`} className={linkCls}>
                                        <Icon name="history" className="text-[16px]" /> {t('alerts.viewLogs')}
                                    </Link>
                                )}
                                {a.relatedUserId && a.relatedUserStatus === 'ACTIVE' && a.relatedUserId !== me?.id && (
                                    <button onClick={() => setDialog({ kind: 'lock', alert: a })}
                                            className="h-9 px-3 rounded-xl border border-error/40 text-error text-sm font-semibold inline-flex items-center gap-1.5 hover:bg-error-container/40">
                                        <Icon name="lock" className="text-[16px]" /> {t('alerts.lockUser')}
                                    </button>
                                )}
                                {a.status === 'OPEN' && (
                                    <button onClick={() => setDialog({ kind: 'ack', alert: a })}
                                            className="h-9 px-4 rounded-xl border border-outline-variant text-sm font-semibold hover:bg-surface-container-low">
                                        {t('alerts.acknowledge')}
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>
            <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />

            <ReasonDialog
                open={!!dialog}
                title={dialog?.kind === 'ack' ? t('alerts.ackDialog.title') : t('users.dialog.lock.title')}
                message={dialog?.kind === 'ack' ? t('alerts.ackDialog.message')
                    : dialog ? t('users.dialog.lock.message', { email: dialog.alert.relatedUserEmail }) : ''}
                confirmLabel={dialog?.kind === 'ack' ? t('alerts.acknowledge') : t('users.dialog.lock.confirm')}
                danger={dialog?.kind === 'lock'}
                askReason
                busy={busy}
                onConfirm={confirm}
                onCancel={() => setDialog(null)}
            />
            <UserDrawer userId={openUserId} onClose={() => setOpenUserId(null)} />
        </AdminLayout>
    );
}
