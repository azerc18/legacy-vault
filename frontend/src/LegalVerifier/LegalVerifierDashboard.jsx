import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router-dom';
import { getRequests } from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import { getUser } from '../auth/auth.js';
import { statusLabel, STATUS_CLASS, formatDate, shortId } from './verifierUi.js';
import VerifierHeader from './VerifierHeader.jsx';
import {PAGE_WIDTH} from "../Common/AppHeader.jsx";
import EmptyState from '../Common/EmptyState.jsx';
import { LoadingSkeleton } from '../Owner/ownerUi.jsx';

export default function LegalVerifierDashboard() {
    const { t } = useTranslation('verifier');
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const user = getUser();

    useEffect(() => {
        getRequests()
            .then(setRequests)
            .catch((e) => setError(getErrorMessage(e, t('dashboard.loadError'))))
            .finally(() => setLoading(false));
    }, []);

    if (!user) return <Navigate to="/login" replace />;

    const awaitingSign = requests.filter((r) => r.status === 'APPROVED');
    const pending = requests.filter((r) => r.status === 'PENDING');

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col">
            <VerifierHeader title={t('dashboard.title')} subtitle={t('dashboard.subtitle')} />

            <main className={`${PAGE_WIDTH} w-full pt-6 pb-12 flex-1`}>
                <Link to="/verifier/history" data-tour="verifier-history-link" className="inline-flex items-center gap-1.5 mb-8 text-secondary font-semibold hover:text-primary">
                    <span className="material-symbols-outlined text-[20px]">history</span>
                    {t('dashboard.viewHistory')}
                </Link>

                {loading && <LoadingSkeleton label={t('loading')}/>}
                {error && <div className="p-4 rounded-xl bg-error-container text-on-error-container">{error}</div>}
                {!loading && !error && requests.length === 0 && (
                    <EmptyState bordered icon="task_alt" title={t('dashboard.emptyTitle')}
                                text={t('dashboard.emptyText')} />
                )}

                <div data-tour="verifier-requests">
                    <RequestGroup title={t('dashboard.awaiting')} items={awaitingSign} />
                    <RequestGroup title={t('dashboard.pending')} items={pending} />
                </div>
            </main>
        </div>
    );
}

function RequestGroup({ title, items }) {
    const { t } = useTranslation('verifier');
    if (items.length === 0) return null;
    return (
        <section className="mb-10">
            <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{title} ({items.length})</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {items.map((req) => (
                    <Link
                        key={req.id}
                        to={`/verifier/request/${req.id}`}
                        className="lv-glass-regular lv-glass-interactive block bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30 transition-all"
                    >
                        <div className="flex justify-between items-start gap-3 mb-2">
                            <span className="font-label-md font-bold text-on-surface">{req.ownerName}</span>
                            <span className={`lv-glass-badge shrink-0 text-[11px] px-2.5 py-0.5 rounded-full font-semibold ${STATUS_CLASS[req.status]}`}>
                                {statusLabel(req.status)}
                            </span>
                        </div>
                        <p className="text-sm text-on-surface-variant">{t('dashboard.executor', { name: req.executorName })}</p>
                        <p className="text-xs text-outline mt-1">
                            {t('dashboard.meta', { id: shortId(req.id), date: formatDate(req.createdAt), count: req.vaultIds?.length ?? 0 })}
                        </p>
                    </Link>
                ))}
            </div>
        </section>
    );
}
