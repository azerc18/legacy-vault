import React, { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { beneficiaryApi } from '../api/beneficiaryApi.js';
import { getErrorMessage } from '../api/http.js';
import { BeneficiaryLayout, Icon, StatusBadge, formatDate } from './beneficiaryUi.jsx';

export default function BeneficiaryVaults() {
    const user = getUser();
    const [vaults, setVaults] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        beneficiaryApi.listVaults()
            .then(setVaults)
            .catch((err) => setError(getErrorMessage(err, 'Could not load your vaults.')))
            .finally(() => setLoading(false));
    }, []);

    if (!user) return <Navigate to="/login" replace />;

    return (
        <BeneficiaryLayout title="Inherited vaults" subtitle="Vaults that have been handed over to you.">
            {error && <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}

            {loading ? (
                <p className="text-on-surface-variant">Loading...</p>
            ) : vaults.length === 0 ? (
                <div className="text-center py-16 bg-surface-container-lowest rounded-2xl border border-outline-variant/30">
                    <Icon name="inventory_2" className="text-[40px] text-outline" />
                    <p className="mt-3 text-on-surface-variant">No vault has been handed over to you yet.</p>
                </div>
            ) : (
                <div className="grid sm:grid-cols-2 gap-5">
                    {vaults.map((v) => (
                        <Link
                            key={v.vaultId}
                            to={`/beneficiary/vaults/${v.vaultId}`}
                            className="block bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 hover:border-primary transition-all"
                        >
                            <div className="flex justify-between items-start gap-3">
                                <div>
                                    <h3 className="font-title-lg text-title-lg">{v.vaultName}</h3>
                                    <p className="text-sm text-on-surface-variant mt-1">From {v.ownerName || 'Unknown owner'}</p>
                                </div>
                                <StatusBadge vaultStatus={v.vaultStatus} claimStatus={v.claimStatus} />
                            </div>
                            <dl className="mt-5 grid grid-cols-2 gap-3 text-sm">
                                <div>
                                    <dt className="text-outline">Unlocked</dt>
                                    <dd className="font-semibold">{formatDate(v.unlockedAt)}</dd>
                                </div>
                                <div>
                                    <dt className="text-outline">{v.claimedAt ? 'Claimed' : 'Claim before'}</dt>
                                    <dd className="font-semibold">{formatDate(v.claimedAt || v.claimDeadlineAt)}</dd>
                                </div>
                            </dl>
                        </Link>
                    ))}
                </div>
            )}
        </BeneficiaryLayout>
    );
}
