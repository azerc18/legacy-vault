import React from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { btnOutline, btnPrimary, Card, Icon, OwnerLayout } from './ownerUi.jsx';

export default function PaymentResultPage() {
    const [searchParams] = useSearchParams();
    const status = searchParams.get('status') || 'failed';
    const txId = searchParams.get('txId');
    const errorCode = searchParams.get('error');
    const isSuccess = status.toLowerCase() === 'success';

    return (
        <OwnerLayout
            title={isSuccess ? 'Payment Successful' : 'Payment Failed'}
            subtitle={
                isSuccess
                    ? 'Your VIP subscription has been activated.'
                    : 'Your VNPay transaction could not be completed.'
            }
        >
            <Card className="max-w-xl mx-auto text-center">
                <div className="flex justify-center">
                    <span
                        className={`w-16 h-16 rounded-full flex items-center justify-center ${
                            isSuccess
                                ? 'bg-success-container text-on-success-container'
                                : 'bg-error-container text-on-error-container'
                        }`}
                    >
                        <Icon name={isSuccess ? 'check_circle' : 'error'} className="text-[36px]" />
                    </span>
                </div>
                <h2 className="mt-4 font-title-lg text-title-lg">
                    {isSuccess
                        ? 'Welcome to LegacyVault VIP!'
                        : 'We could not confirm your payment'}
                </h2>
                <p className="mt-2 text-sm text-on-surface-variant">
                    {isSuccess
                        ? 'You now have access to up to 5 vaults, 5 beneficiaries per vault, 2 GB of encrypted storage, and legacy audio/video messages.'
                        : errorCode
                        ? `Reason: ${errorCode}`
                        : 'The payment was cancelled or declined by the payment gateway.'}
                </p>
                {txId && (
                    <p className="mt-2 text-xs text-outline">
                        Transaction ID: <span className="font-mono">{txId}</span>
                    </p>
                )}
                <div className="mt-6 flex flex-wrap justify-center gap-3">
                    <Link to="/billing" className={btnOutline}>
                        <Icon name="receipt_long" className="text-[18px]" /> View Billing
                    </Link>
                    <Link to={isSuccess ? '/owner/vaults' : '/pricing'} className={btnPrimary}>
                        <Icon name={isSuccess ? 'inventory_2' : 'refresh'} className="text-[18px]" />
                        {isSuccess ? 'Go to My Vaults' : 'Try Again'}
                    </Link>
                </div>
            </Card>
        </OwnerLayout>
    );
}

