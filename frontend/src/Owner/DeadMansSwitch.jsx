import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, formatDate, inputClass, btnPrimary } from './ownerUi.jsx';

// Giới hạn khớp DmsConfigRequest của BE; nhãn/gợi ý ở dms.fields.<key> (namespace dms)
const LIMITS = {
    checkinIntervalDays: { min: 1, max: 365 },
    gracePeriodDays: { min: 1, max: 90 },
    reminderCount: { min: 1, max: 10 },
};

// Nhãn trạng thái ở dms.status.<STATUS>
const STATUS = {
    ACTIVE: { cls: 'bg-success-container text-on-success-container', icon: 'favorite' },
    TRIGGERED: { cls: 'bg-error-container text-on-error-container', icon: 'notifications_active' },
    SUSPENDED: { cls: 'bg-warning-container text-on-warning-container', icon: 'pause_circle' },
};

export default function DeadMansSwitch() {
    const { t } = useTranslation('dms');
    const user = getUser();
    const [config, setConfig] = useState(null);       // DmsConfigResponse; configured=false = chưa cấu hình
    const [loading, setLoading] = useState(true);
    const [form, setForm] = useState({ checkinIntervalDays: 30, gracePeriodDays: 7, reminderCount: 3 });
    const [saving, setSaving] = useState(false);
    const [checking, setChecking] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const applyConfig = (c) => {
        setConfig(c);
        if (c?.configured) setForm({ checkinIntervalDays: c.checkinIntervalDays ?? 30, gracePeriodDays: c.gracePeriodDays ?? 7, reminderCount: c.reminderCount ?? 3 });
    };

    useEffect(() => {
        ownerApi.getDms()
            .then(applyConfig)
            .catch((err) => setError(getErrorMessage(err, t('dms.errors.load'))))
            .finally(() => setLoading(false));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps -- t chỉ dùng cho thông báo lỗi

    if (!user) return <Navigate to="/login" replace />;

    const configured = config?.configured === true;
    const locked = configured && config.status !== 'ACTIVE'; // TRIGGERED / SUSPENDED: không sửa, không check-in
    // FR-03: chưa cấu hình = chưa có Primary để bật lần đầu; đã cấu hình = trigger không kích hoạt được ai
    const noExecutor = config?.hasUsableExecutor === false && config.status !== 'TRIGGERED';

    const save = async (e) => {
        e.preventDefault();
        setError(''); setSuccess('');
        for (const [k, l] of Object.entries(LIMITS)) {
            const v = Number(form[k]);
            if (!Number.isInteger(v) || v < l.min || v > l.max) {
                return setError(t('dms.rangeError', { field: t(`dms.fields.${k}.label`), min: l.min, max: l.max }));
            }
        }
        setSaving(true);
        try {
            applyConfig(await ownerApi.saveDms({
                checkinIntervalDays: Number(form.checkinIntervalDays),
                gracePeriodDays: Number(form.gracePeriodDays),
                reminderCount: Number(form.reminderCount),
            }));
            setSuccess(t('dms.saved'));
        } catch (err) {
            setError(getErrorMessage(err, t('dms.errors.save')));
        } finally {
            setSaving(false);
        }
    };

    const checkIn = async () => {
        setError(''); setSuccess(''); setChecking(true);
        try {
            applyConfig(await ownerApi.checkIn());
            setSuccess(t('dms.checkedIn'));
        } catch (err) {
            setError(getErrorMessage(err, t('dms.errors.checkIn')));
        } finally {
            setChecking(false);
        }
    };

    const statusKey = configured && STATUS[config.status] ? config.status : 'ACTIVE';
    const s = configured ? STATUS[statusKey] : null;
    // Dựa vào câu lỗi của BE (mã CONFLICT, chưa có bản dịch nên giữ nguyên tiếng Anh) — giữ nguyên logic cũ
    const needVault = /Vault/i.test(error) && !configured;

    return (
        <OwnerLayout title={t('dms.title')} subtitle={t('dms.subtitle')}>
            <Alert type="success">{success}</Alert>
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

            {loading ? <p className="text-on-surface-variant">{t('dms.loading')}</p> : (
                <div className="grid lg:grid-cols-[1fr_360px] gap-6 items-start">
                    {/* Trạng thái + check-in */}
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
                                    {config.status === 'ACTIVE' && (
                                        <button onClick={checkIn} disabled={checking} className={btnPrimary}>
                                            <Icon name="how_to_reg" className="text-[18px]" /> {checking ? t('dms.checkingIn') : t('dms.checkIn')}
                                        </button>
                                    )}
                                </div>

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
                                    <Info label={t('dms.info.nextDue')} value={formatDate(config.checkinDueAt, true)} highlight />
                                    <Info label={t('dms.info.triggerAt')} value={formatDate(config.triggerAt, true)} />
                                    <Info label={t('dms.info.nextReminder')} value={formatDate(config.nextReminderAt, true)} />
                                    <Info label={t('dms.info.remindersSent')} value={`${config.remindersSent ?? 0} / ${config.reminderCount ?? '—'}`} />
                                    <Info label={t('dms.info.notifiedBy')} value={channelLabel(t, config.notificationChannel || 'EMAIL')} />
                                </dl>
                            </>
                        )}
                    </Card>

                    {/* Cấu hình */}
                    <Card title={t('dms.schedule')} icon="tune">
                        <form onSubmit={save} className="space-y-4">
                            {Object.entries(LIMITS).map(([k, l]) => (
                                <label key={k} className="block">
                                    <span className="text-sm font-semibold text-on-surface-variant">{t(`dms.fields.${k}.label`)}</span>
                                    <input
                                        type="number" min={l.min} max={l.max} disabled={locked}
                                        value={form[k]}
                                        onChange={(e) => setForm((f) => ({ ...f, [k]: e.target.value }))}
                                        className={`${inputClass} mt-1`}
                                    />
                                    <span className="text-xs text-outline">{t(`dms.fields.${k}.hint`)} ({l.min}–{l.max})</span>
                                </label>
                            ))}
                            <button type="submit" disabled={saving || locked} className={`${btnPrimary} w-full`}>
                                {saving ? t('dms.saving') : configured ? t('dms.saveChanges') : t('dms.turnOn')}
                            </button>
                        </form>
                    </Card>
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
