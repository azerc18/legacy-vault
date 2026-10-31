import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { paymentApi } from '../api/paymentApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnPrimary, Card, formatDate, Icon, OwnerLayout } from './ownerUi.jsx';

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

export default function BillingPage() {
    const [sub, setSub] = useState(null);
    const [payments, setPayments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        Promise.all([paymentApi.getMySubscription(), paymentApi.getMyPayments()])
            .then(([subData, txList]) => {
                setSub(subData);
                setPayments(txList || []);
            })
            .catch((e) => setError(getErrorMessage(e, 'Could not load billing information.')))
            .finally(() => setLoading(false));
    }, []);

    return (
        <OwnerLayout
            title="Billing & Subscription"
            subtitle="Manage your LegacyVault plan, entitlements, and VNPay payment history."
            actions={
                <Link to="/pricing" className={btnPrimary}>
                    <Icon name="workspace_premium" className="text-[18px]" />
                    {sub?.vip ? 'Extend VIP' : 'Upgrade to VIP'}
                </Link>
            }
        >
            <Alert>{error}</Alert>

            {loading ? (
                <p className="text-on-surface-variant">Loading billing details...</p>
            ) : (
                <div className="space-y-6">
                    {sub && (
                        <Card title="Current Subscription" icon="workspace_premium">
                            <div className="grid sm:grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                                <div className="p-4 rounded-xl bg-surface-container-low">
                                    <div className="text-xs text-on-surface-variant">Plan</div>
                                    <div className="mt-1 font-bold text-base flex items-center gap-2">
                                        {sub.planName || sub.planCode}
                                        {sub.vip && (
                                            <span className="text-xs px-2 py-0.5 rounded-full bg-primary text-on-primary">
                                                VIP
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <div className="p-4 rounded-xl bg-surface-container-low">
                                    <div className="text-xs text-on-surface-variant">Status / Expiry</div>
                                    <div className="mt-1 font-bold text-base">
                                        {sub.status}
                                        {sub.expiresAt && (
                                            <span className="block text-xs font-normal text-on-surface-variant">
                                                Expires {formatDate(sub.expiresAt)}
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <div className="p-4 rounded-xl bg-surface-container-low">
                                    <div className="text-xs text-on-surface-variant">Vault &amp; Beneficiary Limit</div>
                                    <div className="mt-1 font-bold text-base">
                                        {sub.maxVaults} vaults · {sub.maxBeneficiariesPerVault} beneficiaries/vault
                                    </div>
                                </div>
                                <div className="p-4 rounded-xl bg-surface-container-low">
                                    <div className="text-xs text-on-surface-variant">Encrypted Storage &amp; Media</div>
                                    <div className="mt-1 font-bold text-base">
                                        {formatStorage(sub.maxStorageBytes)} ·{' '}
                                        {sub.legacyMessageEnabled ? 'Media enabled' : 'No legacy media'}
                                    </div>
                                </div>
                            </div>
                        </Card>
                    )}

                    <Card title="Payment History" icon="receipt_long">
                        {payments.length === 0 ? (
                            <p className="text-sm text-on-surface-variant">
                                No payment transactions yet.
                            </p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm text-left">
                                    <thead>
                                        <tr className="border-b border-outline-variant/40 text-on-surface-variant">
                                            <th className="py-2.5 pr-4">Date</th>
                                            <th className="py-2.5 pr-4">Plan</th>
                                            <th className="py-2.5 pr-4">Amount</th>
                                            <th className="py-2.5 pr-4">Status</th>
                                            <th className="py-2.5 pr-4">VNPay Ref</th>
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
                                                        className={`text-xs font-bold px-2.5 py-1 rounded-full ${
                                                            tx.status === 'SUCCESS'
                                                                ? 'bg-success-container text-on-success-container'
                                                                : tx.status === 'FAILED'
                                                                ? 'bg-error-container text-on-error-container'
                                                                : 'bg-secondary-fixed text-on-secondary-fixed'
                                                        }`}
                                                    >
                                                        {tx.status}
                                                    </span>
                                                </td>
                                                <td className="py-3 pr-4 text-xs text-on-surface-variant">
                                                    {tx.vnpTransactionNo || tx.id}
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

