import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { paymentApi } from '../api/paymentApi.js';
import { formatStorage } from '../i18n/format.js';
import { btnOutline, btnPrimary, Icon } from '../Owner/ownerUi.jsx';

// feature (T1 m6): PlanFeature BE trả về trong data.feature, dùng để chọn câu upgradeDialog.feature.<FEATURE>
export default function UpgradeDialog({ open, message, feature, onClose }) {
    const { t, i18n } = useTranslation('payment');
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

    // Portal + z-[70]: hộp thoại này phải nằm TRƯỚC mọi hộp thoại khác đang mở (vd. form thêm asset, z-50 cũng gắn vào body).
    // Bấm "Để sau" chỉ đóng hộp này, form bên dưới vẫn còn nguyên dữ liệu đã nhập.
    return createPortal(
        <div
            className="lv-modal-backdrop fixed inset-0 z-[70] bg-black/40 dark:bg-black/70 backdrop-blur-md flex items-center justify-center px-4 animate-in fade-in duration-200"
            onClick={onClose}
        >
            <div
                className="lv-surface-secure bg-surface-container-lowest text-on-surface rounded-2xl sm:rounded-3xl p-6 w-full max-w-md border border-outline-variant/30 dark:border-white/15 shadow-2xl animate-in zoom-in-95 duration-200"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-center gap-2 text-primary">
                    <Icon name="workspace_premium" className="text-[26px]" />
                    <h3 className="font-title-lg text-title-lg">{t('upgradeDialog.title')}</h3>
                </div>
                <p className="mt-3 text-sm text-on-surface-variant">
                    {message ||
                        t(
                            feature && i18n.exists(`upgradeDialog.feature.${feature}`, { ns: 'payment' })
                                ? `upgradeDialog.feature.${feature}`
                                : 'upgradeDialog.defaultMessage',
                            {
                                vaults: maxVaults,
                                beneficiaries: maxBeneficiaries,
                                storage: storageStr,
                            },
                        )}
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
        </div>,
        document.body,
    );
}
