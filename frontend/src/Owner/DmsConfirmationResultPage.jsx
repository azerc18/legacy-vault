import React from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { btnOutline, Card, Icon } from './ownerUi.jsx';

// X2: trang công khai sau khi người xác nhận chéo bấm link trong email (BE redirect tới /dms/confirm-done).
export default function DmsConfirmationResultPage() {
    const { t, i18n } = useTranslation('dmsConfirm');
    const [searchParams] = useSearchParams();
    const isError = (searchParams.get('status') || 'success').toLowerCase() === 'error';
    const errorCode = searchParams.get('errorCode');
    const decision = (searchParams.get('decision') || 'ALIVE').toUpperCase();
    const isAlive = decision === 'ALIVE';

    // Mã lỗi BE gửi qua URL -> bản dịch trong namespace errors; mã chưa có bản dịch thì dùng câu chung.
    const errorMessage = errorCode && i18n.exists(errorCode, { ns: 'errors' })
        ? t(errorCode, { ns: 'errors' })
        : t('done.errorGeneric');

    let iconCls = 'bg-success-container text-on-success-container';
    let icon = 'verified_user';
    let title = t('done.aliveTitle');
    let body = t('done.aliveBody');
    if (isError) {
        iconCls = 'bg-error-container text-on-error-container';
        icon = 'error';
        title = t('done.errorTitle');
        body = `${errorMessage} ${t('done.errorHint')}`;
    } else if (!isAlive) {
        iconCls = 'bg-warning-container text-on-warning-container';
        icon = 'notification_important';
        title = t('done.unreachableTitle');
        body = t('done.unreachableBody');
    }

    return (
        <div className="min-h-screen bg-surface text-on-surface flex flex-col justify-center items-center px-5 py-12">
            <Card className="max-w-lg w-full text-center">
                <div className="flex justify-center">
                    <span className={`w-16 h-16 rounded-full flex items-center justify-center ${iconCls}`}>
                        <Icon name={icon} className="text-[36px]" />
                    </span>
                </div>

                <h1 className="mt-4 font-headline-sm text-headline-sm">{title}</h1>

                <p className="mt-2 text-sm text-on-surface-variant">{body}</p>

                <div className="mt-6 flex justify-center">
                    <Link to="/" className={btnOutline}>
                        {t('done.backHome')}
                    </Link>
                </div>
            </Card>
        </div>
    );
}
