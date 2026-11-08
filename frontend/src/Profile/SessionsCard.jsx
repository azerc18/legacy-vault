// Thiết bị đang đăng nhập (ý từ Bitwarden "Devices / Deauthorize sessions" và Google "Your devices").
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { sessionApi, parseUserAgent } from '../api/sessionApi.js';
import { getErrorMessage } from '../api/http.js';
import { Card, Alert, Icon, ConfirmDialog, formatDate, btnOutline } from '../Owner/ownerUi.jsx';

function deviceIcon({ mobile, browser }) {
    if (browser === 'API') return 'terminal';
    return mobile ? 'smartphone' : 'computer';
}

export default function SessionsCard() {
    const { t } = useTranslation('profile');
    const [sessions, setSessions] = useState(null);
    const [error, setError] = useState('');

    const onReload = () => sessionApi.list()
        .then((list) => { setSessions(list); setError(''); })
        .catch((e) => setError(getErrorMessage(e, t('sessions.errors.load'))));

    useEffect(() => { onReload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps -- chỉ tải lần đầu
    const [busyId, setBusyId] = useState(null);
    const [confirmAll, setConfirmAll] = useState(false);
    const [actionError, setActionError] = useState('');
    const [success, setSuccess] = useState('');

    const run = async (id, fn, okMsg) => {
        setActionError(''); setSuccess(''); setBusyId(id);
        try {
            const res = await fn();
            setSuccess(typeof okMsg === 'function' ? okMsg(res) : okMsg);
            await onReload();
        } catch (e) {
            setActionError(getErrorMessage(e, t('sessions.errors.failed')));
        } finally {
            setBusyId(null);
        }
    };

    const others = (sessions || []).filter((s) => !s.current).length;

    return (
        <Card title={t('sessions.title')} icon="devices">
            <p className="-mt-2 mb-4 text-sm text-on-surface-variant">{t('sessions.description')}</p>
            <Alert>{error || actionError}</Alert>
            <Alert type="success">{success}</Alert>

            {sessions == null && !error && <p className="text-sm text-on-surface-variant">{t('loading')}</p>}

            {sessions && (
                <ul className="divide-y divide-outline-variant/30">
                    {sessions.map((s) => {
                        const ua = parseUserAgent(s.userAgent);
                        const name = [ua.browser, ua.os].filter(Boolean).join(' · ') || t('sessions.unknownDevice');
                        return (
                            <li key={s.id} className="py-3 flex items-center gap-3">
                                <span className={`w-10 h-10 shrink-0 rounded-xl flex items-center justify-center ${s.current ? 'bg-primary text-on-primary' : 'bg-surface-container text-on-surface-variant'}`}>
                                    <Icon name={deviceIcon(ua)} className="text-[20px]" />
                                </span>
                                <span className="flex-1 min-w-0">
                                    <span className="flex flex-wrap items-center gap-2">
                                        <span className="text-sm font-semibold">{name}</span>
                                        {s.current && (
                                            <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-success-container text-on-success-container">
                                                {t('sessions.thisDevice')}
                                            </span>
                                        )}
                                    </span>
                                    <span className="block text-xs text-on-surface-variant truncate">
                                        {[s.ipAddress, t('sessions.lastActive', { time: formatDate(s.lastActiveAt, true) })].filter(Boolean).join(' · ')}
                                    </span>
                                </span>
                                {!s.current && (
                                    <button type="button" disabled={busyId != null}
                                            onClick={() => run(s.id, () => sessionApi.revoke(s.id), t('sessions.revoked'))}
                                            className="shrink-0 text-xs font-semibold text-error hover:underline disabled:opacity-50">
                                        {busyId === s.id ? t('sessions.working') : t('sessions.signOut')}
                                    </button>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}

            {others > 0 && (
                <div className="mt-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <p className="text-xs text-on-surface-variant">{t('sessions.note')}</p>
                    <button type="button" className={`${btnOutline} shrink-0`} disabled={busyId != null} onClick={() => setConfirmAll(true)}>
                        <Icon name="logout" className="text-[18px]" /> {t('sessions.signOutOthers')}
                    </button>
                </div>
            )}

            <ConfirmDialog
                open={confirmAll}
                danger
                title={t('sessions.confirmTitle')}
                message={t('sessions.confirmMessage', { count: others })}
                confirmLabel={t('sessions.signOutOthers')}
                busy={busyId === 'all'}
                onCancel={() => setConfirmAll(false)}
                onConfirm={() => {
                    setConfirmAll(false);
                    run('all', () => sessionApi.revokeOthers(), (n) => t('sessions.revokedOthers', { count: n }));
                }}
            />
        </Card>
    );
}
