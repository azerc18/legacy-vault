import React, {useCallback, useEffect, useState} from 'react';
import {Link, Navigate} from 'react-router-dom';
import {getUser} from '../auth/auth.js';
import {getErrorMessage} from '../api/http.js';
import {executorApi, caseStep} from '../api/executorApi.js';
import AppHeader, {PAGE_WIDTH} from '../Common/AppHeader.jsx';
import NotificationBell from '../Beneficiary/NotificationBell.jsx';
import EmptyState from "../Common/EmptyState.jsx";

const EXECUTOR = 'EXECUTOR';
const STEPS = ['Acknowledge', 'Submit documents', 'Legal verification', 'Hand-over'];
const TYPE_LABEL = {PRIMARY: 'Primary executor', BACKUP: 'Backup executor'};
const fmt = (iso) => (iso ? new Date(iso).toLocaleDateString('en-GB', {dateStyle: 'medium'}) : '—');

function Icon({name, className = ''}) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

function StatCard({icon, label, value}) {
    return (
        <div
            className="bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30 flex items-center gap-4">
            <div
                className="w-11 h-11 rounded-xl bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center">
                <Icon name={icon}/>
            </div>
            <div>
                <p className="text-2xl font-bold text-on-surface leading-none">{value ?? '–'}</p>
                <p className="text-sm text-on-surface-variant mt-1">{label}</p>
            </div>
        </div>
    );
}

function Stepper({step}) {
    return (
        <ol className="grid grid-cols-4 gap-2">
            {STEPS.map((label, i) => {
                const done = i < step;
                const active = i === step;
                return (
                    <li key={label} className="flex flex-col gap-1.5">
                        <span
                            className={`h-1.5 rounded-full ${done ? 'bg-primary' : active ? 'bg-secondary' : 'bg-surface-container-high'}`}/>
                        <span
                            className={`text-[11px] font-semibold ${done || active ? 'text-on-surface' : 'text-outline'}`}>
                            {done && <Icon name="check" className="text-[12px] align-middle mr-0.5"/>}
                            {label}
                        </span>
                    </li>
                );
            })}
        </ol>
    );
}

function CaseCard({c, busy, onAcknowledge}) {
    const step = caseStep(c, c.progress);
    const btn = 'h-10 px-4 rounded-xl text-sm font-semibold inline-flex items-center gap-2';
    const vaultCount = new Set(c.progress.map((p) => p.vaultId)).size;

    return (
        <article className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 p-6">
            <div className="flex flex-wrap items-start justify-between gap-4 mb-5">
                <div className="min-w-0">
                    <p className="text-xs font-semibold text-secondary uppercase tracking-wide">{TYPE_LABEL[c.executorType] || c.executorType}</p>
                    <h3 className="font-title-lg text-title-lg mt-1 truncate">Estate of {c.ownerFullName}</h3>
                    <p className="text-sm text-on-surface-variant">
                        Activated {fmt(c.activatedAt)}{vaultCount > 0 && ` · ${vaultCount} vault(s) in progress`}
                    </p>
                </div>
                <div className="flex gap-2">
                    {step === 0 && (
                        <button onClick={onAcknowledge} disabled={busy}
                                className={`${btn} bg-primary text-on-primary hover:bg-primary-container disabled:opacity-60`}>
                            <Icon name="task_alt" className="text-[18px]"/> {busy ? 'Saving...' : 'Acknowledge'}
                        </button>
                    )}
                    <Link to={`/executor/cases/${c.assignmentId}`}
                          className={`${btn} ${step === 0 ? 'border border-outline-variant text-primary' : 'bg-primary text-on-primary hover:bg-primary-container'}`}>
                        <Icon name="folder_open" className="text-[18px]"/> Open case
                    </Link>
                </div>
            </div>
            <Stepper step={step}/>
        </article>
    );
}

