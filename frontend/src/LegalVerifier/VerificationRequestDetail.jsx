import React, { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
    getRequestById, approveRequest, rejectRequest, verifyDocument, sendSignOtp, signRequest,
    getDocumentBlob, getRejectionReasons,
} from '../api/verifierApi.js';
import { getErrorCode, getErrorMessage } from '../api/http.js';
import {
    statusLabel, STATUS_CLASS, formatDate, shortId, REJECT_REASON_OTHER,
} from './verifierUi.js';
import VerificationMessages from './VerificationMessages.jsx';
import VerifierHeader from './VerifierHeader.jsx';
import ConfirmDialog from './ConfirmDialog.jsx';
import { showBlobInTab, getBlobErrorMessage } from './blobUtils.js';
import { PAGE_WIDTH } from '../Common/AppHeader.jsx';

const RESEND_SECONDS = 60;          // khớp SIGN_OTP_RESEND_COOLDOWN_SECONDS của BE
const OTP_VALID_SECONDS = 5 * 60;   // khớp SIGN_OTP_EXPIRE_MINUTES của BE
const otpKey = (id) => `lv_sign_otp_${id}`;
// V1: mã ký không còn dùng được (hết hạn / hết lượt thử) -> quay về nút Send code
const OTP_UNUSABLE_CODES = ['SIGN_CODE_EXPIRED', 'SIGN_CODE_ATTEMPTS_EXHAUSTED'];

// Nhớ thời điểm đã gửi mã ký, để tải lại trang không phải gửi lại (chỉ là tiện lợi, lỗi thì bỏ qua)
const rememberOtpSent = (id, at = Date.now()) => { try { sessionStorage.setItem(otpKey(id), String(at)); } catch { /* ignore */ } };
const forgetOtpSent = (id) => { try { sessionStorage.removeItem(otpKey(id)); } catch { /* ignore */ } };
const secondsSinceOtpSent = (id) => {
    try {
        const t = Number(sessionStorage.getItem(otpKey(id)));
        return t ? Math.floor((Date.now() - t) / 1000) : null;
    } catch { return null; }
};

