import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useParams } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { getErrorMessage } from '../api/http.js';
import { executorApi, caseStep, HANDOVER_CLASS, CLAIM_PROGRESS_CLASS } from '../api/executorApi.js';
import AppHeader, { PAGE_WIDTH, PageTitle } from '../Common/AppHeader.jsx';
import NotificationBell from '../Beneficiary/NotificationBell.jsx';
import FilePicker from '../Common/FilePicker.jsx';
import OcrAnalysisCard from '../Common/OcrAnalysisCard.jsx';
// Dùng lại thành phần giao diện chung của module Owner
import { Card, Icon, Alert, formatDate, inputClass, btnPrimary, btnOutline } from '../Owner/ownerUi.jsx';
import { tEnum } from '../i18n/format.js';
import { showBlobInTab, getBlobErrorMessage } from '../LegalVerifier/blobUtils.js';
import EmptyState from "../Common/EmptyState.jsx";
import VerificationBadge from '../Common/VerificationBadge.jsx';

const FILE_TYPES = '.pdf,.png,.jpg,.jpeg';
const MAX_SIZE = 10 * 1024 * 1024;
// F5: trạng thái checklist (nhãn trong executor.json -> taskStatus)
const TASK_STATUSES = ['TODO', 'DOING', 'DONE'];

/* =====================================================================
   Route: /executor/cases/:assignmentId
   FR-07 acknowledge → FR-08 xem vault → FR-09 nộp giấy chứng tử → FR-10 tiến độ → FR-11 trao đổi
   ===================================================================== */
export default function ExecutorCase() {
    const { t } = useTranslation('executor');
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
            if (!a) { setError(t('case.notFound')); return; }
            setAssignment(a);
            if (a.status !== 'ACTIVE') return; // INACTIVE: chưa được xem gì
            const [v, p] = await Promise.all([executorApi.getVaults(assignmentId), executorApi.getProgress(assignmentId)]);
            setData(v);
            setProgress(p);
        } catch (e) {
            setError(getErrorMessage(e, t('case.loadError')));
        } finally {
            setLoading(false);
        }
    }, [assignmentId, t]);

    useEffect(() => { load(); }, [load]);

    if (!user) return <Navigate to="/login" replace />;

    const acknowledge = async () => {
        setAcking(true); setError('');
        try {
            const res = await executorApi.acknowledge(assignmentId);
            setAssignment((a) => ({ ...a, acknowledgedAt: res.acknowledgedAt }));
        } catch (e) {
            setError(getErrorMessage(e, t('ackError')));
        } finally {
            setAcking(false);
        }
    };

    const step = assignment ? caseStep(assignment, progress) : 0;

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader workspace={t('workspace')} home="/executor/home" userHome extra={<NotificationBell source="executor" />}/>

            <main className={`${PAGE_WIDTH} py-10 space-y-6`}>
                <PageTitle
                    className=""
                    back={{ to: '/executor/home', label: t('case.back') }}
                    title={t('estateOf', { name: assignment?.ownerFullName || assignment?.ownerEmail || data?.ownerFullName || '…' })}
                    subtitle={assignment && t('case.subtitle', { type: t(`type.${assignment.executorType}`, assignment.executorType), date: formatDate(assignment.activatedAt, true) })}
                />
                <Alert>{error}</Alert>
                {loading && <p className="text-on-surface-variant">{t('loading')}</p>}

                {assignment && assignment.status !== 'ACTIVE' && (
                    <Alert type="info">{t('case.standby')}</Alert>
                )}

                {assignment?.status === 'ACTIVE' && (
                    <>
                        {/* Bước 1: acknowledge */}
                        {step === 0 && (
                            <Card>
                                <div className="flex flex-wrap items-center justify-between gap-4">
                                    <div>
                                        <h2 className="font-title-lg text-title-lg">{t('case.activatedTitle')}</h2>
                                        <p className="text-sm text-on-surface-variant mt-1">{t('case.activatedText')}</p>
                                    </div>
                                    <button onClick={acknowledge} disabled={acking} className={btnPrimary}>
                                        <Icon name="task_alt" className="text-[18px]" /> {acking ? t('saving') : t('acknowledge')}
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

                        <BeneficiaryClaimsSection assignmentId={assignmentId} progress={progress} />

                        <AccountTasksSection assignmentId={assignmentId} progress={progress} />

                        <MessagesSection assignmentId={assignmentId} progress={progress} />
                    </>
                )}
            </main>
        </div>
    );
}

