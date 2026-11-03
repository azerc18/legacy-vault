import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate, inputClass } from '../Owner/ownerUi.jsx';
import { AdminLayout, ReasonDialog, Pager, isAdmin } from './adminUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";

const STATUS_CLS = {
    ACTIVE: 'bg-success-container text-on-success-container',
    LOCKED: 'bg-error-container text-on-error-container',
    PENDING: 'bg-surface-container text-outline',
};
const DMS_CLS = {
    ACTIVE: 'bg-success-container text-on-success-container',
    SUSPENDED: 'bg-warning-container text-on-warning-container',
    TRIGGERED: 'bg-error-container text-on-error-container',
};
const STATUSES = ['ACTIVE', 'LOCKED', 'PENDING'];
// Role admin được phép cấp/gỡ (khớp MANAGEABLE_ROLES của BE)
const UNMANAGEABLE = ['OWNER', 'BENEFICIARY', 'EXECUTOR'];
const HIDDEN_ROLES = ['BENEFICIARY'];

export default function AdminUsers() {
    const { t } = useTranslation('admin');
    const me = getUser();
    const [filters, setFilters] = useState({ q: '', role: '', status: '' });
    const [query, setQuery] = useState({ q: '', role: '', status: '' }); // áp dụng khi bấm Search
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busy, setBusy] = useState(false);
    const [dialog, setDialog] = useState(null); // { kind, user, roleCode? }

    const [roles, setRoles] = useState([]);
    useEffect(() => { adminApi.listRoles().then(setRoles).catch(() => {}); }, []);

    // Role hệ thống dịch theo common:roles, role tự tạo hiện tên trong DB
    const labelOf = (code) => t(`common:roles.${code}`, { defaultValue: roles.find((r) => r.code === code)?.name || code });
    const manageable = roles.map((r) => r.code).filter((c) => !UNMANAGEABLE.includes(c));

    const load = useCallback(() => {
        if (!isAdmin(me)) return;
        adminApi.listUsers({ ...query, page, size: 20 })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, t('users.loadError'))));
    }, [query, page]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    // Cập nhật 1 dòng trong bảng bằng AdminUserResponse BE trả về
    const replaceRow = (u) => setData((d) => d && { ...d, content: d.content.map((x) => (x.id === u.id ? u : x)) });

    const run = async (fn, msg) => {
        setBusy(true); setError(''); setSuccess('');
        try {
            const res = await fn();
            if (res?.id) replaceRow(res);
            setSuccess(msg);
            setDialog(null);
        } catch (e) {
            setError(getErrorMessage(e, t('actionFailed')));
            setDialog(null);
        } finally {
            setBusy(false);
        }
    };

    const onConfirm = (reason) => {
        const { kind, user, roleCode } = dialog;
        const v = { email: user.email, role: roleCode && labelOf(roleCode), executor: reason };
        const msg = t(`users.success.${kind}`, v);
        if (kind === 'lock') run(() => adminApi.updateStatus(user.id, 'LOCKED', reason), msg);
        if (kind === 'unlock') run(() => adminApi.updateStatus(user.id, 'ACTIVE'), msg);
        if (kind === 'grant') run(() => adminApi.grantRole(user.id, roleCode), msg);
        if (kind === 'revoke') run(() => adminApi.revokeRole(user.id, roleCode), msg);
        if (kind === 'suspend') run(() => adminApi.suspendDms(user.id, reason).then(() => ({ ...user, dmsStatus: 'SUSPENDED' })), msg);
        if (kind === 'resume') run(() => adminApi.resumeDms(user.id).then(() => ({ ...user, dmsStatus: 'ACTIVE' })), msg);
        if (kind === 'rescue') run(() => adminApi.rescueExecutor(user.id, reason).then(() => ({ ...user, executorRescueNeeded: false })), msg);
    };

    const search = (e) => { e.preventDefault(); setPage(0); setQuery(filters); };
    const rows = data?.content ?? [];

    return (
        <AdminLayout title={t('users.title')} subtitle={t('users.subtitle')}>
            <form onSubmit={search} className="grid sm:grid-cols-[1fr_180px_160px_auto] gap-3 mb-6">
                <input value={filters.q} onChange={(e) => setFilters((f) => ({ ...f, q: e.target.value }))} placeholder={t('users.searchPlaceholder')} className={inputClass} />
                <select value={filters.role} onChange={(e) => setFilters((f) => ({ ...f, role: e.target.value }))} className={inputClass}>
                    <option value="">{t('users.allRoles')}</option>
                    {roles.filter((r) => !HIDDEN_ROLES.includes(r.code)).map((r) => <option key={r.code} value={r.code}>{labelOf(r.code)}</option>)}
                </select>
                <select value={filters.status} onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value }))} className={inputClass}>
                    <option value="">{t('allStatuses')}</option>
                    {STATUSES.map((s) => <option key={s} value={s}>{t(`users.status.${s}`)}</option>)}
                </select>
                <button type="submit" className="h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center justify-center gap-2">
                    <Icon name="search" className="text-[18px]" /> {t('search')}
                </button>
            </form>

            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>

            {!data && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
            {data && rows.length === 0 && <EmptyState bordered icon="person_search" title={t('users.empty.title')} text={t('users.empty.text')} />}

            {rows.length > 0 && (
                <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <thead className="bg-surface-container-low text-on-surface-variant">
                        <tr>
                            <th className="p-4">{t('users.cols.user')}</th>
                            <th className="p-4">{t('users.cols.status')}</th>
                            <th className="p-4">{t('users.cols.roles')}</th>
                            <th className="p-4">{t('users.cols.lastLogin')}</th>
                            <th className="p-4 text-right">{t('users.cols.actions')}</th>
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((u) => (
                            <UserRow key={u.id} u={u} isMe={u.id === me?.id} manageable={manageable} labelOf={labelOf} onAction={setDialog} />
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />

            <ReasonDialog
                open={!!dialog}
                {...dialogText(dialog, labelOf, t)}
                busy={busy}
                onConfirm={onConfirm}
                onCancel={() => setDialog(null)}
            />
        </AdminLayout>
    );
}

function UserRow({ u, isMe, manageable, labelOf, onAction }) {
    const { t } = useTranslation('admin');
    const allRoles = u.roles || [];
    const roles = allRoles.filter((r) => !HIDDEN_ROLES.includes(r));
    const isOwner = allRoles.includes('OWNER');
    const link = 'text-xs font-semibold text-secondary hover:text-primary whitespace-nowrap';

    return (
        <tr className="border-t border-outline-variant/20 align-top">
            <td className="p-4">
                <p className="font-semibold">{u.fullName || '—'}{isMe && <span className="ml-1 text-xs text-outline">({t('users.you')})</span>}</p>
                <p className="text-on-surface-variant">{u.email}</p>
                <p className="text-xs text-outline">{t('users.joined', { date: formatDate(u.createdAt) })}</p>
            </td>
            <td className="p-4">
                <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${STATUS_CLS[u.status] || ''}`}>
                    {t(`users.status.${u.status}`, { defaultValue: u.status })}
                </span>
            </td>
            <td className="p-4">
                <div className="flex flex-wrap gap-1">
                    {roles.length === 0 && <span className="text-xs text-outline">—</span>}
                    {roles.map((r) => (
                        <span key={r}
                              className="text-xs font-semibold px-2 py-0.5 rounded-full bg-secondary-container text-on-secondary-container whitespace-nowrap">
                            {labelOf(r)}
                        </span>
                    ))}
                </div>
            </td>
            <td className="p-4 whitespace-nowrap">{formatDate(u.lastLoginAt, true)}</td>
            <td className="p-4">
                <div className="flex flex-col items-end gap-1.5">
                    {/* Cấp / gỡ role (chỉ với tài khoản ACTIVE) */}
                    {u.status === 'ACTIVE' && manageable.map((r) => {
                        const has = roles.includes(r);
                        if (has && r === 'ADMIN' && isMe) return null; // không tự gỡ ADMIN
                        return (
                            <button key={r} onClick={() => onAction({ kind: has ? 'revoke' : 'grant', user: u, roleCode: r })} className={link}>
                                {t(has ? 'users.actions.revoke' : 'users.actions.grant', { role: labelOf(r) })}
                            </button>
                        );
                    })}
                    {/* Khoá / mở khoá (không áp dụng cho chính mình và tài khoản PENDING) */}
                    {!isMe && u.status === 'ACTIVE' && (
                        <button onClick={() => onAction({ kind: 'lock', user: u })} className={`${link} text-error`}>{t('users.actions.lock')}</button>
                    )}
                    {!isMe && u.status === 'LOCKED' && (
                        <button onClick={() => onAction({ kind: 'unlock', user: u })} className={link}>{t('users.actions.unlock')}</button>
                    )}
                    {/* DMS của Owner: hiện đúng nút theo trạng thái */}
                    {isOwner && u.dmsStatus && (
                        <span className={`mt-1 text-[11px] font-semibold px-2 py-0.5 rounded-full whitespace-nowrap ${DMS_CLS[u.dmsStatus] || ''}`}>
                            {t(`users.dms.${u.dmsStatus}`, { defaultValue: u.dmsStatus })}
                        </span>
                    )}
                    {isOwner && u.dmsStatus === 'ACTIVE' && (
                        <button onClick={() => onAction({ kind: 'suspend', user: u })} className={link}>{t('users.actions.suspend')}</button>
                    )}
                    {isOwner && u.dmsStatus === 'SUSPENDED' && (
                        <button onClick={() => onAction({ kind: 'resume', user: u })} className={link}>{t('users.actions.resume')}</button>
                    )}
                    {u.executorRescueNeeded && (
                        <button onClick={() => onAction({ kind: 'rescue', user: u })}
                                className={`${link} text-error inline-flex items-center gap-1`}>
                            <Icon name="emergency" className="text-[14px]" /> {t('users.actions.rescue')}
                        </button>
                    )}
                </div>
            </td>
        </tr>
    );
}

// Nội dung hộp thoại theo loại thao tác; key ở admin.json > users.dialog.<kind>
const DIALOG_OPTS = {
    lock: { danger: true, askReason: true },
    unlock: {},
    grant: {},
    revoke: { danger: true },
    suspend: { danger: true, askReason: true },
    resume: {},
    rescue: { askEmail: true, placeholder: 'executor@example.com' },
};

function dialogText(d, labelOf, t) {
    if (!d || !DIALOG_OPTS[d.kind]) return {};
    const v = { email: d.user.email, role: d.roleCode && labelOf(d.roleCode) };
    return {
        ...DIALOG_OPTS[d.kind],
        title: t(`users.dialog.${d.kind}.title`, v),
        message: t(`users.dialog.${d.kind}.message`, v),
        confirmLabel: t(`users.dialog.${d.kind}.confirm`, v),
    };
}
