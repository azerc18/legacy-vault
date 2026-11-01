import React from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { btnOutline, btnPrimary, Card, formatDate, Icon } from './ownerUi.jsx';

export default function CheckinDonePage() {
    const { t, i18n } = useTranslation('checkin');
    const [searchParams] = useSearchParams();
    const status = searchParams.get('status') || 'success';
    const nextDue = searchParams.get('nextDue');
    const errorCode = searchParams.get('errorCode');
    const isSuccess = status.toLowerCase() === 'success';

    // Mã lỗi BE gửi qua URL -> bản dịch trong namespace errors; mã chưa có bản dịch thì dùng câu chung.
    const errorMessage = errorCode && i18n.exists(errorCode, { ns: 'errors' })
        ? t(errorCode, { ns: 'errors' })
        : t('done.errorGeneric');

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
                    {isSuccess ? t('done.successTitle') : t('done.errorTitle')}
                </h1>

                <p className="mt-2 text-sm text-on-surface-variant">
                    {isSuccess ? t('done.successBody') : `${errorMessage} ${t('done.errorHint')}`}
                </p>

                {isSuccess && nextDue && (
                    <div className="mt-4 p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 text-sm">
                        <span className="text-on-surface-variant">{t('done.nextDue')} </span>
                        <span className="font-label-lg text-on-surface">{formatDate(nextDue, true)}</span>
                    </div>
                )}

                <div className="mt-6 flex flex-wrap justify-center gap-3">
                    <Link to="/owner/dms" className={btnPrimary}>
                        <Icon name="timer" className="text-[18px]" />
                        {t('done.openDms')}
                    </Link>
                    <Link to="/" className={btnOutline}>
                        {t('done.backHome')}
                    </Link>
                </div>
            </Card>
        </div>
    );
}
