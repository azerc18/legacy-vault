import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { paymentApi } from '../api/paymentApi.js';
import { btnOutline, btnPrimary, Card, Icon, OwnerLayout } from './ownerUi.jsx';

const formatStorage = (bytes) => {
    if (!bytes) return '50 MB';
    const gb = bytes / (1024 * 1024 * 1024);
    if (gb >= 1) return `${gb.toFixed(0)} GB`;
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(0)} MB`;
};

export default function PaymentResultPage() {
    const { t } = useTranslation('payment');
    const [searchParams] = useSearchParams();
    const statusParam = searchParams.get('status') || '';
    const txId = searchParams.get('txnRef') || searchParams.get('txId');
    const errorCode = searchParams.get('errorCode') || searchParams.get('error');
    const isFailedParam = statusParam.toLowerCase() === 'failed' || !!errorCode;

    const [sub, setSub] = useState(null);
    const [confirming, setConfirming] = useState(!isFailedParam);
    const [attempt, setAttempt] = useState(1);

    useEffect(() => {
        // n3: xoá cache khi vào /payment/result
        try {
            sessionStorage.removeItem('lv_sub_cache');
        } catch {
            // ignore sessionStorage error
        }

        if (isFailedParam) {
            setConfirming(false);
            return;
        }

        let mounted = true;
        let timer = null;

        // m1: gọi /subscriptions/me lấy trạng thái thật; chưa VIP thì thử lại mỗi 3 giây, tối đa 5 lần
        const poll = (count) => {
            paymentApi
                .getMySubscription()
                .then((data) => {
                    if (!mounted) return;
                    setSub(data);
                    if (data?.vip) {
                        setConfirming(false);
                    } else if (count < 5) {
                        setAttempt(count + 1);
                        timer = setTimeout(() => poll(count + 1), 3000);
                    } else {
                        setConfirming(false);
                    }
                })
                .catch(() => {
                    if (!mounted) return;
                    if (count < 5) {
                        setAttempt(count + 1);
                        timer = setTimeout(() => poll(count + 1), 3000);
                    } else {
                        setConfirming(false);
                    }
                });
        };

        poll(1);

        return () => {
            mounted = false;
            if (timer) clearTimeout(timer);
        };
    }, [isFailedParam]);

    const isSuccess = !isFailedParam && !confirming && sub?.vip;

    // m4: lấy giới hạn từ availablePlans hoặc sub của API, không viết cứng
    const vipPlan = sub?.availablePlans?.find((p) => p.code === 'VIP_MONTH' || p.code === 'VIP_YEAR')
        || sub?.availablePlans?.find((p) => p.code !== 'FREE');
    const maxVaults = sub?.vip ? sub.maxVaults : (vipPlan?.maxVaults || 5);
    const maxBeneficiaries = sub?.vip ? sub.maxBeneficiariesPerVault : (vipPlan?.maxBeneficiariesPerVault || 10);
    const maxStorage = formatStorage(sub?.vip ? sub.maxStorageBytes : (vipPlan?.maxStorageBytes || 5368709120));
    const hasMedia = sub?.vip ? sub.legacyMessageEnabled : (vipPlan?.legacyMessageEnabled ?? true);

    const title = confirming
        ? t('result.confirmingTitle')
        : isSuccess
        ? t('result.successTitle')
        : t('result.failedTitle');

    const subtitle = confirming
        ? t('result.confirmingSubtitle')
        : isSuccess
        ? t('result.successSubtitle')
        : t('result.failedSubtitle');

    return (
        <OwnerLayout title={title} subtitle={subtitle}>
            <Card className="max-w-xl mx-auto text-center">
                <div className="flex justify-center">
                    <span
                        className={`w-16 h-16 rounded-full flex items-center justify-center ${
                            confirming
                                ? 'bg-secondary-fixed text-on-secondary-fixed animate-pulse'
                                : isSuccess
                                ? 'bg-success-container text-on-success-container'
                                : 'bg-error-container text-on-error-container'
                        }`}
                    >
                        <Icon
                            name={confirming ? 'hourglass_top' : isSuccess ? 'check_circle' : 'error'}
                            className="text-[36px]"
                        />
                    </span>
                </div>

                <h2 className="mt-4 font-title-lg text-title-lg">
                    {confirming
                        ? t('result.confirmingHeading')
                        : isSuccess
                        ? t('result.welcomeVip')
                        : t('result.failedHeading')}
                </h2>

                <p className="mt-2 text-sm text-on-surface-variant">
                    {confirming
                        ? t('result.confirmingMessage', { attempt, maxAttempts: 5 })
                        : isSuccess
                        ? t('result.successDesc', {
                              vaults: maxVaults,
                              beneficiaries: maxBeneficiaries,
                              storage: maxStorage,
                              media: hasMedia ? t('result.withMedia') : '',
                          })
                        : errorCode
                        ? t('result.reason', { code: errorCode })
                        : t('result.failedDefault')}
                </p>

                {txId && (
                    <p className="mt-2 text-xs text-outline">
                        {t('result.txId', { id: txId })}
                    </p>
                )}

                <div className="mt-6 flex flex-wrap justify-center gap-3">
                    <Link to="/billing" className={btnOutline}>
                        <Icon name="receipt_long" className="text-[18px]" /> {t('result.viewBilling')}
                    </Link>
                    <Link
                        to={isSuccess ? '/owner/vaults' : '/pricing'}
                        className={btnPrimary}
                    >
                        <Icon name={isSuccess ? 'inventory_2' : 'refresh'} className="text-[18px]" />
                        {isSuccess ? t('result.myVaults') : t('result.tryAgain')}
                    </Link>
                </div>
            </Card>
        </OwnerLayout>
    );
}