export default function ExecutorHome() {
    const user = getUser();
    const [cases, setCases] = useState(null);
    const [error, setError] = useState('');
    const [busyId, setBusyId] = useState(null);

    const roles = user?.roles?.length ? user.roles : [user?.role];
    const isExecutor = roles.includes(EXECUTOR);

    // /me + tiến độ bàn giao của từng hồ sơ ACTIVE (INACTIVE gọi progress sẽ bị 403)
    const load = useCallback(async () => {
        try {
            const mine = await executorApi.listMine();
            const withProgress = await Promise.all(mine.map(async (a) => ({
                ...a,
                progress: a.status === 'ACTIVE' ? await executorApi.getProgress(a.assignmentId).catch(() => []) : [],
            })));
            setCases(withProgress);
        } catch (e) {
            setError(getErrorMessage(e, 'Could not load your cases.'));
            setCases([]);
        }
    }, []);

    useEffect(() => {
        if (isExecutor) load();
    }, [isExecutor, load]);

    if (!user) return <Navigate to="/login" replace/>;
    if (!isExecutor) return <Navigate to="/home" replace/>;

    const all = cases ?? [];
    const active = all.filter((c) => c.status === 'ACTIVE');
    const standby = all.filter((c) => c.status === 'INACTIVE');
    const needAction = active.filter((c) => caseStep(c, c.progress) <= 1).length;
    const waiting = active.filter((c) => caseStep(c, c.progress) === 2).length;
    const unlocked = active.flatMap((c) => c.progress).filter((p) => ['UNLOCKED', 'CLAIMED'].includes(p.handoverStatus)).length;

    const handleAcknowledge = async (id) => {
        setBusyId(id);
        setError('');
        try {
            const res = await executorApi.acknowledge(id);
            setCases((prev) => prev.map((c) => (c.assignmentId === id ? {
                ...c,
                acknowledgedAt: res.acknowledgedAt,
                status: res.status
            } : c)));
        } catch (e) {
            setError(getErrorMessage(e, 'Could not acknowledge.'));
        } finally {
            setBusyId(null);
        }
    };

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader icon="badge" workspace="Executor workspace" home="/executor/home" userHome extra={<NotificationBell source="executor" />}/>

            <main className={`${PAGE_WIDTH} py-10`}>
                <div className="mb-8">
                    <h1 className="font-headline-md text-headline-md">Welcome back, {user.name}</h1>
                    <p className="mt-1 text-on-surface-variant">Here are the estates you are handling as an
                        executor.</p>
                </div>

                {error && <div
                    className="mb-6 p-4 rounded-xl bg-error-container text-on-error-container text-sm">{error}</div>}

                <section className="mb-10 grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard icon="assignment_late" label="Need your action" value={cases && needAction}/>
                    <StatCard icon="hourglass_top" label="Waiting for verification" value={cases && waiting}/>
                    <StatCard icon="lock_open" label="Vaults unlocked" value={cases && unlocked}/>
                    <StatCard icon="pause_circle" label="On standby" value={cases && standby.length}/>
                </section>

                <section className="mb-10">
                    <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">Active estates</h2>
                    {cases === null && <p className="text-on-surface-variant">Loading...</p>}
                    {cases !== null && active.length === 0 &&
                        <EmptyState bordered icon="badge" title="No active estates right now"
                                    text="You'll get an email when an owner's Dead Man's Switch is triggered and you need to act." />}
                    <div className="flex flex-col gap-5">
                        {active.map((c) => (
                            <CaseCard key={c.assignmentId} c={c} busy={busyId === c.assignmentId}
                                      onAcknowledge={() => handleAcknowledge(c.assignmentId)}/>
                        ))}
                    </div>
                </section>

                {standby.length > 0 && (
                    <section>
                        <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">On standby</h2>
                        <div
                            className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 divide-y divide-outline-variant/20">
                            {standby.map((c) => (
                                <div key={c.assignmentId}
                                     className="p-4 flex flex-wrap items-center justify-between gap-2">
                                    <div>
                                        <p className="font-semibold">{c.ownerFullName}</p>
                                        <p className="text-sm text-on-surface-variant">{TYPE_LABEL[c.executorType]} ·
                                            since {fmt(c.assignedAt)}</p>
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
