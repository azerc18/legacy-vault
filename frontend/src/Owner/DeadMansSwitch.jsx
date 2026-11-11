import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import InfoTip from '../Common/InfoTip.jsx';
import { useToast } from '../Common/Toast.jsx';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorCode, getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, formatDate, inputClass, btnOutline, btnPrimary, LoadingSkeleton } from './ownerUi.jsx';
import { VipChip, VipLocked } from '../Common/VipLock.jsx';
import { useVipStatus } from '../Common/useVipStatus.js';

// Giới hạn khớp DmsConfigRequest của BE; nhãn/gợi ý ở dms.fields.<key> (namespace dms)
const LIMITS = {
    checkinIntervalDays: { min: 1, max: 365 },
    gracePeriodDays: { min: 1, max: 90 },
    reminderCount: { min: 1, max: 10 },
};

// Nhãn trạng thái ở dms.status.<STATUS>
const STATUS = {
    ACTIVE: { cls: 'bg-success-container text-on-success-container', icon: 'favorite' },
    // X2: đang chờ người xác nhận chéo trả lời (tối đa 72 giờ)
    PENDING_CONFIRMATION: { cls: 'bg-warning-container text-on-warning-container', icon: 'verified_user' },
    TRIGGERED: { cls: 'bg-error-container text-on-error-container', icon: 'notifications_active' },
    SUSPENDED: { cls: 'bg-warning-container text-on-warning-container', icon: 'pause_circle' },
    // Không phải trạng thái của BE: DMS ACTIVE / chờ xác nhận nhưng Owner đã xoá hết két (hasVault = false)
    NO_VAULT: { cls: 'bg-warning-container text-on-warning-container', icon: 'pause_circle' },
};

