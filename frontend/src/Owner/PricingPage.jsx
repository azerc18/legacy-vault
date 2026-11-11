import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { paymentApi } from '../api/paymentApi.js';
import { getErrorMessage } from '../api/http.js';
import { formatStorage } from '../i18n/format.js';
import { Alert, btnOutline, btnPrimary, Card, formatDate, Icon, OwnerLayout, LoadingSkeleton } from './ownerUi.jsx';

const formatVnd = (n) =>
    typeof n === 'number'
        ? n.toLocaleString('vi-VN') + ' ₫'
        : '0 ₫';

const PROVIDER_LABEL = { VNPAY: 'VNPay', PAYPAL: 'PayPal' };
const PROVIDER_ICON = { VNPAY: 'qr_code_2', PAYPAL: 'account_balance_wallet' };
// Logo chính thức (chỉ phần biểu tượng, không chữ). Nằm trên ô nền trắng để giữ đúng màu thương hiệu ở chế độ tối.
const PROVIDER_LOGO = { VNPAY: '/brand/payment/vnpay.png', PAYPAL: '/brand/payment/paypal.png' };

function ProviderMark({ code }) {
    const logo = PROVIDER_LOGO[code];
    if (!logo) {
        return <Icon name={PROVIDER_ICON[code] ?? 'payments'} className="text-[28px] text-secondary" />;
    }
    return (
        <span className="w-10 h-10 shrink-0 rounded-lg bg-white border border-outline-variant/40 flex items-center justify-center p-1.5">
            <img src={logo} alt="" width="28" height="28" className="w-7 h-7 object-contain" />
        </span>
    );
}

// n3: % tiết kiệm của gói năm so với 12 tháng gói tháng, tính từ giá thật; không tính được thì null
const yearlySavingPercent = (plans) => {
    const month = plans.find((p) => p.code === 'VIP_MONTH');
    const year = plans.find((p) => p.code === 'VIP_YEAR');
    if (!month?.priceVnd || !year?.priceVnd) return null;
    const percent = Math.round((1 - year.priceVnd / (month.priceVnd * 12)) * 100);
    return percent > 0 ? percent : null;
};

