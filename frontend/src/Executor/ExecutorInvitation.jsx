// Lời mời làm Người thi hành: người được Owner chỉ định tự đồng ý hoặc từ chối.
// Mở từ link trong email mời hoặc từ chuông thông báo. Cần đăng nhập, KHÔNG cần role EXECUTOR trong JWT
// (phiên đăng nhập cũ có thể chưa mang role vừa được cấp); BE tự kiểm lời mời có thuộc người gọi không.
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useParams } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { getErrorMessage } from '../api/http.js';
import { executorApi } from '../api/executorApi.js';
import AppHeader, { PAGE_WIDTH } from '../Common/AppHeader.jsx';
import GlassSurface from '../Common/GlassSurface.jsx';
import { formatDate } from '../i18n/format.js';
import { Alert, Icon, inputClass, btnPrimary, btnOutline, LoadingSkeleton } from '../Owner/ownerUi.jsx';

// Owner chưa có tên thì hiện email
const ownerName = (inv) => inv?.ownerFullName || inv?.ownerEmail;

// Một dòng trách nhiệm trong thẻ lời mời
function Duty({ icon, children }) {
    return (
        <li className="flex items-start gap-3">
            <Icon name={icon} className="text-[20px] text-secondary shrink-0 mt-0.5" />
            <span className="text-sm text-on-surface-variant">{children}</span>
        </li>
    );
}

