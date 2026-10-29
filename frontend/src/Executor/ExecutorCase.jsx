import React, { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { getErrorMessage } from '../api/http.js';
import { executorApi, caseStep, HANDOVER_LABEL } from '../api/executorApi.js';
// Dùng lại thành phần giao diện chung của module Owner
import { Card, Icon, Alert, formatDate, ASSET_TYPES, inputClass, btnPrimary, btnOutline } from '../Owner/ownerUi.jsx';

const FILE_TYPES = '.pdf,.png,.jpg,.jpeg';
const MAX_SIZE = 10 * 1024 * 1024;
const assetLabel = (t) => ASSET_TYPES.find((a) => a.value === t)?.label || t;

/* =====================================================================
   Route: /executor/cases/:assignmentId
   FR-07 acknowledge → FR-08 xem vault → FR-09 nộp giấy chứng tử → FR-10 tiến độ → FR-11 trao đổi
   ===================================================================== */
export default function ExecutorCase() {
    const { assignmentId } = useParams();
    const user = getUser();

    const [assignment, setAssignment] = useState(null);
    const [data, setData] = useState(null);         // { ownerFullName, vaults[] }
    const [progress, setProgress] = useState([]);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [acking, setAcking] = useState(false);

    const load = useCallback(async () => {
        setError('');
        try {
            const mine = await executorApi.listMine();
            const a = mine.find((x) => x.assignmentId === assignmentId);
            if (!a) { setError('Case not found.'); return; }
            setAssignment(a);
            if (a.status !== 'ACTIVE') return; // INACTIVE: chưa được xem gì
            const [v, p] = await Promise.all([executorApi.getVaults(assignmentId), executorApi.getProgress(assignmentId)]);
            setData(v);
            setProgress(p);
        } catch (e) {
            setError(getErrorMessage(e, 'Could not load this case.'));
        } finally {
            setLoading(false);
        }
    }, [assignmentId]);

    useEffect(() => { load(); }, [load]);

    if (!user) return <Navigate to="/login" replace />;

    const acknowledge = async () => {
        setAcking(true); setError('');
        try {
            const res = await executorApi.acknowledge(assignmentId);
            setAssignment((a) => ({ ...a, acknowledgedAt: res.acknowledgedAt }));
        } catch (e) {
            setError(getErrorMessage(e, 'Could not acknowledge.'));
        } finally {
            setAcking(false);
        }
    };

    const step = assignment ? caseStep(assignment, progress) : 0;

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <header className="bg-surface-container-lowest border-b border-outline-variant/30">
                <div className="max-w-5xl mx-auto h-16 px-5 flex items-center gap-3">
                    <Link to="/executor/home" className="p-2 rounded-full hover:bg-surface-container-high text-on-surface-variant">
                        <Icon name="arrow_back" />
                    </Link>
                    <div className="min-w-0">
                        <h1 className="font-headline-sm text-primary font-bold truncate">
                            Estate of {assignment?.ownerFullName || data?.ownerFullName || '…'}
                        </h1>
                        {assignment && (
                            <p className="text-xs text-on-surface-variant">
                                {assignment.executorType === 'PRIMARY' ? 'Primary executor' : 'Backup executor'} · activated {formatDate(assignment.activatedAt, true)}
                            </p>
                        )}
                    </div>
                </div>
            </header>

            <main className="max-w-5xl mx-auto px-5 py-8 space-y-6">
                <Alert>{error}</Alert>
                {loading && <p className="text-on-surface-variant">Loading...</p>}

                {assignment && assignment.status !== 'ACTIVE' && (
                    <Alert type="info">This estate is on standby. You can act once the owner's Dead Man's Switch is triggered.</Alert>
                )}

                {assignment?.status === 'ACTIVE' && (
                    <>
                        {/* Bước 1: acknowledge */}
                        {step === 0 && (
                            <Card>
                                <div className="flex flex-wrap items-center justify-between gap-4">
                                    <div>
                                        <h2 className="font-title-lg text-title-lg">You have been activated</h2>
                                        <p className="text-sm text-on-surface-variant mt-1">Please confirm you received the activation notice and will handle this estate.</p>
                                    </div>
                                    <button onClick={acknowledge} disabled={acking} className={btnPrimary}>
                                        <Icon name="task_alt" className="text-[18px]" /> {acking ? 'Saving...' : 'Acknowledge'}
                                    </button>
                                </div>
                            </Card>
                        )}

                        {data && <VaultList vaults={data.vaults} progress={progress} />}

                        {data && (
                            <div id="submit-verification">
                                <SubmitVerification
                                    assignmentId={assignmentId}
                                    vaults={data.vaults}
                                    progress={progress}
                                    onSubmitted={load}
                                />
                            </div>
                        )}

                        <ProgressSection progress={progress} />

                        <MessagesSection assignmentId={assignmentId} progress={progress} />
                    </>
                )}
            </main>
        </div>
    );
}

