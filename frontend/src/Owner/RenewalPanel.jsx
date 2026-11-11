// Việc 6: hủy gia hạn cuối kỳ và "Gia hạn 1 chạm" trên trang Billing.
// Hệ thống không tự trừ tiền: hủy chỉ là không nhắc gia hạn nữa, VIP giữ tới hết hạn rồi về FREE, không hoàn tiền.
import React, { forwardRef, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { paymentApi } from '../api/paymentApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnOutline, btnPrimary, ConfirmDialog, formatDate, Icon } from './ownerUi.jsx';

const PROVIDER_LABEL = { VNPAY: 'VNPay', PAYPAL: 'PayPal' };
const formatVnd = (n) => (typeof n === 'number' ? n.toLocaleString('vi-VN') + ' ₫' : '');

// sub: GET /subscriptions/me. renewCode: gói từ ?renew= trong mail (ưu tiên hơn renewPlanCode). onChange(newSub)
const RenewalPanel = forwardRef(function RenewalPanel({ sub, renewCode, highlight, onChange }, ref) {
    const { t } = useTranslation('payment');
    const [providers, setProviders] = useState([]);
    const [provider, setProvider] = useState('');
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    const [confirmCancel, setConfirmCancel] = useState(false);

    useEffect(() => {
        paymentApi.getProviders()
            .then((list) => setProviders(list?.length ? list : ['VNPAY']))
            .catch(() => setProviders(['VNPAY']));
    }, []);

    // Chọn sẵn cổng của lần trả gần nhất; người dùng bấm cổng khác thì theo lựa chọn đó
    const chosen = provider || (providers.includes(sub?.lastProvider) ? sub.lastProvider : providers[0]) || 'VNPAY';
    const plans = sub?.availablePlans ?? [];
    const planCode = [renewCode, sub?.renewPlanCode].find((c) => c && plans.some((p) => p.code === c && p.priceVnd > 0));
    const plan = plans.find((p) => p.code === planCode);
    if (!sub || !plan) return null;

    const renew = async () => {
        setError(''); setBusy('renew');
        try {
            const res = await paymentApi.checkout(plan.code, chosen);
            if (res?.paymentUrl) window.location.assign(res.paymentUrl);
            else setBusy('');
        } catch (e) {
            setError(getErrorMessage(e, t('billing.renewal.errors.renew')));
            setBusy('');
        }
    };
    const toggle = async (cancel) => {
        setError(''); setBusy(cancel ? 'cancel' : 'resume');
        try {
            onChange(cancel ? await paymentApi.cancelRenewal() : await paymentApi.resumeRenewal());
            setConfirmCancel(false);
        } catch (e) {
            setError(getErrorMessage(e, t('billing.renewal.errors.toggle')));
        } finally {
            setBusy('');
        }
    };

    const date = sub.expiresAt ? formatDate(sub.expiresAt) : '';
    const state = !sub.vip ? 'lapsed' : sub.cancelAtPeriodEnd ? 'cancelled' : 'active';
    const tone = {
        active: 'border-outline-variant/40',
        cancelled: 'border-warning/50 bg-warning-container/30',
        lapsed: 'border-error/30 bg-error-container/20',
    }[state];

    return (
        <section ref={ref} id="renew"
                 className={`rounded-2xl border p-5 transition-shadow ${tone} ${highlight ? 'ring-2 ring-secondary shadow-lg' : ''}`}>
            <div className="flex items-start gap-3">
                <Icon name={state === 'active' ? 'autorenew' : state === 'cancelled' ? 'event_busy' : 'history'}
                      className={`text-[24px] ${state === 'active' ? 'text-secondary' : state === 'cancelled' ? 'text-warning' : 'text-error'}`} />
                <div className="min-w-0 flex-1">
                    <h3 className="font-bold">{t(`billing.renewal.${state}.title`, { date })}</h3>
                    <p className="mt-1 text-sm text-on-surface-variant">{t(`billing.renewal.${state}.text`, { date })}</p>
                </div>
            </div>

            <Alert>{error}</Alert>

            <div className="mt-4 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
                <div>
                    {providers.length > 1 && (
                        <div role="radiogroup" aria-label={t('billing.renewal.provider')} className="mb-3 inline-flex rounded-xl border border-outline-variant p-1 gap-1">
                            {providers.map((code) => (
                                <button key={code} type="button" role="radio" aria-checked={chosen === code}
                                        onClick={() => setProvider(code)}
                                        className={`h-9 px-4 rounded-lg text-sm font-semibold ${chosen === code ? 'bg-primary text-on-primary' : 'hover:bg-surface-container-low'}`}>
                                    {PROVIDER_LABEL[code] ?? code}
                                </button>
                            ))}
                        </div>
                    )}
                    <div className="flex flex-wrap gap-3">
                        <button type="button" onClick={renew} disabled={!!busy} className={btnPrimary}>
                            <Icon name="bolt" className="text-[18px]" />
                            {busy === 'renew'
                                ? t('billing.renewal.redirecting')
                                : t('billing.renewal.renew', { plan: plan.name || plan.code, price: formatVnd(plan.priceVnd) })}
                        </button>
                        {state === 'cancelled' && (
                            <button type="button" onClick={() => toggle(false)} disabled={!!busy} className={btnOutline}>
                                <Icon name="undo" className="text-[18px]" />
                                {busy === 'resume' ? t('billing.renewal.wait') : t('billing.renewal.resume')}
                            </button>
                        )}
                    </div>
                    {state !== 'lapsed' && (
                        <p className="mt-2 text-xs text-on-surface-variant">{t('billing.renewal.stackHint', { date })}</p>
                    )}
                </div>
                {state === 'active' && (
                    <button type="button" onClick={() => setConfirmCancel(true)} disabled={!!busy}
                            className="self-start lg:self-end text-sm font-semibold text-error hover:underline inline-flex items-center gap-1">
                        <Icon name="cancel" className="text-[18px]" /> {t('billing.renewal.cancel')}
                    </button>
                )}
            </div>

            <ConfirmDialog
                open={confirmCancel}
                title={t('billing.renewal.confirm.title')}
                message={t('billing.renewal.confirm.message', { date, plan: sub.planName || sub.planCode })}
                confirmLabel={t('billing.renewal.confirm.ok')}
                cancelLabel={t('billing.renewal.confirm.keep')}
                danger
                busy={busy === 'cancel'}
                onConfirm={() => toggle(true)}
                onCancel={() => setConfirmCancel(false)}
            />
        </section>
    );
});

export default RenewalPanel;
