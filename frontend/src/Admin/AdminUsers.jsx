import React, { useCallback, useEffect, useState } from 'react';
import { getUser } from '../auth/auth.js';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate, inputClass } from '../Owner/ownerUi.jsx';
import { AdminLayout, ReasonDialog, Pager, isAdmin } from './adminUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";


const ROLE_LABEL = { OWNER: 'Owner', BENEFICIARY: 'Beneficiary', EXECUTOR: 'Executor', LEGAL_VERIFIER: 'Legal Verifier', ADMIN: 'Admin' };
const STATUS_CLS = {
    ACTIVE: 'bg-success-container text-on-success-container',
    LOCKED: 'bg-error-container text-on-error-container',
    PENDING: 'bg-surface-container text-outline',
};
const DMS_BADGE = {
    ACTIVE: { label: 'DMS active', cls: 'bg-success-container text-on-success-container' },
    SUSPENDED: { label: 'DMS suspended', cls: 'bg-warning-container text-on-warning-container' },
    TRIGGERED: { label: 'DMS triggered', cls: 'bg-error-container text-on-error-container' },
};
// Role admin được phép cấp/gỡ (khớp MANAGEABLE_ROLES của BE)
const UNMANAGEABLE = ['OWNER', 'BENEFICIARY', 'EXECUTOR'];
const HIDDEN_ROLES = ['BENEFICIARY'];

export default function AdminUsers() {
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

    const labelOf = (code) => roles.find((r) => r.code === code)?.name || ROLE_LABEL[code] || code;
    const manageable = roles.map((r) => r.code).filter((c) => !UNMANAGEABLE.includes(c));

    const load = useCallback(() => {
        if (!isAdmin(me)) return;
        adminApi.listUsers({ ...query, page, size: 20 })
            .then(setData)
            .catch((e) => setError(getErrorMessage(e, 'Could not load users.')));
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
            setError(getErrorMessage(e, 'Action failed.'));
            setDialog(null);
        } finally {
            setBusy(false);
        }
    };

    const onConfirm = (reason) => {
        const { kind, user, roleCode } = dialog;
        if (kind === 'lock') run(() => adminApi.updateStatus(user.id, 'LOCKED', reason), `${user.email} has been locked.`);
        if (kind === 'unlock') run(() => adminApi.updateStatus(user.id, 'ACTIVE'), `${user.email} has been unlocked.`);
        if (kind === 'grant') run(() => adminApi.grantRole(user.id, roleCode), `${labelOf(roleCode)} granted to ${user.email}. They need to sign in again.`);
        if (kind === 'revoke') run(() => adminApi.revokeRole(user.id, roleCode), `${labelOf(roleCode)} removed from ${user.email}.`);
        if (kind === 'suspend') run(() => adminApi.suspendDms(user.id, reason).then(() => ({ ...user, dmsStatus: 'SUSPENDED' })),
            `Dead Man's Switch of ${user.email} suspended.`);
        if (kind === 'resume') run(() => adminApi.resumeDms(user.id).then(() => ({ ...user, dmsStatus: 'ACTIVE' })),
            `Dead Man's Switch of ${user.email} resumed.`);
        if (kind === 'rescue') run(() => adminApi.rescueExecutor(user.id, reason).then(() => ({ ...user, executorRescueNeeded: false })),
            `${reason} is now the executor of ${user.email} and will receive an activation email within a few minutes.`);    };

    const search = (e) => { e.preventDefault(); setPage(0); setQuery(filters); };
    const rows = data?.content ?? [];

    return (
        <AdminLayout title="Users" subtitle="Manage accounts, role access and Dead Man's Switch holds.">
            <form onSubmit={search} className="grid sm:grid-cols-[1fr_180px_160px_auto] gap-3 mb-6">
                <input value={filters.q} onChange={(e) => setFilters((f) => ({ ...f, q: e.target.value }))} placeholder="Search by email or name" className={inputClass} />
                <select value={filters.role} onChange={(e) => setFilters((f) => ({ ...f, role: e.target.value }))} className={inputClass}>
                    <option value="">All roles</option>
                    {roles.filter((r) => !HIDDEN_ROLES.includes(r.code)).map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
                </select>
                <select value={filters.status} onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value }))} className={inputClass}>
                    <option value="">All statuses</option>
                    <option value="ACTIVE">Active</option>
                    <option value="LOCKED">Locked</option>
                    <option value="PENDING">Pending</option>
                </select>
                <button type="submit" className="h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center justify-center gap-2">
                    <Icon name="search" className="text-[18px]" /> Search
                </button>
            </form>

            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>

            {!data && !error && <p className="text-on-surface-variant">Loading...</p>}
            {data && rows.length === 0 && <EmptyState bordered icon="person_search" title="No users found" text="Check the spelling, or clear the role and status filters." />}

            {rows.length > 0 && (
                <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/30 overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <thead className="bg-surface-container-low text-on-surface-variant">
                            <tr>
                                <th className="p-4">User</th>
                                <th className="p-4">Status</th>
                                <th className="p-4">Roles</th>
                                <th className="p-4">Last login</th>
                                <th className="p-4 text-right">Actions</th>
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
                {...dialogText(dialog, labelOf)}
                busy={busy}
                onConfirm={onConfirm}
                onCancel={() => setDialog(null)}
            />
        </AdminLayout>
    );
}

