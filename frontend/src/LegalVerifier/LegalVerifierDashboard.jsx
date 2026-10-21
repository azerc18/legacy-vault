import React, { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { getRequests } from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import { getUser } from '../auth/auth.js';
import { STATUS_LABEL, STATUS_CLASS, formatDate, shortId } from './verifierUi.js';
import VerifierHeader from './VerifierHeader.jsx';

export default function LegalVerifierDashboard() {
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const user = getUser();

    useEffect(() => {
        getRequests()
            .then(setRequests)
            .catch((e) => setError(getErrorMessage(e, 'Could not load requests.')))
            .finally(() => setLoading(false));
    }, []);

    if (!user) return <Navigate to="/login" replace />;

    const awaitingSign = requests.filter((r) => r.status === 'APPROVED');
    const pending = requests.filter((r) => r.status === 'PENDING');

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col">
            <VerifierHeader title="Verification Requests" subtitle="Select a case to view details and process it." />

            <main className="w-full pt-8 pb-12 flex-1 max-w-[1440px] mx-auto px-6">
                <Link to="/verifier/history" className="inline-flex items-center gap-1.5 mb-8 text-secondary font-semibold hover:text-primary">
                    <span className="material-symbols-outlined text-[20px]">history</span>
                    View verification history
                </Link>

                {loading && <p>Loading...</p>}
                {error && <div className="p-4 rounded-xl bg-error-container text-on-error-container">{error}</div>}
                {!loading && !error && requests.length === 0 && (
                    <p className="text-on-surface-variant">You're all caught up — no requests waiting.</p>
                )}

                <RequestGroup title="Awaiting your signature" items={awaitingSign} />
                <RequestGroup title="Pending review" items={pending} />
            </main>
        </div>
    );
}

function RequestGroup({ title, items }) {
    if (items.length === 0) return null;
    return (
        <section className="mb-10">
            <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{title} ({items.length})</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {items.map((req) => (
                    <Link
                        key={req.id}
                        to={`/verifier/request/${req.id}`}
                        className="block bg-surface-container-lowest rounded-xl p-5 shadow-sm border border-outline-variant/30 hover:shadow-md transition-all"
                    >
                        <div className="flex justify-between items-start gap-3 mb-2">
                            <span className="font-label-md font-bold text-on-surface">{req.ownerName}</span>
                            <span className={`shrink-0 text-[11px] px-2.5 py-0.5 rounded-full font-semibold ${STATUS_CLASS[req.status]}`}>
                                {STATUS_LABEL[req.status]}
                            </span>
                        </div>
                        <p className="text-sm text-on-surface-variant">Executor: {req.executorName}</p>
                        <p className="text-xs text-outline mt-1">
                            #{shortId(req.id)} · Submitted {formatDate(req.createdAt)} · {req.vaultIds?.length ?? 0} vault(s)
                        </p>
                    </Link>
                ))}
            </div>
        </section>
    );
}
