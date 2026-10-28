import React, { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi, needsReplaceConfirm } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import {
    OwnerLayout, Card, Icon, Alert, ConfirmDialog, formatDate, humanize,
    ASSET_TYPES, DOCUMENT_TYPES, inputClass, btnPrimary, btnOutline, btnDanger,
} from './ownerUi.jsx';

const assetLabel = (t) => ASSET_TYPES.find((a) => a.value === t)?.label || t;
const textareaCls = 'w-full mt-1 px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary';

export default function OwnerVaultDetail() {
    const { vaultId } = useParams();
    const location = useLocation();
    const user = getUser();
    const [vault, setVault] = useState(null);      // VaultDetailResponse
    const [error, setError] = useState('');

    const reload = useCallback(() => {
        ownerApi.getVault(vaultId)
            .then(setVault)
            .catch((e) => setError(getErrorMessage(e, 'Vault not found.')));
    }, [vaultId]);

    useEffect(() => { reload(); }, [reload]);

    if (!user) return <Navigate to="/login" replace />;

    // V7: Chỉ sửa được khi vault.modifiable = true
    const editable = vault?.modifiable ?? (vault?.status === 'ACTIVE');

    return (
        <OwnerLayout
            title={vault?.name || 'Vault'}
            subtitle={vault ? `Created ${formatDate(vault.createdAt)} · ${vault.totalAssets} asset(s) · ${vault.totalDocuments} document(s)` : ''}
            actions={vault && (
                <span className="text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">{vault.status}</span>
            )}
        >
            {location.state?.created && <Alert type="success">Vault created. Now choose who will receive it.</Alert>}
            <Alert>{error}</Alert>
            {!vault && !error && <p className="text-on-surface-variant">Loading...</p>}
            {vault && !editable && (
                <Alert type="warning">{vault.lockReason || `This vault is ${vault.status.toLowerCase()} — it can no longer be changed.`}</Alert>
            )}

            {vault && (
                <div className="space-y-6">
                    <DetailsSection vault={vault} editable={editable} onSaved={reload} />
                    <AssetSection vaultId={vaultId} editable={editable} onChanged={reload} />
                    <BeneficiarySection vaultId={vaultId} current={vault.beneficiaryEmail} editable={editable} onSaved={reload} />
                    <DocumentSection vaultId={vaultId} editable={editable} onChanged={reload} />
                    <ActivitySection vaultId={vaultId} />
                    {editable && <DeleteSection vaultId={vaultId} />}
                </div>
            )}
        </OwnerLayout>
    );
}