/* ---------- FR-08: vault và tài sản (chỉ metadata) ---------- */
function VaultList({ vaults, progress }) {
    const { t } = useTranslation('executor');
    return (
        <Card title={t('case.vaults.title', { count: vaults.length })} icon="inventory_2">
            {vaults.length === 0 && <p className="text-sm text-on-surface-variant">{t('case.vaults.none')}</p>}
            <div className="space-y-4">
                {vaults.map((v) => {
                    const p = latestProgress(progress, v.vaultId);
                    const badge = p && { cls: HANDOVER_CLASS[p.handoverStatus], label: t(`handover.${p.handoverStatus}`, p.handoverStatus) };
                    return (
                        <div key={v.vaultId} className="p-4 rounded-xl border border-outline-variant/40">
                            <div className="flex flex-wrap justify-between items-start gap-2">
                                <div>
                                    <p className="font-semibold">{v.name}</p>
                                    <p className="text-sm text-on-surface-variant">
                                        {t('case.vaults.beneficiary', { name: v.beneficiary?.fullName || (v.beneficiaryPending ? t('case.vaults.invited') : t('case.vaults.notAssigned')) })}
                                    </p>
                                </div>
                                {badge
                                    ? <span className={`text-xs font-semibold px-3 py-1 rounded-full ${badge.cls}`}>{badge.label}</span>
                                    : <span className="text-xs font-semibold px-3 py-1 rounded-full bg-surface-container text-outline">{v.status}</span>}
                            </div>
                            {v.handoverInstructions && (
                                <p className="mt-3 text-sm p-3 rounded-lg bg-surface-container-low">
                                    <b>{t('case.vaults.instructions')}</b> {v.handoverInstructions}
                                </p>
                            )}
                            {v.assets?.length > 0 && (
                                <div className="mt-3 flex flex-wrap gap-2">
                                    {v.assets.map((a) => (
                                        <span key={a.assetId} className="text-xs pl-3 pr-1.5 py-1 rounded-full bg-surface-container text-on-surface-variant inline-flex items-center gap-2">
                                            {a.assetName} · {tEnum('assetType', a.assetType)}
                                            <VerificationBadge status={a.verificationStatus || 'SELF_DECLARED'} method={a.verificationMethod} />
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

/* ---------- FR-09: nộp giấy chứng tử + AI OCR ---------- */
function SubmitVerification({ assignmentId, vaults, progress, onSubmitted }) {
    const { t } = useTranslation('executor');
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
    const [ocrLoading, setOcrLoading] = useState(false);
    const [ocrResult, setOcrResult] = useState(null);

    useEffect(() => { setSelected(eligible.map((v) => v.vaultId)); }, [vaults, progress]); // eslint-disable-line react-hooks/exhaustive-deps

    if (eligible.length === 0) return null;

    const toggle = (id) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

    const handleFileSelect = (nextFile) => {
        setFile(nextFile);
        setOcrResult(null);
        setError('');
    };

    const handleOcrScan = async () => {
        if (!file) return setError(t('case.submit.errFile'));
        if (!/\.(pdf|png|jpe?g)$/i.test(file.name)) return setError(t('case.submit.errType'));
        if (file.size > MAX_SIZE) return setError(t('case.submit.errSize'));
        setError('');
        setOcrLoading(true);
        try {
            const res = await executorApi.analyzeDeathCertificate(assignmentId, file);
            setOcrResult(res);
        } catch (err) {
            setError(getErrorMessage(err, t('case.submit.ocrFailed', 'Không thể phân tích tài liệu bằng AI OCR.')));
        } finally {
            setOcrLoading(false);
        }
    };

    const submit = async (e) => {
        e.preventDefault();
        setError(''); setSuccess('');
        if (selected.length === 0) return setError(t('case.submit.errSelect'));
        if (!file) return setError(t('case.submit.errFile'));
        if (!/\.(pdf|png|jpe?g)$/i.test(file.name)) return setError(t('case.submit.errType'));
        if (file.size > MAX_SIZE) return setError(t('case.submit.errSize'));
        if (notes.length > 1000) return setError(t('case.submit.errNotes'));
        setLoading(true);
        try {
            await executorApi.submitVerification(assignmentId, { file, vaultIds: selected, notes: notes.trim() });
            setSuccess(t('case.submit.success'));
            setFile(null); setNotes(''); setOcrResult(null);
            onSubmitted();
        } catch (err) {
            setError(getErrorMessage(err, t('case.submit.failed')));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title={t('case.submit.title')} icon="upload_file">
            <p className="text-sm text-on-surface-variant mb-4">{t('case.submit.intro')}</p>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            <form onSubmit={submit} className="space-y-4">
                <div className="space-y-2">
                    {eligible.map((v) => (
                        <label key={v.vaultId} className="flex items-center gap-3 text-sm">
                            <input type="checkbox" checked={selected.includes(v.vaultId)} onChange={() => toggle(v.vaultId)} />
                            {v.name}
                            {latestProgress(progress, v.vaultId)?.handoverStatus === 'REJECTED' && <span className="text-xs text-error">{t('case.submit.previouslyRejected')}</span>}
                        </label>
                    ))}
                </div>
                <div className="flex flex-wrap items-center gap-3">
                    <FilePicker file={file} onChange={handleFileSelect} accept={FILE_TYPES}
                                label={t('case.submit.chooseFile')} hint={t('case.submit.fileHint')} />
                    {file && (
                        <button
                            type="button"
                            data-testid="executor-ocr-btn"
                            onClick={handleOcrScan}
                            disabled={ocrLoading || loading}
                            className="inline-flex items-center gap-2 rounded-xl border border-primary/40 bg-primary/10 px-4 py-2.5 text-sm font-semibold text-primary hover:bg-primary/20 transition-all disabled:opacity-50 cursor-pointer"
                        >
                            <Icon name={ocrLoading ? 'progress_activity' : 'auto_awesome'} className={`text-[18px] ${ocrLoading ? 'animate-spin' : ''}`} />
                            {ocrLoading
                                ? t('case.submit.ocrScanning')
                                : t('case.submit.ocrScan')}
                        </button>
                    )}
                </div>
                {file && (
                    <p className="text-xs text-on-surface-variant">
                        {t('case.submit.ocrPrivacyHint')}
                    </p>
                )}

                {ocrResult && (
                    <OcrAnalysisCard
                        data={ocrResult}
                        role="executor"
                        onApplyNote={(summary) => setNotes(summary.slice(0, 1000))}
                    />
                )}

                <textarea rows={2} maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t('case.submit.notesPlaceholder')}
                    className="w-full px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary" />
                <button type="submit" disabled={loading} className={btnPrimary}>{loading ? t('case.submit.submitting') : t('case.submit.button')}</button>
            </form>
        </Card>
    );
}

/* ---------- FR-10: tiến độ bàn giao ---------- */
function ProgressSection({ progress }) {
    const { t } = useTranslation('executor');
    if (progress.length === 0) return null;
    return (
        <Card title={t('case.progress.title')} icon="timeline">
            <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                    <thead className="text-on-surface-variant">
                        <tr><th className="py-2 pr-4">{t('case.progress.vault')}</th><th className="py-2 pr-4">{t('case.progress.status')}</th><th className="py-2 pr-4">{t('case.progress.submitted')}</th><th className="py-2 pr-4">{t('case.progress.decided')}</th><th className="py-2">{t('case.progress.details')}</th></tr>
                    </thead>
                    <tbody>
                        {progress.map((p) => {
                            const cls = HANDOVER_CLASS[p.handoverStatus];
                            return (
                                <tr key={`${p.vaultId}-${p.requestId}`} className="border-t border-outline-variant/20">
                                    <td className="py-2 pr-4 font-semibold">{p.vaultName}</td>
                                    <td className="py-2 pr-4"><span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full whitespace-nowrap ${cls}`}>{t(`handover.${p.handoverStatus}`, p.handoverStatus)}</span></td>
                                    <td className="py-2 pr-4 whitespace-nowrap">{formatDate(p.requestedAt)}</td>
                                    <td className="py-2 pr-4 whitespace-nowrap">{formatDate(p.decidedAt)}</td>
                                    <td className="py-2 text-on-surface-variant">
                                        {p.rejectionReason && t('case.progress.reason', { reason: p.rejectionReason })}
                                        {p.unlockedAt && t('case.progress.unlocked', { date: formatDate(p.unlockedAt), deadline: formatDate(p.claimDeadlineAt) })}
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

/* ---------- N2-exec: người thụ hưởng đã nhận tài sản chưa + nhắc nhở ---------- */
function BeneficiaryClaimsSection({ assignmentId, progress }) {
    const { t } = useTranslation('executor');
    // Chỉ vault đã mở khoá trở đi mới có người thụ hưởng phản hồi
    const vaults = [...new Map(progress
        .filter((p) => ['UNLOCKED', 'CLAIMED', 'ARCHIVED_LOCKED'].includes(p.handoverStatus))
        .map((p) => [p.vaultId, p])).values()];
    if (vaults.length === 0) return null;
    return (
        <Card title={t('case.claims.title')} icon="group">
            <div className="space-y-6">
                {vaults.map((p) => <VaultClaims key={p.vaultId} assignmentId={assignmentId} vault={p} />)}
            </div>
        </Card>
    );
}

function VaultClaims({ assignmentId, vault }) {
    const { t } = useTranslation('executor');
    const [rows, setRows] = useState(null);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [busyId, setBusyId] = useState(null);

    useEffect(() => {
        executorApi.getBeneficiaryProgress(assignmentId, vault.vaultId)
            .then(setRows)
            .catch((e) => { setError(getErrorMessage(e, t('case.claims.loadError'))); setRows([]); });
    }, [assignmentId, vault.vaultId, t]);

    const remind = async (row) => {
        setBusyId(row.vaultBeneficiaryId); setError(''); setNotice('');
        try {
            const updated = await executorApi.remindBeneficiary(assignmentId, vault.vaultId, row.vaultBeneficiaryId);
            setRows((prev) => prev.map((r) => (r.vaultBeneficiaryId === updated.vaultBeneficiaryId ? updated : r)));
            setNotice(t('case.claims.sent', { name: row.fullName || row.maskedEmail }));
        } catch (e) {
            setError(getErrorMessage(e, t('case.claims.sendError')));
        } finally {
            setBusyId(null);
        }
    };

    return (
        <div>
            <h3 className="font-semibold mb-2">{vault.vaultName}</h3>
            <Alert>{error}</Alert>
            {notice && <Alert type="info">{notice}</Alert>}
            {rows === null && <p className="text-sm text-on-surface-variant">{t('loading')}</p>}
            {rows?.length === 0 && !error && <p className="text-sm text-on-surface-variant">{t('case.claims.none')}</p>}
            {rows?.length > 0 && (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <thead className="text-on-surface-variant">
                            <tr><th className="py-2 pr-4">{t('case.claims.beneficiary')}</th><th className="py-2 pr-4">{t('case.claims.status')}</th><th className="py-2 pr-4">{t('case.claims.responded')}</th><th className="py-2">{t('case.claims.reminder')}</th></tr>
                        </thead>
                        <tbody>
                            {rows.map((r) => {
                                const cls = CLAIM_PROGRESS_CLASS[r.status];
                                return (
                                    <tr key={r.vaultBeneficiaryId} className="border-t border-outline-variant/20">
                                        <td className="py-2 pr-4">
                                            <p className="font-semibold">{r.fullName || '—'}</p>
                                            <p className="text-xs text-on-surface-variant">{r.maskedEmail}{r.relationship && ` · ${r.relationship}`}</p>
                                        </td>
                                        <td className="py-2 pr-4"><span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full whitespace-nowrap ${cls}`}>{t(`claim.${r.status}`, r.status)}</span></td>
                                        <td className="py-2 pr-4 whitespace-nowrap">{formatDate(r.claimedAt || r.respondedAt)}</td>
                                        <td className="py-2">
                                            {r.canRemind && (
                                                <button type="button" onClick={() => remind(r)} disabled={busyId === r.vaultBeneficiaryId} className={btnOutline}>
                                                    <Icon name="notifications_active" className="text-[18px]" /> {busyId === r.vaultBeneficiaryId ? t('case.claims.sending') : t('case.claims.send')}
                                                </button>
                                            )}
                                            {!r.canRemind && r.nextReminderAllowedAt && (
                                                <span className="text-xs text-on-surface-variant">{t('case.claims.nextAfter', { date: formatDate(r.nextReminderAllowedAt, true) })}</span>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}

/* ---------- F5: checklist xử lý tài khoản số theo di nguyện ---------- */
function AccountTasksSection({ assignmentId, progress }) {
    const { t } = useTranslation('executor');
    const [tasks, setTasks] = useState(null);
    const [error, setError] = useState('');
    // Checklist chỉ có sau khi có vault đã mở khoá
    const handedOver = progress.some((p) => ['UNLOCKED', 'CLAIMED', 'ARCHIVED_LOCKED'].includes(p.handoverStatus));

    useEffect(() => {
        if (!handedOver) return;
        executorApi.getTasks(assignmentId)
            .then(setTasks)
            .catch((e) => { setError(getErrorMessage(e, t('case.tasks.loadError'))); setTasks([]); });
    }, [assignmentId, handedOver, t]);

    if (!handedOver || (tasks?.length === 0 && !error)) return null;
    const done = (tasks || []).filter((x) => x.status === 'DONE').length;
    return (
        <Card title={t('case.tasks.title')} icon="checklist">
            <p className="text-sm text-on-surface-variant mb-4">
                {t('case.tasks.intro')}
                {tasks && ` ${t('case.tasks.done', { done, total: tasks.length })}`}
            </p>
            <Alert>{error}</Alert>
            {tasks === null && <p className="text-sm text-on-surface-variant">{t('loading')}</p>}
            <div className="space-y-4">
                {(tasks || []).map((task) => (
                    <AccountTaskRow key={task.id} assignmentId={assignmentId} task={task}
                                    onSaved={(u) => setTasks((prev) => prev.map((x) => (x.id === u.id ? u : x)))} />
                ))}
            </div>
        </Card>
    );
}

// C.6: màu khung trạng thái xác nhận chéo
const CONFIRM_CLS = {
    PENDING: 'bg-warning-container text-on-warning-container',
    CONFIRMED: 'bg-success-container text-on-success-container',
    DISPUTED: 'bg-error-container text-on-error-container',
    autoConfirmed: 'bg-success-container text-on-success-container',
};

function AccountTaskRow({ assignmentId, task, onSaved }) {
    const { t } = useTranslation(['executor', 'verification']);
    const [status, setStatus] = useState(task.status);
    const [note, setNote] = useState(task.note || '');
    const [evidenceRef, setEvidenceRef] = useState(task.evidenceRef || '');
    const [file, setFile] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    // Người thụ hưởng đã xác nhận (hoặc quá hạn phản đối): khóa hẳn, BE cũng chặn
    const locked = task.confirmationStatus === 'CONFIRMED' || task.autoConfirmed;
    // Đã báo DONE thì phải chuyển về "Đang làm" và lưu trước khi đổi bằng chứng (BE yêu cầu)
    const evidenceLocked = locked || task.status === 'DONE';
    const confirmKey = task.autoConfirmed ? 'autoConfirmed' : task.confirmationStatus;
    const dirty = status !== task.status || note !== (task.note || '')
        || evidenceRef.trim() !== (task.evidenceRef || '') || !!file;

    const save = async () => {
        setError('');
        if (file && file.size > MAX_SIZE) return setError(t('verification:executor.fileTooBig'));
        const hasEvidence = !!evidenceRef.trim() || !!task.evidenceRef || task.hasEvidenceDocument || !!file;
        if (status === 'DONE' && !hasEvidence) return setError(t('verification:executor.needEvidence'));
        setBusy(true);
        try {
            // Tải tệp trước; lỗi thì dừng, chưa đổi trạng thái
            if (file) {
                onSaved(await executorApi.uploadTaskEvidence(assignmentId, task.id, file));
                setFile(null);
            }
            const body = { status, note };
            if (evidenceRef.trim()) body.evidenceRef = evidenceRef.trim();
            onSaved(await executorApi.updateTask(assignmentId, task.id, body));
        } catch (e) {
            setError(getErrorMessage(e, t('case.tasks.saveError')));
        } finally {
            setBusy(false);
        }
    };

    const download = async () => {
        setError('');
        try {
            await executorApi.downloadTaskEvidence(assignmentId, task.id, task.evidenceFileName);
        } catch (e) {
            setError(await getBlobErrorMessage(e, t('verification:executor.downloadError')));
        }
    };

    return (
        <div className="rounded-xl border border-outline-variant/30 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                    <p className="font-semibold">{task.platform || tEnum('assetType', task.assetType)}{task.accountHandle && ` · ${task.accountHandle}`}</p>
                    <p className="text-sm text-on-surface-variant">{task.vaultName} · {t('case.tasks.wish')} <span className="font-semibold">{t(`wish.${task.wish}`, task.wish)}</span></p>
                    {task.helpUrl && (
                        <a href={task.helpUrl} target="_blank" rel="noopener noreferrer" className="text-sm text-primary font-semibold inline-flex items-center gap-1 mt-1">
                            <Icon name="open_in_new" className="text-[16px]" /> {t('case.tasks.help')}
                        </a>
                    )}
                </div>
                <select value={status} disabled={locked} onChange={(e) => setStatus(e.target.value)} className={`${inputClass} w-auto`}>
                    {TASK_STATUSES.map((v) => <option key={v} value={v}>{t(`taskStatus.${v}`)}</option>)}
                </select>
            </div>

            {confirmKey && (
                <p className={`mt-3 text-sm px-3 py-2 rounded-lg ${CONFIRM_CLS[confirmKey] || ''}`}>
                    {t(`verification:confirm.${confirmKey}`, { date: formatDate(task.confirmDeadlineAt), reason: task.disputeReason })}
                </p>
            )}
            {locked && <p className="mt-2 text-xs text-outline">{t('verification:executor.locked')}</p>}

            <div className="mt-3 space-y-3">
                <label className="block">
                    <span className="text-sm font-semibold text-on-surface-variant">{t('verification:executor.refLabel')}</span>
                    <input value={evidenceRef} maxLength={200} disabled={evidenceLocked} onChange={(e) => setEvidenceRef(e.target.value)}
                           placeholder={t('verification:executor.refPlaceholder')} className={`${inputClass} mt-1`} />
                </label>
                <div>
                    <p className="text-sm font-semibold text-on-surface-variant">{t('verification:executor.fileLabel')}</p>
                    {task.hasEvidenceDocument && (
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-sm">
                            <span className="font-medium">{t('verification:executor.fileCurrent', { name: task.evidenceFileName })}</span>
                            {task.evidenceSha256 && <span className="text-xs text-outline font-mono">{t('verification:executor.sha', { hash: task.evidenceSha256.slice(0, 16) })}</span>}
                            <button type="button" onClick={download} className="text-primary font-semibold inline-flex items-center gap-1">
                                <Icon name="download" className="text-[16px]" /> {t('verification:executor.download')}
                            </button>
                        </div>
                    )}
                    <div className="mt-1">
                        <FilePicker file={file} onChange={setFile} accept={FILE_TYPES} disabled={evidenceLocked} hint={t('verification:executor.fileHint')} />
                    </div>
                    {task.status === 'DONE' && !locked && <p className="mt-1 text-xs text-outline">{t('verification:executor.editHint')}</p>}
                </div>
            </div>

            <textarea rows={2} maxLength={1000} value={note} disabled={locked} onChange={(e) => setNote(e.target.value)}
                      placeholder={t('case.tasks.notePlaceholder')} className={`${inputClass} mt-3`} />
            <Alert>{error}</Alert>
            <div className="flex justify-end mt-2">
                <button type="button" onClick={save} disabled={!dirty || busy || locked} className={btnPrimary}>
                    <Icon name="save" className="text-[18px]" /> {busy ? t('saving') : t('case.tasks.save')}
                </button>
            </div>
        </div>
    );
}

/* ---------- FR-11: trao đổi với Legal Verifier ---------- */
function MessagesSection({ assignmentId, progress }) {
    const { t } = useTranslation('executor');
    // Mỗi yêu cầu xác minh = 1 luồng trao đổi
    const requests = [...new Map(progress.filter((p) => p.requestId).map((p) => [p.requestId, p])).values()];
    const [requestId, setRequestId] = useState('');
    const [messages, setMessages] = useState([]);
    const [page, setPage] = useState(0);
    const [hasOlder, setHasOlder] = useState(false);
    const [downloadingId, setDownloadingId] = useState(null);
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
            .then(({ messages: list, last }) => { setMessages(list); setPage(0); setHasOlder(!last); })
            .catch((e) => setError(getErrorMessage(e, t('case.messages.loadError'))));
    }, [assignmentId, requestId, t]);

    // Nối trang cũ hơn lên đầu; bỏ trùng theo id vì tin mới đến sau lần tải trước làm các trang lệch đi
    const loadOlder = () => {
        executorApi.getMessages(assignmentId, requestId, { page: page + 1 })
            .then(({ messages: older, last }) => {
                setMessages((cur) => [...older.filter((m) => !cur.some((c) => c.id === m.id)), ...cur]);
                setPage(page + 1);
                setHasOlder(!last);
            })
            .catch((e) => setError(getErrorMessage(e, t('case.messages.loadError'))));
    };

    useEffect(() => { loadMessages(); }, [loadMessages]);

    // Mở tab ngay trong lúc click (trước await) để trình duyệt không chặn pop-up, giống trang Verifier
    const openAttachment = async (messageId) => {
        setError('');
        const tab = window.open('', '_blank');
        try {
            const res = await executorApi.getAttachmentBlob(assignmentId, requestId, messageId);
            if (tab) showBlobInTab(tab, res.data);
            else setError(t('case.messages.popupBlocked'));
        } catch (err) {
            if (tab) tab.close();
            setError(await getBlobErrorMessage(err, t('case.messages.openError')));
        }
    };

    if (requests.length === 0) return null;
    const selectedRequest = requests.find((r) => r.requestId === requestId);

    const downloadAttachment = async (m) => {
        setError('');
        setDownloadingId(m.id);
        try {
            await executorApi.downloadAttachment(assignmentId, requestId, m.id, m.attachmentName);
        } catch (err) {
            setError(getErrorMessage(err, t('case.messages.downloadError')));
        } finally {
            setDownloadingId(null);
        }
    };

    const send = async (e) => {
        e.preventDefault();
        setError('');
        if (!content.trim() && !file) return setError(t('case.messages.errEmpty'));
        if (file && (!/\.(pdf|png|jpe?g)$/i.test(file.name) || file.size > MAX_SIZE)) return setError(t('case.messages.errFile'));
        setSending(true);
        try {
            await executorApi.sendMessage(assignmentId, requestId, { content: content.trim(), file });
            setContent(''); setFile(null);
            loadMessages();
        } catch (err) {
            setError(getErrorMessage(err, t('case.messages.sendError')));
        } finally {
            setSending(false);
        }
    };

    return (
        <Card title={t('case.messages.title')} icon="forum">
            {requests.length > 1 && (
                <select value={requestId} onChange={(e) => setRequestId(e.target.value)} className={`${inputClass} mb-4`}>
                    {requests.map((r) => (
                        <option key={r.requestId} value={r.requestId}>
                            {t('case.messages.requestOf', { date: formatDate(r.requestedAt), status: t(`handover.${r.handoverStatus}`, r.handoverStatus) })}
                        </option>
                    ))}
                </select>
            )}
            <Alert>{error}</Alert>
            <div className="space-y-3 max-h-80 overflow-y-auto mb-4">
                {hasOlder && (
                    <button type="button" onClick={loadOlder} className="block mx-auto text-xs font-semibold text-primary hover:underline">
                        {t('case.messages.loadOlder')}
                    </button>
                )}
                {messages.length === 0 && (
                    <EmptyState compact icon="forum" title={t('case.messages.emptyTitle')}
                                text={t('case.messages.emptyText')} />
                )}
                {messages.map((m) => {
                    const mine = m.senderRole === 'EXECUTOR';
                    return (
                        <div key={m.id} className={`max-w-[80%] p-3 rounded-xl text-sm ${mine ? 'ml-auto bg-primary-fixed' : 'bg-surface-container-low'}`}>
                            <p className="text-xs text-outline mb-1">{mine ? t('case.messages.you') : m.senderName || t('case.messages.verifier')} · {formatDate(m.createdAt, true)}</p>
                            {m.content && <p className="whitespace-pre-wrap">{m.content}</p>}
                            {m.attachmentName && (
                                <div className="mt-2 flex items-center gap-1.5 max-w-full">
                                    <button type="button" onClick={() => openAttachment(m.id)}
                                            title={t('case.messages.view')} aria-label={t('case.messages.viewNamed', { name: m.attachmentName })}
                                            className="inline-flex items-center justify-center h-8 w-8 shrink-0 rounded-lg border border-outline-variant bg-surface-container-lowest text-primary hover:bg-surface-container">
                                        <Icon name="visibility" className="text-[16px]" />
                                    </button>
                                    <button type="button" onClick={() => downloadAttachment(m)} disabled={downloadingId === m.id}
                                            title={t('case.messages.download')} aria-label={t('case.messages.downloadNamed', { name: m.attachmentName })}
                                            className="inline-flex items-center gap-1.5 min-w-0 h-8 px-2.5 rounded-lg border border-outline-variant bg-surface-container-lowest text-xs font-semibold text-primary hover:bg-surface-container disabled:opacity-60">
                                        <Icon name={downloadingId === m.id ? 'hourglass_top' : 'download'} className="text-[16px] shrink-0" />
                                        <span className="truncate">{m.attachmentName}</span>
                                    </button>
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
            {/* B1: hồ sơ REJECTED đã đóng (backend trả 409 khi nhắn), nên mời nộp hồ sơ mới thay cho ô nhắn tin */}
            {selectedRequest?.handoverStatus === 'REJECTED' ? (
                <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-xl bg-surface-container-low">
                    <p className="text-sm text-on-surface-variant">{t('case.messages.rejectedClosed')}</p>
                    <button type="button" className={btnPrimary}
                        onClick={() => document.getElementById('submit-verification')?.scrollIntoView({ behavior: 'smooth' })}>
                        <Icon name="upload_file" className="text-[18px]" /> {t('case.messages.newRequest')}
                    </button>
                </div>
            ) : (
            <form onSubmit={send} className="space-y-3">
                <textarea rows={2} maxLength={2000} value={content} onChange={(e) => setContent(e.target.value)} placeholder={t('case.messages.placeholder')}
                    className="w-full px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary" />
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <FilePicker file={file} onChange={setFile} accept={FILE_TYPES} label={t('case.messages.attach')} disabled={sending} />
                    <button type="submit" disabled={sending} className={btnOutline}>
                        <Icon name="send" className="text-[18px]" /> {sending ? t('case.messages.sending') : t('case.messages.send')}
                    </button>
                </div>
            </form>
            )}
        </Card>
    );
}
