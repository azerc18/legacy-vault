import React, { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, formatDate, inputClass, btnPrimary } from './ownerUi.jsx';

// Giới hạn khớp DmsConfigRequest của BE
const LIMITS = {
    checkinIntervalDays: { min: 1, max: 365, label: 'Check-in every (days)', hint: 'How often you need to confirm you are okay.' },
    gracePeriodDays: { min: 1, max: 90, label: 'Grace period (days)', hint: 'Extra time after a missed check-in before hand-over starts.' },
    reminderCount: { min: 1, max: 10, label: 'Reminders', hint: 'Emails sent during the grace period.' },
};

const STATUS = {
    ACTIVE: { label: 'Active', cls: 'bg-success-container text-on-success-container', icon: 'favorite' },
    TRIGGERED: { label: 'Triggered', cls: 'bg-error-container text-on-error-container', icon: 'notifications_active' },
    SUSPENDED: { label: 'Suspended', cls: 'bg-warning-container text-on-warning-container', icon: 'pause_circle' },
};

export default function DeadMansSwitch() {
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
            .catch((err) => setError(getErrorMessage(err, 'Could not load settings.')))
            .finally(() => setLoading(false));
    }, []);

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
            if (!Number.isInteger(v) || v < l.min || v > l.max) return setError(`${l.label} must be between ${l.min} and ${l.max}.`);
        }
        setSaving(true);
        try {
            applyConfig(await ownerApi.saveDms({
                checkinIntervalDays: Number(form.checkinIntervalDays),
                gracePeriodDays: Number(form.gracePeriodDays),
                reminderCount: Number(form.reminderCount),
            }));
            setSuccess('Settings saved.');
        } catch (err) {
            setError(getErrorMessage(err, 'Could not save settings.'));
        } finally {
            setSaving(false);
        }
    };

    const checkIn = async () => {
        setError(''); setSuccess(''); setChecking(true);
        try {
            applyConfig(await ownerApi.checkIn());
            setSuccess("Checked in. We'll remind you before the next deadline.");
        } catch (err) {
            setError(getErrorMessage(err, 'Check-in failed.'));
        } finally {
            setChecking(false);
        }
    };

    const s = configured ? STATUS[config.status] || STATUS.ACTIVE : null;
    const needVault = /Vault/i.test(error) && !configured;

    return (
        <OwnerLayout title="Dead Man's Switch" subtitle="If you stop checking in, your vaults are handed over to your Executor.">
            <Alert type="success">{success}</Alert>
            <Alert>{error}</Alert>
            {needVault && (
                <p className="-mt-2 mb-4 text-sm"><Link to="/owner/vaults/new" className="text-secondary font-semibold">Create a vault first →</Link></p>
            )}
            {noExecutor && (
                <Alert type="warning">
                    {configured
                        ? 'No executor can receive the handover. Assign an executor.'
                        : "Assign a primary executor before enabling the Dead Man's Switch."}{' '}
                    <Link to="/owner/executors" className="font-semibold underline">Manage executors →</Link>
                </Alert>
            )}

            {loading ? <p className="text-on-surface-variant">Loading...</p> : (
                <div className="grid lg:grid-cols-[1fr_360px] gap-6 items-start">
                    {/* Trạng thái + check-in */}
                    <Card>
                        {!configured ? (
                            <div className="text-center py-6">
                                <Icon name="heart_minus" className="text-[40px] text-outline" />
                                <p className="mt-3 font-semibold">Not set up yet</p>
                                <p className="text-sm text-on-surface-variant mt-1">Choose your schedule on the right and save to turn it on.</p>
                            </div>
                        ) : (
                            <>
                                <div className="flex items-center justify-between gap-3">
                                    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-semibold ${s.cls}`}>
                                        <Icon name={s.icon} className="text-[18px]" /> {s.label}
                                    </span>
                                    {config.status === 'ACTIVE' && (
                                        <button onClick={checkIn} disabled={checking} className={btnPrimary}>
                                            <Icon name="how_to_reg" className="text-[18px]" /> {checking ? 'Checking in...' : "I'm okay — check in"}
                                        </button>
                                    )}
                                </div>

                                {config.status === 'TRIGGERED' && (
                                    <p className="mt-4 text-sm text-on-surface-variant">
                                        The hand-over was triggered on {formatDate(config.triggeredAt, true)}. Settings can no longer be changed.
                                    </p>
                                )}
                                {config.status === 'SUSPENDED' && (
                                    <p className="mt-4 text-sm text-on-surface-variant">Suspended by an administrator. Please contact support.</p>
                                )}

                                <dl className="mt-6 grid sm:grid-cols-2 gap-4 text-sm">
                                    <Info label="Last check-in" value={formatDate(config.lastCheckinAt, true)} />
                                    <Info label="Next check-in due" value={formatDate(config.checkinDueAt, true)} highlight />
                                    <Info label="Hand-over starts if no check-in" value={formatDate(config.triggerAt, true)} />
                                    <Info label="Next reminder" value={formatDate(config.nextReminderAt, true)} />
                                    <Info label="Reminders sent" value={`${config.remindersSent ?? 0} / ${config.reminderCount ?? '—'}`} />
                                    <Info label="Notified by" value={config.notificationChannel || 'EMAIL'} />
                                </dl>
                            </>
                        )}
                    </Card>

                    {/* Cấu hình */}
                    <Card title="Schedule" icon="tune">
                        <form onSubmit={save} className="space-y-4">
                            {Object.entries(LIMITS).map(([k, l]) => (
                                <label key={k} className="block">
                                    <span className="text-sm font-semibold text-on-surface-variant">{l.label}</span>
                                    <input
                                        type="number" min={l.min} max={l.max} disabled={locked}
                                        value={form[k]}
                                        onChange={(e) => setForm((f) => ({ ...f, [k]: e.target.value }))}
                                        className={`${inputClass} mt-1`}
                                    />
                                    <span className="text-xs text-outline">{l.hint} ({l.min}–{l.max})</span>
                                </label>
                            ))}
                            <button type="submit" disabled={saving || locked} className={`${btnPrimary} w-full`}>
                                {saving ? 'Saving...' : configured ? 'Save changes' : 'Turn on'}
                            </button>
                        </form>
                    </Card>
                </div>
            )}
        </OwnerLayout>
    );
}

function Info({ label, value, highlight }) {
    return (
        <div>
            <dt className="text-outline">{label}</dt>
            <dd className={`font-semibold ${highlight ? 'text-primary' : ''}`}>{value}</dd>
        </div>
    );
}