/* ---------- Tên / mô tả (FR-27) ---------- */
function DetailsSection({ vault, editable, onSaved }) {
    const [editing, setEditing] = useState(false);
    const [name, setName] = useState(vault.name);
    const [description, setDescription] = useState(vault.description || '');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const save = async (e) => {
        e.preventDefault();
        if (!name.trim()) return setError('Name is required.');
        setError(''); setLoading(true);
        try {
            await ownerApi.updateVault(vault.id, { name: name.trim(), description: description.trim() || null });
            setEditing(false);
            onSaved();
        } catch (err) {
            setError(getErrorMessage(err, 'Could not save.'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title="Details" icon="info">
            <Alert>{error}</Alert>
            {!editing ? (
                <div className="flex flex-wrap justify-between gap-4">
                    <dl className="grid sm:grid-cols-2 gap-x-8 gap-y-3 text-sm flex-1">
                        <div className="sm:col-span-2">
                            <dt className="text-outline">Description / hand-over instructions</dt>
                            <dd className="whitespace-pre-wrap">{vault.description || '—'}</dd>
                        </div>
                        <div><dt className="text-outline">Last updated</dt><dd>{formatDate(vault.updatedAt, true)}</dd></div>
                        {vault.unlockedAt && <div><dt className="text-outline">Unlocked</dt><dd>{formatDate(vault.unlockedAt, true)}</dd></div>}
                        {vault.claimDeadlineAt && <div><dt className="text-outline">Claim deadline</dt><dd>{formatDate(vault.claimDeadlineAt)}</dd></div>}
                    </dl>
                    {editable && (
                        <button onClick={() => setEditing(true)} className={btnOutline}><Icon name="edit" className="text-[18px]" /> Edit</button>
                    )}
                </div>
            ) : (
                <form onSubmit={save} className="space-y-4">
                    <label className="block">
                        <span className="text-sm font-semibold text-on-surface-variant">Name *</span>
                        <input value={name} maxLength={100} onChange={(e) => setName(e.target.value)} className={`${inputClass} mt-1`} />
                    </label>
                    <label className="block">
                        <span className="text-sm font-semibold text-on-surface-variant">Description</span>
                        <textarea rows={3} maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} className={textareaCls} />
                    </label>
                    <div className="flex gap-3">
                        <button type="button" onClick={() => setEditing(false)} className={btnOutline}>Cancel</button>
                        <button type="submit" disabled={loading} className={btnPrimary}>{loading ? 'Saving...' : 'Save'}</button>
                    </div>
                </form>
            )}
        </Card>
    );
}

/* ---------- Tài sản số (FR-27) ---------- */
function AssetSection({ vaultId, editable, onChanged }) {
    const [assets, setAssets] = useState([]);
    const [error, setError] = useState('');
    const [viewing, setViewing] = useState(null);   // chi tiết đã giải mã
    const [form, setForm] = useState(null);         // null | { id?, assetType, assetName, secret, notes }
    const [deleting, setDeleting] = useState(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        ownerApi.listAssets(vaultId).then(setAssets).catch((e) => setError(getErrorMessage(e, 'Could not load assets.')));
    }, [vaultId]);

    useEffect(() => { load(); }, [load]);

    const view = async (a) => {
        setError('');
        try { setViewing(await ownerApi.getAsset(vaultId, a.id)); }
        catch (e) { setError(getErrorMessage(e, 'Could not open this asset.')); }
    };

    // Sửa: lấy chi tiết để điền sẵn tên/ghi chú; secret để trống = giữ nguyên
    const edit = async (a) => {
        setError('');
        try {
            const d = await ownerApi.getAsset(vaultId, a.id);
            setForm({ id: a.id, assetType: d.assetType, assetName: d.assetName, secret: '', notes: d.notes || '' });
        } catch (e) {
            setError(getErrorMessage(e, 'Could not open this asset.'));
        }
    };

    const remove = async () => {
        setBusy(true); setError('');
        try {
            await ownerApi.deleteAsset(vaultId, deleting.id);
            setDeleting(null);
            load(); onChanged();
        } catch (e) {
            setError(getErrorMessage(e, 'Could not delete.'));
            setDeleting(null);
        } finally {
            setBusy(false);
        }
    };

    return (
        <Card title={`Assets (${assets.length})`} icon="key">
            <Alert>{error}</Alert>
            {assets.length === 0 && <p className="text-sm text-on-surface-variant mb-4">No assets yet.</p>}
            {assets.length > 0 && (
                <ul className="divide-y divide-outline-variant/30 mb-4">
                    {assets.map((a) => (
                        <li key={a.id} className="py-3 flex items-center gap-3">
                            <div className="flex-1 min-w-0">
                                <p className="font-semibold truncate">{a.assetName}</p>
                                <p className="text-xs text-outline">{assetLabel(a.assetType)} · updated {formatDate(a.updatedAt)}</p>
                            </div>
                            <button onClick={() => view(a)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">View</button>
                            {editable && (
                                <>
                                    <button onClick={() => edit(a)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">Edit</button>
                                    <button onClick={() => setDeleting(a)} className="h-9 px-3 rounded-lg text-sm font-semibold text-error hover:bg-error-container">Delete</button>
                                </>
                            )}
                        </li>
                    ))}
                </ul>
            )}
            {editable && (
                <button onClick={() => setForm({ assetType: 'BANK_ACCOUNT', assetName: '', secret: '', notes: '' })} className={btnOutline}>
                    <Icon name="add" className="text-[18px]" /> Add asset
                </button>
            )}

            {viewing && <AssetViewModal asset={viewing} onClose={() => setViewing(null)} />}
            {form && (
                <AssetFormModal
                    vaultId={vaultId}
                    initial={form}
                    onClose={() => setForm(null)}
                    onSaved={() => { setForm(null); load(); onChanged(); }}
                />
            )}
            <ConfirmDialog
                open={!!deleting}
                danger
                title="Delete this asset?"
                message={`"${deleting?.assetName}" will be permanently deleted.`}
                confirmLabel="Delete"
                busy={busy}
                onConfirm={remove}
                onCancel={() => setDeleting(null)}
            />
        </Card>
    );
}

function Modal({ children, onClose }) {
    return (
        <div className="fixed inset-0 z-40 bg-black/40 flex items-center justify-center px-4" onClick={onClose}>
            <div className="bg-surface-container-lowest rounded-2xl p-6 w-full max-w-lg max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                {children}
            </div>
        </div>
    );
}

function AssetViewModal({ asset, onClose }) {
    const [reveal, setReveal] = useState(false);
    return (
        <Modal onClose={onClose}>
            <div className="flex justify-between items-start">
                <div>
                    <p className="text-xs text-outline">{assetLabel(asset.assetType)}</p>
                    <h3 className="font-title-lg text-title-lg">{asset.assetName}</h3>
                </div>
                <button onClick={onClose}><Icon name="close" className="text-outline" /></button>
            </div>
            <p className="mt-5 text-sm font-semibold text-on-surface-variant">Secret</p>
            <div className="mt-1 flex items-center gap-2 p-3 rounded-xl bg-surface-container-low font-mono text-sm break-all">
                <span className="flex-1">{reveal ? asset.secret : '••••••••••••'}</span>
                <button onClick={() => setReveal(!reveal)}><Icon name={reveal ? 'visibility_off' : 'visibility'} className="text-[20px] text-outline" /></button>
            </div>
            {asset.notes && (
                <>
                    <p className="mt-4 text-sm font-semibold text-on-surface-variant">Notes</p>
                    <p className="mt-1 text-sm whitespace-pre-wrap">{asset.notes}</p>
                </>
            )}
            <p className="mt-5 text-xs text-outline">Every view is recorded in the vault's activity log.</p>
        </Modal>
    );
}

function AssetFormModal({ vaultId, initial, onClose, onSaved }) {
    const isEdit = !!initial.id;
    const [f, setF] = useState(initial);
    const [show, setShow] = useState(false);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }));

    const submit = async (e) => {
        e.preventDefault();
        setError('');
        if (!f.assetName.trim()) return setError('Asset name is required.');
        if (!isEdit && !f.secret.trim()) return setError('Secret is required for a new asset.');
        setLoading(true);
        try {
            const body = {
                assetType: f.assetType,
                assetName: f.assetName.trim(),
                secret: f.secret ? f.secret : null,        // sửa: để trống = giữ nguyên secret cũ
                notes: isEdit ? f.notes.trim() : (f.notes.trim() || null), // sửa: "" = xoá ghi chú
            };
            if (isEdit) await ownerApi.updateAsset(vaultId, f.id, body);
            else await ownerApi.addAsset(vaultId, body);
            onSaved();
        } catch (err) {
            setError(getErrorMessage(err, 'Could not save.'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal onClose={onClose}>
            <h3 className="font-title-lg text-title-lg mb-4">{isEdit ? 'Edit asset' : 'Add asset'}</h3>
            <Alert>{error}</Alert>
            <form onSubmit={submit} className="space-y-3">
                <select value={f.assetType} onChange={set('assetType')} className={inputClass}>
                    {ASSET_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
                <input value={f.assetName} maxLength={200} onChange={set('assetName')} placeholder="Asset name" className={inputClass} />
                <div className="relative">
                    <input
                        type={show ? 'text' : 'password'} value={f.secret} maxLength={5000} onChange={set('secret')} autoComplete="new-password"
                        placeholder={isEdit ? 'New secret (leave empty to keep current)' : 'Secret'} className={`${inputClass} pr-12`}
                    />
                    <button type="button" onClick={() => setShow(!show)} className="absolute right-3 top-1/2 -translate-y-1/2 text-outline">
                        <Icon name={show ? 'visibility_off' : 'visibility'} className="text-[20px]" />
                    </button>
                </div>
                <textarea rows={3} maxLength={2000} value={f.notes} onChange={set('notes')} placeholder="Notes for the beneficiary (optional)" className={textareaCls} />
                <div className="flex justify-end gap-3 pt-2">
                    <button type="button" onClick={onClose} className={btnOutline}>Cancel</button>
                    <button type="submit" disabled={loading} className={btnPrimary}>{loading ? 'Saving...' : 'Save'}</button>
                </div>
            </form>
        </Modal>
    );
}

/* ---------- FR-02: người thụ hưởng ---------- */
function BeneficiarySection({ vaultId, current, editable, onSaved }) {
    const [email, setEmail] = useState('');
    const [result, setResult] = useState(null);
    const [error, setError] = useState('');
    const [confirm, setConfirm] = useState(null);
    const [removing, setRemoving] = useState(false);
    const [loading, setLoading] = useState(false);

    const submit = async (confirmReplace = false) => {
        setError(''); setLoading(true);
        try {
            setResult(await ownerApi.assignBeneficiary(vaultId, email.trim(), confirmReplace));
            setConfirm(null); setEmail('');
            onSaved();
        } catch (err) {
            if (needsReplaceConfirm(err)) setConfirm(getErrorMessage(err));
            else setError(getErrorMessage(err, 'Could not assign the beneficiary.'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title="Beneficiary" icon="family_restroom">
            <div className="flex justify-between items-center mb-4">
                <p className="text-sm">
                    Current: <b>{current || 'Not assigned'}</b>
                </p>
                {current && editable && (
                    <button
                        type="button"
                        onClick={() => setRemoving(true)}
                        disabled={loading}
                        className="text-xs font-semibold text-error hover:underline"
                    >
                        Remove
                    </button>
                )}
            </div>
            <Alert>{error}</Alert>
            {result && (
                <Alert type="success">
                    {result.replaced ? 'Beneficiary replaced: ' : 'Beneficiary set: '}
                    <b>{result.beneficiaryName || result.beneficiaryEmail}</b>
                    {result.pendingRegistration && ' — invitation sent, they will be linked after registering.'}
                </Alert>
            )}
            {editable && (
                <form onSubmit={(e) => { e.preventDefault(); if (email.trim()) submit(false); }} className="flex flex-col sm:flex-row gap-3">
                    <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                           placeholder={current ? 'New beneficiary email' : 'beneficiary@example.com'} className={inputClass} />
                    <button type="submit" disabled={loading} className={`${btnPrimary} shrink-0`}>
                        {loading ? 'Saving...' : current ? 'Replace' : 'Assign'}
                    </button>
                </form>
            )}
            <ConfirmDialog
                open={removing}
                danger
                title="Remove beneficiary?"
                message="Are you sure you want to remove the beneficiary from this vault?"
                confirmLabel="Remove"
                busy={loading}
                onConfirm={async () => {
                    setLoading(true); setError('');
                    try {
                        await ownerApi.removeBeneficiary(vaultId);
                        setRemoving(false);
                        onSaved();
                    } catch (err) {
                        setError(getErrorMessage(err, 'Could not remove beneficiary.'));
                    } finally {
                        setLoading(false);
                    }
                }}
                onCancel={() => setRemoving(false)}
            />
            <ConfirmDialog
                open={!!confirm}
                title="Replace beneficiary?"
                message={confirm}
                confirmLabel="Replace"
                busy={loading}
                onConfirm={() => submit(true)}
                onCancel={() => setConfirm(null)}
            />
        </Card>
    );
}

/* ---------- FR-04: tài liệu pháp lý ---------- */
const ALLOWED = ['.pdf', '.doc', '.docx', '.jpg', '.jpeg', '.png'];
const MAX_SIZE = 10 * 1024 * 1024;

function DocumentSection({ vaultId, editable, onChanged }) {
    const [docs, setDocs] = useState([]);
    const [type, setType] = useState('WILL');
    const [file, setFile] = useState(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [deleting, setDeleting] = useState(null);

    const load = useCallback(() => {
        ownerApi.listDocuments(vaultId).then(setDocs).catch((e) => setError(getErrorMessage(e, 'Could not load documents.')));
    }, [vaultId]);

    useEffect(() => { load(); }, [load]);

    const upload = async (e) => {
        e.preventDefault();
        setError('');
        if (!file) return setError('Please choose a file.');
        if (!ALLOWED.some((ext) => file.name.toLowerCase().endsWith(ext))) return setError('Only PDF, DOC/DOCX, JPG/PNG are allowed.');
        if (file.size > MAX_SIZE) return setError('File exceeds the 10MB limit.');
        setLoading(true);
        try {
            await ownerApi.uploadDocument(vaultId, type, file);
            setFile(null); e.target.reset();
            load(); onChanged();
        } catch (err) {
            setError(getErrorMessage(err, 'Upload failed.'));
        } finally {
            setLoading(false);
        }
    };

    const download = async (d) => {
        setError('');
        try { await ownerApi.downloadDocument(d.id, d.fileName); }
        catch (err) { setError(getErrorMessage(err, 'Download failed.')); }
    };

    const remove = async () => {
        setLoading(true); setError('');
        try {
            await ownerApi.deleteDocument(deleting.id);
            setDeleting(null);
            load(); onChanged();
        } catch (err) {
            setError(getErrorMessage(err, 'Could not delete.')); // 409: đang nằm trong hồ sơ xác minh
            setDeleting(null);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title={`Legal documents (${docs.length})`} icon="description">
            <Alert>{error}</Alert>
            {docs.length > 0 && (
                <ul className="divide-y divide-outline-variant/30 mb-5">
                    {docs.map((d) => (
                        <li key={d.id} className="py-3 flex items-center gap-3 text-sm">
                            <Icon name="description" className="text-secondary" />
                            <div className="flex-1 min-w-0">
                                <p className="font-semibold truncate">{d.fileName}</p>
                                <p className="text-xs text-outline">{humanize(d.documentType)} · {formatDate(d.uploadedAt, true)}</p>
                            </div>
                            {!d.archived ? (
                                <button onClick={() => download(d)} className="h-9 px-3 rounded-lg font-semibold text-primary hover:bg-surface-container-low flex items-center gap-1">
                                    <Icon name="download" className="text-[18px]" />
                                </button>
                            ) : (
                                <span className="text-xs text-outline px-2 py-1 bg-surface-container-low rounded">Archived</span>
                            )}
                            {editable && (
                                <button onClick={() => setDeleting(d)} className="h-9 px-3 rounded-lg font-semibold text-error hover:bg-error-container">Delete</button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
            {editable && (
                <>
                    <p className="text-sm text-on-surface-variant mb-3">PDF, DOC/DOCX, JPG/PNG — up to 10MB. Files are encrypted before storage.</p>
                    <form onSubmit={upload} className="grid sm:grid-cols-[180px_1fr_auto] gap-3 items-center">
                        <select value={type} onChange={(e) => setType(e.target.value)} className={inputClass}>
                            {DOCUMENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                        </select>
                        <input type="file" accept={ALLOWED.join(',')} onChange={(e) => setFile(e.target.files?.[0] || null)}
                               className="text-sm file:mr-3 file:h-10 file:px-4 file:rounded-lg file:border-0 file:bg-surface-container file:font-semibold" />
                        <button type="submit" disabled={loading} className={btnPrimary}>{loading ? 'Uploading...' : 'Upload'}</button>
                    </form>
                </>
            )}
            <ConfirmDialog
                open={!!deleting}
                danger
                title="Delete this document?"
                message={`"${deleting?.fileName}" will be permanently deleted.`}
                confirmLabel="Delete"
                busy={loading}
                onConfirm={remove}
                onCancel={() => setDeleting(null)}
            />
        </Card>
    );
}

/* ---------- Nhật ký hoạt động (mặc định 30 ngày gần nhất) ---------- */
function ActivitySection({ vaultId }) {
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        setError('');
        ownerApi.getActivityLogs(vaultId, { page, size: 10 })
            .then(setData)
            .catch((err) => setError(getErrorMessage(err, 'Could not load activity.')));
    }, [vaultId, page]);

    const rows = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;

    return (
        <Card title="Activity log (last 30 days)" icon="history">
            <Alert>{error}</Alert>
            {!data && !error && <p className="text-sm text-on-surface-variant">Loading...</p>}
            {data && rows.length === 0 && <p className="text-sm text-on-surface-variant">No activity yet.</p>}
            {rows.length > 0 && (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <thead className="text-on-surface-variant">
                        <tr><th className="py-2 pr-4">Time</th><th className="py-2 pr-4">Action</th><th className="py-2 pr-4">By</th><th className="py-2">Details</th></tr>
                        </thead>
                        <tbody>
                        {rows.map((r) => (
                            <tr key={r.id} className="border-t border-outline-variant/20">
                                <td className="py-2 pr-4 whitespace-nowrap">{formatDate(r.createdAt, true)}</td>
                                <td className="py-2 pr-4 font-semibold">{humanize(r.action)}</td>
                                <td className="py-2 pr-4">{r.actorName || '—'}</td>
                                <td className="py-2 text-on-surface-variant">{r.description || '—'}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            {totalPages > 1 && (
                <div className="flex justify-between items-center mt-4 text-sm">
                    <button onClick={() => setPage((p) => p - 1)} disabled={page === 0} className="font-semibold text-secondary disabled:text-outline">← Previous</button>
                    <span className="text-on-surface-variant">Page {page + 1} / {totalPages}</span>
                    <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= totalPages} className="font-semibold text-secondary disabled:text-outline">Next →</button>
                </div>
            )}
        </Card>
    );
}

/* ---------- Xoá vault ---------- */
function DeleteSection({ vaultId }) {
    const navigate = useNavigate();
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const remove = async () => {
        setLoading(true); setError('');
        try {
            await ownerApi.deleteVault(vaultId);
            navigate('/owner/vaults');
        } catch (err) {
            setError(getErrorMessage(err, 'Could not delete the vault.'));
            setOpen(false);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title="Danger zone" icon="warning">
            <Alert>{error}</Alert>
            <p className="text-sm text-on-surface-variant mb-4">Delete this vault and all its assets and documents.</p>
            <button onClick={() => setOpen(true)} className={btnDanger}><Icon name="delete" className="text-[18px]" /> Delete vault</button>
            <ConfirmDialog
                open={open}
                danger
                title="Delete this vault?"
                message="This cannot be undone."
                confirmLabel="Delete"
                busy={loading}
                onConfirm={remove}
                onCancel={() => setOpen(false)}
            />
            <p className="mt-4 text-xs text-outline"><Link to="/owner/vaults" className="text-secondary font-semibold">← Back to my vaults</Link></p>
        </Card>
    );
}
