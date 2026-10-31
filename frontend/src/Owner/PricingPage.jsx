import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { paymentApi } from '../api/paymentApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnOutline, btnPrimary, Card, formatDate, Icon, OwnerLayout } from './ownerUi.jsx';

const formatVnd = (n) =>
    typeof n === 'number'
        ? n.toLocaleString('vi-VN') + ' ₫'
        : '0 ₫';

const formatStorage = (bytes) => {
    if (!bytes) return '50 MB';
    const gb = bytes / (1024 * 1024 * 1024);
    if (gb >= 1) return `${gb.toFixed(0)} GB`;
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(0)} MB`;
};

export default function PricingPage() {
    const [sub, setSub] = useState(null);
    const [loading, setLoading] = useState(true);
    const [busyCode, setBusyCode] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        paymentApi
            .getMySubscription()
            .then(setSub)
            .catch((e) => setError(getErrorMessage(e, 'Could not load subscription plans.')))
            .finally(() => setLoading(false));
    }, []);

    const handleCheckout = async (planCode) => {
        setError('');
        setBusyCode(planCode);
        try {
            const res = await paymentApi.checkout(planCode);
            if (res?.paymentUrl) {
                window.location.href = res.paymentUrl;
            }
        } catch (e) {
            setError(getErrorMessage(e, 'Could not initiate VNPay checkout.'));
            setBusyCode('');
        }
    };

    const plans = sub?.availablePlans?.length
        ? sub.availablePlans
        : [
              {
                  code: 'FREE',
                  name: 'Free Plan',
                  priceVnd: 0,
                  durationDays: null,
                  maxVaults: 1,
                  maxBeneficiariesPerVault: 2,
                  maxStorageBytes: 52428800,
                  legacyMessageEnabled: false,
              },
              {
                  code: 'VIP_MONTH',
                  name: 'VIP Monthly',
                  priceVnd: 99000,
                  durationDays: 30,
                  maxVaults: 5,
                  maxBeneficiariesPerVault: 10,
                  maxStorageBytes: 5368709120,
                  legacyMessageEnabled: true,
              },
              {
                  code: 'VIP_YEAR',
                  name: 'VIP Yearly',
                  priceVnd: 990000,
                  durationDays: 365,
                  maxVaults: 5,
                  maxBeneficiariesPerVault: 10,
                  maxStorageBytes: 5368709120,
                  legacyMessageEnabled: true,
              },
          ];

    return (
        <OwnerLayout
            title="Plans & Pricing"
            subtitle="Protect your digital estate with specialized vaults, contingent beneficiaries, proportional (%) allocation, and 5 GB encrypted storage."
            back={{ to: '/billing', label: 'Billing & Subscription' }}
        >
            <Alert>{error}</Alert>

            {sub && (
                <div className="mb-6 p-4 rounded-2xl bg-surface-container-lowest border border-outline-variant/30 flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2 text-sm">
                        <Icon name="workspace_premium" className="text-primary text-[20px]" />
                        <span>
                            Current plan: <strong>{sub.planName || sub.planCode}</strong> ({sub.status})
                            {sub.expiresAt && (
                                <span className="text-on-surface-variant">
                                    {' '}
                                    · Expires {formatDate(sub.expiresAt)}
                                </span>
                            )}
                        </span>
                    </div>
                    <Link to="/billing" className={btnOutline}>
                        <Icon name="receipt_long" className="text-[18px]" /> View billing history
                    </Link>
                </div>
            )}

            {loading ? (
                <p className="text-on-surface-variant">Loading plans...</p>
            ) : (
                <div className="grid md:grid-cols-3 gap-6">
                    {plans.map((p) => {
                        const isFree = p.code === 'FREE' || p.priceVnd <= 0;
                        const isCurrent = sub?.planCode === p.code && sub?.status === 'ACTIVE';
                        const isPopular = p.code === 'VIP_YEAR';

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
                                        {isPopular && (
                                            <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-primary text-on-primary">
                                                Save 17%
                                            </span>
                                        )}
                                    </div>
                                    <p className="mt-3 text-2xl font-bold text-primary">
                                        {isFree ? 'Free' : formatVnd(p.priceVnd)}
                                        {!isFree && p.durationDays && (
                                            <span className="text-sm font-normal text-on-surface-variant">
                                                {' '}
                                                / {p.durationDays} days
                                            </span>
                                        )}
                                    </p>

                                    <ul className="mt-6 space-y-3 text-sm text-on-surface">
                                        <li className="flex items-center gap-2">
                                            <Icon name="inventory_2" className="text-secondary text-[18px]" />
                                            <span>
                                                {isFree ? (
                                                    <><strong>1</strong> Master Estate Vault</>
                                                ) : (
                                                    <>Up to <strong>{p.maxVaults}</strong> specialized vaults</>
                                                )}
                                            </span>
                                        </li>
                                        <li className="flex items-center gap-2">
                                            <Icon name="group" className="text-secondary text-[18px]" />
                                            <span>
                                                Up to <strong>{p.maxBeneficiariesPerVault}</strong> beneficiaries / vault
                                            </span>
                                        </li>
                                        <li className="flex items-center gap-2">
                                            <Icon
                                                name={!isFree ? 'check_circle' : 'cancel'}
                                                className={`text-[18px] ${!isFree ? 'text-primary' : 'text-outline'}`}
                                            />
                                            <span>
                                                Contingent beneficiaries &amp; Proportional (%) split{' '}
                                                {!isFree ? '(Included)' : '(VIP only)'}
                                            </span>
                                        </li>
                                        <li className="flex items-center gap-2">
                                            <Icon name="cloud_upload" className="text-secondary text-[18px]" />
                                            <strong>{formatStorage(p.maxStorageBytes)}</strong> encrypted storage
                                        </li>
                                    </ul>
                                </div>

                                <div className="mt-8">
                                    {isFree ? (
                                        <button type="button" disabled className={`${btnOutline} w-full`}>
                                            {isCurrent ? 'Current Plan' : 'Included by Default'}
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
                                                ? 'Redirecting to VNPay...'
                                                : isCurrent
                                                ? 'Extend with VNPay'
                                                : 'Upgrade with VNPay'}
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
