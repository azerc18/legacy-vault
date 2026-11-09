import React, {useCallback, useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Link, Navigate} from 'react-router-dom';
import {getUser} from '../auth/auth.js';
import {getErrorMessage} from '../api/http.js';
import {executorApi, caseStep} from '../api/executorApi.js';
import AppHeader, {PAGE_WIDTH, PageTitle} from '../Common/AppHeader.jsx';
import NotificationBell from '../Beneficiary/NotificationBell.jsx';
import EmptyState from "../Common/EmptyState.jsx";
import {formatDate} from '../i18n/format.js';

const EXECUTOR = 'EXECUTOR';
// FR-07/FR-14: Owner chưa có tên thì hiện email
const ownerName = (c) => c.ownerFullName || c.ownerEmail;
// Chỉ nhấn mạnh (đỏ) khi đã quá hạn xác nhận hoặc còn từ 3 ngày trở xuống
const URGENT_MS = 3 * 24 * 60 * 60 * 1000;

function Icon({name, className = ''}) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

function StatCard({icon, label, value}) {
    return (
        <div
            className="lv-feature lv-feature-link bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30 flex items-center gap-4">
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
    const {t} = useTranslation('executor');
    return (
        <ol className="grid grid-cols-4 gap-2">
            {t('steps', {returnObjects: true}).map((label, i) => {
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
    const {t} = useTranslation('executor');
    const step = caseStep(c, c.progress);
    const btn = 'h-10 px-4 rounded-xl text-sm font-semibold inline-flex items-center gap-2';

    return (
        <article className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 p-6">
            <div className="flex flex-wrap items-start justify-between gap-4 mb-5">
                <div className="min-w-0">
                    <p className="text-xs font-semibold text-secondary uppercase tracking-wide">{t(`type.${c.executorType}`, c.executorType)}</p>
                    <h3 className="font-title-lg text-title-lg mt-1 truncate">{t('estateOf', {name: ownerName(c)})}</h3>
                    <p className="text-sm text-on-surface-variant">
                        {t('home.activated', {date: formatDate(c.activatedAt)})} · {t('home.vaults', {count: c.vaultCount ?? 0})}
                        {c.unreadMessageCount > 0 && ` · ${t('home.unread', {count: c.unreadMessageCount})}`}
                    </p>
                    {c.latestRequestStatus && (
                        <p className={`text-sm mt-1 ${c.latestRequestStatus === 'REJECTED' ? 'text-error' : 'text-on-surface-variant'}`}>
                            {t(`request.${c.latestRequestStatus}`, c.latestRequestStatus)}
                        </p>
                    )}
                    {c.acknowledgeDeadlineAt && (() => {
                        const msLeft = new Date(c.acknowledgeDeadlineAt) - new Date();
                        return (
                            <p className={`text-sm mt-1 ${msLeft <= URGENT_MS ? 'text-error font-semibold' : 'text-on-surface-variant'}`}>
                                {msLeft < 0
                                    ? t('home.ackOverdue', {date: formatDate(c.acknowledgeDeadlineAt)})
                                    : t('home.ackBefore', {date: formatDate(c.acknowledgeDeadlineAt)})}
                            </p>
                        );
                    })()}
                </div>
                <div className="flex gap-2">
                    {step === 0 && (
                        <button onClick={onAcknowledge} disabled={busy}
                                className={`${btn} bg-primary text-on-primary hover:bg-primary-container disabled:opacity-60`}>
                            <Icon name="task_alt" className="text-[18px]"/> {busy ? t('saving') : t('acknowledge')}
                        </button>
                    )}
                    <Link to={`/executor/cases/${c.assignmentId}`}
                          className={`${btn} ${step === 0 ? 'border border-outline-variant text-primary' : 'bg-primary text-on-primary hover:bg-primary-container'}`}>
                        <Icon name="folder_open" className="text-[18px]"/> {t('home.openCase')}
                    </Link>
                </div>
            </div>
            <Stepper step={step}/>
        </article>
    );
}

export default function ExecutorHome() {
    const {t} = useTranslation('executor');
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
            setError(getErrorMessage(e, t('home.loadError')));
            setCases([]);
        }
    }, [t]);

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
                acknowledgeDeadlineAt: null,
                status: res.status
            } : c)));
        } catch (e) {
            setError(getErrorMessage(e, t('ackError')));
        } finally {
            setBusyId(null);
        }
    };

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader workspace={t('workspace')} home="/executor/home" userHome extra={<NotificationBell source="executor" />}/>

            <main className={`${PAGE_WIDTH} py-10`}>
                <PageTitle
                    title={t('home.welcome', {name: user.name})}
                    subtitle={t('home.intro')}
                />

                {error && <div
                    className="mb-6 p-4 rounded-xl bg-error-container text-on-error-container text-sm">{error}</div>}

                <section className="lv-feature-grid mb-10 grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard icon="assignment_late" label={t('home.stats.needAction')} value={cases && needAction}/>
                    <StatCard icon="hourglass_top" label={t('home.stats.waiting')} value={cases && waiting}/>
                    <StatCard icon="lock_open" label={t('home.stats.unlocked')} value={cases && unlocked}/>
                    <StatCard icon="pause_circle" label={t('home.stats.standby')} value={cases && standby.length}/>
                </section>

                <section className="mb-10">
                    <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{t('home.activeTitle')}</h2>
                    {cases === null && <p className="text-on-surface-variant">{t('loading')}</p>}
                    {cases !== null && active.length === 0 &&
                        <EmptyState bordered icon="badge" title={t('home.emptyTitle')}
                                    text={t('home.emptyText')} />}
                    <div className="flex flex-col gap-5">
                        {active.map((c) => (
                            <CaseCard key={c.assignmentId} c={c} busy={busyId === c.assignmentId}
                                      onAcknowledge={() => handleAcknowledge(c.assignmentId)}/>
                        ))}
                    </div>
                </section>

                {standby.length > 0 && (
                    <section>
                        <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{t('home.standbyTitle')}</h2>
                        <div
                            className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 divide-y divide-outline-variant/20">
                            {standby.map((c) => (
                                <div key={c.assignmentId}
                                     className="p-4 flex flex-wrap items-center justify-between gap-2">
                                    <div>
                                        <p className="font-semibold">{ownerName(c)}</p>
                                        <p className="text-sm text-on-surface-variant">{t('home.standbySince', {type: t(`type.${c.executorType}`, c.executorType), date: formatDate(c.assignedAt)})}</p>
                                    </div>
                                    <span className="text-sm text-outline">{t('home.standbyHint')}</span>
                                </div>
                            ))}
                        </div>
                    </section>
                )}
            </main>
        </div>
    );
}
