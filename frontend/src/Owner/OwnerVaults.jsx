import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router-dom';
import { Tilt, TiltContent } from '@animate-ui/react/primitives/effects/tilt';
import { Effects } from '@animate-ui/react/primitives/effects/effect';
import { ReducedMotionConfig } from '@animate-ui/react/utils/motion';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Icon, Alert, formatDate, btnPrimary } from './ownerUi.jsx';
import EmptyState from '../Common/EmptyState.jsx';
import EstateReadinessCard from './EstateReadinessCard.jsx';
import { openOnboardingGuide } from '../Common/OnboardingGuideModal.jsx';
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
                <p className="text-on-surface-variant">{t('ui.loading')}</p>
            ) : vaults.length === 0 ? (
                <div className="space-y-5">
                    <EmptyState bordered icon="inventory_2" title={t('list.emptyTitle')}
                        text={t('list.emptyText')}
                        action={{ to: '/owner/vaults/new', label: t('list.emptyAction'), icon: 'add' }} />

                    {/* Banner gợi ý hướng dẫn tân thủ */}
                    <div className="p-5 sm:p-6 rounded-2xl bg-surface-container-lowest border border-outline-variant/30 flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-sm">
                        <div className="flex items-start gap-4">
                            <div className="w-11 h-11 rounded-2xl bg-gradient-to-br from-amber-400 to-orange-500 text-slate-950 flex items-center justify-center shrink-0 shadow-md">
                                <Icon name="school" className="text-[24px]" />
                            </div>
                            <div>
                                <div className="flex items-center gap-2">
                                    <h4 className="font-title-lg text-title-lg text-on-surface">{tOnboarding('banner.title')}</h4>
                                    <span className="px-2 py-0.5 rounded text-[10px] font-extrabold uppercase bg-amber-400/20 text-amber-600 dark:text-amber-400">
                                        {tOnboarding('newFeatureTag')}
                                    </span>
                                </div>
                                <p className="text-sm text-on-surface-variant mt-1 max-w-xl">{tOnboarding('banner.subtitle')}</p>
                            </div>
                        </div>
                        <div className="flex items-center gap-2.5 shrink-0">
                            <button
                                type="button"
                                onClick={openOnboardingGuide}
                                className="h-10 px-3.5 rounded-xl border border-outline-variant/60 hover:bg-surface-container-low text-xs font-semibold text-on-surface transition-colors"
                            >
                                <span>{tOnboarding('banner.exploreGuide', 'Xem cẩm nang')}</span>
                            </button>
                            <button
                                type="button"
                                onClick={startSpotlightTour}
                                className="h-10 px-4 rounded-xl bg-primary text-on-primary text-xs font-bold hover:opacity-90 transition-opacity flex items-center justify-center gap-2 shadow-sm"
                            >
                                <span>{tOnboarding('banner.action')}</span>
                                <Icon name="rocket_launch" className="text-[16px]" />
                            </button>
                        </div>
                    </div>
                </div>
            ) : (
                <ReducedMotionConfig remount>
                    <div className="grid sm:grid-cols-2 gap-5">
                        <Effects fade slide={{ direction: 'up', offset: 14 }} blur={{ initialBlur: 4 }} holdDelay={65}>
                            {vaults.map((v) => (
                                <Tilt key={v.id} maxTilt={3.5} perspective={1000}>
                                    <TiltContent className="h-full">
                                        <Link
                                            to={`/owner/vaults/${v.id}`}
                                            className="block h-full bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 hover:border-secondary transition-colors"
                                        >
                                            <div className="flex justify-between items-start">
                                                <div className="flex items-center gap-3">
                                                    <span className="w-12 h-12 rounded-2xl bg-surface-container-high flex items-center justify-center text-primary border border-outline-variant/30">
                                                        <Icon name="inventory_2" className="text-[26px]" />
                                                    </span>
                                                    <div>
                                                        <div className="flex items-center gap-2">
                                                            <h3 className="font-title-lg text-title-lg text-on-surface">{v.name}</h3>
                                                            {v.isSealed && (
                                                                <span className="inline-flex items-center gap-0.5 px-2 py-0.5 rounded text-[11px] font-semibold bg-primary/10 text-primary">
                                                                    <Icon name="lock" className="text-[13px]" /> {t('detail.sealed')}
                                                                </span>
                                                            )}
                                                        </div>
                                                        <p className="text-xs text-on-surface-variant mt-0.5">
                                                            {t('list.created', { date: formatDate(v.createdAt) })}
                                                        </p>
                                                    </div>
                                                </div>
                                            </div>
                                            {v.description && (
                                                <p className="text-sm text-on-surface-variant line-clamp-2 mt-4">{v.description}</p>
                                            )}
                                        </Link>
                                    </TiltContent>
                                </Tilt>
                            ))}
                        </Effects>
                    </div>
                </ReducedMotionConfig>
            )}
        </OwnerLayout>
    );
}
