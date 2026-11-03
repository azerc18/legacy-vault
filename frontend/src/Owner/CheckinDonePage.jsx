import React from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { btnOutline, btnPrimary, Card, formatDate, Icon } from './ownerUi.jsx';

// F2: kết quả check-in một chạm (sau khi Owner bấm nút ở /dms/checkin).
export default function CheckinDonePage() {
    const { t } = useTranslation('checkin');
    const [searchParams] = useSearchParams();
    // n1: chỉ status=success mới là thành công; mở trang trống không hiện "Đã check-in"
    const isSuccess = searchParams.get('status') === 'success';
    const nextDue = searchParams.get('nextDue');
    const reason = searchParams.get('reason');

    // m5: lý do BE trả (data.reason) -> câu riêng trong namespace checkin; lạ hoặc thiếu thì dùng câu chung.
    // Gợi ý "đăng nhập để check-in thủ công" chỉ đúng khi lỗi nằm ở link (TOKEN_INVALID).
    const errorMessage = t(`done.reason.${reason}`, { defaultValue: t('done.errorGeneric') });
    const showHint = !reason || reason === 'TOKEN_INVALID';

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
                    {isSuccess ? t('done.successBody') : (showHint ? `${errorMessage} ${t('done.errorHint')}` : errorMessage)}
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
