import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate } from '../Owner/ownerUi.jsx';
import { AdminLayout, Pager } from './adminUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";

const SEVERITY_CLS = {
    HIGH: 'bg-error-container text-on-error-container',
    MEDIUM: 'bg-warning-container text-on-warning-container',
};

export default function AdminAlerts() {
    const { t } = useTranslation('admin');
    const [status, setStatus] = useState('OPEN');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busyId, setBusyId] = useState(null);

    const load = useCallback(() => {
        setError('');
        adminApi.listAlerts({ status, page })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, t('alerts.loadError'))));
    }, [status, page]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    const switchTab = (s) => { setStatus(s); setPage(0); setData(null); setSuccess(''); };

    const acknowledge = async (alert) => {
        setBusyId(alert.id); setError(''); setSuccess('');
        try {
            await adminApi.acknowledgeAlert(alert.id);
            setData((d) => d && { ...d, content: d.content.filter((a) => a.id !== alert.id) });
            setSuccess(t('alerts.acknowledgedMsg'));
        } catch (e) {
            setError(getErrorMessage(e, t('alerts.ackError')));
            load();
        } finally {
            setBusyId(null);
        }
    };

    const rows = data?.content ?? [];
    const tab = (s, label) => (
        <button onClick={() => switchTab(s)}
                className={`px-4 h-10 rounded-xl text-sm font-semibold ${status === s ? 'bg-primary text-on-primary' : 'border border-outline-variant text-on-surface-variant'} lv-pill`}>
            {label}
        </button>
    );

    return (
        <AdminLayout title={t('alerts.title')} subtitle={t('alerts.subtitle')}>
            <div className="flex gap-2 mb-6">
                {tab('OPEN', t('alerts.tabs.open'))}
                {tab('ACKNOWLEDGED', t('alerts.tabs.acknowledged'))}
            </div>

            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>

            {!data && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
            {data && rows.length === 0 && (
                <EmptyState bordered icon={status === 'OPEN' ? 'verified' : 'notifications_off'}
                            title={status === 'OPEN' ? t('alerts.empty.openTitle') : t('alerts.empty.ackTitle')}
                            text={status === 'OPEN' ? t('alerts.empty.openText') : t('alerts.empty.ackText')} />
            )}

            <div className="space-y-3">
                {rows.map((a) => (
                    <div key={a.id} className="bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                            <div className="min-w-0">
                                <div className="flex items-center gap-2">
                                    <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${SEVERITY_CLS[a.severity] || ''}`}>{t(`alerts.severity.${a.severity}`, { defaultValue: a.severity })}</span>
                                    <span className="font-semibold">{t(`alerts.rules.${a.rule}`, { defaultValue: a.rule })}</span>
                                </div>
                                <p className="mt-2 text-sm">{a.message}</p>
                                <p className="mt-2 text-xs text-outline">
                                    {t('alerts.meta', { count: a.eventCount, first: formatDate(a.firstSeenAt, true), last: formatDate(a.lastSeenAt, true) })}
                                    {a.acknowledgedAt && <> · {t('alerts.acknowledgedAt', { date: formatDate(a.acknowledgedAt, true) })}</>}
                                </p>
                            </div>
                            <div className="flex items-center gap-3">
                                {a.rule === 'LOGIN_FAILURES_FROM_IP' && (
                                    <Link to={`/admin/audit-logs?action=LOGIN_FAILED&ip=${encodeURIComponent(a.subject)}`}
                                          className="text-xs font-semibold text-secondary hover:text-primary inline-flex items-center gap-1">
                                        <Icon name="history" className="text-[16px]" /> {t('alerts.viewLogs')}
                                    </Link>
                                )}
                                {a.status === 'OPEN' && (
                                    <button onClick={() => acknowledge(a)} disabled={busyId === a.id}
                                            className="h-9 px-4 rounded-xl border border-outline-variant text-sm font-semibold hover:bg-surface-container-low disabled:opacity-60">
                                        {busyId === a.id ? t('wait') : t('alerts.acknowledge')}
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>
            <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />
        </AdminLayout>
    );
}