import React, { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
    getRequestById, approveRequest, rejectRequest, verifyDocument, sendSignOtp, signRequest,
    getDocumentBlob,
} from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import { STATUS_LABEL, STATUS_CLASS, formatDate, shortId } from './verifierUi.js';
import VerificationMessages from './VerificationMessages.jsx';
import { showBlobInTab, getBlobErrorMessage } from './blobUtils.js';

const RESEND_SECONDS = 60; // khớp SIGN_OTP_RESEND_COOLDOWN_SECONDS của BE

export default function VerificationRequestDetail() {
    const { requestId } = useParams();

    const [req, setReq] = useState(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [info, setInfo] = useState('');
    const [rejectReason, setRejectReason] = useState('');
    const [docCheck, setDocCheck] = useState(null);   // { valid, message }
    const [codeSent, setCodeSent] = useState(false);
    const [code, setCode] = useState('');
    const [cooldown, setCooldown] = useState(0);

    const load = useCallback(() => {
        return getRequestById(requestId)
            .then(setReq)
            .catch((e) => setError(getErrorMessage(e, 'Request not found.')));
    }, [requestId]);

    useEffect(() => { load().finally(() => setLoading(false)); }, [load]);

    useEffect(() => {
        if (cooldown <= 0) return;
        const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(t);
    }, [cooldown]);

    // Chạy 1 hành động; 409 = hồ sơ đã được xử lý ở nơi khác -> tải lại trạng thái mới
    const run = async (action, fallback) => {
        setBusy(true); setError(''); setInfo('');
        try {
            const updated = await action();
            if (updated) setReq(updated);
            return true;
        } catch (e) {
            setError(getErrorMessage(e, fallback));
            if (e.response?.status === 409) load();
            return false;
        } finally {
            setBusy(false);
        }
    };

    const handleApprove = () => run(() => approveRequest(req.id), 'Could not approve.');

    const handleReject = () => {
        const reason = rejectReason.trim();
        if (!reason) return setError('Please enter a rejection reason.');
        if (reason.length > 1000) return setError('Reason must be at most 1000 characters.');
        run(() => rejectRequest(req.id, reason), 'Could not reject.');
    };

    const handleCheckDocument = async () => {
        setBusy(true); setError('');
        try { setDocCheck(await verifyDocument(req.id)); }
        catch (e) { setError(getErrorMessage(e, 'Could not check the document.')); }
        finally { setBusy(false); }
    };

    // D1: mở giấy chứng tử trong tab mới
    const handleViewDocument = async () => {
        setError('');
        // Mở tab trống ngay lúc bấm (trình duyệt không chặn), rồi nạp file vào sau
        const tab = window.open('', '_blank');
        try {
            const res = await getDocumentBlob(req.id);
            if (tab) showBlobInTab(tab, res.data);
            else setError('Your browser blocked the pop-up. Please allow pop-ups for this site.');
        } catch (e) {
            if (tab) tab.close();
            setError(await getBlobErrorMessage(e, `Could not open the document (HTTP ${e.response?.status ?? 'no response'}).`));
        }
    };

    const handleSendCode = async () => {
        const ok = await run(async () => { await sendSignOtp(req.id); return null; }, 'Could not send the code.');
        if (ok) {
            setCodeSent(true);
            setCooldown(RESEND_SECONDS);
            setInfo('A 6-digit code has been sent to your email. It expires in 5 minutes.');
        }
    };

    const handleSign = () => {
        if (!/^\d{6}$/.test(code)) return setError('The code must be exactly 6 digits.');
        run(() => signRequest(req.id, code), 'Could not sign.');
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

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased">
            {/* Header */}
            <header className="sticky top-0 z-10 bg-background/95 backdrop-blur-sm border-b border-outline-variant/30">
                <div className="max-w-[1600px] mx-auto px-5 h-20 flex items-center justify-between gap-6">
                    <div className="flex items-center gap-3">
                        <Link to="/verifier" className="p-2 rounded-full hover:bg-surface-container-high text-on-surface-variant">
                            <span className="material-symbols-outlined">arrow_back</span>
                        </Link>
                        <div>
                            <h1 className="font-headline-sm text-primary font-bold tracking-tight">Request #{shortId(req.id)}</h1>
                            <p className="text-sm text-on-surface-variant mt-0.5">Exclusive to the Legal Verifier role</p>
                        </div>
                    </div>
                    <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider ${STATUS_CLASS[req.status]}`}>
                        {STATUS_LABEL[req.status]}
                    </span>
                </div>
            </header>

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
                            {(req.vaultIds ?? []).map((v) => (
                                <div key={v} className="flex items-center gap-4 p-4 rounded-xl bg-surface-container-high border border-outline-variant/50">
                                    <span className="material-symbols-outlined text-[28px] text-outline">inventory_2</span>
                                    <p className="font-mono text-sm">{v}</p>
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
                            <div className="flex flex-wrap items-center gap-4">
                                <span className="flex items-center gap-2 font-semibold">
                                    <span className="material-symbols-outlined text-success">check_circle</span>
                                    Document submitted by the Executor
                                </span>
                                <button
                                    onClick={handleCheckDocument}
                                    disabled={busy}
                                    className="h-10 px-4 rounded-xl border border-outline-variant text-primary font-semibold hover:bg-surface-container-low disabled:opacity-50"
                                >
                                    Check document
                                </button>
                                <button
                                    onClick={handleViewDocument}
                                    disabled={busy}
                                    className="h-10 px-4 rounded-xl border border-outline-variant text-primary font-semibold hover:bg-surface-container-low disabled:opacity-50"
                                >
                                    View document
                                </button>
                            </div>
                        ) : (
                            <p className="text-on-surface-variant">No death certificate attached.</p>
                        )}
                        {docCheck && (
                            <div className={`mt-4 p-3 rounded-lg text-sm ${docCheck.valid ? 'bg-success-container text-on-success-container' : 'bg-error-container text-on-error-container'}`}>
                                <b>{docCheck.valid ? 'Valid' : 'Invalid'}</b>{docCheck.message ? ` — ${docCheck.message}` : ''}
                            </div>
                        )}
                    </section>
                </div>

                {/* Right column: actions */}
                <div>
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 sticky top-28">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">gavel</span>
                            Legal Actions
                        </h2>

                        {error && <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}
                        {info && <div className="mb-4 p-3 rounded-lg bg-secondary-fixed text-on-secondary-fixed text-sm">{info}</div>}

                        {/* PENDING: approve or reject */}
                        {req.status === 'PENDING' && (
                            <div className="space-y-4">
                                <button
                                    onClick={handleApprove}
                                    disabled={busy}
                                    className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50"
                                >
                                    {busy ? 'Processing...' : 'Approve request'}
                                </button>

                                <div className="pt-4 border-t border-outline-variant/30 flex flex-col gap-2">
                                    <label className="text-sm text-on-surface-variant">Rejection reason</label>
                                    <textarea
                                        rows={3}
                                        maxLength={1000}
                                        placeholder="Enter reason here..."
                                        value={rejectReason}
                                        onChange={(e) => setRejectReason(e.target.value)}
                                        className="w-full px-4 py-2 rounded-lg border border-outline-variant bg-background focus:outline-none focus:border-error"
                                    />
                                    <span className="text-xs text-outline text-right">{rejectReason.length}/1000</span>
                                    <button
                                        onClick={handleReject}
                                        disabled={busy}
                                        className="w-full h-12 rounded-xl bg-error-container text-on-error-container font-bold hover:bg-error hover:text-on-error transition-all disabled:opacity-50"
                                    >
                                        Reject request
                                    </button>
                                </div>
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
                                    <>
                                        <label className="text-sm text-on-surface-variant">Signing code (6 digits)</label>
                                        <input
                                            inputMode="numeric"
                                            maxLength={6}
                                            placeholder="• • • • • •"
                                            value={code}
                                            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-lg text-center tracking-[0.5em] focus:outline-none focus:border-primary"
                                        />
                                        <button
                                            onClick={handleSendCode}
                                            disabled={busy || cooldown > 0}
                                            className="text-sm font-semibold text-secondary disabled:text-outline"
                                        >
                                            {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
                                        </button>
                                        <button
                                            onClick={handleSign}
                                            disabled={busy}
                                            className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50"
                                        >
                                            {busy ? 'Signing...' : 'Sign and unlock Vault'}
                                        </button>
                                    </>
                                )}
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

                        <VerificationMessages requestId={req.id} status={req.status} />
                        <p className="text-xs text-outline mt-6 pt-6 border-t border-outline-variant/20 text-center">
                            This decision will be recorded in the system history.
                        </p>
                    </section>
                </div>
            </main>
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
