import React, { useCallback, useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi, needsReplaceConfirm } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import {
    OwnerLayout, Card, Icon, Alert, ConfirmDialog, formatDate, humanize,
    DOCUMENT_TYPES, inputClass, btnPrimary, btnDanger,
} from './ownerUi.jsx';

export default function OwnerVaultDetail() {
    const { vaultId } = useParams();
    const location = useLocation();
    const user = getUser();
    const vault = ownerApi.getLocalVault(vaultId); // có thể null nếu vault tạo ở máy khác

    if (!user) return <Navigate to="/login" replace />;

    return (
        <OwnerLayout
            title={vault?.name || 'Vault'}
            subtitle={vault ? `Created ${formatDate(vault.createdAt)} · ${vault.status}` : `ID ${vaultId}`}
        >
            {location.state?.created && <Alert type="success">Vault created. Now choose who will receive it.</Alert>}
            <div className="space-y-6">
                <BeneficiarySection vaultId={vaultId} />
                <DocumentSection vaultId={vaultId} />
                <ActivitySection vaultId={vaultId} />
                <DeleteSection vaultId={vaultId} />
            </div>
        </OwnerLayout>
    );
}

/* ---------- FR-02: người thụ hưởng ---------- */
function BeneficiarySection({ vaultId }) {
    const [email, setEmail] = useState('');
    const [result, setResult] = useState(null);
    const [error, setError] = useState('');
    const [confirm, setConfirm] = useState(null); // message 409 cần xác nhận thay thế
    const [loading, setLoading] = useState(false);

    const submit = async (confirmReplace = false) => {
        setError(''); setLoading(true);
        try {
            setResult(await ownerApi.assignBeneficiary(vaultId, email.trim(), confirmReplace));
            setConfirm(null);
        } catch (err) {
            if (needsReplaceConfirm(err)) setConfirm(getErrorMessage(err));
            else setError(getErrorMessage(err, 'Could not assign the beneficiary.'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title="Beneficiary" icon="family_restroom">
            <p className="text-sm text-on-surface-variant mb-4">
                The person who receives this vault. If they don't have an account yet, they will get an invitation email.
            </p>
            <Alert>{error}</Alert>
            {result && (
                <Alert type="success">
                    {result.replaced ? 'Beneficiary replaced: ' : 'Beneficiary set: '}
                    <b>{result.beneficiaryName || result.beneficiaryEmail}</b>
                    {result.pendingRegistration && ' — invitation sent, they will be linked after registering.'}
                </Alert>
            )}
            <form onSubmit={(e) => { e.preventDefault(); if (email.trim()) submit(false); }} className="flex flex-col sm:flex-row gap-3">
                <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="beneficiary@example.com" className={inputClass} />
                <button type="submit" disabled={loading} className={`${btnPrimary} shrink-0`}>{loading ? 'Saving...' : 'Assign'}</button>
            </form>
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

function DocumentSection({ vaultId }) {
    const [type, setType] = useState('WILL');
    const [file, setFile] = useState(null);
    const [uploaded, setUploaded] = useState([]);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        setError('');
        if (!file) return setError('Please choose a file.');
        if (!ALLOWED.some((ext) => file.name.toLowerCase().endsWith(ext))) return setError('Only PDF, DOC/DOCX, JPG/PNG are allowed.');
        if (file.size > MAX_SIZE) return setError('File exceeds the 10MB limit.');
        setLoading(true);
        try {
            const doc = await ownerApi.uploadDocument(vaultId, type, file);
            setUploaded((l) => [doc, ...l]);
            setFile(null);
            e.target.reset();
        } catch (err) {
            setError(getErrorMessage(err, 'Upload failed.'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title="Legal documents" icon="upload_file">
            <p className="text-sm text-on-surface-variant mb-4">PDF, DOC/DOCX, JPG/PNG — up to 10MB. Files are encrypted before storage.</p>
            <Alert>{error}</Alert>
            <form onSubmit={submit} className="grid sm:grid-cols-[180px_1fr_auto] gap-3 items-center">
                <select value={type} onChange={(e) => setType(e.target.value)} className={inputClass}>
                    {DOCUMENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
                <input type="file" accept={ALLOWED.join(',')} onChange={(e) => setFile(e.target.files?.[0] || null)}
                    className="text-sm file:mr-3 file:h-10 file:px-4 file:rounded-lg file:border-0 file:bg-surface-container file:font-semibold" />
                <button type="submit" disabled={loading} className={btnPrimary}>{loading ? 'Uploading...' : 'Upload'}</button>
            </form>
            {uploaded.length > 0 && (
                <ul className="mt-5 divide-y divide-outline-variant/30 border-t border-outline-variant/30">
                    {uploaded.map((d) => (
                        <li key={d.id} className="py-3 flex items-center gap-3 text-sm">
                            <Icon name="description" className="text-secondary" />
                            <span className="flex-1 truncate font-semibold">{d.fileName}</span>
                            <span className="text-outline">{humanize(d.documentType)} · {formatDate(d.uploadedAt, true)}</span>
                        </li>
                    ))}
                </ul>
            )}
        </Card>
    );
}

/* ---------- Nhật ký hoạt động ---------- */
function ActivitySection({ vaultId }) {
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');

    const load = useCallback(() => {
        setError('');
        ownerApi.getActivityLogs(vaultId, { page, size: 10 })
            .then(setData)
            .catch((err) => setError(getErrorMessage(err, 'Could not load activity.')));
    }, [vaultId, page]);

    useEffect(() => { load(); }, [load]);

    const rows = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;

    return (
        <Card title="Activity log" icon="history">
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

/* ---------- Xoá vault (chỉ khi ACTIVE) ---------- */
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
            <p className="text-sm text-on-surface-variant mb-4">Delete this vault and all its assets. Only possible while the vault is active.</p>
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
        </Card>
    );
}
