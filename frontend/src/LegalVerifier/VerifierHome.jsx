import React, {useEffect, useState} from 'react';
import {Link, Navigate} from 'react-router-dom';
import {getUser, ROLES} from '../auth/auth.js';
import {getRequests, getSummary} from '../api/verifierApi.js';
import {getErrorMessage} from '../api/http.js';
import {formatDate, STATUS_LABEL} from './verifierUi.js';
import AppHeader, {PAGE_WIDTH} from '../Common/AppHeader.jsx';
import EmptyState from '../Common/EmptyState.jsx';

// Trang home của Legal Verifier — cùng bố cục với UserHome.
// to = null => chưa làm, hiện "Coming soon".
const FEATURES = [
    {
        icon: 'gavel',
        title: 'Verification requests',
        text: 'Review, approve and sign pending requests.',
        to: '/verifier'
    },
    {
        icon: 'manage_search',
        title: 'Verification history',
        text: 'Requests you have already signed or rejected.',
        to: '/verifier/history'
    },
];

function Icon({name, className = ''}) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

function FeatureCard({f}) {
    const body = (
        <>
            <div className="flex items-start justify-between">
                <div className="w-11 h-11 rounded-xl bg-primary-fixed text-primary flex items-center justify-center">
                    <Icon name={f.icon}/>
                </div>
                {!f.to && (
                    <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-surface-container text-outline">
                        Coming soon
                    </span>
                )}
            </div>
            <h3 className="font-title-lg text-title-lg mt-4">{f.title}</h3>
            <p className="mt-1 text-sm text-on-surface-variant">{f.text}</p>
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
            .catch((e) => setError(getErrorMessage(e, 'Could not load requests.')));
        getSummary()
            .then(setSummary)
            .catch((e) => setError((prev) => prev || getErrorMessage(e, 'Could not load statistics.')));
    }, [isVerifier]);

    // Chưa đăng nhập -> login; không phải verifier -> home thường
    if (!user) return <Navigate to="/login" replace/>;
    if (!isVerifier) return <Navigate to="/home" replace/>;

    const todo = (requests ?? []).filter((r) => r.status === 'PENDING' || r.status === 'APPROVED').slice(0, 5);
    const hasOtherRoles = roles.some((r) => r !== ROLES.LEGAL_VERIFIER);

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            {/* Top bar */}
            <AppHeader icon="verified_user" workspace="Legal Verifier portal" home="/verifier/home" userHome={hasOtherRoles}/>

            <main className={`${PAGE_WIDTH} py-10`}>
                {/* Lời chào */}
                <div className="mb-8">
                    <h1 className="font-headline-md text-headline-md">Welcome back, {user.name}</h1>
                    <div className="mt-3 flex flex-wrap gap-2">
                        <span
                            className="text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                            Legal Verifier
                        </span>
                    </div>
                </div>

                {error && (
                    <div
                        className="mb-6 p-4 rounded-xl bg-error-container text-on-error-container text-sm">{error}</div>
                )}

                {/* Thống kê nhanh */}
                <section className="mb-10 grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard icon="pending_actions" label="Pending review" value={summary?.pending} to="/verifier"/>
                    <StatCard icon="draw" label="Awaiting signature" value={summary?.awaitingSignature} to="/verifier"/>
                    <StatCard icon="task_alt" label="Signed"
                              value={summary?.signed} to="/verifier/history"/>
                    <StatCard icon="block" label="Rejected"
                              value={summary?.rejected} to="/verifier/history"/>
                </section>

                {/* Chức năng */}
                <section className="mb-10">
                    <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">Legal Verifier</h2>
                    <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
                        {FEATURES.map((f) => <FeatureCard key={f.title} f={f}/>)}
                    </div>
                </section>

                {/* Việc cần xử lý */}
                <section>
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="font-label-lg text-label-lg text-on-surface-variant">Needs your action</h2>
                        <Link to="/verifier" className="text-sm font-semibold text-secondary hover:text-primary">View
                            all →</Link>
                    </div>

                    {requests === null && !error && <p className="text-on-surface-variant">Loading...</p>}
                    {requests !== null && todo.length === 0 && (
                        <EmptyState bordered compact icon="task_alt" title="You're all caught up"
                                    text="No requests are waiting for review."
                                    action={{ to: '/verifier/history', label: 'View history', icon: 'history' }} />
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
                                            Executor: {r.executorName} · Submitted {formatDate(r.createdAt)}
                                        </p>
                                    </div>
                                    <span
                                        className="shrink-0 text-[11px] px-2.5 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed font-semibold">
                                        {STATUS_LABEL[r.status]}
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
