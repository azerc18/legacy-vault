import React, { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { getUser, logout } from '../auth/auth.js';
import { getMyCases, acknowledgeAssignment } from '../api/executorApi.js';

const EXECUTOR = 'EXECUTOR';

// Các bước executor phải làm cho mỗi owner đã mất
const STEPS = ['Acknowledge', 'Submit documents', 'Legal verification', 'Handle vaults'];

const TYPE_LABEL = { PRIMARY: 'Primary executor', BACKUP: 'Backup executor' };
const fmt = (iso) => (iso ? new Date(iso).toLocaleDateString() : '—');

// Trả về index bước hiện tại (0..3), 4 = xong hết
function currentStep(c) {
    if (!c.acknowledgedAt) return 0;
    if (c.verificationStatus === 'NOT_SUBMITTED' || c.verificationStatus === 'REJECTED') return 1;
    if (c.verificationStatus !== 'SIGNED') return 2;
    return 3;
}

function Icon({ name, className = '' }) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

function StatCard({ icon, label, value }) {
    return (
        <div className="bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30 flex items-center gap-4">
            <div className="w-11 h-11 rounded-xl bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center">
                <Icon name={icon} />
            </div>
            <div>
                <p className="text-2xl font-bold text-on-surface leading-none">{value ?? '–'}</p>
                <p className="text-sm text-on-surface-variant mt-1">{label}</p>
            </div>
        </div>
    );
}

function Stepper({ step }) {
    return (
        <ol className="grid grid-cols-4 gap-2">
            {STEPS.map((label, i) => {
                const done = i < step;
                const active = i === step;
                return (
                    <li key={label} className="flex flex-col gap-1.5">
                        <span className={`h-1.5 rounded-full ${done ? 'bg-primary' : active ? 'bg-secondary' : 'bg-surface-container-high'}`} />
                        <span className={`text-[11px] font-semibold ${done || active ? 'text-on-surface' : 'text-outline'}`}>
                            {done && <Icon name="check" className="text-[12px] align-middle mr-0.5" />}
                            {label}
                        </span>
                    </li>
                );
            })}
        </ol>
    );
}

function NextAction({ c, step, busy, onAcknowledge }) {
    const btn = 'h-10 px-4 rounded-xl text-sm font-semibold flex items-center gap-2';
    if (step === 0) {
        return (
            <button onClick={onAcknowledge} disabled={busy} className={`${btn} bg-primary text-on-primary hover:bg-primary-container disabled:opacity-60`}>
                <Icon name="task_alt" className="text-[18px]" /> {busy ? 'Saving...' : 'Acknowledge activation'}
            </button>
        );
    }
    if (step === 1) {
        return (
            <div className="flex flex-col items-end gap-1">
                {c.verificationStatus === 'REJECTED' && (
                    <span className="text-xs text-error">Previous submission was rejected — please resubmit.</span>
                )}
                <button disabled className={`${btn} bg-primary text-on-primary opacity-60 cursor-not-allowed`} title="Coming soon">
                    <Icon name="upload_file" className="text-[18px]" /> Submit death certificate
                </button>
            </div>
        );
    }
    if (step === 2) {
        return (
            <span className="text-sm text-on-surface-variant flex items-center gap-1.5">
                <Icon name="hourglass_top" className="text-[18px]" />
                {c.verificationStatus === 'APPROVED' ? 'Approved — waiting for signature' : 'Waiting for Legal Verifier'}
            </span>
        );
    }
    return (
        <button disabled className={`${btn} border border-outline-variant opacity-60 cursor-not-allowed`} title="Coming soon">
            <Icon name="folder_open" className="text-[18px]" /> Open vaults
        </button>
    );
}

function CaseCard({ c, busy, onAcknowledge }) {
    const step = currentStep(c);
    return (
        <article className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 p-6">
            <div className="flex flex-wrap items-start justify-between gap-4 mb-5">
                <div className="min-w-0">
                    <p className="text-xs font-semibold text-secondary uppercase tracking-wide">{TYPE_LABEL[c.executorType] || c.executorType}</p>
                    <h3 className="font-title-lg text-title-lg mt-1 truncate">Estate of {c.ownerName}</h3>
                    <p className="text-sm text-on-surface-variant">{c.ownerEmail} · Activated {fmt(c.activatedAt)}</p>
                </div>
                <NextAction c={c} step={step} busy={busy} onAcknowledge={onAcknowledge} />
            </div>

            <Stepper step={step} />

            {c.vaults.length > 0 && (
                <div className="mt-5 pt-5 border-t border-outline-variant/20 flex flex-wrap gap-2">
                    {c.vaults.map((v) => (
                        <span key={v.id} className="text-xs px-3 py-1.5 rounded-full bg-surface-container text-on-surface-variant flex items-center gap-1.5">
                            <Icon name={v.status === 'UNLOCKED' ? 'lock_open' : 'lock'} className="text-[14px]" />
                            {v.name} · {v.assetCount} assets
                        </span>
                    ))}
                </div>
            )}
        </article>
    );
}

export default function ExecutorHome() {
    const navigate = useNavigate();
    const user = getUser();
    const [cases, setCases] = useState(null);
    const [error, setError] = useState('');
    const [busyId, setBusyId] = useState(null);

    const roles = user?.roles?.length ? user.roles : [user?.role];
    const isExecutor = roles.includes(EXECUTOR);

    useEffect(() => {
        if (!isExecutor) return;
        getMyCases()
            .then(setCases)
            .catch((e) => setError(e.message));
    }, [isExecutor]);

    // Chưa đăng nhập -> login; chưa được kích hoạt làm executor -> home thường
    if (!user) return <Navigate to="/login" replace />;
    if (!isExecutor) return <Navigate to="/home" replace />;

    const all = cases ?? [];
    const active = all.filter((c) => c.status === 'ACTIVE');
    const standby = all.filter((c) => c.status === 'INACTIVE');
    const needAction = active.filter((c) => currentStep(c) <= 1);
    const waiting = active.filter((c) => currentStep(c) === 2);
    const unlocked = active.flatMap((c) => c.vaults).filter((v) => v.status === 'UNLOCKED').length;

    const handleAcknowledge = async (id) => {
        setBusyId(id);
        setError('');
        try {
            const res = await acknowledgeAssignment(id);
            // BE chỉ trả id/status/thời gian -> gộp vào hồ sơ cũ
            setCases((prev) => prev.map((c) => (c.id === id ? { ...c, ...res } : c)));
        } catch (e) {
            setError(e.response?.data?.message || e.message);
        } finally {
            setBusyId(null);
        }
    };

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            {/* Top bar */}
            <header className="bg-surface-container-lowest border-b border-outline-variant/30">
                <div className="max-w-6xl mx-auto h-16 px-5 flex items-center justify-between">
                    <Link to="/executor/home" className="flex items-center gap-2">
                        <span className="w-9 h-9 rounded-xl bg-primary text-on-primary flex items-center justify-center">
                            <Icon name="badge" className="text-[20px]" />
                        </span>
                        <span>
                            <span className="font-headline-sm text-primary font-bold text-xl block leading-tight">LegacyVault</span>
                            <span className="text-[11px] text-secondary font-semibold tracking-wide uppercase block">Executor Workspace</span>
                        </span>
                    </Link>
                    <div className="flex items-center gap-4">
                        <Link to="/home" className="hidden sm:flex items-center gap-1 text-sm font-semibold text-on-surface-variant hover:text-primary">
                            <Icon name="swap_horiz" className="text-[18px]" /> User home
                        </Link>
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
                    <p className="mt-1 text-on-surface-variant">Here are the estates you are handling as an executor.</p>
                </div>

                {error && (
                    <div className="mb-6 p-4 rounded-xl bg-error-container text-on-error-container text-sm">{error}</div>
                )}

                {/* Thống kê nhanh */}
                <section className="mb-10 grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard icon="assignment_late" label="Need your action" value={cases && needAction.length} />
                    <StatCard icon="hourglass_top" label="Waiting for verification" value={cases && waiting.length} />
                    <StatCard icon="lock_open" label="Vaults unlocked" value={cases && unlocked} />
                    <StatCard icon="pause_circle" label="On standby" value={cases && standby.length} />
                </section>

                {/* Hồ sơ đang xử lý */}
                <section className="mb-10">
                    <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">Active estates</h2>
                    {cases === null && !error && <p className="text-on-surface-variant">Loading...</p>}
                    {cases !== null && active.length === 0 && (
                        <p className="text-on-surface-variant">No active estates right now.</p>
                    )}
                    <div className="flex flex-col gap-5">
                        {active.map((c) => (
                            <CaseCard key={c.id} c={c} busy={busyId === c.id} onAcknowledge={() => handleAcknowledge(c.id)} />
                        ))}
                    </div>
                </section>

                {/* Chờ kích hoạt */}
                {standby.length > 0 && (
                    <section>
                        <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">On standby</h2>
                        <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 divide-y divide-outline-variant/20">
                            {standby.map((c) => (
                                <div key={c.id} className="p-4 flex flex-wrap items-center justify-between gap-2">
                                    <div>
                                        <p className="font-semibold">{c.ownerName}</p>
                                        <p className="text-sm text-on-surface-variant">{TYPE_LABEL[c.executorType]}</p>
                                    </div>
                                    <span className="text-sm text-outline">Nothing to do until the Dead Man's Switch is triggered.</span>
                                </div>
                            ))}
                        </div>
                    </section>
                )}
            </main>
        </div>
    );
}