export default function DeadMansSwitch() {
    const { t } = useTranslation('dms');
    const vip = useVipStatus();   // false = gói FREE: Trusted Social Confirmers hiện mờ + khoá
    const toast = useToast();
    const user = getUser();
    const [config, setConfig] = useState(null);       // DmsConfigResponse; configured=false = chưa cấu hình
    const [confirmers, setConfirmers] = useState([]); // X2: Trusted Social Confirmers
    const [loading, setLoading] = useState(true);
    const [form, setForm] = useState({ checkinIntervalDays: 30, gracePeriodDays: 7, reminderCount: 3 });
    const [saving, setSaving] = useState(false);
    const [checking, setChecking] = useState(false);
    const [justCheckedIn, setJustCheckedIn] = useState(false);
    const [confirmerForm, setConfirmerForm] = useState({ name: '', email: '', relationship: '' });
    const [addingConfirmer, setAddingConfirmer] = useState(false);
    const [removingId, setRemovingId] = useState('');
    const [error, setError] = useState('');

    const applyConfig = (c) => {
        setConfig(c);
        if (c?.configured) setForm({ checkinIntervalDays: c.checkinIntervalDays ?? 30, gracePeriodDays: c.gracePeriodDays ?? 7, reminderCount: c.reminderCount ?? 3 });
    };

    // X2: chỉ tải danh sách confirmer khi DMS đã cấu hình (chưa cấu hình thì BE không có DmsConfig để gắn)
    const loadConfirmers = (c) => {
        if (!c?.configured) return;
        ownerApi.listDmsConfirmers()
            .then(setConfirmers)
            .catch(() => {});
    };

    useEffect(() => {
        ownerApi.getDms()
            .then((c) => {
                applyConfig(c);
                loadConfirmers(c);
            })
            .catch((err) => setError(getErrorMessage(err, t('dms.errors.load'))))
            .finally(() => setLoading(false));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps -- t chỉ dùng cho thông báo lỗi

    if (!user) return <Navigate to="/login" replace />;

    const configured = config?.configured === true;
    // X2: đang chờ xác nhận vẫn check-in / sửa cấu hình được (cả hai đều huỷ quy trình xác nhận)
    const canCheckIn = configured && (config.status === 'ACTIVE' || config.status === 'PENDING_CONFIRMATION');
    const locked = configured && !canCheckIn; // TRIGGERED / SUSPENDED: không sửa, không check-in
    // Owner đã xoá hết két: BE tạm dừng DMS (không nhắc, không bàn giao); nút check-in vẫn hiện nhưng BE trả DMS_NO_VAULT.
    // Giữ lần check-in cuối, các mốc của chu kỳ không còn chạy nên hiện "—". Tạo két mới thì BE bắt đầu chu kỳ mới.
    const pausedNoVault = canCheckIn && config.hasVault === false;
    // FR-03: chưa cấu hình = chưa có Primary để bật lần đầu; đã cấu hình = trigger không kích hoạt được ai
    const noExecutor = config?.hasUsableExecutor === false && config.status !== 'TRIGGERED';

    const save = async (e) => {
        e.preventDefault();
        setError('');
        for (const [k, l] of Object.entries(LIMITS)) {
            const v = Number(form[k]);
            if (!Number.isInteger(v) || v < l.min || v > l.max) {
                return setError(t('dms.rangeError', { field: t(`dms.fields.${k}.label`), min: l.min, max: l.max }));
            }
        }
        setSaving(true);
        try {
            const saved = await ownerApi.saveDms({
                checkinIntervalDays: Number(form.checkinIntervalDays),
                gracePeriodDays: Number(form.gracePeriodDays),
                reminderCount: Number(form.reminderCount),
            });
            applyConfig(saved);
            loadConfirmers(saved);
            toast.success(t('dms.saved'));
        } catch (err) {
            setError(getErrorMessage(err, t('dms.errors.save')));
        } finally {
            setSaving(false);
        }
    };

    const checkIn = async () => {
        setError(''); setChecking(true);
        try {
            applyConfig(await ownerApi.checkIn());
            setJustCheckedIn(true);   // khoá nút tới khi tải lại trang, BE cũng chặn check-in liên tục
            toast.success(t('dms.checkedIn'));
        } catch (err) {
            setError(getErrorMessage(err, t('dms.errors.checkIn')));
            // Két vừa bị xoá ở tab khác: tải lại để khung trạng thái chuyển sang "Tạm dừng — chưa có két"
            if (getErrorCode(err) === 'DMS_NO_VAULT') {
                ownerApi.getDms().then(applyConfig).catch(() => {});
            }
        } finally {
            setChecking(false);
        }
    };

    const handleAddConfirmer = async (e) => {
        e.preventDefault();
        setError('');
        if (!confirmerForm.name.trim() || !confirmerForm.email.trim()) {
            setError(t('dms.confirmers.missingFields'));
            return;
        }
        setAddingConfirmer(true);
        try {
            const added = await ownerApi.addDmsConfirmer({
                name: confirmerForm.name.trim(),
                email: confirmerForm.email.trim(),
                relationship: confirmerForm.relationship.trim() || null,
            });
            setConfirmers((prev) => [...prev, added]);
            setConfirmerForm({ name: '', email: '', relationship: '' });
            toast.success(t('dms.confirmers.added'));
        } catch (err) {
            // PLAN_LIMIT_REACHED (gói FREE): getErrorMessage hiện câu đã dịch; PlanLimitUpgradeDialog chung (T1) tự mở
            setError(getErrorMessage(err, t('dms.confirmers.addFailed')));
        } finally {
            setAddingConfirmer(false);
        }
    };

    const handleRemoveConfirmer = async (id) => {
        setError('');
        setRemovingId(id);
        try {
            await ownerApi.removeDmsConfirmer(id);
            setConfirmers((prev) => prev.filter((c) => c.id !== id));
            toast.success(t('dms.confirmers.removed'));
        } catch (err) {
            setError(getErrorMessage(err, t('dms.confirmers.removeFailed')));
        } finally {
            setRemovingId('');
        }
    };

    const statusKey = pausedNoVault ? 'NO_VAULT' : configured && STATUS[config.status] ? config.status : 'ACTIVE';
    const s = configured ? STATUS[statusKey] : null;
    // Dựa vào câu lỗi của BE (mã CONFLICT, chưa có bản dịch nên giữ nguyên tiếng Anh) — giữ nguyên logic cũ
    const needVault = /Vault/i.test(error) && !configured;

    return (
        <OwnerLayout title={t('dms.title')} subtitle={t('dms.subtitle')}>
            <Alert>{error}</Alert>
            {needVault && (
                <p className="-mt-2 mb-4 text-sm"><Link to="/owner/vaults/new" className="text-secondary font-semibold">{t('dms.createVaultFirst')}</Link></p>
            )}
            {noExecutor && (
                <Alert type="warning">
                    {configured ? t('dms.noExecutor.configured') : t('dms.noExecutor.notConfigured')}{' '}
                    <Link to="/owner/executors" className="font-semibold underline">{t('dms.manageExecutors')}</Link>
                </Alert>
            )}

            {loading ? <LoadingSkeleton label={t('dms.loading')} /> : (
                <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-6 items-start">
                    <div className="space-y-6">
                    {/* Trạng thái + check-in */}
                    <div data-tour="dms-status">
                    <Card>
                        {!configured ? (
                            <div className="text-center py-6">
                                <Icon name="heart_minus" className="text-[40px] text-outline" />
                                <p className="mt-3 font-semibold">{t('dms.notSetUp')}</p>
                                <p className="text-sm text-on-surface-variant mt-1">{t('dms.notSetUpHint')}</p>
                            </div>
                        ) : (
                            <>
                                <div className="flex items-center justify-between gap-3">
                                    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-semibold ${s.cls}`}>
                                        <Icon name={s.icon} className="text-[18px]" /> {t(`dms.status.${statusKey}`)}
                                    </span>
                                    {canCheckIn && (
                                        <button onClick={checkIn} disabled={checking || justCheckedIn} className={btnPrimary}>
                                            <Icon name="how_to_reg" className="text-[18px]" /> {checking ? t('dms.checkingIn') : justCheckedIn ? t('dms.checkedInShort') : t('dms.checkIn')}
                                        </button>
                                    )}
                                </div>

                                {pausedNoVault && (
                                    <p className="mt-4 text-sm text-on-surface-variant">
                                        {t('dms.noVaultNote')}{' '}
                                        <Link to="/owner/vaults/new" className="text-secondary font-semibold">{t('dms.createVaultFirst')}</Link>
                                    </p>
                                )}
                                {!pausedNoVault && config.status === 'PENDING_CONFIRMATION' && (
                                    <p className="mt-4 text-sm text-on-surface-variant">
                                        {t('dms.pending.body', { deadline: formatDate(config.triggerAt, true), button: t('dms.checkIn') })}
                                    </p>
                                )}
                                {config.status === 'TRIGGERED' && (
                                    <p className="mt-4 text-sm text-on-surface-variant">
                                        {t('dms.triggeredNote', { date: formatDate(config.triggeredAt, true) })}
                                    </p>
                                )}
                                {config.status === 'SUSPENDED' && (
                                    <p className="mt-4 text-sm text-on-surface-variant">{t('dms.suspendedNote')}</p>
                                )}

                                <dl className="mt-6 grid sm:grid-cols-2 gap-4 text-sm">
                                    <Info label={t('dms.info.lastCheckin')} value={formatDate(config.lastCheckinAt, true)} />
                                    <Info label={t('dms.info.nextDue')} value={pausedNoVault ? '—' : formatDate(config.checkinDueAt, true)} highlight={!pausedNoVault} />
                                    <Info label={t('dms.info.triggerAt')} value={pausedNoVault ? '—' : formatDate(config.triggerAt, true)} />
                                    <Info label={t('dms.info.nextReminder')} value={pausedNoVault ? '—' : formatDate(config.nextReminderAt, true)} />
                                    <Info label={t('dms.info.remindersSent')} value={pausedNoVault ? '—' : `${config.remindersSent ?? 0} / ${config.reminderCount ?? '—'}`} />
                                    <Info label={t('dms.info.notifiedBy')} value={channelLabel(t, config.notificationChannel || 'EMAIL')} />
                                </dl>
                            </>
                        )}
                    </Card>
                    </div>

                    {/* X2: Trusted Social Confirmers (chỉ khi DMS đã cấu hình) */}
                    {configured && (
                        <div data-tour="dms-confirmers">
                        <VipLocked locked={vip === false}>
                        <Card title={<>{t('dms.confirmers.title')} <InfoTip term="crossConfirm" /> <VipChip className="ml-1" /></>} icon="verified_user">
                            {/* Card không có prop subtitle nên mô tả đặt trong thân card */}
                            <p className="text-sm text-on-surface-variant mb-4">{t('dms.confirmers.subtitle')}</p>
                            {confirmers.length === 0 ? (
                                <p className="text-sm text-on-surface-variant">{t('dms.confirmers.empty')}</p>
                            ) : (
                                <div className="space-y-3 mb-5">
                                    {confirmers.map((c) => (
                                        <div
                                            key={c.id}
                                            className="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/20 flex items-center justify-between gap-3"
                                        >
                                            <div>
                                                <p className="font-semibold text-sm text-on-surface">
                                                    {c.name}{' '}
                                                    {c.relationship && (
                                                        <span className="text-xs font-normal text-on-surface-variant">
                                                            ({c.relationship})
                                                        </span>
                                                    )}
                                                </p>
                                                <p className="text-xs text-on-surface-variant">{c.email}</p>
                                            </div>
                                            {!locked && (
                                                <button
                                                    type="button"
                                                    disabled={removingId === c.id}
                                                    onClick={() => handleRemoveConfirmer(c.id)}
                                                    className={btnOutline}
                                                >
                                                    <Icon name="delete" className="text-[16px]" />
                                                    {t('dms.confirmers.remove')}
                                                </button>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            )}

                            {!locked && confirmers.length < 2 && (
                                <form onSubmit={handleAddConfirmer} className="grid sm:grid-cols-3 gap-3 pt-2">
                                    <input
                                        type="text"
                                        placeholder={t('dms.confirmers.namePlaceholder')}
                                        value={confirmerForm.name}
                                        onChange={(e) => setConfirmerForm((f) => ({ ...f, name: e.target.value }))}
                                        className={inputClass}
                                    />
                                    <input
                                        type="email"
                                        placeholder={t('dms.confirmers.emailPlaceholder')}
                                        value={confirmerForm.email}
                                        onChange={(e) => setConfirmerForm((f) => ({ ...f, email: e.target.value }))}
                                        className={inputClass}
                                    />
                                    <div className="flex gap-2">
                                        <input
                                            type="text"
                                            placeholder={t('dms.confirmers.relationshipPlaceholder')}
                                            value={confirmerForm.relationship}
                                            onChange={(e) => setConfirmerForm((f) => ({ ...f, relationship: e.target.value }))}
                                            className={inputClass}
                                        />
                                        <button type="submit" disabled={addingConfirmer} className={`${btnPrimary} shrink-0`}>
                                            <Icon name="person_add" className="text-[18px]" />
                                            {t('dms.confirmers.add')}
                                        </button>
                                    </div>
                                </form>
                            )}
                        </Card>
                        </VipLocked>
                        </div>
                    )}
                    </div>

                    {/* Cấu hình */}
                    <div data-tour="dms-interval">
                    <Card title={<>{t('dms.schedule')} <InfoTip term="dms" /></>} icon="tune">
                        <form onSubmit={save} className="space-y-4">
                            {Object.entries(LIMITS).map(([k, l]) => (
                                <label key={k} className="block">
                                    <span className="text-sm font-semibold text-on-surface-variant">{t(`dms.fields.${k}.label`)}</span>
                                    <input
                                        type="number" min={l.min} max={l.max} disabled={locked || pausedNoVault}
                                        value={form[k]}
                                        onChange={(e) => setForm((f) => ({ ...f, [k]: e.target.value }))}
                                        className={`${inputClass} mt-1`}
                                    />
                                    <span className="text-xs text-outline">{t(`dms.fields.${k}.hint`)} ({l.min}–{l.max})</span>
                                </label>
                            ))}
                            <button type="submit" disabled={saving || locked || pausedNoVault} className={`${btnPrimary} w-full`}>
                                {saving ? t('dms.saving') : configured ? t('dms.saveChanges') : t('dms.turnOn')}
                            </button>
                            {/* Review #263: đang tạm dừng vì hết két thì BE từ chối lưu lịch, nên khoá form và nói lý do */}
                            {pausedNoVault && (
                                <p className="text-xs text-on-surface-variant text-center">{t('dms.noVaultScheduleHint')}</p>
                            )}
                        </form>
                    </Card>
                    </div>
                </div>
            )}
        </OwnerLayout>
    );
}

// Kênh chưa có bản dịch thì hiện nguyên giá trị enum như trước
const channelLabel = (t, channel) => t(`dms.channel.${channel}`, { defaultValue: channel });

function Info({ label, value, highlight }) {
    return (
        <div>
            <dt className="text-outline">{label}</dt>
            <dd className={`font-semibold ${highlight ? 'text-primary' : ''}`}>{value}</dd>
        </div>
    );
}
