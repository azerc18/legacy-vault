// A1: Trang tổng quan Admin — thẻ số liệu + danh sách việc cần xử lý (GET /api/admin/stats)
import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CountingNumber } from '@animate-ui/react/primitives/texts/counting-number';
import { ReducedMotionConfig } from '@animate-ui/react/utils/motion';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate } from '../Owner/ownerUi.jsx';
import EmptyState from '../Common/EmptyState.jsx';
import { AdminLayout, Dossier, Stamp, StatCard, formatVnd } from './adminUi.jsx';

const USER_ORDER = ['ACTIVE', 'PENDING', 'LOCKED'];
const VAULT_ORDER = ['ACTIVE', 'UNLOCKED', 'CLAIMED', 'ARCHIVED_LOCKED'];
const SPLIT_COLOR = {
    ACTIVE: 'rgb(var(--c-success))', PENDING: 'rgb(var(--c-warning))', LOCKED: 'rgb(var(--adm-seal))',
    UNLOCKED: 'rgb(var(--c-secondary))', CLAIMED: 'rgb(var(--c-success) / .55)', ARCHIVED_LOCKED: 'rgb(var(--c-outline))',
};

const sum = (obj) => Object.values(obj || {}).reduce((a, b) => a + (b || 0), 0);

// Thanh tỉ lệ + chú thích nhỏ cho số đếm theo trạng thái
function StatusSplit({ counts, order, label }) {
    const total = sum(counts);
    return (
        <>
            <div className="lv-split" aria-hidden="true">
                {total > 0 && order.map((k) => counts?.[k] > 0 && (
                    <span key={k} style={{ width: `${(counts[k] / total) * 100}%`, background: SPLIT_COLOR[k] }} />
                ))}
            </div>
            <p className="lv-stat-foot flex flex-wrap gap-x-3 gap-y-0.5">
                {order.map((k) => (
                    <span key={k} className="inline-flex items-center gap-1">
                        <i className="inline-block w-2 h-2 rounded-full" style={{ background: SPLIT_COLOR[k] }} />
                        {label(k)} <b className="lv-mono">{counts?.[k] ?? 0}</b>
                    </span>
                ))}
            </p>
        </>
    );
}

// C2: biểu đồ cột tối giản (không thêm thư viện). Nhãn trục: điểm đầu, giữa, cuối.
function BarChart({ points, format, labelOf, empty }) {
    const { t } = useTranslation('admin');
    const max = Math.max(0, ...points.map((p) => p.value));
    if (max === 0) return <p className="text-sm text-on-surface-variant py-10 text-center">{empty}</p>;
    const marks = [0, Math.floor(points.length / 2), points.length - 1];
    return (
        <div>
            <div className="lv-bars" role="img" aria-label={points.map((p) => `${labelOf(p.date)}: ${format(p.value)}`).join(', ')}>
                {points.map((p) => (
                    <div key={p.date} title={`${labelOf(p.date)} · ${format(p.value)}`}
                         style={{ height: `${Math.max(p.value > 0 ? 3 : 0, (p.value / max) * 100)}%` }} />
                ))}
            </div>
            <div className="flex justify-between mt-2 text-[11px] text-outline lv-mono">
                {marks.map((i) => <span key={i}>{labelOf(points[i].date)}</span>)}
            </div>
            <p className="mt-1 text-[11px] text-outline">{t('overview.charts.max', { value: format(max) })}</p>
        </div>
    );
}

const shortDate = (iso) => {
    const [, m, d] = String(iso).split('-');
    return `${d}/${m}`;
};

function TodoList({ items, empty, render }) {
    if (!items || items.length === 0) {
        return <p className="text-sm text-on-surface-variant py-2 flex items-center gap-2"><Icon name="task_alt" className="text-[18px] text-success" />{empty}</p>;
    }
    return <ul className="lv-todo">{items.map(render)}</ul>;
}

