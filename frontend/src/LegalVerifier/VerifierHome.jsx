import React, { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { getUser, logout, ROLES } from '../auth/auth.js';
import { getRequests, getHistory } from '../api/verifierApi.js';

// Trang home của Legal Verifier — cùng bố cục với UserHome.
// to = null => chưa làm, hiện "Coming soon".
const FEATURES = [
    { icon: 'gavel', title: 'Verification requests', text: 'Review, approve and sign pending requests.', to: '/verifier' },
    { icon: 'manage_search', title: 'Verification history', text: 'Requests you have already signed or rejected.', to: '/verifier/history' },
    { icon: 'description', title: 'Document checks', text: 'Verify death certificates and legal papers.', to: null },
];

const STATUS_LABEL = {
    PENDING: 'Pending review',
    APPROVED: 'Awaiting signature',
    SIGNED: 'Signed',
    REJECTED: 'Rejected',
};

function Icon({ name, className = '' }) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

function FeatureCard({ f }) {
    const body = (
        <>
            <div className="flex items-start justify-between">
                <div className="w-11 h-11 rounded-xl bg-primary-fixed text-primary flex items-center justify-center">
                    <Icon name={f.icon} />
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

function StatCard({ icon, label, value, to }) {
    return (
        <Link
            to={to}
            className="bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30 hover:border-primary transition-all flex items-center gap-4"
        >
            <div className="w-11 h-11 rounded-xl bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center">
                <Icon name={icon} />
            </div>
            <div>
                <p className="text-2xl font-bold text-on-surface leading-none">{value ?? '–'}</p>
                <p className="text-sm text-on-surface-variant mt-1">{label}</p>
            </div>
        </Link>
    );
}

export default function VerifierHome() {
    const navigate = useNavigate();
    const user = getUser();
    const [requests, setRequests] = useState(null);
    const [history, setHistory] = useState(null);
    const [error, setError] = useState('');

    const roles = user?.roles?.length ? user.roles : [user?.role];
    const isVerifier = roles.includes(ROLES.LEGAL_VERIFIER);

    useEffect(() => {
        if (!isVerifier) return;
        Promise.all([getRequests(), getHistory()])
            .then(([reqs, hist]) => {
                setRequests(reqs);
                setHistory(hist);
            })
            .catch((e) => setError(e.message));
    }, [isVerifier]);

    // Chưa đăng nhập -> login; không phải verifier -> home thường
    if (!user) return <Navigate to="/login" replace />;
    if (!isVerifier) return <Navigate to="/home" replace />;

    const count = (status) => requests?.filter((r) => r.status === status).length;
    const todo = (requests ?? []).filter((r) => r.status === 'PENDING' || r.status === 'APPROVED').slice(0, 5);
    const hasOtherRoles = roles.some((r) => r !== ROLES.LEGAL_VERIFIER);

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            {/* Top bar */}
            <header className="bg-surface-container-lowest border-b border-outline-variant/30">
                <div className="max-w-6xl mx-auto h-16 px-5 flex items-center justify-between">
                    <Link to="/verifier/home" className="flex items-center gap-2">
                        <span className="w-9 h-9 rounded-xl bg-primary text-on-primary flex items-center justify-center">
                            <Icon name="verified_user" className="text-[20px]" />
                        </span>
                        <span>
                            <span className="font-headline-sm text-primary font-bold text-xl block leading-tight">LegacyVault</span>
                            <span className="text-[11px] text-secondary font-semibold tracking-wide uppercase block">Legal Verifier Portal</span>
                        </span>
                    </Link>
                    <div className="flex items-center gap-4">
                        {hasOtherRoles && (
                            <Link to="/home" className="hidden sm:flex items-center gap-1 text-sm font-semibold text-on-surface-variant hover:text-primary">
                                <Icon name="swap_horiz" className="text-[18px]" /> User home
                            </Link>
                        )}
                        <span className="hidden sm:block text-sm text-on-surface-variant">{user.email}</span>
                        <button
                            onClick={handleLogout}
                            className="h-10 px-4 rounded-xl border border-outline-variant text-sm font-semibold flex items-center gap-2 hover:bg-surface-container-low"
                        >
                            <Icon name="logout" className="text-[18px]" /> Log out
                        </button>
                    </div>
                </div>
            </header>

            <main className="max-w-6xl mx-auto px-5 py-10">
                {/* Lời chào */}
                <div className="mb-8">
                    <h1 className="font-headline-md text-headline-md">Welcome back, {user.name}</h1>
                    <div className="mt-3 flex flex-wrap gap-2">
                        <span className="text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                            Legal Verifier
                        </span>
                    </div>
                </div>

                {error && (
                    <div className="mb-6 p-4 rounded-xl bg-error-container text-on-error-container text-sm">{error}</div>
                )}

                {/* Thống kê nhanh */}
                <section className="mb-10 grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard icon="pending_actions" label="Pending review" value={count('PENDING')} to="/verifier" />
                    <StatCard icon="draw" label="Awaiting signature" value={count('APPROVED')} to="/verifier" />
                    <StatCard icon="task_alt" label="Signed" value={history?.filter((h) => h.action === 'SIGNED').length} to="/verifier/history" />
                    <StatCard icon="block" label="Rejected" value={history?.filter((h) => h.action === 'REJECTED').length} to="/verifier/history" />
                </section>

                {/* Chức năng */}
                <section className="mb-10">
                    <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">Legal Verifier</h2>
                    <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
                        {FEATURES.map((f) => <FeatureCard key={f.title} f={f} />)}
                    </div>
                </section>

                {/* Việc cần xử lý */}
                <section>
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="font-label-lg text-label-lg text-on-surface-variant">Needs your action</h2>
                        <Link to="/verifier" className="text-sm font-semibold text-secondary hover:text-primary">View all →</Link>
                    </div>

                    {requests === null && !error && <p className="text-on-surface-variant">Loading...</p>}
                    {requests !== null && todo.length === 0 && (
                        <p className="text-on-surface-variant">You're all caught up — no requests waiting.</p>
                    )}

                    {todo.length > 0 && (
                        <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 divide-y divide-outline-variant/20">
                            {todo.map((r) => (
                                <Link
                                    key={r.id}
                                    to={`/verifier/request/${r.id}`}
                                    className="flex items-center justify-between gap-4 p-4 hover:bg-surface-container-low transition-colors"
                                >
                                    <div className="min-w-0">
                                        <p className="font-semibold text-on-surface truncate">{r.ownerName}</p>
                                        <p className="text-sm text-on-surface-variant truncate">
                                            Executor: {r.executorName} · Submitted {r.submittedDate}
                                        </p>
                                    </div>
                                    <span className="shrink-0 text-[11px] px-2.5 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed font-semibold">
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
