import React, { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Icon, Alert, formatDate, btnPrimary } from './ownerUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";
import EstateReadinessCard from './EstateReadinessCard.jsx';

export default function OwnerVaults() {
    const user = getUser();
    const [vaults, setVaults] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        ownerApi.listVaults()
            .then(setVaults)
            .catch((e) => setError(getErrorMessage(e, 'Could not load your vaults.')))
            .finally(() => setLoading(false));
    }, []);

    if (!user) return <Navigate to="/login" replace />;

    return (
        <OwnerLayout
            title="My vaults"
            subtitle="Store assets and legal documents for the people you trust."
            actions={
                <Link to="/owner/vaults/new" className={btnPrimary}>
                    <Icon name="add" className="text-[18px]" /> New vault
                </Link>
            }
        >
            <Alert>{error}</Alert>

            <EstateReadinessCard />

            {loading ? (
                <p className="text-on-surface-variant">Loading...</p>
            ) : vaults.length === 0 ? (
                <EmptyState bordered icon="inventory_2" title="No vaults yet"
                            text="A vault holds the assets and documents you want to pass on. Create one, then choose who receives it."
                            action={{ to: '/owner/vaults/new', label: 'Create your first vault', icon: 'add' }} />
            ) : (
                <div className="grid sm:grid-cols-2 gap-5">
                    {vaults.map((v) => (
                        <Link
                            key={v.id}
                            to={`/owner/vaults/${v.id}`}
                            className="block bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 hover:border-primary transition-all"
                        >
                            <div className="flex justify-between items-start gap-3">
                                <h3 className="font-title-lg text-title-lg">{v.name}</h3>
                                <div className="flex items-center gap-2">
                                    {!v.modifiable && (
                                        <span
                                            title={v.lockReason}
                                            className="shrink-0 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-error-container text-on-error-container flex items-center gap-1 cursor-help"
                                        >
                                            <Icon name="lock" className="text-[14px]" /> Locked
                                        </span>
                                    )}
                                    <span className="shrink-0 text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                                        {v.status}
                                    </span>
                                </div>
                            </div>
                            {v.description && <p className="mt-2 text-sm text-on-surface-variant line-clamp-2">{v.description}</p>}
                            <div className="mt-4 flex flex-wrap gap-4 text-xs text-on-surface-variant">
                                <span>Beneficiary: <strong className="text-on-surface">{v.beneficiaryEmail || 'None'}</strong></span>
                                <span>·</span>
                                <span>{v.totalAssets ?? 0} asset(s)</span>
                                <span>·</span>
                                <span>{v.totalDocuments ?? 0} doc(s)</span>
                            </div>
                            <p className="mt-2 text-xs text-outline">Created {formatDate(v.createdAt)}</p>
                        </Link>
                    ))}
                </div>
            )}
        </OwnerLayout>
    );
}
