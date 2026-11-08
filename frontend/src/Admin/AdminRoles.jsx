import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, ConfirmDialog, inputClass, btnPrimary, btnOutline } from '../Owner/ownerUi.jsx';
import { AdminLayout, Stamp } from './adminUi.jsx';

// Khớp @Pattern của CreateRoleRequest ở BE
const CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,49}$/;
const EMPTY_FORM = { code: '', name: '', description: '', authorities: [] };
const byCode = (a, b) => a.code.localeCompare(b.code);

export default function AdminRoles() {
    const { t } = useTranslation('admin');
    const [roles, setRoles] = useState(null);
    const [authorities, setAuthorities] = useState([]);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [editing, setEditing] = useState(null);   // null | { mode: 'create' | 'edit', form }
    const [deleting, setDeleting] = useState(null); // role đang chờ xác nhận xoá
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        Promise.all([adminApi.listRoles(), adminApi.listAuthorities()])
            .then(([r, a]) => { setRoles(r); setAuthorities(a); })
            .catch((e) => setError(getErrorMessage(e, t('roles.loadError'))));
    }, []);

    const resetMessages = () => { setError(''); setSuccess(''); };

    const openCreate = () => { resetMessages(); setEditing({ mode: 'create', form: EMPTY_FORM }); };
    const openEdit = (r) => {
        resetMessages();
        setEditing({
            mode: 'edit',
            form: { code: r.code, name: r.name, description: r.description || '', authorities: r.authorities || [] },
        });
    };

    const save = async (form) => {
        setBusy(true); resetMessages();
        const body = {
            name: form.name.trim(),
            description: form.description.trim() || null,
            authorities: form.authorities,
        };
        try {
            if (editing.mode === 'create') {
                const created = await adminApi.createRole({ code: form.code.trim(), ...body });
                setRoles((rs) => [...rs, created].sort(byCode));
                setSuccess(t('roles.created', { code: created.code }));
            } else {
                const updated = await adminApi.updateRole(form.code, body);
                setRoles((rs) => rs.map((r) => (r.code === updated.code ? updated : r)));
                setSuccess(t('roles.updated', { code: updated.code }));
            }
            setEditing(null);
        } catch (e) {
            setError(getErrorMessage(e, t('roles.saveError'))); // giữ form mở để sửa lại
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        setBusy(true); resetMessages();
        try {
            await adminApi.deleteRole(deleting.code);
            setRoles((rs) => rs.filter((r) => r.code !== deleting.code));
            setSuccess(t('roles.deleted', { code: deleting.code }));
        } catch (e) {
            setError(getErrorMessage(e, t('roles.deleteError')));
        } finally {
            setBusy(false);
            setDeleting(null);
        }
    };

    return (
        <AdminLayout title={t('roles.title')} subtitle={t('roles.subtitle')}>
            <div className="flex justify-end mb-6">
                {!editing && (
                    <button onClick={openCreate} className={btnPrimary}>
                        <Icon name="add" className="text-[18px]" /> {t('roles.newRole')}
                    </button>
                )}
            </div>

            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>

            {editing && (
                <RoleForm
                    key={`${editing.mode}-${editing.form.code}`}
                    mode={editing.mode}
                    initial={editing.form}
                    authorities={authorities}
                    busy={busy}
                    onSave={save}
                    onCancel={() => setEditing(null)}
                />
            )}

            {!roles && !error && <p className="text-on-surface-variant">{t('loading')}</p>}

            {roles && (
                <div className="lv-dossier overflow-x-auto">
                    <table className="lv-ledger is-stack">
                        <thead>
                        <tr>
                            <th>{t('roles.cols.role')}</th>
                            <th>{t('roles.cols.permissions')}</th>
                            <th className="text-right">{t('roles.cols.actions')}</th>
                        </tr>
                        </thead>
                        <tbody>
                        {roles.map((r) => (
                            <tr key={r.code} className="is-row">
                                <td data-label={t('roles.cols.role')}>
                                    <p className="font-semibold flex items-center gap-2">
                                        {t(`common:roles.${r.code}`, { defaultValue: r.name })}
                                        {r.system && (
                                            <Stamp tone="muted" icon="lock">{t('roles.system')}</Stamp>
                                        )}
                                    </p>
                                    <p className="text-xs text-outline font-mono">{r.code}</p>
                                    {r.description && <p className="mt-1 text-on-surface-variant">{r.description}</p>}
                                </td>
                                <td data-label={t('roles.cols.permissions')}>
                                    <div className="flex flex-wrap gap-1">
                                        {(r.authorities || []).map((a) => (
                                            <span key={a} className="lv-mono text-[11px] font-semibold px-2 py-0.5 rounded border border-outline-variant bg-surface-container-low">{a}</span>
                                        ))}
                                    </div>
                                </td>
                                <td>
                                    <div className="flex flex-col items-end gap-1.5">
                                        <button onClick={() => openEdit(r)} className="text-xs font-semibold text-secondary hover:text-primary">{t('roles.edit')}</button>
                                        {!r.system && (
                                            <button onClick={() => { resetMessages(); setDeleting(r); }} className="text-xs font-semibold text-error">{t('roles.delete')}</button>
                                        )}
                                    </div>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            <ConfirmDialog
                open={!!deleting}
                title={t('roles.deleteDialog.title', { code: deleting?.code })}
                message={t('roles.deleteDialog.message')}
                confirmLabel={t('roles.delete')}
                danger
                busy={busy}
                onConfirm={remove}
                onCancel={() => setDeleting(null)}
            />
        </AdminLayout>
    );
}

function RoleForm({ mode, initial, authorities, busy, onSave, onCancel }) {
    const { t } = useTranslation('admin');
    const [form, setForm] = useState(initial);
    const [submitted, setSubmitted] = useState(false);
    const isCreate = mode === 'create';
    const isAdminRole = form.code === 'ADMIN';

    const codeError = isCreate && !CODE_PATTERN.test(form.code)
        ? t('roles.form.codeError') : '';
    const nameError = !form.name.trim() ? t('roles.form.nameError') : '';
    const authError = form.authorities.length === 0 ? t('roles.form.authError') : '';
    const valid = !codeError && !nameError && !authError;

    const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

    const toggle = (code) => {
        if (isAdminRole && code === 'ADMIN_MANAGE') return; // BE chặn bỏ quyền này khỏi role ADMIN
        setForm((f) => ({
            ...f,
            authorities: f.authorities.includes(code)
                ? f.authorities.filter((c) => c !== code)
                : [...f.authorities, code],
        }));
    };

    const submit = (e) => {
        e.preventDefault();
        setSubmitted(true);
        if (valid) onSave(form);
    };

    const fieldError = (msg) => submitted && msg && <p className="mt-1 text-xs text-error">{msg}</p>;

    return (
        <form onSubmit={submit} className="mb-8 lv-dossier p-6 pt-8">
            <h2 className="font-title-lg text-title-lg mb-4">{isCreate ? t('roles.newRole') : t('roles.form.editTitle', { code: form.code })}</h2>

            <div className="grid sm:grid-cols-2 gap-4">
                <label className="block">
                    <span className="text-sm font-semibold">{t('roles.form.code')}</span>
                    <input
                        value={form.code}
                        disabled={!isCreate}
                        onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase().replace(/\s+/g, '_') }))}
                        placeholder={t('roles.form.codePlaceholder')}
                        className={`${inputClass} mt-1 font-mono disabled:opacity-60`}
                    />
                    {fieldError(codeError)}
                </label>
                <label className="block">
                    <span className="text-sm font-semibold">{t('roles.form.name')}</span>
                    <input value={form.name} onChange={set('name')} maxLength={100} placeholder={t('roles.form.namePlaceholder')} className={`${inputClass} mt-1`} />
                    {fieldError(nameError)}
                </label>
            </div>

            <label className="block mt-4">
                <span className="text-sm font-semibold">{t('roles.form.description')}</span>
                <input value={form.description} onChange={set('description')} maxLength={255} placeholder={t('roles.form.optional')} className={`${inputClass} mt-1`} />
            </label>

            <p className="mt-6 text-sm font-semibold">{t('roles.cols.permissions')}</p>
            <div className="mt-2 grid sm:grid-cols-2 gap-2">
                {authorities.map((a) => {
                    const checked = form.authorities.includes(a.code);
                    const locked = isAdminRole && a.code === 'ADMIN_MANAGE';
                    return (
                        <label key={a.code}
                               className={`flex items-start gap-3 p-3 rounded-xl border ${checked ? 'border-primary bg-surface-container-low' : 'border-outline-variant'} ${locked ? 'opacity-70' : 'cursor-pointer'}`}>
                            <input type="checkbox" checked={checked} disabled={locked} onChange={() => toggle(a.code)} className="mt-1" />
                            <span>
                                <span className="block font-mono text-xs font-semibold">{a.code}</span>
                                <span className="block text-sm text-on-surface-variant">{a.description}</span>
                                {locked && <span className="block text-xs text-outline">{t('roles.form.adminRequired')}</span>}
                            </span>
                        </label>
                    );
                })}
            </div>
            {fieldError(authError)}

            <div className="mt-6 flex justify-end gap-3">
                <button type="button" onClick={onCancel} className={btnOutline}>{t('common:actions.cancel')}</button>
                <button type="submit" disabled={busy} className={btnPrimary}>
                    {busy ? t('wait') : isCreate ? t('roles.form.create') : t('roles.form.save')}
                </button>
            </div>
        </form>
    );
}