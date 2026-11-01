import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { paymentApi } from '../api/paymentApi.js';
import { btnOutline, btnPrimary, Icon } from '../Owner/ownerUi.jsx';

const formatStorage = (bytes) => {
    if (!bytes) return '50 MB';
    const gb = bytes / (1024 * 1024 * 1024);
    if (gb >= 1) return `${gb.toFixed(0)} GB`;
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(0)} MB`;
};

export default function UpgradeDialog({ open, message, onClose }) {
    const { t } = useTranslation('payment');
    const navigate = useNavigate();
    const [sub, setSub] = useState(null);

    useEffect(() => {
        if (!open) return;
        paymentApi
            .getMySubscription()
            .then(setSub)
            .catch(() => {});
    }, [open]);

    if (!open) return null;

    const handleUpgrade = () => {
        if (onClose) onClose();
        navigate('/pricing');
    };

    // m4: lấy giới hạn từ availablePlans của API, không viết cứng
    const vipPlan = sub?.availablePlans?.find((p) => p.code === 'VIP_MONTH' || p.code === 'VIP_YEAR')
        || sub?.availablePlans?.find((p) => p.code !== 'FREE');
    const maxVaults = vipPlan?.maxVaults || 5;
    const maxBeneficiaries = vipPlan?.maxBeneficiariesPerVault || 10;
    const storageStr = formatStorage(vipPlan?.maxStorageBytes || 5368709120);

    return (
        <div
            className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center px-4"
            onClick={onClose}
        >
            <div
                className="bg-surface-container-lowest rounded-2xl p-6 w-full max-w-md border border-outline-variant/30 shadow-xl"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-center gap-2 text-primary">
                    <Icon name="workspace_premium" className="text-[26px]" />
                    <h3 className="font-title-lg text-title-lg">{t('upgradeDialog.title')}</h3>
                </div>
                <p className="mt-3 text-sm text-on-surface-variant">
                    {message ||
                        t('upgradeDialog.defaultMessage', {
                            vaults: maxVaults,
                            beneficiaries: maxBeneficiaries,
                            storage: storageStr,
                        })}
                </p>
                <ul className="mt-4 space-y-2 text-sm text-on-surface">
                    <li className="flex items-center gap-2">
                        <Icon name="check_circle" className="text-primary text-[18px]" />
                        {t('upgradeDialog.features.vaults', {
                            vaults: maxVaults,
                            beneficiaries: maxBeneficiaries,
                        })}
                    </li>
                    <li className="flex items-center gap-2">
                        <Icon name="check_circle" className="text-primary text-[18px]" />
                        {t('upgradeDialog.features.storage', { storage: storageStr })}
                    </li>
                    <li className="flex items-center gap-2">
                        <Icon name="check_circle" className="text-primary text-[18px]" />
                        {t('upgradeDialog.features.media')}
                    </li>
                </ul>
                <div className="mt-6 flex justify-end gap-3">
                    <button type="button" onClick={onClose} className={btnOutline}>
                        {t('upgradeDialog.notNow')}
                    </button>
                    <button type="button" onClick={handleUpgrade} className={btnPrimary}>
                        <Icon name="workspace_premium" className="text-[18px]" />
                        {t('upgradeDialog.viewPlans')}
                    </button>
                </div>
            </div>
        </div>
    );
}