export default function ExecutorInvitation() {
    const { t } = useTranslation('executor');
    const { assignmentId } = useParams();
    const user = getUser();

    const [invitation, setInvitation] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState('');            // '' | 'accept' | 'decline'
    const [declining, setDeclining] = useState(false); // đang mở ô nhập lý do
    const [reason, setReason] = useState('');
    const [result, setResult] = useState('');        // '' | 'accepted' | 'declined'

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setInvitation(await executorApi.getInvitation(assignmentId));
            setError('');
        } catch (err) {
            setError(getErrorMessage(err, t('invitation.loadError')));
            setInvitation(null);
        } finally {
            setLoading(false);
        }
    }, [assignmentId, t]);

    useEffect(() => { load(); }, [load]);

    if (!user) return <Navigate to="/login" replace />;

    const respond = async (accept) => {
        setBusy(accept ? 'accept' : 'decline');
        setError('');
        try {
            const updated = accept
                ? await executorApi.acceptInvitation(assignmentId)
                : await executorApi.declineInvitation(assignmentId, reason.trim() || null);
            setInvitation(updated);
            setDeclining(false);
            setResult(accept ? 'accepted' : 'declined');
        } catch (err) {
            setError(getErrorMessage(err, t('invitation.respondError')));
            // Owner vừa thu hồi / DMS vừa kích hoạt: tải lại để hiện đúng trạng thái mới
            load();
        } finally {
            setBusy('');
        }
    };

    // Đã trả lời trước đó (mở lại link) hoặc vừa trả lời xong.
    // Chỉ nhận đúng hai giá trị của BE: dữ liệu lạ thì coi như chưa trả lời, không hiện nhầm "Bạn đã từ chối".
    const accepted = invitation?.responseStatus === 'ACCEPTED';
    const answered = accepted || invitation?.responseStatus === 'DECLINED';

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader workspace={t('workspace')} home="/executor/home" userHome />

            <main className={`${PAGE_WIDTH} py-10`}>
                <div className="max-w-2xl mx-auto">
                    <Alert>{error}</Alert>

                    {loading && <LoadingSkeleton label={t('loading')} />}

                    {!loading && !invitation && !error && (
                        <GlassSurface material="thick" className="rounded-2xl p-8 text-center">
                            <Icon name="link_off" className="text-[40px] text-outline" />
                            <h1 className="mt-3 font-title-lg text-title-lg">{t('invitation.notFoundTitle')}</h1>
                            <p className="mt-2 text-sm text-on-surface-variant">{t('invitation.notFoundText')}</p>
                            <Link to="/executor/home" className={`${btnOutline} mt-6 inline-flex`}>
                                {t('invitation.backHome')}
                            </Link>
                        </GlassSurface>
                    )}

                    {!loading && invitation && (
                        <GlassSurface material="thick" className="rounded-2xl p-7 sm:p-8">
                            {/* Kết quả vừa phản hồi, hoặc trạng thái khi mở lại link đã xử lý */}
                            {result || answered ? (
                                <div className="text-center">
                                    <span className={`inline-flex items-center justify-center w-14 h-14 rounded-2xl ${
                                        accepted
                                            ? 'bg-success-container text-on-success-container'
                                            : 'bg-surface-container-high text-on-surface-variant'}`}>
                                        <Icon name={accepted ? 'task_alt' : 'person_off'} className="text-[28px]" />
                                    </span>
                                    <h1 className="mt-4 font-headline-sm text-headline-sm">
                                        {accepted ? t('invitation.acceptedTitle') : t('invitation.declinedTitle')}
                                    </h1>
                                    <p className="mt-2 text-sm text-on-surface-variant">
                                        {accepted
                                            ? t('invitation.acceptedText', { name: ownerName(invitation) })
                                            : t('invitation.declinedText', { name: ownerName(invitation) })}
                                    </p>
                                    {invitation.respondedAt && (
                                        <p className="mt-1 text-xs text-outline">
                                            {t('invitation.respondedAt', { date: formatDate(invitation.respondedAt, true) })}
                                        </p>
                                    )}
                                    <Link to="/executor/home" className={`${btnPrimary} mt-6 inline-flex`}>
                                        <Icon name="home" className="text-[18px]" /> {t('invitation.backHome')}
                                    </Link>
                                </div>
                            ) : (
                                <>
                                    <p className="text-xs font-semibold text-secondary uppercase tracking-wide">
                                        {t(`type.${invitation.executorType}`, invitation.executorType)}
                                    </p>
                                    <h1 className="mt-1 font-headline-sm text-headline-sm">
                                        {t('invitation.title', { name: ownerName(invitation) })}
                                    </h1>
                                    <p className="mt-2 text-sm text-on-surface-variant">
                                        {t('invitation.intro', { name: ownerName(invitation) })}
                                    </p>

                                    <GlassSurface material="inline" className="mt-6 rounded-xl p-4 flex flex-wrap gap-x-8 gap-y-3">
                                        <div>
                                            <p className="text-xs text-outline">{t('invitation.from')}</p>
                                            <p className="text-sm font-semibold">{ownerName(invitation)}</p>
                                            {invitation.ownerFullName && (
                                                <p className="text-xs text-on-surface-variant">{invitation.ownerEmail}</p>
                                            )}
                                        </div>
                                        <div>
                                            <p className="text-xs text-outline">{t('invitation.role')}</p>
                                            <p className="text-sm font-semibold">
                                                {t(`type.${invitation.executorType}`, invitation.executorType)}
                                            </p>
                                        </div>
                                        {invitation.assignedAt && (
                                            <div>
                                                <p className="text-xs text-outline">{t('invitation.invitedAt')}</p>
                                                <p className="text-sm font-semibold">{formatDate(invitation.assignedAt)}</p>
                                            </div>
                                        )}
                                    </GlassSurface>

                                    <h2 className="mt-7 font-label-lg text-label-lg text-on-surface-variant">
                                        {t('invitation.dutiesTitle')}
                                    </h2>
                                    <ul className="mt-3 space-y-3">
                                        <Duty icon="notifications_active">{t('invitation.duties.notified')}</Duty>
                                        <Duty icon="upload_file">{t('invitation.duties.submit')}</Duty>
                                        <Duty icon="handshake">{t('invitation.duties.handover')}</Duty>
                                        {invitation.executorType === 'PRIMARY' && (
                                            <Duty icon="timer">
                                                {t('invitation.duties.deadline', { days: invitation.backupActivationWaitDays })}
                                            </Duty>
                                        )}
                                        <Duty icon="lock">{t('invitation.duties.noAccess')}</Duty>
                                    </ul>

                                    {!invitation.canRespond ? (
                                        <div className="mt-7">
                                            <Alert type="warning">{t('invitation.closed')}</Alert>
                                        </div>
                                    ) : declining ? (
                                        <div className="mt-7">
                                            <label className="block">
                                                <span className="text-sm font-semibold">{t('invitation.reasonLabel')}</span>
                                                <textarea
                                                    rows={3}
                                                    maxLength={500}
                                                    value={reason}
                                                    onChange={(e) => setReason(e.target.value)}
                                                    placeholder={t('invitation.reasonPlaceholder')}
                                                    className={`${inputClass} mt-1 h-auto py-2.5`}
                                                />
                                            </label>
                                            <p className="mt-2 text-sm text-on-surface-variant">{t('invitation.declineConfirm')}</p>
                                            <div className="mt-4 flex flex-wrap gap-3">
                                                <button
                                                    onClick={() => { setDeclining(false); setReason(''); }}
                                                    disabled={busy !== ''}
                                                    className={btnOutline}
                                                >
                                                    {t('invitation.back')}
                                                </button>
                                                <button
                                                    onClick={() => respond(false)}
                                                    disabled={busy !== ''}
                                                    className={`${btnPrimary} bg-error text-on-error hover:bg-error`}
                                                >
                                                    <Icon name="person_off" className="text-[18px]" />
                                                    {busy === 'decline' ? t('saving') : t('invitation.declineConfirmButton')}
                                                </button>
                                            </div>
                                        </div>
                                    ) : (
                                        <div className="mt-7 flex flex-wrap gap-3">
                                            <button
                                                onClick={() => respond(true)}
                                                disabled={busy !== ''}
                                                className={btnPrimary}
                                            >
                                                <Icon name="task_alt" className="text-[18px]" />
                                                {busy === 'accept' ? t('saving') : t('invitation.accept')}
                                            </button>
                                            <button
                                                onClick={() => setDeclining(true)}
                                                disabled={busy !== ''}
                                                className={btnOutline}
                                            >
                                                {t('invitation.decline')}
                                            </button>
                                        </div>
                                    )}
                                </>
                            )}
                        </GlassSurface>
                    )}
                </div>
            </main>
        </div>
    );
}
