// "Cuộc sống có thay đổi?" (ý từ Life Event Engine của EstateOS), chỉ Owner.
// Chọn sự kiện -> gợi ý việc cần rà soát kèm link tới đúng trang. "Tôi đã rà soát xong" ghi 1 lần check-in DMS,
// nên tiêu chí "rà soát trong 6 tháng" của điểm sẵn sàng thừa kế được cập nhật mà không cần bảng mới.
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AutoHeight } from '@animate-ui/react/primitives/effects/auto-height';
import { ownerApi, PROTECTION_CHANGED } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { useToast } from '../Common/Toast.jsx';
import { Card, Alert, Icon, formatDate, btnPrimary } from '../Owner/ownerUi.jsx';

// Mỗi sự kiện: biểu tượng + các việc gợi ý (key i18n -> đường dẫn; '#id' = cuộn tới thẻ trên trang Hồ sơ)
const EVENTS = [
    { key: 'marriage', icon: 'favorite', tasks: [['addSpouse', '/owner/vaults'], ['updateWill', '/owner/vaults'], ['addConfirmer', '/owner/dms']] },
    { key: 'newChild', icon: 'child_care', tasks: [['addChild', '/owner/vaults'], ['futureMessage', '/owner/vaults'], ['backupExecutor', '/owner/executors']] },
    { key: 'separation', icon: 'heart_broken', tasks: [['reviewBeneficiaries', '/owner/vaults'], ['reviewExecutors', '/owner/executors'], ['reviewConfirmers', '/owner/dms']] },
    { key: 'bereavement', icon: 'local_florist', tasks: [['replaceBeneficiary', '/owner/vaults'], ['replaceExecutor', '/owner/executors']] },
    { key: 'newAsset', icon: 'add_card', tasks: [['addAsset', '/owner/vaults'], ['allocateAsset', '/owner/vaults'], ['uploadPapers', '/owner/vaults']] },
    { key: 'contactChanged', icon: 'contact_phone', tasks: [['updatePhone', '#profile-info'], ['checkReminders', '/owner/dms']] },
];

const SIX_MONTHS = 183 * 24 * 3600 * 1000;

function TaskLink({ to, children }) {
    const cls = 'flex items-center gap-2 p-3 rounded-xl bg-surface-container-low hover:bg-surface-container text-sm transition-colors';
    if (to.startsWith('#')) {
        return (
            <button type="button" className={`${cls} w-full text-left`}
                    onClick={() => document.getElementById(to.slice(1))?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>
                {children}
            </button>
        );
    }
    return <Link to={to} className={cls}>{children}</Link>;
}

export default function LifeEventsCard() {
    const { t } = useTranslation('profile');
    const toast = useToast();
    const [dms, setDmsState] = useState(undefined);  // undefined = đang tải, null = chưa cấu hình
    const [overdue, setOverdue] = useState(false);
    // Tính "quá 6 tháng" ngay khi nhận dữ liệu (không gọi Date.now trong lúc render)
    const setDms = (d) => {
        setDmsState(d);
        setOverdue(!d?.lastCheckinAt || Date.now() - new Date(d.lastCheckinAt).getTime() > SIX_MONTHS);
    };
    const [selected, setSelected] = useState([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    useEffect(() => {
        ownerApi.getDms().then((d) => setDms(d ?? null)).catch(() => setDms(null));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps -- chỉ tải lần đầu

    const toggle = (key) => setSelected((cur) => (cur.includes(key) ? cur.filter((k) => k !== key) : [...cur, key]));

    const canCheckIn = dms && (dms.status === 'ACTIVE' || dms.status === 'PENDING_CONFIRMATION');
    const lastReview = !!dms?.lastCheckinAt;

    const markReviewed = async () => {
        setError(''); setSuccess(''); setBusy(true);
        try {
            const updated = await ownerApi.checkIn();
            setDms(updated);
            setSelected([]);
            setSuccess(t('lifeEvents.reviewed'));
            toast.success(t('lifeEvents.reviewed'));
            window.dispatchEvent(new Event(PROTECTION_CHANGED));   // điểm sẵn sàng thừa kế tải lại
        } catch (e) {
            setError(getErrorMessage(e, t('lifeEvents.errors.review')));
        } finally {
            setBusy(false);
        }
    };

    const tasks = EVENTS.filter((e) => selected.includes(e.key))
        .flatMap((e) => e.tasks.map(([key, to]) => ({ key, to, event: e.key })));
    // Bỏ việc trùng (vd. vừa kết hôn vừa có con đều dẫn tới /owner/vaults nhưng khác nội dung -> giữ theo key)
    const uniqueTasks = tasks.filter((x, i) => tasks.findIndex((y) => y.key === x.key) === i);

    return (
        <Card title={t('lifeEvents.title')} icon="diversity_1">
            <p className="-mt-2 mb-4 text-sm text-on-surface-variant">{t('lifeEvents.description')}</p>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>

            {dms !== undefined && (
                <div className={`mb-4 p-3 rounded-xl text-sm flex items-center gap-2 ${overdue ? 'bg-warning-container text-on-warning-container' : 'bg-surface-container-low text-on-surface-variant'}`}>
                    <Icon name={overdue ? 'schedule' : 'event_available'} className="text-[18px]" />
                    <span>
                        {lastReview
                            ? t('lifeEvents.lastReview', { date: formatDate(dms.lastCheckinAt) })
                            : t('lifeEvents.neverReviewed')}
                        {overdue && ` ${t('lifeEvents.overdue')}`}
                    </span>
                </div>
            )}

            <div className="flex flex-wrap gap-2" role="group" aria-label={t('lifeEvents.title')}>
                {EVENTS.map((e) => {
                    const on = selected.includes(e.key);
                    return (
                        <button key={e.key} type="button" aria-pressed={on} onClick={() => toggle(e.key)}
                                className={`h-9 px-3 rounded-full border text-sm font-semibold inline-flex items-center gap-1.5 transition-colors ${on
                                    ? 'bg-primary text-on-primary border-primary'
                                    : 'border-outline-variant text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'}`}>
                            <Icon name={e.icon} className="text-[18px]" />
                            {t(`lifeEvents.events.${e.key}`)}
                        </button>
                    );
                })}
            </div>

            <AutoHeight>
                {uniqueTasks.length > 0 && (
                    <div className="pt-5">
                        <p className="text-sm font-semibold mb-2">{t('lifeEvents.suggested')}</p>
                        <div className="grid sm:grid-cols-2 gap-2">
                            {uniqueTasks.map((task) => (
                                <TaskLink key={task.key} to={task.to}>
                                    <Icon name="arrow_circle_right" className="text-[18px] text-secondary" />
                                    <span className="flex-1">{t(`lifeEvents.tasks.${task.key}`)}</span>
                                </TaskLink>
                            ))}
                        </div>
                    </div>
                )}
            </AutoHeight>

            <div className="mt-5 flex flex-col sm:flex-row sm:items-center justify-end gap-3">
                {dms !== undefined && !canCheckIn && (
                    <p className="text-xs text-on-surface-variant sm:mr-auto">
                        {t('lifeEvents.needDms')}{' '}
                        <Link to="/owner/dms" className="font-semibold text-secondary hover:underline">{t('lifeEvents.openDms')}</Link>
                    </p>
                )}
                <button type="button" onClick={markReviewed} disabled={!canCheckIn || busy} className={btnPrimary}>
                    <Icon name="task_alt" className="text-[18px]" />
                    {busy ? t('lifeEvents.saving') : t('lifeEvents.markReviewed')}
                </button>
            </div>
        </Card>
    );
}
