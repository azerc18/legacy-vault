import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getHistory } from '../api/verifierApi.js';

const ACTION_LABEL = {
    SIGNED: 'Signed',
    REJECTED: 'Rejected',
};

export default function VerificationHistory() {
    const [action, setAction] = useState(''); // '' = all
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Re-run whenever the filter changes
    useEffect(() => {
        setLoading(true);
        setError('');
        getHistory({ action: action || undefined })
            .then(setItems)
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    }, [action]);

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased">
            <header className="sticky top-0 z-10 bg-background/95 backdrop-blur-sm border-b border-outline-variant/30">
                <div className="max-w-[1200px] mx-auto px-5 h-20 flex items-center gap-3">
                    <Link to="/verifier" className="p-2 rounded-full hover:bg-surface-container-high text-on-surface-variant">
                        <span className="material-symbols-outlined">arrow_back</span>
                    </Link>
                    <h1 className="font-headline-sm text-primary font-bold tracking-tight">Verification History</h1>
                </div>
            </header>

            <main className="max-w-[1200px] mx-auto p-5 mt-6">
                <div className="flex items-center gap-3 mb-6">
                    <label className="text-sm text-on-surface-variant">Filter by result:</label>
                    <select
                        value={action}
                        onChange={(e) => setAction(e.target.value)}
                        className="h-10 px-3 rounded-lg border border-outline-variant bg-surface-container-lowest"
                    >
                        <option value="">All</option>
                        <option value="SIGNED">Signed</option>
                        <option value="REJECTED">Rejected</option>
                    </select>
                </div>

                {loading && <p>Loading...</p>}
                {error && <div className="p-4 rounded-xl bg-error-container text-on-error-container">{error}</div>}
                {!loading && !error && items.length === 0 && <p>No history yet.</p>}

                {!loading && items.length > 0 && (
                    <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 overflow-hidden">
                        <table className="w-full text-left">
                            <thead className="bg-surface-container-low text-sm text-on-surface-variant">
                            <tr>
                                <th className="p-4">Date</th>
                                <th className="p-4">Vault Owner</th>
                                <th className="p-4">Result</th>
                                <th className="p-4">Note</th>
                            </tr>
                            </thead>
                            <tbody>
                            {items.map((h) => (
                                <tr key={h.id} className="border-t border-outline-variant/20">
                                    <td className="p-4">{h.at}</td>
                                    <td className="p-4 font-bold">{h.owner}</td>
                                    <td className="p-4">
                                            <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                                                h.action === 'SIGNED'
                                                    ? 'bg-success-container text-on-success-container'
                                                    : 'bg-error-container text-on-error-container'
                                            }`}>
                                                {ACTION_LABEL[h.action]}
                                            </span>
                                    </td>
                                    <td className="p-4 text-on-surface-variant">{h.note || '—'}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </main>
        </div>
    );
}