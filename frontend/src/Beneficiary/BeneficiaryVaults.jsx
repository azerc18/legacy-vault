import React, { useEffect, useRef, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';
import { beneficiaryApi } from '../api/beneficiaryApi.js';
import { getErrorMessage } from '../api/http.js';
import { BeneficiaryLayout, Icon, StatusBadge, formatDate } from './beneficiaryUi.jsx';
import { LoadingSkeleton } from '../Owner/ownerUi.jsx';

export default function BeneficiaryVaults() {
    const { t } = useTranslation('beneficiary');
    const tRef = useRef(t);          // để đổi ngôn ngữ không kích hoạt tải lại danh sách
    tRef.current = t;

    const user = getUser();
    const [vaults, setVaults] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        beneficiaryApi.listVaults()
            .then((list) => setVaults(Array.isArray(list) ? list : []))
            .catch((err) => setError(getErrorMessage(err, tRef.current('list.loadError'))))
            .finally(() => setLoading(false));
    }, []);

    if (!user) return <Navigate to="/login" replace />;

    return (
        <BeneficiaryLayout title={t('list.title')} subtitle={t('list.subtitle')}>
            <div data-tour="beneficiary-header">
                {error && <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}
            </div>

            {loading ? (
                <LoadingSkeleton label={t('list.loading')}/>
            ) : vaults.length === 0 ? (
                <div data-tour="beneficiary-vault-list" className="text-center py-16 bg-surface-container-lowest rounded-2xl border border-outline-variant/30">
                    <Icon name="inventory_2" className="text-[40px] text-outline" />
                    <p className="mt-3 text-on-surface-variant">{t('list.empty')}</p>
                </div>
            ) : (
                <div data-tour="beneficiary-vault-list" className="lv-feature-grid grid sm:grid-cols-2 gap-5">
                    {vaults.map((v) => (
                        <Link
                            key={v.vaultId}
                            to={`/beneficiary/vaults/${v.vaultId}`}
                            className="lv-feature lv-feature-link block bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 hover:border-primary"
                        >
                            <div className="flex justify-between items-start gap-3">
                                <div>
                                    <h3 className="font-title-lg text-title-lg">{v.vaultName}</h3>
                                    <p className="text-sm text-on-surface-variant mt-1">
                                        {t('list.from', { owner: v.ownerName || t('unknownOwner') })}
                                    </p>
                                </div>
                                <StatusBadge vaultStatus={v.vaultStatus} claimStatus={v.claimStatus} myStatus={v.myStatus} />
                            </div>
                            <dl className="mt-5 grid grid-cols-2 gap-3 text-sm">
                                <div>
                                    <dt className="text-outline">{t('list.unlocked')}</dt>
                                    <dd className="font-semibold">{formatDate(v.unlockedAt)}</dd>
                                </div>
                                <div>
                                    <dt className="text-outline">{v.claimedAt ? t('list.claimed') : t('list.claimBefore')}</dt>
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