export default function PricingPage() {
    const { t } = useTranslation('payment');
    const [sub, setSub] = useState(null);
    const [loading, setLoading] = useState(true);
    const [busyCode, setBusyCode] = useState('');
    const [error, setError] = useState('');
    const [loadError, setLoadError] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [providers, setProviders] = useState(['VNPAY']);
    const [provider, setProvider] = useState('VNPAY');

    // Lỗi thì giữ VNPay như trước khi có nhiều cổng
    useEffect(() => {
        paymentApi
            .getProviders()
            .then((list) => {
                if (list?.length) {
                    setProviders(list);
                    setProvider(list[0]);
                }
            })
            .catch(() => {});
    }, []);

    // n1: danh sách gói lấy từ API (availablePlans của /subscriptions/me), không có danh sách viết cứng
    useEffect(() => {
        paymentApi
            .getMySubscription()
            .then((data) => {
                setSub(data);
                setLoadError('');
            })
            .catch((e) => setLoadError(getErrorMessage(e, t('pricing.errors.loadFailed'))))
            .finally(() => setLoading(false));
    }, [t, reloadKey]);

    const retryLoad = () => {
        setLoading(true);
        setLoadError('');
        setReloadKey((k) => k + 1);
    };

    const handleCheckout = async (planCode) => {
        setError('');
        setBusyCode(planCode);
        try {
            const res = await paymentApi.checkout(planCode, provider);
            if (res?.paymentUrl) {
                window.location.href = res.paymentUrl;
            }
        } catch (e) {
            setError(getErrorMessage(e, t('pricing.errors.checkoutFailed')));
            setBusyCode('');
        }
    };

    const plans = sub?.availablePlans ?? [];
    const savingPercent = yearlySavingPercent(plans);
    const providerName = PROVIDER_LABEL[provider] ?? provider;

    return (
        <OwnerLayout
            title={t('pricing.title')}
            subtitle={t('pricing.subtitle')}
            back={{ to: '/billing', label: t('pricing.back') }}
        >
            <Alert>{error}</Alert>

            {sub && (
                <div className="mb-6 p-4 rounded-2xl bg-surface-container-lowest border border-outline-variant/30 flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2 text-sm">
                        <Icon name="workspace_premium" className="text-primary text-[20px]" />
                        <span>
                            {t('pricing.currentPlan')}{' '}
                            <strong>{sub.planName || sub.planCode}</strong> ({sub.status})
                            {sub.expiresAt && (
                                <span className="text-on-surface-variant">
                                    {' '}
                                    · {t('pricing.expires', { date: formatDate(sub.expiresAt) })}
                                </span>
                            )}
                        </span>
                    </div>
                    <Link to="/billing" className={btnOutline}>
                        <Icon name="receipt_long" className="text-[18px]" /> {t('pricing.viewBilling')}
                    </Link>
                </div>
            )}

            {/* Hiện cả khi chỉ bật 1 cổng để người dùng luôn thấy logo cổng sẽ dùng */}
            {providers.length > 0 && (
                <fieldset className="mb-6">
                    <legend className="text-sm font-medium mb-2">{t('pricing.providerLabel')}</legend>
                    <div className="flex flex-wrap gap-4">
                        {providers.map((code) => (
                            <label key={code}
                                   className={`flex items-center gap-3 rounded-xl border-2 px-4 py-3 cursor-pointer transition-colors focus-within:ring-2 focus-within:ring-secondary focus-within:ring-offset-2 ${
                                       provider === code
                                           ? 'border-secondary bg-secondary/10'
                                           : 'border-outline-variant/50 hover:border-outline'}`}>
                                <input
                                    type="radio"
                                    name="payment-provider"
                                    value={code}
                                    checked={provider === code}
                                    onChange={() => setProvider(code)}
                                    className="sr-only"
                                />
                                <ProviderMark code={code} />
                                <span className="text-sm font-semibold">{PROVIDER_LABEL[code] ?? code}</span>
                                {provider === code && <Icon name="check_circle" className="text-[18px] text-secondary" />}
                            </label>
                        ))}
                    </div>
                    {provider === 'PAYPAL' && (
                        <p className="mt-2 text-xs text-on-surface-variant">{t('pricing.paypalNote')}</p>
                    )}
                </fieldset>
            )}

            {loading ? (
                <LoadingSkeleton label={t('pricing.loading')} />
            ) : loadError || plans.length === 0 ? (
                <Card className="text-center">
                    <p className="text-sm text-on-surface-variant">{loadError || t('pricing.errors.noPlans')}</p>
                    <button type="button" onClick={retryLoad} className={`${btnOutline} mt-4`}>
                        <Icon name="refresh" className="text-[18px]" /> {t('pricing.retry')}
                    </button>
                </Card>
            ) : (
                <div className="grid md:grid-cols-3 gap-6">
                    {plans.map((p) => {
                        const isFree = p.code === 'FREE' || p.priceVnd <= 0;
                        const isCurrent = sub?.planCode === p.code && sub?.status === 'ACTIVE';
                        const isPopular = p.code === 'VIP_YEAR';
                        const showSaving = isPopular && savingPercent !== null;

                        return (
                            <Card
                                key={p.code}
                                className={`flex flex-col justify-between ${
                                    isPopular ? 'border-primary ring-1 ring-primary' : ''
                                }`}
                            >
                                <div>
                                    <div className="flex items-center justify-between gap-2">
                                        <h2 className="font-title-lg text-title-lg">{p.name}</h2>
                                        {showSaving && (
                                            <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-primary text-on-primary">
                                                {t('pricing.savePercent', { percent: savingPercent })}
                                            </span>
                                        )}
                                    </div>
                                    <p className="mt-3 text-2xl font-bold text-primary">
                                        {isFree ? t('pricing.free') : formatVnd(p.priceVnd)}
                                        {!isFree && p.durationDays && (
                                            <span className="text-sm font-normal text-on-surface-variant">
                                                {' '}
                                                {t('pricing.perDays', { days: p.durationDays })}
                                            </span>
                                        )}
                                    </p>

                                    <ul className="mt-6 space-y-3 text-sm text-on-surface">
                                        <li className="flex items-center gap-2">
                                            <Icon name="inventory_2" className="text-secondary text-[18px]" />
                                            <span>
                                                {isFree
                                                    ? t('pricing.features.oneMasterVault')
                                                    : t('pricing.features.upToVaults', { count: p.maxVaults })}
                                            </span>
                                        </li>
                                        <li className="flex items-center gap-2">
                                            <Icon name="group" className="text-secondary text-[18px]" />
                                            <span>
                                                {t('pricing.features.upToBeneficiaries', {
                                                    count: p.maxBeneficiariesPerVault,
                                                })}
                                            </span>
                                        </li>
                                        <li className="flex items-center gap-2">
                                            <Icon
                                                name={!isFree ? 'check_circle' : 'cancel'}
                                                className={`text-[18px] ${!isFree ? 'text-primary' : 'text-outline'}`}
                                            />
                                            <span>
                                                {!isFree
                                                    ? t('pricing.features.advancedAllocationIncluded')
                                                    : t('pricing.features.advancedAllocationVipOnly')}
                                            </span>
                                        </li>
                                        <li className="flex items-center gap-2">
                                            <Icon name="cloud_upload" className="text-secondary text-[18px]" />
                                            <span>
                                                {t('pricing.features.storage', {
                                                    size: formatStorage(p.maxStorageBytes),
                                                })}
                                            </span>
                                        </li>
                                    </ul>
                                </div>

                                <div className="mt-8">
                                    {isFree ? (
                                        <button type="button" disabled className={`${btnOutline} w-full`}>
                                            {isCurrent
                                                ? t('pricing.buttons.currentPlan')
                                                : t('pricing.buttons.included')}
                                        </button>
                                    ) : (
                                        <button
                                            type="button"
                                            disabled={busyCode === p.code}
                                            onClick={() => handleCheckout(p.code)}
                                            className={`${btnPrimary} w-full`}
                                        >
                                            <Icon name="payments" className="text-[18px]" />
                                            {busyCode === p.code
                                                ? t('pricing.buttons.redirecting', { provider: providerName })
                                                : isCurrent
                                                ? t('pricing.buttons.extend', { provider: providerName })
                                                : t('pricing.buttons.upgrade', { provider: providerName })}
                                        </button>
                                    )}
                                </div>
                            </Card>
                        );
                    })}
                </div>
            )}
        </OwnerLayout>
    );
}
