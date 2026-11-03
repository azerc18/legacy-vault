import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { dmsConfirmApi } from '../api/dmsConfirmApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnOutline, btnPrimary, Card, Icon } from './ownerUi.jsx';

// X2 (C1): trang công khai mở từ link trong email. Mở trang KHÔNG gọi API (trình quét link của hộp thư có thể tự mở);
// quyết định chỉ gửi khi người nhận bấm nút. "Không liên lạc được" có thêm bước xác nhận vì không thể hoàn tác.
export default function DmsConfirmPage() {
    const { t } = useTranslation('dmsConfirm');
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token') || '';
    const [confirmingUnreachable, setConfirmingUnreachable] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    const submit = async (decision) => {
        setError('');
        setSubmitting(true);
        try {
            const res = await dmsConfirmApi.submitDecision(token, decision);
            const params = new URLSearchParams({ status: 'success', decision: res?.decision || decision });
            navigate(`/dms/confirm-done?${params.toString()}`, { replace: true });
        } catch (err) {
            setError(getErrorMessage(err, t('confirm.submitFailed')));
            setSubmitting(false);
            setConfirmingUnreachable(false);
        }
    };

    return (
        <div className="min-h-screen bg-surface text-on-surface flex flex-col justify-center items-center px-5 py-12">
            <Card className="max-w-lg w-full">
                <div className="flex justify-center">
                    <span className="w-16 h-16 rounded-full flex items-center justify-center bg-warning-container text-on-warning-container">
                        <Icon name="verified_user" className="text-[36px]" />
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

                        {!confirmingUnreachable ? (
                            <div className="mt-2 grid gap-3">
                                <button type="button" disabled={submitting} onClick={() => submit('ALIVE')} className={`${btnPrimary} w-full`}>
                                    <Icon name="favorite" className="text-[18px]" />
                                    {submitting ? t('confirm.submitting') : t('confirm.aliveButton')}
                                </button>
                                <button type="button" disabled={submitting} onClick={() => setConfirmingUnreachable(true)} className={`${btnOutline} w-full`}>
                                    <Icon name="notification_important" className="text-[18px]" />
                                    {t('confirm.unreachableButton')}
                                </button>
                            </div>
                        ) : (
                            <div className="mt-2 p-4 rounded-xl bg-error-container/40 border border-outline-variant/30">
                                <p className="font-semibold text-sm">{t('confirm.unreachableConfirmTitle')}</p>
                                <p className="mt-1 text-sm text-on-surface-variant">{t('confirm.unreachableConfirmBody')}</p>
                                <div className="mt-4 flex flex-wrap gap-3 justify-end">
                                    <button type="button" disabled={submitting} onClick={() => setConfirmingUnreachable(false)} className={btnOutline}>
                                        {t('confirm.back')}
                                    </button>
                                    <button type="button" disabled={submitting} onClick={() => submit('UNREACHABLE')} className={btnPrimary}>
                                        {submitting ? t('confirm.submitting') : t('confirm.unreachableConfirm')}
                                    </button>
                                </div>
                            </div>
                        )}

                        <p className="mt-5 text-xs text-outline">{t('confirm.hint')}</p>
                    </>
                )}
            </Card>
        </div>
    );
}