/* ---------- FR-08: vault và tài sản (chỉ metadata) ---------- */
function VaultList({ vaults, progress }) {
    return (
        <Card title={`Vaults (${vaults.length})`} icon="inventory_2">
            {vaults.length === 0 && <p className="text-sm text-on-surface-variant">The owner has no vaults.</p>}
            <div className="space-y-4">
                {vaults.map((v) => {
                    const p = latestProgress(progress, v.vaultId);
                    const badge = p && HANDOVER_LABEL[p.handoverStatus];
                    return (
                        <div key={v.vaultId} className="p-4 rounded-xl border border-outline-variant/40">
                            <div className="flex flex-wrap justify-between items-start gap-2">
                                <div>
                                    <p className="font-semibold">{v.name}</p>
                                    <p className="text-sm text-on-surface-variant">
                                        Beneficiary: {v.beneficiary?.fullName || (v.beneficiaryPending ? 'Invited, not registered yet' : 'Not assigned')}
                                    </p>
                                </div>
                                {badge
                                    ? <span className={`text-xs font-semibold px-3 py-1 rounded-full ${badge.cls}`}>{badge.label}</span>
                                    : <span className="text-xs font-semibold px-3 py-1 rounded-full bg-surface-container text-outline">{v.status}</span>}
                            </div>
                            {v.handoverInstructions && (
                                <p className="mt-3 text-sm p-3 rounded-lg bg-surface-container-low">
                                    <b>Owner's instructions:</b> {v.handoverInstructions}
                                </p>
                            )}
                            {v.assets?.length > 0 && (
                                <div className="mt-3 flex flex-wrap gap-2">
                                    {v.assets.map((a) => (
                                        <span key={a.assetId} className="text-xs px-3 py-1.5 rounded-full bg-surface-container text-on-surface-variant">
                                            {a.assetName} · {assetLabel(a.assetType)}
                                        </span>
                                    ))}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </Card>
    );
}

// Bản ghi tiến độ mới nhất của 1 vault (1 vault có thể đã bị từ chối rồi nộp lại)
function latestProgress(progress, vaultId) {
    return progress
        .filter((p) => p.vaultId === vaultId)
        .sort((a, b) => new Date(b.requestedAt || 0) - new Date(a.requestedAt || 0))[0];
}

/* ---------- FR-09: nộp giấy chứng tử ---------- */
function SubmitVerification({ assignmentId, vaults, progress, onSubmitted }) {
    // Chỉ nộp được cho vault ACTIVE và chưa nằm trong hồ sơ đang chờ / đã duyệt
    const eligible = vaults.filter((v) => {
        const p = latestProgress(progress, v.vaultId);
        return v.status === 'ACTIVE' && (!p || p.handoverStatus === 'REJECTED');
    });

    const [selected, setSelected] = useState([]);
    const [file, setFile] = useState(null);
    const [notes, setNotes] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => { setSelected(eligible.map((v) => v.vaultId)); }, [vaults, progress]); // eslint-disable-line react-hooks/exhaustive-deps

    if (eligible.length === 0) return null;

    const toggle = (id) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

    const submit = async (e) => {
        e.preventDefault();
        setError(''); setSuccess('');
        if (selected.length === 0) return setError('Select at least one vault.');
        if (!file) return setError('Please attach the death certificate.');
        if (!/\.(pdf|png|jpe?g)$/i.test(file.name)) return setError('Only PDF, PNG or JPG files are allowed.');
        if (file.size > MAX_SIZE) return setError('File exceeds the 10MB limit.');
        if (notes.length > 1000) return setError('Notes must be at most 1000 characters.');
        setLoading(true);
        try {
            await executorApi.submitVerification(assignmentId, { file, vaultIds: selected, notes: notes.trim() });
            setSuccess('Submitted. A Legal Verifier will review your request.');
            setFile(null); setNotes(''); e.target.reset();
            onSubmitted();
        } catch (err) {
            setError(getErrorMessage(err, 'Submission failed.'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title="Submit death certificate" icon="upload_file">
            <p className="text-sm text-on-surface-variant mb-4">A Legal Verifier reviews the certificate before any vault is unlocked. PDF, PNG or JPG, up to 10MB.</p>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            <form onSubmit={submit} className="space-y-4">
                <div className="space-y-2">
                    {eligible.map((v) => (
                        <label key={v.vaultId} className="flex items-center gap-3 text-sm">
                            <input type="checkbox" checked={selected.includes(v.vaultId)} onChange={() => toggle(v.vaultId)} />
                            {v.name}
                            {latestProgress(progress, v.vaultId)?.handoverStatus === 'REJECTED' && <span className="text-xs text-error">(previously rejected)</span>}
                        </label>
                    ))}
                </div>
                <input type="file" accept={FILE_TYPES} onChange={(e) => setFile(e.target.files?.[0] || null)}
                    className="text-sm file:mr-3 file:h-10 file:px-4 file:rounded-lg file:border-0 file:bg-surface-container file:font-semibold" />
                <textarea rows={2} maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Notes for the verifier (optional)"
                    className="w-full px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary" />
                <button type="submit" disabled={loading} className={btnPrimary}>{loading ? 'Submitting...' : 'Submit for verification'}</button>
            </form>
        </Card>
    );
}

/* ---------- FR-10: tiến độ bàn giao ---------- */
function ProgressSection({ progress }) {
    if (progress.length === 0) return null;
    return (
        <Card title="Hand-over progress" icon="timeline">
            <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                    <thead className="text-on-surface-variant">
                        <tr><th className="py-2 pr-4">Vault</th><th className="py-2 pr-4">Status</th><th className="py-2 pr-4">Submitted</th><th className="py-2 pr-4">Decided</th><th className="py-2">Details</th></tr>
                    </thead>
                    <tbody>
                        {progress.map((p) => {
                            const b = HANDOVER_LABEL[p.handoverStatus] || {};
                            return (
                                <tr key={`${p.vaultId}-${p.requestId}`} className="border-t border-outline-variant/20">
                                    <td className="py-2 pr-4 font-semibold">{p.vaultName}</td>
                                    <td className="py-2 pr-4"><span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full whitespace-nowrap ${b.cls}`}>{b.label || p.handoverStatus}</span></td>
                                    <td className="py-2 pr-4 whitespace-nowrap">{formatDate(p.requestedAt)}</td>
                                    <td className="py-2 pr-4 whitespace-nowrap">{formatDate(p.decidedAt)}</td>
                                    <td className="py-2 text-on-surface-variant">
                                        {p.rejectionReason && `Reason: ${p.rejectionReason}`}
                                        {p.unlockedAt && `Unlocked ${formatDate(p.unlockedAt)} · claim before ${formatDate(p.claimDeadlineAt)}`}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </Card>
    );
}

/* ---------- FR-11: trao đổi với Legal Verifier ---------- */
function MessagesSection({ assignmentId, progress }) {
    // Mỗi yêu cầu xác minh = 1 luồng trao đổi
    const requests = [...new Map(progress.filter((p) => p.requestId).map((p) => [p.requestId, p])).values()];
    const [requestId, setRequestId] = useState('');
    const [messages, setMessages] = useState([]);
    const [content, setContent] = useState('');
    const [file, setFile] = useState(null);
    const [error, setError] = useState('');
    const [sending, setSending] = useState(false);

    useEffect(() => {
        if (!requestId && requests.length) setRequestId(requests[0].requestId);
    }, [requests.length]); // eslint-disable-line react-hooks/exhaustive-deps

    const loadMessages = useCallback(() => {
        if (!requestId) return;
        executorApi.getMessages(assignmentId, requestId)
            .then(setMessages)
            .catch((e) => setError(getErrorMessage(e, 'Could not load messages.')));
    }, [assignmentId, requestId]);

    useEffect(() => { loadMessages(); }, [loadMessages]);

    if (requests.length === 0) return null;
    const selectedRequest = requests.find((r) => r.requestId === requestId);

    const send = async (e) => {
        e.preventDefault();
        setError('');
        if (!content.trim() && !file) return setError('Write a message or attach a file.');
        if (file && (!/\.(pdf|png|jpe?g)$/i.test(file.name) || file.size > MAX_SIZE)) return setError('Attachment must be PDF/PNG/JPG up to 10MB.');
        setSending(true);
        try {
            await executorApi.sendMessage(assignmentId, requestId, { content: content.trim(), file });
            setContent(''); setFile(null); e.target.reset();
            loadMessages();
        } catch (err) {
            setError(getErrorMessage(err, 'Could not send.'));
        } finally {
            setSending(false);
        }
    };

    return (
        <Card title="Messages with Legal Verifier" icon="forum">
            {requests.length > 1 && (
                <select value={requestId} onChange={(e) => setRequestId(e.target.value)} className={`${inputClass} mb-4`}>
                    {requests.map((r) => (
                        <option key={r.requestId} value={r.requestId}>
                            Request of {formatDate(r.requestedAt)} — {HANDOVER_LABEL[r.handoverStatus]?.label || r.handoverStatus}
                        </option>
                    ))}
                </select>
            )}
            <Alert>{error}</Alert>
            <div className="space-y-3 max-h-80 overflow-y-auto mb-4">
                {messages.length === 0 && <p className="text-sm text-on-surface-variant">No messages yet.</p>}
                {messages.map((m) => {
                    const mine = m.senderRole === 'EXECUTOR';
                    return (
                        <div key={m.id} className={`max-w-[80%] p-3 rounded-xl text-sm ${mine ? 'ml-auto bg-primary-fixed' : 'bg-surface-container-low'}`}>
                            <p className="text-xs text-outline mb-1">{mine ? 'You' : m.senderName || 'Legal Verifier'} · {formatDate(m.createdAt, true)}</p>
                            {m.content && <p className="whitespace-pre-wrap">{m.content}</p>}
                            {m.attachmentName && (
                                <p className="mt-1 text-xs flex items-center gap-1"><Icon name="attach_file" className="text-[16px]" />{m.attachmentName}</p>
                            )}
                        </div>
                    );
                })}
            </div>
            {/* B1: hồ sơ REJECTED đã đóng (backend trả 409 khi nhắn), nên mời nộp hồ sơ mới thay cho ô nhắn tin */}
            {selectedRequest?.handoverStatus === 'REJECTED' ? (
                <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-xl bg-surface-container-low">
                    <p className="text-sm text-on-surface-variant">This request was rejected and is closed for messages.</p>
                    <button type="button" className={btnPrimary}
                        onClick={() => document.getElementById('submit-verification')?.scrollIntoView({ behavior: 'smooth' })}>
                        <Icon name="upload_file" className="text-[18px]" /> Submit a new request
                    </button>
                </div>
            ) : (
            <form onSubmit={send} className="space-y-3">
                <textarea rows={2} maxLength={2000} value={content} onChange={(e) => setContent(e.target.value)} placeholder="Write a message..."
                    className="w-full px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary" />
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <input type="file" accept={FILE_TYPES} onChange={(e) => setFile(e.target.files?.[0] || null)}
                        className="text-sm file:mr-3 file:h-9 file:px-3 file:rounded-lg file:border-0 file:bg-surface-container file:font-semibold" />
                    <button type="submit" disabled={sending} className={btnOutline}>
                        <Icon name="send" className="text-[18px]" /> {sending ? 'Sending...' : 'Send'}
                    </button>
                </div>
            </form>
            )}
        </Card>
    );
}
