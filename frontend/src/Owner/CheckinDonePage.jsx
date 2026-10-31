import React from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { btnOutline, btnPrimary, Card, formatDate, Icon } from './ownerUi.jsx';

export default function CheckinDonePage() {
    const [searchParams] = useSearchParams();
    const status = searchParams.get('status') || 'success';
    const nextDue = searchParams.get('nextDue');
    const errorCode = searchParams.get('errorCode');
    const isSuccess = status.toLowerCase() === 'success';

    return (
        <div className="min-h-screen bg-surface text-on-surface flex flex-col justify-center items-center px-5 py-12">
            <Card className="max-w-lg w-full text-center">
                <div className="flex justify-center">
                    <span
                        className={`w-16 h-16 rounded-full flex items-center justify-center ${
                            isSuccess
                                ? 'bg-success-container text-on-success-container'
                                : 'bg-error-container text-on-error-container'
                        }`}
                    >
                        <Icon name={isSuccess ? 'verified_user' : 'error'} className="text-[36px]" />
                    </span>
                </div>

                <h1 className="mt-4 font-headline-sm text-headline-sm">
                    {isSuccess
                        ? "You're Checked In — Dead Man's Switch Reset"
                        : 'Check-In Link Could Not Be Verified'}
                </h1>

                <p className="mt-2 text-sm text-on-surface-variant">
                    {isSuccess
                        ? "We have confirmed that you are safe. Your Dead Man's Switch countdown has been reset for a new cycle."
                        : errorCode === 'CHECKIN_TOKEN_INVALID'
                        ? 'This one-time check-in link has already been used or has expired. Please sign in to check in manually.'
                        : 'This check-in link is no longer valid. Please sign in to LegacyVault to check in manually.'}
                </p>

                {isSuccess && nextDue && (
                    <div className="mt-4 p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 text-sm">
                        <span className="text-on-surface-variant">Next check-in due: </span>
                        <span className="font-label-lg text-on-surface">{formatDate(nextDue, true)}</span>
                    </div>
                )}

                <div className="mt-6 flex flex-wrap justify-center gap-3">
                    <Link to="/owner/dms" className={btnPrimary}>
                        <Icon name="timer" className="text-[18px]" />
                        Open Dead Man&apos;s Switch
                    </Link>
                    <Link to="/" className={btnOutline}>
                        Back to Home
                    </Link>
                </div>
            </Card>
        </div>
    );
}

