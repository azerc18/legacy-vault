import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { paymentApi } from '../api/paymentApi.js';
import { getErrorMessage } from '../api/http.js';
import { formatStorage } from '../i18n/format.js';
import { Alert, btnPrimary, Card, formatDate, Icon, OwnerLayout } from './ownerUi.jsx';

const formatVnd = (n) =>
    typeof n === 'number'
        ? n.toLocaleString('vi-VN') + ' ₫'
        : '0 ₫';

export default function BillingPage() {
    const { t } = useTranslation('payment');
    const [sub, setSub] = useState(null);
    const [payments, setPayments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [downloadingId, setDownloadingId] = useState(null);

    const handleDownloadReceipt = async (txId) => {
        setError('');
        setDownloadingId(txId);
        try {
            await paymentApi.downloadReceipt(txId);
        } catch (e) {
            setError(getErrorMessage(e, t('billing.errors.receiptFailed')));
        } finally {
            setDownloadingId(null);
        }
    };

    useEffect(() => {
        Promise.all([paymentApi.getMySubscription(), paymentApi.getMyPayments()])
            .then(([subData, txList]) => {
                setSub(subData);
                setPayments(txList || []);
            })
            .catch((e) => setError(getErrorMessage(e, t('billing.errors.loadFailed'))))
            .finally(() => setLoading(false));
    }, [t]);

    return (
        <OwnerLayout
            title={t('billing.title')}
            subtitle={t('billing.subtitle')}
            loading={loading}
            actions={
                <Link to="/pricing" className={btnPrimary}>
                    <Icon name="workspace_premium" className="text-[18px]" />
                    {sub?.vip ? t('billing.extendVip') : t('billing.upgradeToVip')}
                </Link>
            }
        >
            <Alert>{error}</Alert>

            {loading ? (
                <div role="status" aria-busy="true" className="space-y-6">
                    <span className="sr-only">{t('billing.loading')}</span>
                    <div className="lv-skeleton p-5 space-y-4" aria-hidden="true">
                        <div className="lv-skeleton-bar h-5 w-44" />
                        <div className="grid sm:grid-cols-2 md:grid-cols-4 gap-4">
                            {[0, 1, 2, 3].map((i) => <div key={i} className="lv-skeleton h-20" />)}
                        </div>
                    </div>
                    <div className="lv-skeleton p-5 space-y-3" aria-hidden="true">
                        <div className="lv-skeleton-bar h-5 w-36" />
                        {[0, 1, 2].map((i) => <div key={i} className="lv-skeleton h-12" />)}
                    </div>
                </div>
            ) : (
                <div className="space-y-6">
                    {sub && (
                        <Card title={t('billing.current.title')} icon="workspace_premium">
                            <div className="grid sm:grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                                <div className="p-4 rounded-xl bg-surface-container-low">
                                    <div className="text-xs text-on-surface-variant">
                                        {t('billing.current.plan')}
                                    </div>
                                    <div className="mt-1 font-bold text-base flex items-center gap-2">
                                        {sub.planName || sub.planCode}
                                        {sub.vip && (
                                            <span className="text-xs px-2 py-0.5 rounded-full bg-primary text-on-primary">
                                                {t('billing.current.vip')}
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <div className="p-4 rounded-xl bg-surface-container-low">
                                    <div className="text-xs text-on-surface-variant">
                                        {t('billing.current.statusExpiry')}
                                    </div>
                                    <div className="mt-1 font-bold text-base">
                                        {sub.status}
                                        {/* m4: không còn VIP mà có expiresAt = gói VIP gần nhất đã hết hạn (EXPIRED + FREE) */}
                                        {sub.expiresAt && (
                                            <span className="block text-xs font-normal text-on-surface-variant">
                                                {sub.vip
                                                    ? t('billing.current.expires', { date: formatDate(sub.expiresAt) })
                                                    : t('billing.current.expiredOn', { date: formatDate(sub.expiresAt) })}
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <div className="p-4 rounded-xl bg-surface-container-low">
                                    <div className="text-xs text-on-surface-variant">
                                        {t('billing.current.limits')}
                                    </div>
                                    <div className="mt-1 font-bold text-base">
                                        {t('billing.current.vaultsAndBens', {
                                            vaults: sub.maxVaults,
                                            beneficiaries: sub.maxBeneficiariesPerVault,
                                        })}
                                    </div>
                                </div>
                                <div className="p-4 rounded-xl bg-surface-container-low">
                                    <div className="text-xs text-on-surface-variant">
                                        {t('billing.current.storage')}
                                    </div>
                                    <div className="mt-1 font-bold text-base">
                                        {t('billing.current.storageDetail', {
                                            storage: formatStorage(sub.maxStorageBytes),
                                            media: sub.legacyMessageEnabled
                                                ? t('billing.current.mediaEnabled')
                                                : t('billing.current.noMedia'),
                                        })}
                                    </div>
                                </div>
                            </div>
                        </Card>
                    )}

                    <Card title={t('billing.history.title')} icon="receipt_long">
                        {payments.length === 0 ? (
                            <p className="text-sm text-on-surface-variant">
                                {t('billing.history.empty')}
                            </p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm text-left">
                                    <thead>
                                        <tr className="border-b border-outline-variant/40 text-on-surface-variant">
                                            <th className="py-2.5 pr-4">{t('billing.history.date')}</th>
                                            <th className="py-2.5 pr-4">{t('billing.history.plan')}</th>
                                            <th className="py-2.5 pr-4">{t('billing.history.amount')}</th>
                                            <th className="py-2.5 pr-4">{t('billing.history.status')}</th>
                                            <th className="py-2.5 pr-4">{t('billing.history.ref')}</th>
                                            <th className="py-2.5 pr-4">{t('billing.history.receipt')}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {payments.map((tx) => (
                                            <tr key={tx.id} className="border-b border-outline-variant/20">
                                                <td className="py-3 pr-4">{formatDate(tx.createdAt, true)}</td>
                                                <td className="py-3 pr-4 font-semibold">
                                                    {tx.planName || tx.planCode}
                                                </td>
                                                <td className="py-3 pr-4">{formatVnd(tx.amountVnd)}</td>
                                                <td className="py-3 pr-4">
                                                    <span
                                                        title={tx.status === 'EXPIRED' ? t('billing.history.expiredHint') : undefined}
                                                        className={`text-xs font-bold px-2.5 py-1 rounded-full ${
                                                            tx.status === 'SUCCESS'
                                                                ? 'bg-success-container text-on-success-container'
                                                                : tx.status === 'FAILED'
                                                                ? 'bg-error-container text-on-error-container'
                                                                : tx.status === 'EXPIRED'
                                                                ? 'bg-surface-container-high text-on-surface-variant'
                                                                : 'bg-secondary-fixed text-on-secondary-fixed'
                                                        }`}
                                                    >
                                                        {t(`billing.history.statusLabel.${tx.status}`, { defaultValue: tx.status })}
                                                    </span>
                                                </td>
                                                <td className="py-3 pr-4 text-xs text-on-surface-variant">
                                                    {tx.vnpTransactionNo || tx.id}
                                                </td>
                                                <td className="py-3 pr-4">
                                                    {tx.status === 'SUCCESS' && (
                                                        <button
                                                            type="button"
                                                            onClick={() => handleDownloadReceipt(tx.id)}
                                                            disabled={downloadingId === tx.id}
                                                            className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:underline disabled:opacity-60"
                                                        >
                                                            <Icon name="download" className="text-[16px]" />
                                                            {downloadingId === tx.id
                                                                ? t('billing.history.downloading')
                                                                : t('billing.history.downloadReceipt')}
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </Card>
                </div>
            )}
        </OwnerLayout>
    );
}
