import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { paymentApi } from '../api/paymentApi.js';
import { getToken } from '../auth/auth.js';
import { formatStorage } from '../i18n/format.js';
import LanguageSwitch from '../Common/LanguageSwitch.jsx';
import { btnOutline, btnPrimary, Icon } from './ownerUi.jsx';

const MAX_ATTEMPTS = 5;
const POLL_INTERVAL_MS = 3000;

// Giao diện từng trạng thái: icon + màu + khóa i18n (result.<prefix>Title / Subtitle / Heading)
const VIEW = {
    confirming: { icon: 'hourglass_top', cls: 'bg-secondary-fixed text-on-secondary-fixed animate-pulse', prefix: 'confirming' },
    success: { icon: 'check_circle', cls: 'bg-success-container text-on-success-container', prefix: 'success' },
    processing: { icon: 'schedule', cls: 'bg-secondary-fixed text-on-secondary-fixed', prefix: 'processing' },
    loginRequired: { icon: 'check_circle', cls: 'bg-success-container text-on-success-container', prefix: 'loginRequired' },
    failed: { icon: 'error', cls: 'bg-error-container text-on-error-container', prefix: 'failed' },
};

/**
 * T1 (m1): trang VNPay chuyển về (công khai, không bắt buộc đăng nhập).
 * Kết quả lấy từ status trong query (BE đã kiểm chữ ký trước khi redirect):
 * - status khác "success" -> thất bại;
 * - success mà chưa đăng nhập -> báo thành công, mời đăng nhập để xem gói (không gọi API);
 * - đã đăng nhập -> poll /subscriptions/me tối đa 5 lần; hết lần mà chưa VIP -> "Đang xử lý" (không báo thất bại);
 *   401 (phiên hết hạn) cũng không báo thất bại mà mời đăng nhập.
 */
export default function PaymentResultPage() {
    const { t } = useTranslation('payment');
    const [searchParams] = useSearchParams();
    const paid = (searchParams.get('status') || '').toLowerCase() === 'success';
    const txId = searchParams.get('txnRef') || searchParams.get('txId');
    const errorCode = searchParams.get('errorCode') || searchParams.get('error');
    const loggedIn = !!getToken();

    const initialState = !paid ? 'failed' : loggedIn ? 'confirming' : 'loginRequired';
    const [state, setState] = useState(initialState);
    const [sub, setSub] = useState(null);
    const [attempt, setAttempt] = useState(1);

    useEffect(() => {
        // n3: badge VIP ở header phải đọc lại gói sau thanh toán
        try {
            sessionStorage.removeItem('lv_sub_cache');
        } catch {
            // sessionStorage có thể bị chặn
        }
        if (initialState !== 'confirming') return undefined;

        let mounted = true;
        let timer = null;
        const retryOrGiveUp = (count) => {
            if (count < MAX_ATTEMPTS) {
                setAttempt(count + 1);
                timer = setTimeout(() => poll(count + 1), POLL_INTERVAL_MS);
            } else {
                setState('processing');
            }
        };
        const poll = (count) => {
            paymentApi
                .getMySubscription({ skipLoginRedirect: true })
                .then((data) => {
                    if (!mounted) return;
                    setSub(data);
                    if (data?.vip) setState('success');
                    else retryOrGiveUp(count);
                })
                .catch((err) => {
                    if (!mounted) return;
                    if (err?.response?.status === 401) setState('loginRequired');
                    else retryOrGiveUp(count);
                });
        };
        poll(1);

        return () => {
            mounted = false;
            if (timer) clearTimeout(timer);
        };
    }, [initialState]);

    const view = VIEW[state];

    let message;
    if (state === 'confirming') {
        message = t('result.confirmingMessage', { attempt, maxAttempts: MAX_ATTEMPTS });
    } else if (state === 'success') {
        message = t('result.successDesc', {
            vaults: sub.maxVaults,
            beneficiaries: sub.maxBeneficiariesPerVault,
            storage: formatStorage(sub.maxStorageBytes),
            media: sub.legacyMessageEnabled ? t('result.withMedia') : '',
        });
    } else if (state === 'processing') {
        message = t('result.processingMessage');
    } else if (state === 'loginRequired') {
        message = t('result.loginRequiredMessage');
    } else {
        message = errorCode
            ? t('result.reason', { code: t(errorCode, { ns: 'errors', defaultValue: errorCode }) })
            : t('result.failedDefault');
    }

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface flex flex-col items-center justify-center px-4 py-10">
            <div className="w-full max-w-xl flex justify-end mb-3">
                <LanguageSwitch />
            </div>
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-xl w-full text-center">
                <div className="flex justify-center">
                    <span className={`w-16 h-16 rounded-full flex items-center justify-center ${view.cls}`}>
                        <Icon name={view.icon} className="text-[36px]" />
                    </span>
                </div>

                <h1 className="mt-4 font-headline-md text-headline-md">{t(`result.${view.prefix}Title`)}</h1>
                <p className="mt-1 text-sm text-on-surface-variant">{t(`result.${view.prefix}Subtitle`)}</p>

                <h2 className="mt-5 font-title-lg text-title-lg">{t(`result.${view.prefix}Heading`)}</h2>
                <p className="mt-2 text-sm text-on-surface-variant">{message}</p>

                {txId && <p className="mt-2 text-xs text-outline">{t('result.txId', { id: txId })}</p>}

                <div className="mt-6 flex flex-wrap justify-center gap-3">
                    {state === 'loginRequired' ? (
                        <Link to="/login" className={btnPrimary}>
                            <Icon name="login" className="text-[18px]" /> {t('result.login')}
                        </Link>
                    ) : (
                        <>
                            <Link to="/billing" className={btnOutline}>
                                <Icon name="receipt_long" className="text-[18px]" /> {t('result.viewBilling')}
                            </Link>
                            {state === 'success' && (
                                <Link to="/owner/vaults" className={btnPrimary}>
                                    <Icon name="inventory_2" className="text-[18px]" /> {t('result.myVaults')}
                                </Link>
                            )}
                            {state === 'failed' && (
                                <Link to="/pricing" className={btnPrimary}>
                                    <Icon name="refresh" className="text-[18px]" /> {t('result.tryAgain')}
                                </Link>
                            )}
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}
