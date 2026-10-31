import React, { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, formatDate, inputClass, btnOutline, btnPrimary } from './ownerUi.jsx';

// Giới hạn khớp DmsConfigRequest của BE
const LIMITS = {
    checkinIntervalDays: { min: 1, max: 365, label: 'Check-in every (days)', hint: 'How often you need to confirm you are okay.' },
    gracePeriodDays: { min: 1, max: 90, label: 'Grace period (days)', hint: 'Extra time after a missed check-in before hand-over starts.' },
    reminderCount: { min: 1, max: 10, label: 'Reminders', hint: 'Emails sent during the grace period.' },
};

const STATUS = {
    ACTIVE: { label: 'Active', cls: 'bg-success-container text-on-success-container', icon: 'favorite' },
    PENDING_CONFIRMATION: { label: 'Awaiting Social Confirmation (72h)', cls: 'bg-warning-container text-on-warning-container', icon: 'verified_user' },
    TRIGGERED: { label: 'Triggered', cls: 'bg-error-container text-on-error-container', icon: 'notifications_active' },
    SUSPENDED: { label: 'Suspended', cls: 'bg-warning-container text-on-warning-container', icon: 'pause_circle' },
};

export default function DeadMansSwitch() {
    const user = getUser();
    const [config, setConfig] = useState(null);       // DmsConfigResponse | null (chưa cấu hình)
    const [confirmers, setConfirmers] = useState([]); // X2: Trusted Social Confirmers
    const [loading, setLoading] = useState(true);
    const [form, setForm] = useState({ checkinIntervalDays: 30, gracePeriodDays: 7, reminderCount: 3 });
    const [confirmerForm, setConfirmerForm] = useState({ name: '', email: '', relationship: '' });
    const [saving, setSaving] = useState(false);
    const [checking, setChecking] = useState(false);
    const [addingConfirmer, setAddingConfirmer] = useState(false);
    const [removingId, setRemovingId] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const applyConfig = (c) => {
        setConfig(c);
        if (c) setForm({ checkinIntervalDays: c.checkinIntervalDays ?? 30, gracePeriodDays: c.gracePeriodDays ?? 7, reminderCount: c.reminderCount ?? 3 });
    };

    const loadConfirmers = () => {
        ownerApi.listDmsConfirmers()
            .then(setConfirmers)
            .catch(() => {});
    };

    useEffect(() => {
        ownerApi.getDms()
            .then((c) => {
                applyConfig(c);
                loadConfirmers();
            })
            .catch((err) => { if (err.response?.status !== 404) setError(getErrorMessage(err, 'Could not load settings.')); })
            .finally(() => setLoading(false));
    }, []);

    if (!user) return <Navigate to="/login" replace />;

    const canCheckIn = config && (config.status === 'ACTIVE' || config.status === 'PENDING_CONFIRMATION');
    const locked = config && !canCheckIn; // TRIGGERED / SUSPENDED: không sửa, không check-in

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
            loadConfirmers();
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

    const handleAddConfirmer = async (e) => {
        e.preventDefault();
        setError(''); setSuccess('');
        if (!confirmerForm.name.trim() || !confirmerForm.email.trim()) {
            setError('Please enter both name and email for your trusted confirmer.');
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
            setSuccess('Trusted social confirmer added.');
        } catch (err) {
            setError(getErrorMessage(err, 'Could not add trusted confirmer.'));
        } finally {
            setAddingConfirmer(false);
        }
    };

    const handleRemoveConfirmer = async (id) => {
        setError(''); setSuccess('');
        setRemovingId(id);
        try {
            await ownerApi.removeDmsConfirmer(id);
            setConfirmers((prev) => prev.filter((c) => c.id !== id));
            setSuccess('Trusted confirmer removed.');
        } catch (err) {
            setError(getErrorMessage(err, 'Could not remove trusted confirmer.'));
        } finally {
            setRemovingId('');
        }
    };

    const s = config ? STATUS[config.status] || STATUS.ACTIVE : null;
    const needVault = /Vault/i.test(error) && !config;

    return (
        <OwnerLayout title="Dead Man's Switch" subtitle="If you stop checking in, your vaults are handed over to your Executor.">
            <Alert type="success">{success}</Alert>
            <Alert>{error}</Alert>
            {needVault && (
                <p className="-mt-2 mb-4 text-sm"><Link to="/owner/vaults/new" className="text-secondary font-semibold">Create a vault first →</Link></p>
            )}

            {loading ? <p className="text-on-surface-variant">Loading...</p> : (
                <div className="grid lg:grid-cols-[1fr_360px] gap-6 items-start">
                    <div className="space-y-6">
                        {/* Trạng thái + check-in */}
                        <Card>
                            {!config ? (
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
                                        {canCheckIn && (
                                            <button onClick={checkIn} disabled={checking} className={btnPrimary}>
                                                <Icon name="how_to_reg" className="text-[18px]" /> {checking ? 'Checking in...' : "I'm okay — check in"}
                                            </button>
                                        )}
                                    </div>

                                    {config.status === 'PENDING_CONFIRMATION' && (
                                        <p className="mt-4 text-sm text-on-surface-variant">
                                            Your check-in and grace period expired. We have asked your Trusted Social Confirmers to verify your status before {formatDate(config.triggerAt, true)}. Click &quot;I&apos;m okay — check in&quot; to cancel immediately.
                                        </p>
                                    )}
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

                        {/* X2: Trusted Social Confirmers */}
                        {config && (
                            <Card
                                title="Trusted Social Confirmers (Anti-False-Trigger)"
                                subtitle="Up to 2 trusted relatives/friends who will receive an emergency confirmation email (72h window) before your Executor is activated."
                                icon="verified_user"
                            >
                                {confirmers.length === 0 ? (
                                    <p className="text-sm text-on-surface-variant">
                                        No trusted confirmers configured yet. Add up to 2 contacts below so accidental missed check-ins (e.g., travel or hospital stay) can be postponed by 7 days.
                                    </p>
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
                                                        Remove
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
                                            placeholder="Full name"
                                            value={confirmerForm.name}
                                            onChange={(e) => setConfirmerForm((f) => ({ ...f, name: e.target.value }))}
                                            className={inputClass}
                                        />
                                        <input
                                            type="email"
                                            placeholder="Email address"
                                            value={confirmerForm.email}
                                            onChange={(e) => setConfirmerForm((f) => ({ ...f, email: e.target.value }))}
                                            className={inputClass}
                                        />
                                        <div className="flex gap-2">
                                            <input
                                                type="text"
                                                placeholder="Relation (e.g. Spouse)"
                                                value={confirmerForm.relationship}
                                                onChange={(e) => setConfirmerForm((f) => ({ ...f, relationship: e.target.value }))}
                                                className={inputClass}
                                            />
                                            <button type="submit" disabled={addingConfirmer} className={`${btnPrimary} shrink-0`}>
                                                <Icon name="person_add" className="text-[18px]" />
                                                Add
                                            </button>
                                        </div>
                                    </form>
                                )}
                            </Card>
                        )}
                    </div>

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
                                {saving ? 'Saving...' : config ? 'Save changes' : 'Turn on'}
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
