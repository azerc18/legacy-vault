import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Icon, Alert, formatDate, btnPrimary, LoadingSkeleton } from './ownerUi.jsx';
import EmptyState from '../Common/EmptyState.jsx';
import EstateReadinessCard from './EstateReadinessCard.jsx';
import { startSpotlightTour } from '../Common/SpotlightTour.jsx';

export default function OwnerVaults() {
    const { t } = useTranslation('vault');
    const { t: tOnboarding } = useTranslation('onboarding');
    const user = getUser();
    const [vaults, setVaults] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        ownerApi.listVaults()
            .then(setVaults)
            .catch((e) => setError(getErrorMessage(e, t('list.loadError'))))
            .finally(() => setLoading(false));
    }, [t]);

    if (!user) return <Navigate to="/login" replace />;

    return (
        <OwnerLayout
            title={t('list.title')}
            subtitle={t('list.subtitle')}
            actions={
                <div className="flex items-center gap-2.5">
                    <button
                        type="button"
                        onClick={startSpotlightTour}
                        className="h-10 px-3.5 rounded-full border border-outline-variant/40 hover:border-primary text-xs font-bold inline-flex items-center gap-1.5 bg-surface-container-lowest text-on-surface hover:text-primary transition-colors shadow-sm"
                        title={tOnboarding('startInteractiveTour', 'Bắt đầu hướng dẫn từng bước (Interactive Tour)')}
                    >
                        <Icon name="rocket_launch" className="text-[18px] text-amber-500" />
                        <span>{tOnboarding('guideBtn', 'Hướng dẫn')}</span>
                    </button>
                    <Link to="/owner/vaults/new" data-tour="new-vault-btn" className={btnPrimary}>
                        <Icon name="add" className="text-[18px]" /> {t('list.newVault')}
                    </Link>
                </div>
            }
        >
            <Alert>{error}</Alert>

            <EstateReadinessCard />

            {loading ? (
                <LoadingSkeleton label={t('ui.loading')} />
            ) : vaults.length === 0 ? (
                <EmptyState bordered icon="inventory_2" title={t('list.emptyTitle')}
                    text={t('list.emptyText')}
                    action={{ to: '/owner/vaults/new', label: t('list.emptyAction'), icon: 'add' }} />
            ) : (
                <div className="lv-feature-grid grid sm:grid-cols-2 gap-5">
                    {vaults.map((v) => (
                        <Link
                            key={v.id}
                            to={`/owner/vaults/${v.id}`}
                            className="lv-feature lv-feature-link lv-glass-regular block h-full bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 hover:border-secondary transition-all"
                        >
                            <div className="flex justify-between items-start gap-3">
                                <h3 className="font-title-lg text-title-lg">{v.name}</h3>
                                <div className="flex items-center gap-2">
                                    {!v.modifiable && (
                                        <span
                                            title={v.lockReason}
                                            className="shrink-0 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-error-container text-on-error-container flex items-center gap-1 cursor-help"
                                        >
                                            <Icon name="lock" className="text-[14px]" /> {t('list.locked')}
                                        </span>
                                    )}
                                    <span className="shrink-0 text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                                        {t(`status.${v.status}`, { defaultValue: v.status })}
                                    </span>
                                </div>
                            </div>
                            {v.description && <p className="mt-2 text-sm text-on-surface-variant line-clamp-2">{v.description}</p>}
                            <div className="mt-4 flex flex-wrap gap-4 text-xs text-on-surface-variant">
                                <span>{t('list.beneficiary')} <strong className="text-on-surface">{v.beneficiaryEmail || t('list.none')}</strong></span>
                                <span>·</span>
                                <span>{t('list.assetsCount', { count: v.totalAssets ?? 0 })}</span>
                                <span>·</span>
                                <span>{t('list.docsCount', { count: v.totalDocuments ?? 0 })}</span>
                            </div>
                            <p className="mt-2 text-xs text-outline">{t('list.createdOn', { date: formatDate(v.createdAt) })}</p>
                        </Link>
                    ))}
                </div>
            )}
        </OwnerLayout>
    );
}
