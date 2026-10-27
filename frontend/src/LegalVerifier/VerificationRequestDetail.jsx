import React, { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
    getRequestById, approveRequest, rejectRequest, verifyDocument, sendSignOtp, signRequest,
    getDocumentBlob, getRejectionReasons,
} from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import {
    STATUS_LABEL, STATUS_CLASS, formatDate, shortId, REJECT_REASON_OTHER,
} from './verifierUi.js';
import VerificationMessages from './VerificationMessages.jsx';
import VerifierHeader from './VerifierHeader.jsx';
import ConfirmDialog from './ConfirmDialog.jsx';
import { showBlobInTab, getBlobErrorMessage } from './blobUtils.js';

const RESEND_SECONDS = 60;          // khớp SIGN_OTP_RESEND_COOLDOWN_SECONDS của BE
const OTP_VALID_SECONDS = 5 * 60;   // khớp SIGN_OTP_EXPIRE_MINUTES của BE
const otpKey = (id) => `lv_sign_otp_${id}`;
// Thông báo của BE khi mã ký không còn dùng được (khớp InvalidSignCodeException trong sign)
const OTP_UNUSABLE = /no attempts left|too many attempts|has expired|was not generated/i;

// Nhớ thời điểm đã gửi mã ký, để tải lại trang không phải gửi lại (chỉ là tiện lợi, lỗi thì bỏ qua)
const rememberOtpSent = (id) => { try { sessionStorage.setItem(otpKey(id), String(Date.now())); } catch { /* ignore */ } };
const forgetOtpSent = (id) => { try { sessionStorage.removeItem(otpKey(id)); } catch { /* ignore */ } };
const secondsSinceOtpSent = (id) => {
    try {
        const t = Number(sessionStorage.getItem(otpKey(id)));
        return t ? Math.floor((Date.now() - t) / 1000) : null;
    } catch { return null; }
};

const CONFIRM_TEXT = {
    approve: {
        title: 'Approve this request?',
        message: 'You confirm the documents are valid. After approval you still need to sign with a one-time code to unlock the vault(s).',
        confirmText: 'Approve',
    },
    reject: {
        title: 'Reject this request?',
        message: 'This cannot be undone. Your reason will be saved with the request and shown to the Executor on their request page.',
        confirmText: 'Reject',
        danger: true,
    },
    sign: {
        title: 'Sign and unlock the vault(s)?',
        message: 'This cannot be undone. All vaults of this request will be unlocked for their beneficiaries.',
        confirmText: 'Sign and unlock',
    },
};

