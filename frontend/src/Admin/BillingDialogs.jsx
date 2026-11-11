// Việc 3: hộp thoại Admin hoàn tiền giao dịch và tặng VIP thủ công.
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { btnDanger, btnOutline, btnPrimary } from '../Owner/ownerUi.jsx';
import { formatVnd } from './adminUi.jsx';

const fieldClass = 'lv-glass-inline w-full px-4 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary';

function Shell({ title, message, children, onCancel }) {
    return (
        <div className="lv-modal-backdrop fixed inset-0 z-40 bg-black/45 backdrop-blur-md flex items-center justify-center px-4" onClick={onCancel}>
            <div role="dialog" aria-modal="true" aria-label={title}
                 className="lv-glass-secure lv-surface-secure bg-surface-container-lowest rounded-2xl p-6 w-full max-w-md"
                 onClick={(e) => e.stopPropagation()}>
                <h3 className="font-title-lg text-title-lg">{title}</h3>
                {message && <p className="mt-2 text-sm text-on-surface-variant">{message}</p>}
                {children}
            </div>
        </div>
    );
}

// payment: dòng giao dịch SUCCESS. onConfirm({ reason, viaGateway })
export function RefundDialog({ payment, busy, onConfirm, onCancel }) {
    const { t } = useTranslation('admin');
    const [reason, setReason] = useState('');
    const [viaGateway, setViaGateway] = useState(true);
    if (!payment) return null;
    const close = () => { setReason(''); setViaGateway(true); onCancel(); };
    return (
        <Shell title={t('payments.refund.title')} onCancel={close}
               message={t('payments.refund.message', {
                   amount: formatVnd(payment.amountVnd), email: payment.userEmail, plan: payment.planName || payment.planCode,
               })}>
            <label className="mt-4 flex items-start gap-3 text-sm cursor-pointer">
                <input type="checkbox" checked={viaGateway} onChange={(e) => setViaGateway(e.target.checked)} className="mt-1" />
                <span>
                    <strong className="block">{t('payments.refund.viaGateway', { provider: payment.provider })}</strong>
                    <span className="text-xs text-on-surface-variant">
                        {viaGateway ? t('payments.refund.viaGatewayHint') : t('payments.refund.manualHint')}
                    </span>
                </span>
            </label>
            <textarea rows={3} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)}
                      placeholder={t('payments.refund.reasonPlaceholder')} className={`${fieldClass} mt-4 py-2`} />
            <div className="mt-6 flex justify-end gap-3">
                <button onClick={close} className={btnOutline}>{t('common:actions.cancel')}</button>
                <button onClick={() => onConfirm({ reason: reason.trim(), viaGateway })}
                        disabled={busy || !reason.trim()} className={btnDanger}>
                    {busy ? t('wait') : t('payments.refund.confirm')}
                </button>
            </div>
        </Shell>
    );
}

// onConfirm({ days, reason })
export function GrantVipDialog({ open, email, busy, onConfirm, onCancel }) {
    const { t } = useTranslation('admin');
    const [days, setDays] = useState('30');
    const [reason, setReason] = useState('');
    if (!open) return null;
    const n = Number(days);
    const valid = Number.isInteger(n) && n >= 1 && n <= 365 && reason.trim();
    const close = () => { setDays('30'); setReason(''); onCancel(); };
    return (
        <Shell title={t('users.grantVip.title')} message={t('users.grantVip.message', { email })} onCancel={close}>
            <label className="block mt-4 text-sm font-medium">
                {t('users.grantVip.days')}
                <input type="number" min={1} max={365} value={days} onChange={(e) => setDays(e.target.value)}
                       className={`${fieldClass} mt-1 h-11`} />
            </label>
            <div className="mt-2 flex flex-wrap gap-2">
                {[7, 30, 90, 365].map((d) => (
                    <button key={d} type="button" onClick={() => setDays(String(d))}
                            className={`h-8 px-3 rounded-full border text-xs font-semibold ${n === d ? 'border-secondary bg-secondary/10 text-secondary' : 'border-outline-variant'}`}>
                        {t('users.grantVip.preset', { count: d })}
                    </button>
                ))}
            </div>
            <textarea rows={3} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)}
                      placeholder={t('users.grantVip.reasonPlaceholder')} className={`${fieldClass} mt-4 py-2`} />
            <div className="mt-6 flex justify-end gap-3">
                <button onClick={close} className={btnOutline}>{t('common:actions.cancel')}</button>
                <button onClick={() => onConfirm({ days: n, reason: reason.trim() })} disabled={busy || !valid} className={btnPrimary}>
                    {busy ? t('wait') : t('users.grantVip.confirm')}
                </button>
            </div>
        </Shell>
    );
}
