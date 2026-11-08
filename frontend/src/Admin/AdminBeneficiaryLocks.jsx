// #10: người thụ hưởng bị khóa xác minh danh tính (sai 5 lần / 24h) + Admin mở khóa sớm
// GET /api/admin/beneficiary-locks, POST /api/admin/beneficiary-locks/unlock
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon, Alert, formatDate } from '../Owner/ownerUi.jsx';
import EmptyState from '../Common/EmptyState.jsx';
import { AdminLayout, Dossier, ReasonDialog } from './adminUi.jsx';

export default function AdminBeneficiaryLocks() {
    const { t } = useTranslation('admin');
    const [rows, setRows] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [target, setTarget] = useState(null);   // dòng đang chờ xác nhận mở khóa
    const [busy, setBusy] = useState(false);

    const load = useCallback(() => {
        setError('');
        adminApi.listBeneficiaryLocks()
            .then(setRows)
            .catch((e) => setError(getErrorMessage(e, t('beneficiaryLocks.loadError'))));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    const unlock = async (reason) => {
        if (!target) return;
        setBusy(true); setError(''); setSuccess('');
        try {
            await adminApi.unlockBeneficiary({ vaultId: target.vaultId, beneficiaryId: target.beneficiaryId, reason });
            setRows((r) => r.filter((x) => !(x.vaultId === target.vaultId && x.beneficiaryId === target.beneficiaryId)));
            setSuccess(t('beneficiaryLocks.unlocked', { email: target.beneficiaryEmail }));
        } catch (e) {
            setError(getErrorMessage(e, t('beneficiaryLocks.unlockError')));
            load();
        } finally {
            setBusy(false);
            setTarget(null);
        }
    };

    return (
        <AdminLayout title={t('beneficiaryLocks.title')} subtitle={t('beneficiaryLocks.subtitle')}>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            {!rows && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
            {rows && rows.length === 0 && (
                <EmptyState bordered icon="lock_open" title={t('beneficiaryLocks.emptyTitle')} text={t('beneficiaryLocks.emptyText')} />
            )}

            {rows && rows.length > 0 && (
                <Dossier title={t('beneficiaryLocks.ledger')} tab={t('beneficiaryLocks.total', { count: rows.length })} bodyClass="overflow-x-auto">
                    <table className="lv-ledger is-stack">
                        <thead>
                        <tr>
                            <th>{t('beneficiaryLocks.cols.beneficiary')}</th>
                            <th>{t('beneficiaryLocks.cols.vault')}</th>
                            <th>{t('beneficiaryLocks.cols.lockedAt')}</th>
                            <th>{t('beneficiaryLocks.cols.lockedUntil')}</th>
                            <th />
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((r) => (
                            <tr key={`${r.vaultId}:${r.beneficiaryId}`} className="is-row">
                                <td data-label={t('beneficiaryLocks.cols.beneficiary')}>
                                    <p className="font-semibold">{r.beneficiaryName || r.beneficiaryEmail}</p>
                                    {r.beneficiaryName && <p className="text-[11px] text-outline">{r.beneficiaryEmail}</p>}
                                </td>
                                <td data-label={t('beneficiaryLocks.cols.vault')}>{r.vaultName}</td>
                                <td data-label={t('beneficiaryLocks.cols.lockedAt')} className="whitespace-nowrap">{formatDate(r.lockedAt, true)}</td>
                                <td data-label={t('beneficiaryLocks.cols.lockedUntil')} className="whitespace-nowrap">{formatDate(r.lockedUntil, true)}</td>
                                <td className="text-right">
                                    <button onClick={() => setTarget(r)} disabled={busy}
                                            className="h-9 px-3 rounded-xl border border-outline-variant text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-surface-container-low disabled:opacity-60">
                                        <Icon name="lock_open" className="text-[16px]" /> {t('beneficiaryLocks.unlock')}
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </Dossier>
            )}

            <ReasonDialog
                open={!!target}
                title={t('beneficiaryLocks.confirmTitle')}
                message={target ? t('beneficiaryLocks.confirmText', { email: target.beneficiaryEmail, vault: target.vaultName }) : ''}
                confirmLabel={t('beneficiaryLocks.unlock')}
                askReason
                busy={busy}
                onConfirm={unlock}
                onCancel={() => setTarget(null)}
            />
        </AdminLayout>
    );
}
