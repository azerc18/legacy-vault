import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import InfoTip from '../Common/InfoTip.jsx';
import { Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi, needsReplaceConfirm } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, ConfirmDialog, formatDate, inputClass, btnPrimary, btnOutline, LoadingSkeleton } from './ownerUi.jsx';

// Tiêu đề/mô tả ở executors.positions.<type> (namespace dms)
const POSITIONS = [
    { type: 'PRIMARY' },
    { type: 'BACKUP' },
];

// Nhãn ở executors.status.<STATUS>
const STATUS = {
    INACTIVE: { cls: 'bg-secondary-fixed text-on-secondary-fixed' },
    ACTIVE: { cls: 'bg-success-container text-on-success-container' },
    // Không phải ExecutorStatus: người được chỉ định chưa trả lời lời mời (responseStatus = PENDING)
    PENDING: { cls: 'bg-warning-container text-on-warning-container' },
};

export default function OwnerExecutors() {
    const { t } = useTranslation('dms');
    const user = getUser();
    const [list, setList] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const load = () =>
        ownerApi.listExecutors()
            .then((l) => setList(l ?? []))
            .catch((err) => setError(getErrorMessage(err, t('executors.errors.load'))))
            .finally(() => setLoading(false));

    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps -- chỉ tải lần đầu

    if (!user) return <Navigate to="/login" replace />;

    // BE trả cả vị trí đã thu hồi -> mỗi loại lấy bản ghi chưa REVOKED (nếu có)
    const current = (type) => list.find((a) => a.executorType === type && a.status !== 'REVOKED') || null;

    return (
        <OwnerLayout title={t('executors.title')} subtitle={t('executors.subtitle')}>
            <Alert>{error}</Alert>
            {loading ? <LoadingSkeleton label={t('executors.loading')} /> : (
                <div data-tour="executors-overview" className="grid md:grid-cols-2 gap-6">
                    {POSITIONS.map((p) => (
                        <ExecutorCard key={p.type} position={p} assignment={current(p.type)} onChanged={load} />
                    ))}
                </div>
            )}
        </OwnerLayout>
    );
}

function ExecutorCard({ position, assignment, onChanged }) {
    const { t } = useTranslation('dms');
    const [editing, setEditing] = useState(false);
    const [email, setEmail] = useState('');
    const [confirmMsg, setConfirmMsg] = useState(null);
    const [revokeOpen, setRevokeOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const assign = async (confirmReplace = false) => {
        setError(''); setBusy(true);
        try {
            await ownerApi.assignExecutor(position.type, email.trim(), confirmReplace);
            setConfirmMsg(null); setEditing(false); setEmail('');
            onChanged();
        } catch (err) {
            if (needsReplaceConfirm(err)) setConfirmMsg(getErrorMessage(err));
            else setError(getErrorMessage(err, t('executors.errors.assign')));
        } finally {
            setBusy(false);
        }
    };

    const revoke = async () => {
        setError(''); setBusy(true);
        try {
            await ownerApi.revokeExecutor(position.type);
            setRevokeOpen(false);
            onChanged();
        } catch (err) {
            setError(getErrorMessage(err, t('executors.errors.revoke')));
            setRevokeOpen(false);
        } finally {
            setBusy(false);
        }
    };

    const invitationPending = assignment?.responseStatus === 'PENDING';
    const stKey = assignment && (invitationPending ? 'PENDING'
        : STATUS[assignment.status] ? assignment.status : 'INACTIVE');
    const st = assignment && STATUS[stKey];

    return (
        <div data-tour={position.type === 'PRIMARY' ? 'executors-primary' : 'executors-backup'}>
        <Card>
            <div className="flex items-start justify-between gap-3">
                <div>
                    <h2 className="font-title-lg text-title-lg">{t(`executors.positions.${position.type}.title`)} <InfoTip term="executor" /></h2>
                    <p className="text-sm text-on-surface-variant mt-1">{t(`executors.positions.${position.type}.text`)}</p>
                </div>
                {st && <span className={`lv-glass-badge shrink-0 text-xs font-semibold px-3 py-1 rounded-full ${st.cls}`}>{t(`executors.status.${stKey}`)}</span>}
            </div>

            <div className="mt-5"><Alert>{error}</Alert></div>

            {assignment && !editing && (
                <div className="lv-glass-inline flex items-center gap-3 p-4 rounded-xl">
                    <div className="w-10 h-10 rounded-full bg-primary-fixed text-primary flex items-center justify-center shadow-sm"><Icon name="person" /></div>
                    <div className="min-w-0">
                        <p className="font-semibold truncate">{assignment.fullName || assignment.email}</p>
                        <p className="text-sm text-on-surface-variant truncate">{assignment.email}</p>
                        <p className="text-xs text-outline">
                            {t('executors.since', { date: formatDate(assignment.assignedAt) })}
                            {invitationPending && ` · ${t('executors.awaitingResponse')}`}
                            {assignment.activatedAt && ` · ${t('executors.activated', { date: formatDate(assignment.activatedAt, true) })}`}
                            {assignment.status === 'ACTIVE' && (assignment.acknowledgedAt
                                ? ` · ${t('executors.acknowledged', { date: formatDate(assignment.acknowledgedAt, true) })}`
                                : ` · ${t('executors.awaitingAck')}`)}
                        </p>
                    </div>
                </div>
            )}

            {!assignment && !editing && <p className="text-sm text-on-surface-variant">{t('executors.noneAssigned')}</p>}

            {editing ? (
                <form onSubmit={(e) => { e.preventDefault(); if (email.trim()) assign(false); }} className="mt-4 space-y-3">
                    <input type="email" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} placeholder="executor@example.com" className={inputClass} />
                    <div className="flex gap-3">
                        <button type="button" onClick={() => { setEditing(false); setError(''); }} className={btnOutline}>{t('actions.cancel', { ns: 'common' })}</button>
                        <button type="submit" disabled={busy} className={btnPrimary}>{busy ? t('executors.saving') : t('actions.save', { ns: 'common' })}</button>
                    </div>
                </form>
            ) : (
                <div className="mt-4 flex gap-3">
                    <button onClick={() => setEditing(true)} className={btnPrimary}>
                        <Icon name={assignment ? 'swap_horiz' : 'person_add'} className="text-[18px]" /> {assignment ? t('executors.replace') : t('executors.assign')}
                    </button>
                    {assignment && (
                        <button onClick={() => setRevokeOpen(true)} className={btnOutline}>{t('executors.revoke')}</button>
                    )}
                </div>
            )}

            <ConfirmDialog
                open={!!confirmMsg}
                title={t(`executors.replaceTitle.${position.type}`)}
                message={confirmMsg}
                confirmLabel={t('executors.replace')}
                busy={busy}
                onConfirm={() => assign(true)}
                onCancel={() => setConfirmMsg(null)}
            />
            <ConfirmDialog
                open={revokeOpen}
                danger
                title={t(`executors.revokeTitle.${position.type}`)}
                message={t('executors.revokeMessage', { email: assignment?.email })}
                confirmLabel={t('executors.revoke')}
                busy={busy}
                onConfirm={revoke}
                onCancel={() => setRevokeOpen(false)}
            />
        </Card>
        </div>
    );
}
