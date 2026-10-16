import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getRequestById, approveRequest, signRequest, rejectRequest } from '../api/verifierApi.js';

const STATUS_LABEL = {
    PENDING: 'Pending review',
    APPROVED: 'Approved, awaiting signature',
    SIGNED: 'Signed',
    REJECTED: 'Rejected',
};

export default function VerificationRequestDetail() {
    const { requestId } = useParams();

    const [req, setReq] = useState(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [pin, setPin] = useState('');
    const [rejectReason, setRejectReason] = useState('');

    useEffect(() => {
        getRequestById(requestId)
            .then(setReq)
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    }, [requestId]);

    // Shared helper: run an action, update the request, catch errors
    const run = async (action) => {
        setBusy(true);
        setError('');
        try {
            setReq(await action());
        } catch (e) {
            setError(e.message);
        } finally {
            setBusy(false);
        }
    };

    const handleApprove = () => run(() => approveRequest(req.id));
    const handleSign = () => run(() => signRequest(req.id, pin));
    const handleReject = () => {
        if (!rejectReason.trim()) {
            setError('Please enter a rejection reason.');
            return;
        }
        run(() => rejectRequest(req.id, rejectReason.trim()));
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
                            <h1 className="font-headline-sm text-primary font-bold tracking-tight">
                                Process Request #{req.id}
                            </h1>
                            <p className="text-sm text-on-surface-variant mt-0.5">Exclusive to the Legal Verifier role</p>
                        </div>
                    </div>
                    <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider ${
                        req.status === 'REJECTED'
                            ? 'bg-error-container text-on-error-container'
                            : req.status === 'SIGNED'
                                ? 'bg-success-container text-on-success-container'
                                : 'bg-warning-container text-on-warning-container'
                    }`}>
                        {STATUS_LABEL[req.status]}
                    </span>
                </div>
            </header>

            <main className="max-w-[1600px] mx-auto p-5 grid grid-cols-1 xl:grid-cols-3 gap-8 mt-8">
                {/* Left column: case information */}
                <div className="xl:col-span-2 space-y-8">
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">person</span>
                            Requester (Executor)
                        </h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
                            <div>
                                <label className="text-sm text-on-surface-variant">Full name</label>
                                <p className="font-body-lg font-medium mt-1">{req.executorName}</p>
                            </div>
                            <div>
                                <label className="text-sm text-on-surface-variant">Contact email</label>
                                <p className="font-body-lg font-medium mt-1">{req.executorEmail}</p>
                            </div>
                            <div>
                                <label className="text-sm text-on-surface-variant">Relationship to the deceased</label>
                                <p className="font-body-lg font-medium mt-1">{req.relationship}</p>
                            </div>
                            <div>
                                <label className="text-sm text-on-surface-variant">Submitted date</label>
                                <p className="font-body-lg font-medium mt-1">{req.submittedDate}</p>
                            </div>
                        </div>
                    </section>

                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">folder_shared</span>
                            Deceased (Vault Owner) and assets
                        </h2>
                        <p className="font-headline-sm font-bold text-primary mb-5">{req.ownerName}</p>
                        <div className="space-y-3">
                            {req.vaults.map((v) => (
                                <div key={v} className="flex items-center gap-4 p-4 rounded-xl bg-surface-container-high border border-outline-variant/50">
                                    <span className="material-symbols-outlined text-[32px] text-outline">inventory_2</span>
                                    <p className="font-label-md font-bold">{v}</p>
                                </div>
                            ))}
                        </div>
                    </section>

                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">description</span>
                            Death certificate
                        </h2>
                        <div className="flex items-center gap-3 p-4 rounded-xl bg-surface-container-low">
                            <span className="material-symbols-outlined text-secondary text-[28px]">description</span>
                            <p className="font-bold text-primary">{req.deathCertificate}</p>
                        </div>
                    </section>
                </div>

                {/* Right column: actions */}
                <div>
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 sticky top-28">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">gavel</span>
                            Legal Actions
                        </h2>

                        {error && (
                            <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">
                                {error}
                            </div>
                        )}

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
                                    <input
                                        type="text"
                                        placeholder="Enter reason here..."
                                        value={rejectReason}
                                        onChange={(e) => setRejectReason(e.target.value)}
                                        className="w-full h-10 px-4 rounded-lg border border-outline-variant bg-background focus:outline-none focus:border-error"
                                    />
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

                        {/* APPROVED: enter PIN to sign */}
                        {req.status === 'APPROVED' && (
                            <div className="space-y-4">
                                <div className="p-3 rounded-lg bg-success-container text-on-success-container text-sm">
                                    Request approved. Enter your PIN to digitally sign and unlock the Vault.
                                </div>
                                <label className="text-sm text-on-surface-variant">Digital signature PIN (6 digits)</label>
                                <input
                                    type="password"
                                    maxLength={6}
                                    placeholder="• • • • • •"
                                    value={pin}
                                    onChange={(e) => setPin(e.target.value)}
                                    className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-lg tracking-[0.5em] focus:outline-none focus:border-primary"
                                />
                                <button
                                    onClick={handleSign}
                                    disabled={busy}
                                    className="w-full h-14 rounded-xl bg-primary text-on-primary font-bold hover:bg-primary-container transition-all disabled:opacity-50"
                                >
                                    {busy ? 'Signing...' : 'Sign and unlock Vault'}
                                </button>
                            </div>
                        )}

                        {/* SIGNED */}
                        {req.status === 'SIGNED' && (
                            <div className="p-5 rounded-xl bg-success-container text-on-success-container">
                                <p className="font-bold">Signed successfully</p>
                                <p className="text-sm mt-1">The Vault has been unlocked for the Executor.</p>
                            </div>
                        )}

                        {/* REJECTED */}
                        {req.status === 'REJECTED' && (
                            <div className="p-5 rounded-xl bg-error-container text-on-error-container">
                                <p className="font-bold">This request has been rejected</p>
                                <p className="text-sm mt-1">Reason: {req.rejectReason}</p>
                            </div>
                        )}

                        <p className="text-xs text-outline mt-6 pt-6 border-t border-outline-variant/20 text-center">
                            This decision will be recorded in the system history.
                        </p>
                    </section>
                </div>
            </main>
        </div>
    );
}