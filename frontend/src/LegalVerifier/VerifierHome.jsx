import React, {useEffect, useState} from 'react';
import {Link, Navigate} from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {getUser, ROLES} from '../auth/auth.js';
import {getRequests, getSummary} from '../api/verifierApi.js';
import {getErrorMessage} from '../api/http.js';
import {formatDate, statusLabel} from './verifierUi.js';
import AppHeader, {PAGE_WIDTH} from '../Common/AppHeader.jsx';
import EmptyState from '../Common/EmptyState.jsx';

// Trang home của Legal Verifier — cùng bố cục với UserHome.
// to = null => chưa làm, hiện "Coming soon".
const FEATURES = [
    {icon: 'gavel', key: 'requests', to: '/verifier'},
    {icon: 'manage_search', key: 'history', to: '/verifier/history'},
];

function Icon({name, className = ''}) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

function FeatureCard({f}) {
    const {t} = useTranslation('verifier');
    const body = (
        <>
            <div className="flex items-start justify-between">
                <div className="w-11 h-11 rounded-xl bg-primary-fixed text-primary flex items-center justify-center">
                    <Icon name={f.icon}/>
                </div>
                {!f.to && (
                    <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-surface-container text-outline">
                        {t('home.comingSoon')}
                    </span>
                )}
            </div>
            <h3 className="font-title-lg text-title-lg mt-4">{t(`home.features.${f.key}.title`)}</h3>
            <p className="mt-1 text-sm text-on-surface-variant">{t(`home.features.${f.key}.text`)}</p>
        </>
    );

    const base = 'block bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30';
    return f.to ? (
        <Link to={f.to} className={`${base} hover:border-primary hover:shadow-sm transition-all`}>{body}</Link>
    ) : (
        <div className={`${base} opacity-60 cursor-not-allowed`}>{body}</div>
    );
}

function StatCard({icon, label, value, to}) {
    return (
        <Link
            to={to}
            className="bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30 hover:border-primary transition-all flex items-center gap-4"
        >
            <div
                className="w-11 h-11 rounded-xl bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center">
                <Icon name={icon}/>
            </div>
            <div>
                <p className="text-2xl font-bold text-on-surface leading-none">{value ?? '–'}</p>
                <p className="text-sm text-on-surface-variant mt-1">{label}</p>
            </div>
        </Link>
    );
}

export default function VerifierHome() {
    const {t} = useTranslation('verifier');
    const user = getUser();
    const [requests, setRequests] = useState(null);
    const [summary, setSummary] = useState(null);
    const [error, setError] = useState('');

    const roles = user?.roles?.length ? user.roles : [user?.role];
    const isVerifier = roles.includes(ROLES.LEGAL_VERIFIER);

    useEffect(() => {
        if (!isVerifier) return;
        // Hai nguồn độc lập: một bên lỗi thì bên kia vẫn hiển thị
        getRequests()
            .then(setRequests)
            .catch((e) => setError(getErrorMessage(e, t('home.loadError'))));
        getSummary()
            .then(setSummary)
            .catch((e) => setError((prev) => prev || getErrorMessage(e, t('home.statsError'))));
    }, [isVerifier]);

    // Chưa đăng nhập -> login; không phải verifier -> home thường
    if (!user) return <Navigate to="/login" replace/>;
    if (!isVerifier) return <Navigate to="/home" replace/>;

    const todo = (requests ?? []).filter((r) => r.status === 'PENDING' || r.status === 'APPROVED').slice(0, 5);
    const hasOtherRoles = roles.some((r) => r !== ROLES.LEGAL_VERIFIER);

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            {/* Top bar */}
            <AppHeader workspace={t('portal')} home="/verifier/home" userHome={hasOtherRoles}/>

            <main className={`${PAGE_WIDTH} py-10`}>
                {/* Lời chào */}
                <div className="mb-8 lv-page-hero lv-rise">
                    <h1 className="font-headline-md text-headline-md text-white">{t('home.welcome', {name: user.name})}</h1>
                    <div className="mt-3 flex flex-wrap gap-2">
                        <span
                            className="text-xs font-semibold px-3 py-1 rounded-full bg-white/15 text-white border border-white/20">
                            {t('role')}
                        </span>
                    </div>
                </div>

                {error && (
                    <div
                        className="mb-6 p-4 rounded-xl bg-error-container text-on-error-container text-sm">{error}</div>
                )}

                {/* Thống kê nhanh */}
                <section className="mb-10 grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard icon="pending_actions" label={t('home.stats.pending')} value={summary?.pending} to="/verifier"/>
                    <StatCard icon="draw" label={t('home.stats.awaiting')} value={summary?.awaitingSignature} to="/verifier"/>
                    <StatCard icon="task_alt" label={t('home.stats.signed')}
                              value={summary?.signed} to="/verifier/history"/>
                    <StatCard icon="block" label={t('home.stats.rejected')}
                              value={summary?.rejected} to="/verifier/history"/>
                </section>

                {/* Chức năng */}
                <section className="mb-10">
                    <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{t('role')}</h2>
                    <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
                        {FEATURES.map((f) => <FeatureCard key={f.key} f={f}/>)}
                    </div>
                </section>

                {/* Việc cần xử lý */}
                <section>
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="font-label-lg text-label-lg text-on-surface-variant">{t('home.needsAction')}</h2>
                        <Link to="/verifier" className="text-sm font-semibold text-secondary hover:text-primary">{t('home.viewAll')}</Link>
                    </div>

                    {requests === null && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
                    {requests !== null && todo.length === 0 && (
                        <EmptyState bordered compact icon="task_alt" title={t('home.emptyTitle')}
                                    text={t('home.emptyText')}
                                    action={{ to: '/verifier/history', label: t('home.viewHistory'), icon: 'history' }} />
                    )}

                    {todo.length > 0 && (
                        <div
                            className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 divide-y divide-outline-variant/20">
                            {todo.map((r) => (
                                <Link
                                    key={r.id}
                                    to={`/verifier/request/${r.id}`}
                                    className="flex items-center justify-between gap-4 p-4 hover:bg-surface-container-low transition-colors"
                                >
                                    <div className="min-w-0">
                                        <p className="font-semibold text-on-surface truncate">{r.ownerName}</p>
                                        <p className="text-sm text-on-surface-variant truncate">
                                            {t('home.line', { executor: r.executorName, date: formatDate(r.createdAt) })}
                                        </p>
                                    </div>
                                    <span
                                        className="shrink-0 text-[11px] px-2.5 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed font-semibold">
                                        {statusLabel(r.status)}
                                    </span>
                                </Link>
                            ))}
                        </div>
                    )}
                </section>
            </main>
        </div>
    );
}