function UserRow({ u, isMe, manageable, labelOf, onAction }) {
    const allRoles = u.roles || [];
    const roles = allRoles.filter((r) => !HIDDEN_ROLES.includes(r));
    const isOwner = allRoles.includes('OWNER');
    const link = 'text-xs font-semibold text-secondary hover:text-primary whitespace-nowrap';

    return (
        <tr className="border-t border-outline-variant/20 align-top">
            <td className="p-4">
                <p className="font-semibold">{u.fullName || '—'}{isMe && <span className="ml-1 text-xs text-outline">(you)</span>}</p>
                <p className="text-on-surface-variant">{u.email}</p>
                <p className="text-xs text-outline">Joined {formatDate(u.createdAt)}</p>
            </td>
            <td className="p-4">
                <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${STATUS_CLS[u.status] || ''}`}>{u.status}</span>
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
                                {has ? `Remove ${labelOf(r)}` : `Make ${labelOf(r)}`}
                            </button>
                        );
                    })}
                    {/* Khoá / mở khoá (không áp dụng cho chính mình và tài khoản PENDING) */}
                    {!isMe && u.status === 'ACTIVE' && (
                        <button onClick={() => onAction({ kind: 'lock', user: u })} className={`${link} text-error`}>Lock account</button>
                    )}
                    {!isMe && u.status === 'LOCKED' && (
                        <button onClick={() => onAction({ kind: 'unlock', user: u })} className={link}>Unlock account</button>
                    )}
                    {/* DMS của Owner: BE không trả trạng thái DMS nên hiện cả 2, BE báo lỗi nếu không hợp lệ */}
                    {/* DMS của Owner: hiện đúng nút theo trạng thái */}
                    {isOwner && u.dmsStatus && (
                        <span className={`mt-1 text-[11px] font-semibold px-2 py-0.5 rounded-full whitespace-nowrap ${DMS_BADGE[u.dmsStatus]?.cls || ''}`}>
                            {DMS_BADGE[u.dmsStatus]?.label || u.dmsStatus}
                        </span>
                    )}
                    {isOwner && u.dmsStatus === 'ACTIVE' && (
                        <button onClick={() => onAction({ kind: 'suspend', user: u })} className={link}>Suspend DMS</button>
                    )}
                    {isOwner && u.dmsStatus === 'SUSPENDED' && (
                        <button onClick={() => onAction({ kind: 'resume', user: u })} className={link}>Resume DMS</button>
                    )}
                    {u.executorRescueNeeded && (
                        <button onClick={() => onAction({ kind: 'rescue', user: u })}
                                className={`${link} text-error inline-flex items-center gap-1`}>
                            <Icon name="emergency" className="text-[14px]" /> Assign executor
                        </button>
                    )}
                </div>
            </td>
        </tr>
    );
}

function dialogText(d, labelOf) {
    if (!d) return {};
    const who = d.user.email;
    switch (d.kind) {
        case 'lock': return { title: 'Lock this account?', message: `${who} will not be able to sign in.`, confirmLabel: 'Lock', danger: true, askReason: true };
        case 'unlock': return { title: 'Unlock this account?', message: `${who} will be able to sign in again.`, confirmLabel: 'Unlock' };
        case 'grant': return { title: `Grant ${labelOf(d.roleCode)}?`, message: `${who} will get ${labelOf(d.roleCode)} access after signing in again.`, confirmLabel: 'Grant' };
        case 'revoke': return { title: `Remove ${labelOf(d.roleCode)}?`, message: `${who} will lose ${labelOf(d.roleCode)} access.`, confirmLabel: 'Remove', danger: true };
        case 'suspend': return { title: "Suspend Dead Man's Switch?", message: `The switch of ${who} will stop counting until resumed. The owner cannot resume it.`, confirmLabel: 'Suspend', danger: true, askReason: true };
        case 'resume': return { title: "Resume Dead Man's Switch?", message: `The switch of ${who} will start counting again.`, confirmLabel: 'Resume' };
        case 'rescue': return {
            title: 'Assign an emergency executor?',
            message: `The Dead Man's Switch of ${who} has been triggered, but no executor can act. The person you enter becomes the primary executor immediately and must have a verified LegacyVault account.`,
            confirmLabel: 'Assign & activate', askEmail: true, placeholder: 'executor@example.com',
        };
        default: return {};
    }
}
