// Việc 4: ô nhập mã giảm giá và lời mời dùng thử VIP trên trang Bảng giá.
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { paymentApi } from '../api/paymentApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnOutline, btnPrimary, formatDate, Icon, inputClass } from './ownerUi.jsx';

// coupon: kết quả GET /payments/coupons/preview đang áp dụng (hoặc null). onApply(preview | null)
export function CouponBox({ coupon, onApply }) {
    const { t } = useTranslation('payment');
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const apply = async (e) => {
        e.preventDefault();
        if (!code.trim()) return;
        setBusy(true); setError('');
        try {
            onApply(await paymentApi.previewCoupon(code.trim()));
        } catch (err) {
            setError(getErrorMessage(err, t('pricing.coupon.error')));
            onApply(null);
        } finally {
            setBusy(false);
        }
    };

    if (coupon) {
        return (
            <div className="mb-6 p-4 rounded-2xl border border-success/40 bg-success-container/40 flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm flex items-center gap-2">
                    <Icon name="sell" className="text-[20px] text-success" />
                    <span>
                        {t('pricing.coupon.applied', { code: coupon.code, percent: coupon.percentOff })}
                        {coupon.validUntil && (
                            <span className="text-on-surface-variant"> · {t('pricing.coupon.until', { date: formatDate(coupon.validUntil) })}</span>
                        )}
                    </span>
                </p>
                <button type="button" onClick={() => { onApply(null); setCode(''); }} className="text-sm font-semibold text-on-surface-variant hover:underline">
                    {t('pricing.coupon.remove')}
                </button>
            </div>
        );
    }

    return (
        <form onSubmit={apply} className="mb-6">
            <label htmlFor="coupon" className="text-sm font-medium">{t('pricing.coupon.label')}</label>
            <div className="mt-2 flex gap-2 max-w-md">
                <input id="coupon" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} maxLength={40}
                       placeholder={t('pricing.coupon.placeholder')} autoComplete="off" className={`${inputClass} lv-mono uppercase`} />
                <button type="submit" disabled={busy || !code.trim()} className={btnOutline}>
                    {busy ? t('pricing.coupon.checking') : t('pricing.coupon.apply')}
                </button>
            </div>
            {error && <div className="mt-2 max-w-md"><Alert>{error}</Alert></div>}
        </form>
    );
}

// Chỉ hiện khi tài khoản chưa từng có gói nào (sub.trialEligible). onStarted(newSub)
export function TrialBanner({ sub, onStarted }) {
    const { t } = useTranslation('payment');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    if (!sub?.trialEligible) return null;

    const start = async () => {
        setBusy(true); setError('');
        try {
            onStarted(await paymentApi.startTrial());
        } catch (err) {
            setError(getErrorMessage(err, t('pricing.trial.error')));
        } finally {
            setBusy(false);
        }
    };

    return (
        <section className="mb-6 p-5 rounded-2xl border border-secondary/40 bg-secondary-fixed/40 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="flex items-start gap-3">
                <Icon name="redeem" className="text-[28px] text-secondary" />
                <div>
                    <h2 className="font-bold">{t('pricing.trial.title', { days: sub.trialDays })}</h2>
                    <p className="text-sm text-on-surface-variant">{t('pricing.trial.text', { days: sub.trialDays })}</p>
                    {error && <div className="mt-2"><Alert>{error}</Alert></div>}
                </div>
            </div>
            <button type="button" onClick={start} disabled={busy} className={`${btnPrimary} shrink-0`}>
                <Icon name="rocket_launch" className="text-[18px]" />
                {busy ? t('pricing.trial.starting') : t('pricing.trial.start', { days: sub.trialDays })}
            </button>
        </section>
    );
}
