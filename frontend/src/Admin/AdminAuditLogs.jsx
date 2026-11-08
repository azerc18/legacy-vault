import React, { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate, inputClass } from '../Owner/ownerUi.jsx';
import { AdminLayout, Pager, Stamp, toneOf } from './adminUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";

// <input type="datetime-local"> -> ISO string cho BE
const toIso = (local) => (local ? new Date(local).toISOString() : undefined);

// Nhóm mã hành động theo tiền tố (LOGIN_FAILED -> LOGIN) để dropdown ~100 mục dễ tìm
const groupActions = (actions) => {
    const groups = {};
    [...actions].sort().forEach((a) => { (groups[a.split('_')[0]] ??= []).push(a); });
    return Object.entries(groups).sort(([x], [y]) => x.localeCompare(y));
};

export default function AdminAuditLogs() {
    const { t } = useTranslation('admin');
    const [params] = useSearchParams();
    // Cho phép mở sẵn bộ lọc từ trang Alerts: /admin/audit-logs?ip=...&action=...
    const initial = {
        action: params.get('action') || '',
        actorEmail: params.get('actorEmail') || '',
        ip: params.get('ip') || '',
        from: '',
        to: '',
    };
    const [filters, setFilters] = useState(initial);
    const [query, setQuery] = useState(initial);
    const [page, setPage] = useState(0);
    const [actions, setActions] = useState([]);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const [expanded, setExpanded] = useState(null);
    const [exporting, setExporting] = useState(false);

    // B4: xuất đúng bộ lọc đang áp dụng
    const exportCsv = async () => {
        setExporting(true); setError('');
        try { await adminApi.exportAuditLogs({ ...query, from: toIso(query.from), to: toIso(query.to) }); }
        catch (e) { setError(getErrorMessage(e, t('exportError'))); }
        finally { setExporting(false); }
    };

    useEffect(() => { adminApi.listAuditActions().then(setActions).catch(() => {}); }, []);

    const load = useCallback(() => {
        setError('');
        adminApi.searchAuditLogs({ ...query, from: toIso(query.from), to: toIso(query.to), page, size: 50 })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, t('audit.loadError'))));
    }, [query, page]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    const set = (field) => (e) => setFilters((f) => ({ ...f, [field]: e.target.value }));
    const search = (e) => { e.preventDefault(); setPage(0); setQuery(filters); };
    const reset = () => {
        const empty = { action: '', actorEmail: '', ip: '', from: '', to: '' };
        setFilters(empty); setQuery(empty); setPage(0);
    };
    const rows = data?.content ?? [];

    return (
        <AdminLayout title={t('audit.title')} subtitle={t('audit.subtitle')}
                     actions={(
                         <button onClick={exportCsv} disabled={exporting}
                                 className="h-10 px-4 rounded-xl border border-outline-variant text-sm font-semibold inline-flex items-center gap-2 hover:bg-surface-container-low disabled:opacity-60">
                             <Icon name="download" className="text-[18px]" /> {exporting ? t('wait') : t('exportCsv')}
                         </button>
                     )}>
            <form onSubmit={search} className="grid gap-3 mb-6 sm:grid-cols-3 lg:grid-cols-[200px_1fr_160px_190px_190px_auto]">
                <select value={filters.action} onChange={set('action')} className={inputClass}>
                    <option value="">{t('audit.allActions')}</option>
                    {groupActions(actions).map(([group, items]) => (
                        <optgroup key={group} label={group}>
                            {items.map((a) => <option key={a} value={a}>{a}</option>)}
                        </optgroup>
                    ))}
                </select>
                <input value={filters.actorEmail} onChange={set('actorEmail')} placeholder={t('audit.actorEmail')} className={inputClass} />
                <input value={filters.ip} onChange={set('ip')} placeholder={t('audit.ip')} className={inputClass} />
                <input type="datetime-local" value={filters.from} onChange={set('from')} className={inputClass} title={t('audit.from')} />
                <input type="datetime-local" value={filters.to} onChange={set('to')} className={inputClass} title={t('audit.to')} />
                <div className="flex gap-2">
                    <button type="submit" className="h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center gap-2 lv-pill">
                        <Icon name="search" className="text-[18px]" /> {t('search')}
                    </button>
                    <button type="button" onClick={reset} className="h-11 px-3 rounded-xl border border-outline-variant text-sm font-semibold">{t('reset')}</button>
                </div>
            </form>

            <Alert>{error}</Alert>

            {!data && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
            {data && rows.length === 0 && (
                <EmptyState bordered icon="manage_search" title={t('audit.empty.title')}
                            text={t('audit.empty.text')} />
            )}

            {rows.length > 0 && (
                <div className="lv-dossier overflow-x-auto">
                    <table className="lv-ledger is-stack">
                        <thead>
                        <tr>
                            <th>{t('audit.cols.time')}</th>
                            <th>{t('audit.cols.action')}</th>
                            <th>{t('audit.cols.result')}</th>
                            <th>{t('audit.cols.actor')}</th>
                            <th>IP</th>
                            <th>{t('audit.cols.detail')}</th>
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((l) => (
                            <React.Fragment key={l.id}>
                                <tr className="is-row cursor-pointer"
                                    onClick={() => setExpanded(expanded === l.id ? null : l.id)}>
                                    <td data-label={t('audit.cols.time')} className="whitespace-nowrap">{formatDate(l.createdAt, true)}</td>
                                    <td data-label={t('audit.cols.action')} className="lv-mono text-xs font-semibold">{l.action}</td>
                                    <td data-label={t('audit.cols.result')}>
                                        {l.result && (
                                            <Stamp tone={toneOf(l.result)}>{t(`audit.result.${l.result}`, { defaultValue: l.result })}</Stamp>
                                        )}
                                    </td>
                                    <td data-label={t('audit.cols.actor')}>{l.actorEmail || <span className="text-outline">{t('audit.system')}</span>}</td>
                                    <td data-label="IP" className="lv-mono text-xs">{l.ipAddress || '—'}</td>
                                    <td data-label={t('audit.cols.detail')} className="text-on-surface-variant max-w-xs truncate">{l.detail || '—'}</td>
                                </tr>
                                {expanded === l.id && (
                                    <tr className="bg-surface-container-low">
                                        <td colSpan={6} className="px-4 pb-4 text-xs text-on-surface-variant">
                                            <div className="grid sm:grid-cols-2 gap-x-6 gap-y-1">
                                                <span><b>{t('audit.cols.detail')}:</b> {l.detail || '—'}</span>
                                                <span><b>{t('audit.expand.entity')}:</b> {l.entityType ? `${l.entityType} ${l.entityId || ''}` : '—'}</span>
                                                <span><b>{t('audit.expand.vault')}:</b> {l.vaultId || '—'}</span>
                                                <span><b>{t('audit.expand.actorId')}:</b> {l.actorId || '—'}</span>
                                                <span className="sm:col-span-2 break-all"><b>{t('audit.expand.userAgent')}:</b> {l.userAgent || '—'}</span>
                                            </div>
                                        </td>
                                    </tr>
                                )}
                            </React.Fragment>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />
        </AdminLayout>
    );
}