export default function VerificationRequestDetail() {
    const { requestId } = useParams();

    const [req, setReq] = useState(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [viewing, setViewing] = useState(false);
    const [checking, setChecking] = useState(false);
    const [error, setError] = useState('');
    const [info, setInfo] = useState('');
    const [rejectReasons, setRejectReasons] = useState([]);   // [{ code, label }] từ BE
    const [reasonsError, setReasonsError] = useState(false);
    const [rejectChoice, setRejectChoice] = useState('');   // '' | code lý do (OTHER = phải nhập ghi chú)
    const [rejectNote, setRejectNote] = useState('');
    const [docCheck, setDocCheck] = useState(null);   // { valid, message }
    const [codeSent, setCodeSent] = useState(false);
    const [code, setCode] = useState('');
    const [cooldown, setCooldown] = useState(0);
    const [confirm, setConfirm] = useState(null);     // null | 'approve' | 'reject' | 'sign'

    const load = useCallback(() => {
        return getRequestById(requestId)
            .then(setReq)
            .catch((e) => setError(getErrorMessage(e, 'Request not found.')));
    }, [requestId]);

    useEffect(() => { load().finally(() => setLoading(false)); }, [load]);

    // FR-12: lý do từ chối lấy từ BE; lỗi thì báo và cho thử lại
    const loadReasons = useCallback(() => {
        setReasonsError(false);
        getRejectionReasons().then(setRejectReasons).catch(() => setReasonsError(true));
    }, []);
    useEffect(() => { loadReasons(); }, [loadReasons]);

    // Khôi phục trạng thái "đã gửi mã" nếu vừa tải lại trang khi đang chờ ký
    useEffect(() => {
        if (req?.status !== 'APPROVED') return;
        const elapsed = secondsSinceOtpSent(req.id);
        if (elapsed !== null && elapsed < OTP_VALID_SECONDS) {
            setCodeSent(true);
            setCooldown(Math.max(0, RESEND_SECONDS - elapsed));
        }
    }, [req?.id, req?.status]);

    useEffect(() => {
        if (cooldown <= 0) return;
        const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(t);
    }, [cooldown]);

    // Chạy 1 hành động; 409 = hồ sơ đã được xử lý ở nơi khác -> tải lại trạng thái mới
    const run = async (action, fallback, onFail) => {
        setBusy(true); setError(''); setInfo('');
        try {
            const updated = await action();
            if (updated) setReq(updated);
            return true;
        } catch (e) {
            const message = getErrorMessage(e, fallback);
            setError(message);
            if (e.response?.status === 409) load();
            onFail?.(message);
            return false;
        } finally {
            setBusy(false);
        }
    };

    const rejectNoteText = rejectNote.trim();

    const askApprove = () => { setError(''); setConfirm('approve'); };

    const askReject = () => {
        if (!rejectChoice) return setError('Please select a rejection reason.');
        if (rejectChoice === REJECT_REASON_OTHER && !rejectNoteText) return setError('Please enter a note for the "other" reason.');
        if (rejectNoteText.length > 500) return setError('Note must be at most 500 characters.');
        setError('');
        setConfirm('reject');
    };

    const askSign = (e) => {
        e?.preventDefault();
        if (!/^\d{6}$/.test(code)) return setError('The code must be exactly 6 digits.');
        setError('');
        setConfirm('sign');
    };

    const doConfirmed = async () => {
        const action = confirm;
        if (action === 'approve') await run(() => approveRequest(req.id), 'Could not approve.');
        if (action === 'reject') await run(() => rejectRequest(req.id, rejectChoice, rejectNoteText), 'Could not reject.');
        if (action === 'sign') {
            const ok = await run(() => signRequest(req.id, code), 'Could not sign.', (message) => {
                // Mã hết hạn / hết lượt thử: bỏ trạng thái "đã gửi mã", quay về nút Send code
                if (OTP_UNUSABLE.test(message)) {
                    forgetOtpSent(req.id);
                    setCodeSent(false);
                    setCode('');
                    setCooldown(0);
                }
            });
            if (ok) forgetOtpSent(req.id);
        }
        setConfirm(null);
    };

    const handleCheckDocument = async () => {
        setBusy(true); setChecking(true); setError('');
        try { setDocCheck(await verifyDocument(req.id)); await load(); }
        catch (e) { setError(getErrorMessage(e, 'Could not check the document.')); }
        finally { setBusy(false); setChecking(false); }
    };

    // D1: mở giấy chứng tử trong tab mới
    const handleViewDocument = async () => {
        if (viewing) return;
        setError('');
        setViewing(true);
        // Mở tab trống ngay lúc bấm (trình duyệt không chặn), rồi nạp file vào sau
        const tab = window.open('', '_blank');
        try {
            const res = await getDocumentBlob(req.id);
            if (tab) {
                showBlobInTab(tab, res.data);
                load(); // FR-13: BE vừa ghi nhận đã xem -> mở khóa bước Check / Reject
            } else setError('Your browser blocked the pop-up. Please allow pop-ups for this site.');
        } catch (e) {
            if (tab) tab.close();
            setError(await getBlobErrorMessage(e, `Could not open the document (HTTP ${e.response?.status ?? 'no response'}).`));
        } finally {
            setViewing(false);
        }
    };

    const handleSendCode = async () => {
        const ok = await run(async () => { await sendSignOtp(req.id); return null; }, 'Could not send the code.');
        if (ok) {
            rememberOtpSent(req.id);
            setCodeSent(true);
            setCooldown(RESEND_SECONDS);
            setInfo('A 6-digit code has been sent to your email. It expires in 5 minutes.');
        }
    };

    if (loading) return <div className="p-8">Loading...</div>;
    if (!req) {
        return (
            <div className="p-8">
                <p className="mb-4">{error || 'Request not found.'}</p>
                <Link to="/verifier" className="text-secondary underline">Back to list</Link>
            </div>
        );
    }

    const dialog = confirm ? CONFIRM_TEXT[confirm] : null;
    // FR-13: bắt buộc View → Check (hợp lệ) → Approve; Reject chỉ cần đã View
    const isPending = req.status === 'PENDING';
    const canApprove = isPending && req.documentViewed && req.documentChecked && req.documentValid === true;
    const checkResult = docCheck ?? (req.documentChecked
        ? { valid: req.documentValid === true, message: '' } : null);
    // BE mới trả vaults (id + tên); dự phòng về vaultIds nếu thiếu
    const vaultList = req.vaults ?? (req.vaultIds ?? []).map((id) => ({ id, name: null }));

    // Form từ chối: dùng cho hồ sơ PENDING và cho hồ sơ APPROVED của chính verifier đã duyệt (FR-12)
    const rejectBlock = (
        <div className="pt-4 border-t border-outline-variant/30 flex flex-col gap-2">
            <label htmlFor="reject-reason" className="text-sm text-on-surface-variant">Rejection reason</label>
            <select
                id="reject-reason"
                value={rejectChoice}
                onChange={(e) => setRejectChoice(e.target.value)}
                className="w-full h-12 px-3 rounded-lg border border-outline-variant bg-background focus:outline-none focus:border-error"
            >
                <option value="">Select a reason...</option>
                {rejectReasons.map((r) => <option key={r.code} value={r.code}>{r.label}</option>)}
            </select>
            {reasonsError && (
                <div role="alert" className="text-sm text-error flex items-center gap-2">
                    Could not load the rejection reasons.
                    <button type="button" onClick={loadReasons} className="font-semibold underline">Retry</button>
                </div>
            )}

            {rejectChoice === REJECT_REASON_OTHER && (
                <>
                    <textarea
                        rows={3}
                        maxLength={500}
                        placeholder="Enter a note for this reason..."
                        value={rejectNote}
                        onChange={(e) => setRejectNote(e.target.value)}
                        aria-label="Rejection note"
                        className="w-full px-4 py-2 rounded-lg border border-outline-variant bg-background focus:outline-none focus:border-error"
                    />
                    <span className="text-xs text-outline text-right">{rejectNote.length}/500</span>
                </>
            )}

            <button
                onClick={askReject}
                disabled={busy || (isPending && !req.documentViewed)}
                title={isPending && !req.documentViewed ? 'View the document first' : undefined}
                className="w-full h-12 rounded-xl bg-error-container text-on-error-container font-bold hover:bg-error hover:text-on-error transition-all disabled:opacity-50"
            >
                Reject request
            </button>
        </div>
    );

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased">
            <VerifierHeader
                title={`Request #${shortId(req.id)}`}
                subtitle={req.ownerName ? `Vault owner: ${req.ownerName}` : undefined}
                backTo="/verifier"
                right={
                    <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider ${STATUS_CLASS[req.status]}`}>
                        {STATUS_LABEL[req.status]}
                    </span>
                }
            />

            <main className="max-w-[1600px] mx-auto p-5 grid grid-cols-1 xl:grid-cols-3 gap-8 mt-8">
                {/* Left column: case information */}
                <div className="xl:col-span-2 space-y-8">
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">badge</span>
                            Case information
                        </h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
                            <Field label="Deceased (Vault Owner)" value={req.ownerName} />
                            <Field label="Requester (Executor)" value={req.executorName} />
                            <Field label="Submitted" value={formatDate(req.createdAt, true)} />
                            <Field label="Decided" value={formatDate(req.decidedAt, true)} />
                        </div>
                    </section>

                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-2 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">folder_shared</span>
                            Vaults to unlock ({req.vaultIds?.length ?? 0})
                        </h2>
                        <p className="text-sm text-on-surface-variant mb-5">Signing this request will unlock all vaults below for their beneficiaries.</p>
                        <div className="space-y-3">
                            {vaultList.map((v) => (
                                <div key={v.id} className="flex items-center gap-4 p-4 rounded-xl bg-surface-container-high border border-outline-variant/50">
                                    <span className="material-symbols-outlined text-[28px] text-outline">inventory_2</span>
                                    <div className="min-w-0">
                                        <p className="font-semibold truncate">{v.name || 'Unnamed vault'}</p>
                                        <p className="font-mono text-xs text-on-surface-variant">#{shortId(v.id)}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </section>

                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">description</span>
                            Death certificate
                        </h2>
                        {req.hasDeathCertificate ? (
                            <div className="space-y-5">
                                <p className="flex items-center gap-2 font-semibold">
                                    <span className="material-symbols-outlined text-success">check_circle</span>
                                    Document submitted by the Executor
                                </p>
                                {isPending && (
                                    <div className="flex flex-wrap gap-3">
                                        {/* Bước 1: xem tài liệu thật (BE ghi nhận), sau đó mới Check / Reject / Approve */}
                                        <button
                                            onClick={handleViewDocument}
                                            disabled={busy || viewing}
                                            className="h-11 px-5 rounded-xl border border-outline-variant text-primary font-semibold hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-2"
                                        >
                                            <span className="material-symbols-outlined text-[20px]">visibility</span>
                                            {viewing ? 'Opening...' : 'View document'}
                                        </button>
                                        <button
                                            onClick={handleCheckDocument}
                                            disabled={busy || !req.documentViewed}
                                            title={req.documentViewed ? undefined : 'View the document first'}
                                            className="h-11 px-5 rounded-xl border border-outline-variant text-primary font-semibold hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-2"
                                        >
                                            <span className="material-symbols-outlined text-[20px]">fact_check</span>
                                            {checking ? 'Checking...' : 'Check document'}
                                        </button>
                                    </div>
                                )}
                                {!isPending && (
                                    <button
                                        onClick={handleViewDocument}
                                        disabled={busy || viewing}
                                        className="h-11 px-5 rounded-xl border border-outline-variant text-primary font-semibold hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-2"
                                    >
                                        <span className="material-symbols-outlined text-[20px]">visibility</span>
                                        {viewing ? 'Opening...' : 'View document'}
                                    </button>
                                )}
                            </div>
                        ) : (
                            <p className="text-on-surface-variant">No death certificate attached.</p>
                        )}
                        {checkResult && (
                            <div className={`mt-5 p-3 rounded-lg text-sm ${checkResult.valid ? 'bg-success-container text-on-success-container' : 'bg-error-container text-on-error-container'}`}>
                                <b>{checkResult.valid ? 'Valid' : 'Invalid'}</b>{checkResult.message ? ` — ${checkResult.message}` : ''}
                            </div>
                        )}
                        {isPending && req.hasDeathCertificate && (
                            <div className="mt-6 p-4 rounded-xl bg-surface-container-low border border-outline-variant/40">
                                <ol className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm font-semibold">
                                    {[
                                        { n: 1, label: 'View', done: !!req.documentViewed },
                                        { n: 2, label: 'Check', done: !!req.documentChecked && req.documentValid !== false },
                                        { n: 3, label: 'Approve', done: false },
                                    ].map((st, i) => (
                                        <React.Fragment key={st.n}>
                                            {i > 0 && <span className="material-symbols-outlined text-[18px] text-outline">arrow_forward</span>}
                                            <li className={`flex items-center gap-2 ${st.done ? 'text-success' : 'text-on-surface-variant'}`}>
                                                {st.done ? (
                                                    <span className="material-symbols-outlined text-[20px]">check_circle</span>
                                                ) : (
                                                    <span className="w-5 h-5 rounded-full border border-outline-variant text-xs flex items-center justify-center">{st.n}</span>
                                                )}
                                                {st.label}
                                            </li>
                                        </React.Fragment>
                                    ))}
                                </ol>
                                <p className="mt-2 text-xs text-on-surface-variant">Rejecting only needs step 1 (View).</p>
                            </div>
                        )}
                    </section>

                    {/* Trao đổi với Executor — nằm ở cột trái để thẻ hành động bên phải luôn vừa màn hình */}
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <VerificationMessages requestId={req.id} status={req.status} />
                    </section>
                </div>

                {/* Right column: actions */}
                <div>
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 sticky top-28">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">gavel</span>
                            Legal Actions
                        </h2>

                        {error && <div role="alert" className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}
                        {info && <div className="mb-4 p-3 rounded-lg bg-secondary-fixed text-on-secondary-fixed text-sm">{info}</div>}

                        {/* PENDING: approve or reject */}
                        {req.status === 'PENDING' && (
                            <div className="space-y-4">
                                <button
                                    onClick={askApprove}
                                    disabled={busy || !canApprove}
                                    className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50"
                                >
                                    Approve request
                                </button>

                                {rejectBlock}
                            </div>
                        )}

                        {/* APPROVED: gửi mã -> nhập mã -> ký */}
                        {req.status === 'APPROVED' && (
                            <div className="space-y-4">
                                <div className="p-3 rounded-lg bg-success-container text-on-success-container text-sm">
                                    Request approved. Sign with a one-time code to unlock the vault(s).
                                </div>

                                {!codeSent ? (
                                    <button
                                        onClick={handleSendCode}
                                        disabled={busy}
                                        className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50"
                                    >
                                        {busy ? 'Sending...' : 'Send signing code to my email'}
                                    </button>
                                ) : (
                                    <form onSubmit={askSign} className="space-y-4">
                                        <label htmlFor="sign-code" className="text-sm text-on-surface-variant block">Signing code (6 digits)</label>
                                        <input
                                            id="sign-code"
                                            inputMode="numeric"
                                            autoComplete="one-time-code"
                                            maxLength={6}
                                            placeholder="• • • • • •"
                                            value={code}
                                            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-lg text-center tracking-[0.5em] focus:outline-none focus:border-primary"
                                        />
                                        <button
                                            type="button"
                                            onClick={handleSendCode}
                                            disabled={busy || cooldown > 0}
                                            className="text-sm font-semibold text-secondary disabled:text-outline"
                                        >
                                            {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
                                        </button>
                                        <button
                                            type="submit"
                                            disabled={busy}
                                            className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50"
                                        >
                                            Sign and unlock vault(s)
                                        </button>
                                    </form>
                                )}

                                {/* Chỉ verifier đã duyệt mới xem được hồ sơ APPROVED, nên được phép từ chối trước khi ký */}
                                {rejectBlock}
                            </div>
                        )}

                        {req.status === 'SIGNED' && (
                            <div className="p-5 rounded-xl bg-success-container text-on-success-container">
                                <p className="font-bold">Signed successfully</p>
                                <p className="text-sm mt-1">The vault(s) have been unlocked for the beneficiaries.</p>
                            </div>
                        )}

                        {req.status === 'REJECTED' && (
                            <div className="p-5 rounded-xl bg-error-container text-on-error-container">
                                <p className="font-bold">This request has been rejected</p>
                                <p className="text-sm mt-1">Reason: {req.rejectionReason || '—'}</p>
                            </div>
                        )}

                        {(req.status === 'PENDING' || req.status === 'APPROVED') && (
                            <p className="text-xs text-outline mt-6 pt-6 border-t border-outline-variant/20 text-center">
                                Your decision will be recorded in the system history.
                            </p>
                        )}
                    </section>
                </div>
            </main>

            <ConfirmDialog
                open={!!dialog}
                title={dialog?.title}
                message={dialog?.message}
                confirmText={dialog?.confirmText}
                danger={dialog?.danger}
                busy={busy}
                onConfirm={doConfirmed}
                onCancel={() => setConfirm(null)}
            />
        </div>
    );
}

function Field({ label, value }) {
    return (
        <div>
            <label className="text-sm text-on-surface-variant">{label}</label>
            <p className="font-body-lg font-medium mt-1">{value || '—'}</p>
        </div>
    );
}
