import React from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { btnOutline, Card, Icon } from './ownerUi.jsx';

export default function DmsConfirmationResultPage() {
    const [searchParams] = useSearchParams();
    const decision = (searchParams.get('decision') || 'ALIVE').toUpperCase();
    const isAlive = decision === 'ALIVE';

    return (
        <div className="min-h-screen bg-surface text-on-surface flex flex-col justify-center items-center px-5 py-12">
            <Card className="max-w-lg w-full text-center">
                <div className="flex justify-center">
                    <span
                        className={`w-16 h-16 rounded-full flex items-center justify-center ${
                            isAlive
                                ? 'bg-success-container text-on-success-container'
                                : 'bg-warning-container text-on-warning-container'
                        }`}
                    >
                        <Icon name={isAlive ? 'verified_user' : 'notification_important'} className="text-[36px]" />
                    </span>
                </div>

                <h1 className="mt-4 font-headline-sm text-headline-sm">
                    {isAlive
                        ? 'Thank You — Owner Marked as Safe'
                        : 'Thank You — Digital Executor Has Been Notified'}
                </h1>

                <p className="mt-2 text-sm text-on-surface-variant">
                    {isAlive
                        ? "We have postponed the owner's Dead Man's Switch activation by 7 days and sent them a reminder to check in."
                        : "We have recorded your confirmation and notified the owner's designated Digital Executor to begin the legal verification process."}
                </p>

                <div className="mt-6 flex justify-center">
                    <Link to="/" className={btnOutline}>
                        Return to LegacyVault Home
                    </Link>
                </div>
            </Card>
        </div>
    );
}

