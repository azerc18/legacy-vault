import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { dmsCheckinApi } from '../api/dmsCheckinApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnPrimary, Card, Icon } from './ownerUi.jsx';

// F2: trang công khai mở từ link trong email nhắc. Mở trang KHÔNG gọi API (trình quét link của hộp thư có thể
// tự mở link); chỉ check-in khi Owner bấm nút.
export default function CheckinConfirmPage() {
    const { t } = useTranslation('checkin');
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token') || '';
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    const submit = async () => {
        setError('');
        setSubmitting(true);
        try {
            const res = await dmsCheckinApi.checkInWithToken(token);
            const params = new URLSearchParams({ status: 'success' });
            if (res?.checkinDueAt) params.set('nextDue', res.checkinDueAt);
            navigate(`/dms/checkin-done?${params.toString()}`, { replace: true });
        } catch (err) {
            // BE trả data.reason khi token / trạng thái DMS không cho check-in: kết quả cuối, sang trang kết quả.
            // Không có reason (mất mạng, lỗi máy chủ): ở lại để bấm lại.
            const reason = err?.response?.data?.data?.reason;
            if (reason) {
                navigate(`/dms/checkin-done?${new URLSearchParams({ status: 'error', reason }).toString()}`,
                    { replace: true });
                return;
            }
            setError(getErrorMessage(err, t('confirm.submitFailed')));
            setSubmitting(false);
        }
    };

    return (
        <div className="min-h-screen bg-surface text-on-surface flex flex-col justify-center items-center px-5 py-12">
            <Card className="max-w-lg w-full">
                <div className="flex justify-center">
                    <span className="w-16 h-16 rounded-full flex items-center justify-center bg-primary-container text-on-primary-container">
                        <Icon name="timer" className="text-[36px]" />
                    </span>
                </div>
                <h1 className="mt-4 font-headline-sm text-headline-sm text-center">{t('confirm.title')}</h1>

                {!token ? (
                    <p className="mt-3 text-sm text-on-surface-variant text-center">{t('confirm.missingToken')}</p>
                ) : (
                    <>
                        <p className="mt-3 text-sm text-on-surface-variant">{t('confirm.intro')}</p>
                        <div className="mt-4">
                            <Alert>{error}</Alert>
                        </div>
                        <button type="button" disabled={submitting} onClick={submit} className={`${btnPrimary} w-full mt-2`}>
                            <Icon name="verified_user" className="text-[18px]" />
                            {submitting ? t('confirm.submitting') : t('confirm.button')}
                        </button>
                        <p className="mt-5 text-xs text-outline">{t('confirm.hint')}</p>
                    </>
                )}
            </Card>
        </div>
    );
}