export default function AdminDashboard() {
    const { t } = useTranslation('admin');
    const [stats, setStats] = useState(null);
    const [error, setError] = useState('');
    const [loadedAt, setLoadedAt] = useState(null);
    const [charts, setCharts] = useState(null);

    const load = useCallback(() => {
        setError('');
        adminApi.getStats()
            .then((s) => { setStats(s); setLoadedAt(new Date()); })
            .catch((e) => setError(getErrorMessage(e, t('overview.loadError'))));
        adminApi.getCharts().then(setCharts).catch(() => setCharts(null));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    const s = stats;
    const todoCount = s ? (s.highAlerts?.length || 0) + (s.claimDeadlines?.length || 0) + (s.stalePendingPayments?.length || 0) : 0;

    return (
        <AdminLayout
            title={t('overview.title')}
            subtitle={t('overview.subtitle')}
            actions={(
                <button onClick={load} className="h-10 px-4 rounded-xl border border-outline-variant text-sm font-semibold inline-flex items-center gap-2 hover:bg-surface-container-low">
                    <Icon name="refresh" className="text-[18px]" /> {t('overview.refresh')}
                </button>
            )}
        >
            <Alert>{error}</Alert>
            {!s && !error && <p className="text-on-surface-variant">{t('loading')}</p>}

            {s && (
                <>
                    <ReducedMotionConfig remount>
                        <div className="lv-feature-grid grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                            <StatCard icon="group" label={t('overview.cards.users')} value={<CountingNumber number={sum(s.usersByStatus)} thousandSeparator="," />} to="/admin/users">
                                <StatusSplit counts={s.usersByStatus} order={USER_ORDER} label={(k) => t(`users.status.${k}`)} />
                            </StatCard>
                            <StatCard icon="inventory_2" label={t('overview.cards.vaults')} value={<CountingNumber number={sum(s.vaultsByStatus)} thousandSeparator="," />} to="/admin/vaults">
                                <StatusSplit counts={s.vaultsByStatus} order={VAULT_ORDER} label={(k) => t(`vaults.status.${k}`)} />
                            </StatCard>
                            <StatCard icon="notifications_active" tone={s.openHighAlerts > 0 ? 'danger' : s.openAlerts > 0 ? 'warn' : 'ok'}
                                      label={t('overview.cards.alerts')} value={<CountingNumber number={s.openAlerts ?? 0} />}
                                      foot={t('overview.cards.alertsFoot', { count: s.openHighAlerts })} to="/admin/alerts" />
                            <StatCard icon="timer" tone={s.executorRescueNeeded > 0 ? 'danger' : s.dmsPendingConfirmation > 0 ? 'warn' : 'ok'}
                                      label={t('overview.cards.dms')} value={<CountingNumber number={(s.dmsPendingConfirmation ?? 0) + (s.executorRescueNeeded ?? 0)} />}
                                      foot={t('overview.cards.dmsFoot', { pending: s.dmsPendingConfirmation, rescue: s.executorRescueNeeded, triggered: s.dmsTriggered })}
                                      to="/admin/users" />
                            <StatCard icon="payments" tone="ok" label={t('overview.cards.revenue')} value={formatVnd(s.revenueThisMonthVnd)}
                                      foot={t('overview.cards.revenueFoot', { count: s.successfulPaymentsThisMonth })} to="/admin/payments" />
                            <StatCard icon="workspace_premium" label={t('overview.cards.vip')} value={<CountingNumber number={s.vipUsers ?? 0} />}
                                      foot={t('overview.cards.vipFoot')} to="/admin/payments" />
                        </div>
                    </ReducedMotionConfig>

                    {charts && (
                        <div className="mt-4 grid gap-4 lg:grid-cols-2">
                            <Dossier title={t('overview.charts.revenue')} tab={t('overview.charts.last30')}>
                                <BarChart points={charts.revenueByDay} format={formatVnd} labelOf={shortDate} empty={t('overview.charts.noData')} />
                            </Dossier>
                            <Dossier title={t('overview.charts.users')} tab={t('overview.charts.last12w')}>
                                <BarChart points={charts.newUsersByWeek} format={(v) => String(v)} labelOf={shortDate} empty={t('overview.charts.noData')} />
                            </Dossier>
                        </div>
                    )}

                    <div className="mt-8 flex items-end justify-between gap-3 flex-wrap">
                        <h2 className="lv-dossier-title text-xl">{t('overview.todo.title')}</h2>
                        <span className="text-xs text-outline">
                            {loadedAt && t('overview.updatedAt', { time: loadedAt.toLocaleTimeString() })}
                        </span>
                    </div>

                    {todoCount === 0 && (
                        <div className="mt-4">
                            <EmptyState bordered icon="verified" title={t('overview.todo.emptyTitle')} text={t('overview.todo.emptyText')} />
                        </div>
                    )}

                    {todoCount > 0 && (
                        <div className="mt-4 grid gap-4 lg:grid-cols-3">
                            <Dossier title={t('overview.todo.alerts')} tab={`${s.highAlerts.length}`}
                                     right={<Link to="/admin/alerts" className="text-xs font-semibold text-secondary">{t('overview.todo.viewAll')}</Link>}>
                                <TodoList items={s.highAlerts} empty={t('overview.todo.none')} render={(a) => (
                                    <li key={a.id} className="py-3">
                                        <div className="flex items-center gap-2"><Stamp tone="danger">{t('alerts.severity.HIGH')}</Stamp>
                                            <span className="text-sm font-semibold truncate">{t(`alerts.rules.${a.rule}`, { defaultValue: a.rule })}</span></div>
                                        <p className="mt-1 text-xs text-on-surface-variant line-clamp-2">{a.message}</p>
                                        <p className="mt-1 text-[11px] text-outline">{formatDate(a.lastSeenAt, true)}</p>
                                    </li>
                                )} />
                            </Dossier>

                            <Dossier title={t('overview.todo.deadlines')} tab={`${s.claimDeadlines.length}`}
                                     right={<Link to="/admin/vaults?expiring=7" className="text-xs font-semibold text-secondary">{t('overview.todo.viewAll')}</Link>}>
                                <TodoList items={s.claimDeadlines} empty={t('overview.todo.none')} render={(v) => (
                                    <li key={v.id} className="py-3">
                                        <p className="text-sm font-semibold truncate">{v.name}</p>
                                        <p className="text-xs text-on-surface-variant truncate">{v.ownerEmail}</p>
                                        <p className="mt-1"><Stamp tone="warn" icon="hourglass_bottom" flat>{t('overview.todo.deadlineAt', { date: formatDate(v.claimDeadlineAt, true) })}</Stamp></p>
                                    </li>
                                )} />
                            </Dossier>

                            <Dossier title={t('overview.todo.payments')} tab={`${s.stalePendingPayments.length}`}
                                     right={<Link to="/admin/payments?status=PENDING" className="text-xs font-semibold text-secondary">{t('overview.todo.viewAll')}</Link>}>
                                <TodoList items={s.stalePendingPayments} empty={t('overview.todo.none')} render={(p) => (
                                    <li key={p.id} className="py-3 flex items-start justify-between gap-3">
                                        <div className="min-w-0">
                                            <p className="text-sm font-semibold truncate">{p.userEmail}</p>
                                            <p className="text-xs text-on-surface-variant">{p.planCode} · {formatDate(p.createdAt, true)}</p>
                                        </div>
                                        <span className="lv-num text-sm font-semibold">{formatVnd(p.amountVnd)}</span>
                                    </li>
                                )} />
                            </Dossier>
                        </div>
                    )}
                </>
            )}
        </AdminLayout>
    );
}