export default function VerificationRequestDetail() {
    const { requestId } = useParams();
    const { t } = useTranslation('verifier');

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
            .catch((e) => setError(getErrorMessage(e, t('detail.notFound'))));
    }, [requestId]); // eslint-disable-line react-hooks/exhaustive-deps

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
        const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(timer);
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
            onFail?.(message, getErrorCode(e), e);
            return false;
        } finally {
            setBusy(false);
        }
    };

    const rejectNoteText = rejectNote.trim();

    const askApprove = () => { setError(''); setConfirm('approve'); };

    const askReject = () => {
        if (!rejectChoice) return setError(t('detail.err.pickReason'));
        if (rejectChoice === REJECT_REASON_OTHER && !rejectNoteText) return setError(t('detail.err.needNote'));
        if (rejectNoteText.length > 500) return setError(t('detail.err.noteTooLong'));
        setError('');
        setConfirm('reject');
    };

    const askSign = (e) => {
        e?.preventDefault();
        if (!/^\d{6}$/.test(code)) return setError(t('detail.err.codeFormat'));
        setError('');
        setConfirm('sign');
    };

    const doConfirmed = async () => {
        const action = confirm;
        if (action === 'approve') await run(() => approveRequest(req.id), t('detail.err.approve'));
        if (action === 'reject') await run(() => rejectRequest(req.id, rejectChoice, rejectNoteText), t('detail.err.reject'));
        if (action === 'sign') {
            const ok = await run(() => signRequest(req.id, code), t('detail.err.sign'), (message, errorCode) => {
                // Mã hết hạn / hết lượt thử: bỏ trạng thái "đã gửi mã", quay về nút Send code
                if (OTP_UNUSABLE_CODES.includes(errorCode)) {
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
        catch (e) { setError(getErrorMessage(e, t('detail.err.check'))); }
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
                load(); // FR-13: BE vừa ghi nhận đã xem -> mở khóa Approve
            } else setError(t('detail.err.popupBlocked'));
        } catch (e) {
            if (tab) tab.close();
            setError(await getBlobErrorMessage(e, t('detail.err.openDocument', { status: e.response?.status ?? t('detail.err.noResponse') })));
        } finally {
            setViewing(false);
        }
    };

    const handleSendCode = async () => {
        const ok = await run(async () => { await sendSignOtp(req.id); return null; }, t('detail.err.sendCode'),
            (message, errorCode, e) => {
                // V1: bị chặn gửi lại (429) -> đồng hồ chờ theo retryAfterSeconds của BE, đúng cả sau khi F5
                if (errorCode === 'TOO_MANY_OTP_REQUESTS') {
                    const wait = Number(e?.response?.data?.data?.retryAfterSeconds);
                    if (wait > 0) {
                        setCooldown(wait);
                        setCodeSent(true);
                        // lưu mốc gửi ước tính để F5 vẫn giữ trạng thái "đã gửi mã" và đồng hồ chờ
                        rememberOtpSent(req.id, Date.now() - (RESEND_SECONDS - wait) * 1000);
                    }
                }
            });
        if (ok) {
            rememberOtpSent(req.id);
            setCodeSent(true);
            setCooldown(RESEND_SECONDS);
            setInfo(t('detail.codeSent'));
        }
    };

    if (loading) return <div className="p-8">{t('loading')}</div>;
    if (!req) {
        return (
            <div className="p-8">
                <p className="mb-4">{error || t('detail.notFound')}</p>
                <Link to="/verifier" className="text-secondary underline">{t('detail.backToList')}</Link>
            </div>
        );
    }

    const dialog = confirm ? {
        title: t(`confirm.${confirm}.title`),
        message: t(`confirm.${confirm}.message`),
        confirmText: t(`confirm.${confirm}.button`),
        danger: confirm === 'reject',
    } : null;
    // FR-13: bắt buộc Check (hợp lệ) → View → Approve; Reject cần đã View (View chỉ mở được sau Check)
    const isPending = req.status === 'PENDING';
    const canApprove = isPending && req.documentViewed && req.documentChecked && req.documentValid === true;
    const checkResult = docCheck ?? (req.documentChecked
        ? { valid: req.documentValid === true, message: '' } : null);
    // BE mới trả vaults (id + tên); dự phòng về vaultIds nếu thiếu
    const vaultList = req.vaults ?? (req.vaultIds ?? []).map((id) => ({ id, name: null }));

    // Form từ chối: dùng cho hồ sơ PENDING và cho hồ sơ APPROVED của chính verifier đã duyệt (FR-12)
    const rejectBlock = (
        <div className="pt-4 border-t border-outline-variant/30 flex flex-col gap-2">
            <label htmlFor="reject-reason" className="text-sm text-on-surface-variant">{t('detail.rejectReason')}</label>
            <select
                id="reject-reason"
                value={rejectChoice}
                onChange={(e) => setRejectChoice(e.target.value)}
                className="w-full h-12 px-3 rounded-lg border border-outline-variant bg-background focus:outline-none focus:border-error"
            >
                <option value="">{t('detail.selectReason')}</option>
                {rejectReasons.map((r) => <option key={r.code} value={r.code}>{t(`rejectReason.${r.code}`, { defaultValue: r.label })}</option>)}
            </select>
            {reasonsError && (
                <div role="alert" className="text-sm text-error flex items-center gap-2">
                    {t('detail.reasonsError')}
                    <button type="button" onClick={loadReasons} className="font-semibold underline">{t('detail.retry')}</button>
                </div>
            )}

            {rejectChoice === REJECT_REASON_OTHER && (
                <>
                    <textarea
                        rows={3}
                        maxLength={500}
                        placeholder={t('detail.notePlaceholder')}
                        value={rejectNote}
                        onChange={(e) => setRejectNote(e.target.value)}
                        aria-label={t('detail.noteLabel')}
                        className="w-full px-4 py-2 rounded-lg border border-outline-variant bg-background focus:outline-none focus:border-error"
                    />
                    <span className="text-xs text-outline text-right">{rejectNote.length}/500</span>
                </>
            )}

            <button
                onClick={askReject}
                disabled={busy || (isPending && !req.documentViewed)}
                title={isPending && !req.documentViewed ? t('detail.viewFirst') : undefined}
                className="w-full h-12 rounded-xl bg-error-container text-on-error-container font-bold hover:bg-error hover:text-on-error transition-all disabled:opacity-50"
            >
                {t('detail.rejectRequest')}
            </button>
        </div>
    );

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased">
            <VerifierHeader
                title={t('detail.requestTitle', { id: shortId(req.id) })}
                subtitle={req.ownerName ? t('detail.ownerLine', { name: req.ownerName }) : undefined}
                backTo="/verifier"
                right={
                    <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider ${STATUS_CLASS[req.status]}`}>
                        {statusLabel(req.status)}
                    </span>
                }
            />

            <main className={`${PAGE_WIDTH} py-8 grid grid-cols-1 xl:grid-cols-3 gap-8`}>
                {/* Left column: case information */}
                <div className="xl:col-span-2 space-y-8">
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">badge</span>
                            {t('detail.caseInfo')}
                        </h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
                            <Field label={t('detail.deceased')} value={req.ownerName} />
                            <Field label={t('detail.requester')} value={req.executorName} />
                            <Field label={t('detail.submitted')} value={formatDate(req.createdAt, true)} />
                            <Field label={t('detail.decided')} value={formatDate(req.decidedAt, true)} />
                        </div>
                    </section>

                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-2 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">folder_shared</span>
                            {t('detail.vaultsTitle', { count: req.vaultIds?.length ?? 0 })}
                        </h2>
                        <p className="text-sm text-on-surface-variant mb-5">{t('detail.vaultsHint')}</p>
                        <div className="space-y-3">
                            {vaultList.map((v) => (
                                <div key={v.id} className="flex items-center gap-4 p-4 rounded-xl bg-surface-container-high border border-outline-variant/50">
                                    <span className="material-symbols-outlined text-[28px] text-outline">inventory_2</span>
                                    <div className="min-w-0">
                                        <p className="font-semibold truncate">{v.name || t('detail.unnamedVault')}</p>
                                        <p className="font-mono text-xs text-on-surface-variant">#{shortId(v.id)}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </section>

                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">description</span>
                            {t('detail.certificate')}
                        </h2>
                        {req.hasDeathCertificate ? (
                            <div className="space-y-5">
                                <p className="flex items-center gap-2 font-semibold">
                                    <span className="material-symbols-outlined text-success">check_circle</span>
                                    {t('detail.docSubmitted')}
                                </p>
                                {isPending && (
                                    <div className="flex flex-wrap gap-3">
                                        {/* Bước 1: Check (sàng lọc tự động). Bước 2: View (xem tài liệu thật, BE ghi nhận) */}
                                        <button
                                            onClick={handleCheckDocument}
                                            disabled={busy || viewing}
                                            className="h-11 px-5 rounded-xl border border-outline-variant text-primary font-semibold hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-2"
                                        >
                                            <span className="material-symbols-outlined text-[20px]">fact_check</span>
                                            {checking ? t('detail.checking') : t('detail.checkDocument')}
                                        </button>
                                        <button
                                            onClick={handleViewDocument}
                                            disabled={busy || viewing || !req.documentChecked}
                                            title={req.documentChecked ? undefined : t('detail.checkFirst')}
                                            className="h-11 px-5 rounded-xl border border-outline-variant text-primary font-semibold hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-2"
                                        >
                                            <span className="material-symbols-outlined text-[20px]">visibility</span>
                                            {viewing ? t('detail.opening') : t('detail.viewDocument')}
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
                                        {viewing ? t('detail.opening') : t('detail.viewDocument')}
                                    </button>
                                )}
                            </div>
                        ) : (
                            <p className="text-on-surface-variant">{t('detail.noCertificate')}</p>
                        )}
                        {checkResult && (
                            <div className={`mt-5 p-3 rounded-lg text-sm ${checkResult.valid ? 'bg-success-container text-on-success-container' : 'bg-error-container text-on-error-container'}`}>
                                <b>{checkResult.valid ? t('detail.valid') : t('detail.invalid')}</b>{checkResult.message ? ` — ${checkResult.message}` : ''}
                            </div>
                        )}
                        {isPending && req.hasDeathCertificate && (
                            <div className="mt-6 p-4 rounded-xl bg-surface-container-low border border-outline-variant/40">
                                <ol className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm font-semibold">
                                    {[
                                        { n: 1, label: t('detail.steps.check'), state: !req.documentChecked ? 'todo' : (req.documentValid === false ? 'failed' : 'done') },
                                        { n: 2, label: t('detail.steps.view'), state: req.documentViewed ? 'done' : 'todo' },
                                        { n: 3, label: t('detail.steps.approve'), state: 'todo' },
                                    ].map((st, i) => (
                                        <React.Fragment key={st.n}>
                                            {i > 0 && <span className="material-symbols-outlined text-[18px] text-outline">arrow_forward</span>}
                                            <li className={`flex items-center gap-2 ${st.state === 'done' ? 'text-success' : st.state === 'failed' ? 'text-error' : 'text-on-surface-variant'}`}>
                                                {st.state === 'done' && <span className="material-symbols-outlined text-[20px]">check_circle</span>}
                                                {st.state === 'failed' && <span className="material-symbols-outlined text-[20px]">cancel</span>}
                                                {st.state === 'todo' && (
                                                    <span className="w-5 h-5 rounded-full border border-outline-variant text-xs flex items-center justify-center">{st.n}</span>
                                                )}
                                                {st.label}
                                            </li>
                                        </React.Fragment>
                                    ))}
                                </ol>
                                <p className="mt-2 text-xs text-on-surface-variant">{t('detail.rejectNeeds')}</p>
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
                            {t('detail.actions')}
                        </h2>

                        {error && <div role="alert" className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}
                        {info && <div className="mb-4 p-3 rounded-lg bg-secondary-fixed text-on-secondary-fixed text-sm">{info}</div>}

                        {/* PENDING: approve or reject */}
                        {req.status === 'PENDING' && (
                            <div className="space-y-4">
                                <button
                                    onClick={askApprove}
                                    disabled={busy || !canApprove}
                                    className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50 lv-pill"
                                >
                                    {t('detail.approveRequest')}
                                </button>

                                {rejectBlock}
                            </div>
                        )}

                        {/* APPROVED: gửi mã -> nhập mã -> ký */}
                        {req.status === 'APPROVED' && (
                            <div className="space-y-4">
                                <div className="p-3 rounded-lg bg-success-container text-on-success-container text-sm">
                                    {t('detail.approvedBanner')}
                                </div>

                                {!codeSent ? (
                                    <button
                                        onClick={handleSendCode}
                                        disabled={busy}
                                        className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50 lv-pill"
                                    >
                                        {busy ? t('detail.sendingCode') : t('detail.sendCode')}
                                    </button>
                                ) : (
                                    <form onSubmit={askSign} className="space-y-4">
                                        <label htmlFor="sign-code" className="text-sm text-on-surface-variant block">{t('detail.codeLabel')}</label>
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
                                            {cooldown > 0 ? t('detail.resendIn', { s: cooldown }) : t('detail.resend')}
                                        </button>
                                        <button
                                            type="submit"
                                            disabled={busy}
                                            className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50 lv-pill"
                                        >
                                            {t('detail.signUnlock')}
                                        </button>
                                    </form>
                                )}

                                {/* Chỉ verifier đã duyệt mới xem được hồ sơ APPROVED, nên được phép từ chối trước khi ký */}
                                {rejectBlock}
                            </div>
                        )}

                        {req.status === 'SIGNED' && (
                            <div className="p-5 rounded-xl bg-success-container text-on-success-container">
                                <p className="font-bold">{t('detail.signedTitle')}</p>
                                <p className="text-sm mt-1">{t('detail.signedText')}</p>
                            </div>
                        )}

                        {req.status === 'REJECTED' && (
                            <div className="p-5 rounded-xl bg-error-container text-on-error-container">
                                <p className="font-bold">{t('detail.rejectedTitle')}</p>
                                <p className="text-sm mt-1">{t('detail.reason', { reason: req.rejectionReason || '—' })}</p>
                            </div>
                        )}

                        {(req.status === 'PENDING' || req.status === 'APPROVED') && (
                            <p className="text-xs text-outline mt-6 pt-6 border-t border-outline-variant/20 text-center">
                                {t('detail.recorded')}
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
