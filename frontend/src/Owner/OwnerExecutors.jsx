import React, { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi, needsReplaceConfirm } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, ConfirmDialog, formatDate, inputClass, btnPrimary, btnOutline } from './ownerUi.jsx';

const POSITIONS = [
    { type: 'PRIMARY', title: 'Primary Executor', text: 'Notified first when your Dead Man\'s Switch triggers.' },
    { type: 'BACKUP', title: 'Backup Executor', text: 'Takes over if the primary does not respond within 14 days.' },
];

const STATUS = {
    INACTIVE: { label: 'Assigned', cls: 'bg-secondary-fixed text-on-secondary-fixed' },
    ACTIVE: { label: 'Activated', cls: 'bg-success-container text-on-success-container' },
};

export default function OwnerExecutors() {
    const user = getUser();
    const [list, setList] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const load = () =>
        ownerApi.listExecutors()
            .then((l) => setList(l ?? []))
            .catch((err) => setError(getErrorMessage(err, 'Could not load executors.')))
            .finally(() => setLoading(false));

    useEffect(() => { load(); }, []);

    if (!user) return <Navigate to="/login" replace />;

    // BE trả cả vị trí đã thu hồi -> mỗi loại lấy bản ghi chưa REVOKED (nếu có)
    const current = (type) => list.find((a) => a.executorType === type && a.status !== 'REVOKED') || null;

    return (
        <OwnerLayout title="Executors" subtitle="Trusted people who handle the hand-over. They must have a verified LegacyVault account.">
            <Alert>{error}</Alert>
            {loading ? <p className="text-on-surface-variant">Loading...</p> : (
                <div className="grid md:grid-cols-2 gap-6">
                    {POSITIONS.map((p) => (
                        <ExecutorCard key={p.type} position={p} assignment={current(p.type)} onChanged={load} />
                    ))}
                </div>
            )}
        </OwnerLayout>
    );
}

function ExecutorCard({ position, assignment, onChanged }) {
    const [editing, setEditing] = useState(false);
    const [email, setEmail] = useState('');
    const [confirmMsg, setConfirmMsg] = useState(null);
    const [revokeOpen, setRevokeOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const assign = async (confirmReplace = false) => {
        setError(''); setBusy(true);
        try {
            await ownerApi.assignExecutor(position.type, email.trim(), confirmReplace);
            setConfirmMsg(null); setEditing(false); setEmail('');
            onChanged();
        } catch (err) {
            if (needsReplaceConfirm(err)) setConfirmMsg(getErrorMessage(err));
            else setError(getErrorMessage(err, 'Could not assign the executor.'));
        } finally {
            setBusy(false);
        }
    };

    const revoke = async () => {
        setError(''); setBusy(true);
        try {
            await ownerApi.revokeExecutor(position.type);
            setRevokeOpen(false);
            onChanged();
        } catch (err) {
            setError(getErrorMessage(err, 'Could not revoke.'));
            setRevokeOpen(false);
        } finally {
            setBusy(false);
        }
    };

    const st = assignment && (STATUS[assignment.status] || STATUS.INACTIVE);

    return (
        <Card>
            <div className="flex items-start justify-between gap-3">
                <div>
                    <h2 className="font-title-lg text-title-lg">{position.title}</h2>
                    <p className="text-sm text-on-surface-variant mt-1">{position.text}</p>
                </div>
                {st && <span className={`shrink-0 text-xs font-semibold px-3 py-1 rounded-full ${st.cls}`}>{st.label}</span>}
            </div>

            <div className="mt-5"><Alert>{error}</Alert></div>

            {assignment && !editing && (
                <div className="flex items-center gap-3 p-4 rounded-xl bg-surface-container-low">
                    <div className="w-10 h-10 rounded-full bg-primary-fixed text-primary flex items-center justify-center"><Icon name="person" /></div>
                    <div className="min-w-0">
                        <p className="font-semibold truncate">{assignment.fullName || assignment.email}</p>
                        <p className="text-sm text-on-surface-variant truncate">{assignment.email}</p>
                        <p className="text-xs text-outline">
                            Since {formatDate(assignment.assignedAt)}
                            {assignment.activatedAt && ` · activated ${formatDate(assignment.activatedAt, true)}`}
                            {assignment.status === 'ACTIVE' && (assignment.acknowledgedAt
                                ? ` · acknowledged ${formatDate(assignment.acknowledgedAt, true)}`
                                : ' · awaiting acknowledgement')}
                        </p>
                    </div>
                </div>
            )}

            {!assignment && !editing && <p className="text-sm text-on-surface-variant">No one assigned.</p>}

            {editing ? (
                <form onSubmit={(e) => { e.preventDefault(); if (email.trim()) assign(false); }} className="mt-4 space-y-3">
                    <input type="email" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} placeholder="executor@example.com" className={inputClass} />
                    <div className="flex gap-3">
                        <button type="button" onClick={() => { setEditing(false); setError(''); }} className={btnOutline}>Cancel</button>
                        <button type="submit" disabled={busy} className={btnPrimary}>{busy ? 'Saving...' : 'Save'}</button>
                    </div>
                </form>
            ) : (
                <div className="mt-4 flex gap-3">
                    <button onClick={() => setEditing(true)} className={btnPrimary}>
                        <Icon name={assignment ? 'swap_horiz' : 'person_add'} className="text-[18px]" /> {assignment ? 'Replace' : 'Assign'}
                    </button>
                    {assignment && (
                        <button onClick={() => setRevokeOpen(true)} className={btnOutline}>Revoke</button>
                    )}
                </div>
            )}

            <ConfirmDialog
                open={!!confirmMsg}
                title={`Replace ${position.title.toLowerCase()}?`}
                message={confirmMsg}
                confirmLabel="Replace"
                busy={busy}
                onConfirm={() => assign(true)}
                onCancel={() => setConfirmMsg(null)}
            />
            <ConfirmDialog
                open={revokeOpen}
                danger
                title={`Revoke ${position.title.toLowerCase()}?`}
                message={`${assignment?.email} will no longer be able to handle your hand-over.`}
                confirmLabel="Revoke"
                busy={busy}
                onConfirm={revoke}
                onCancel={() => setRevokeOpen(false)}
            />
        </Card>
    );
}
