// A3: ngăn kéo chi tiết người dùng (GET /api/admin/users/{id}). Mở từ trang Người dùng, Kho, Thanh toán, Cảnh báo.
// Thao tác nhanh dùng lại API sẵn có: khoá / mở khoá tài khoản, tạm dừng / tiếp tục DMS.
// Demo: nút "Giả quá hạn" chỉ hiện khi server bật APP_DEMO_TOOLS_ENABLED, DMS của Owner đang ACTIVE và Owner còn két.
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { getUser } from '../auth/auth.js';
import { Icon, Alert, formatDate } from '../Owner/ownerUi.jsx';
import { ReasonDialog, Stamp, toneOf } from './adminUi.jsx';
import { GrantVipDialog } from './BillingDialogs.jsx';
import VerificationBadge from '../Common/VerificationBadge.jsx';
import { tEnum } from '../i18n/format.js';

const HIDDEN_ROLES = ['BENEFICIARY'];

function Field({ label, children }) {
    return (
        <div>
            <dt className="lv-stat-label">{label}</dt>
            <dd className="mt-0.5 text-sm font-semibold break-words">{children ?? '—'}</dd>
        </div>
    );
}

function Section({ title, count, children }) {
    return (
        <section className="mt-6">
            <h3 className="lv-dossier-title text-base flex items-center gap-2 pb-2 border-b border-dashed border-outline-variant">
                {title}{typeof count === 'number' && <span className="lv-dossier-tab">{count}</span>}
            </h3>
            <div className="mt-2">{children}</div>
        </section>
    );
}

/**
 * @param userId   id người dùng cần xem; null = đóng
 * @param onClose  đóng ngăn kéo
 * @param onChanged(user) gọi sau khi khoá/mở khoá/DMS để trang cha cập nhật dòng tương ứng (tuỳ chọn)
 */
// Khoá tạm do sai mật khẩu / mã nhiều lần: trạng thái vẫn ACTIVE nhưng không đăng nhập được tới lockedUntil
const isTemporarilyLocked = (u) => !!u?.lockedUntil && new Date(u.lockedUntil) > new Date();

export default function UserDrawer({ userId, onClose, onChanged }) {
    const { t } = useTranslation('admin');
    const me = getUser();
    const [u, setU] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [dialog, setDialog] = useState(null);   // 'lock' | 'unlock' | 'suspend' | 'resume' | 'simulateOverdue'
    const [busy, setBusy] = useState(false);
    const [granting, setGranting] = useState(false);    // Việc 3: hộp thoại tặng VIP
    const [demoTools, setDemoTools] = useState(false);  // Demo: cờ APP_DEMO_TOOLS_ENABLED của server
    const refreshTimer = useRef(null);

    // Hỏi 1 lần mỗi lần mở; lỗi (server cũ chưa có API) coi như tắt
    useEffect(() => {
        if (!userId) return undefined;
        let alive = true;
        adminApi.demoTools()
            .then((r) => { if (alive) setDemoTools(!!r?.dmsSimulateOverdue); })
            .catch(() => { if (alive) setDemoTools(false); });
        return () => { alive = false; clearTimeout(refreshTimer.current); };
    }, [userId]);

    const load = useCallback(() => {
        if (!userId) return;
        setError('');
        adminApi.getUserDetail(userId)
            .then(setU)
            .catch((e) => setError(getErrorMessage(e, t('drawer.loadError'))));
    }, [userId]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { setU(null); setSuccess(''); load(); }, [load]);

    // Esc để đóng
    useEffect(() => {
        if (!userId) return undefined;
        const onKey = (e) => { if (e.key === 'Escape' && !dialog) onClose(); };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [userId, dialog, onClose]);

    if (!userId) return null;

    const isMe = u && u.id === me?.id;
    const isOwner = u?.roles?.includes('OWNER');

    const confirm = async (reason) => {
        const kind = dialog;
        setBusy(true); setError(''); setSuccess('');
        try {
            let res;
            if (kind === 'lock') res = await adminApi.updateStatus(u.id, 'LOCKED', reason);
            if (kind === 'unlock') res = await adminApi.updateStatus(u.id, 'ACTIVE');
            if (kind === 'suspend') await adminApi.suspendDms(u.id, reason);
            if (kind === 'resume') await adminApi.resumeDms(u.id);
            if (kind === 'simulateOverdue') {
                await adminApi.simulateOverdue(u.id);
                // Job DMS quét mỗi ~20 giây: hẹn tải lại để thấy PENDING_CONFIRMATION / TRIGGERED
                clearTimeout(refreshTimer.current);
                refreshTimer.current = setTimeout(load, 25000);
            }
            setSuccess(t(`users.success.${kind}`, { email: u.email }));
            if (res && onChanged) onChanged(res);
            load();
        } catch (e) {
            setError(getErrorMessage(e, t('actionFailed')));
        } finally {
            setBusy(false);
            setDialog(null);
        }
    };

    const confirmGrant = async ({ days, reason }) => {
        setBusy(true); setError(''); setSuccess('');
        try {
            const res = await adminApi.grantVip(u.id, days, reason);
            setSuccess(t('users.grantVip.success', { email: u.email, count: days, date: formatDate(res?.expiresAt) }));
            load();
        } catch (e) {
            setError(getErrorMessage(e, t('actionFailed')));
        } finally {
            setBusy(false);
            setGranting(false);
        }
    };

    const btn = 'h-9 px-3 rounded-xl border border-outline-variant text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-surface-container-low disabled:opacity-60';

    return (
        <>
            <div className="lv-modal-backdrop lv-drawer-backdrop" onClick={onClose} />
            <aside className="lv-drawer lv-admin" role="dialog" aria-modal="true" aria-label={t('drawer.title')}>
                <div className="sticky top-0 z-10 bg-surface-container-lowest/95 backdrop-blur px-6 pt-6 pb-4 border-b border-outline-variant">
                    <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                            <p className="lv-adm-kicker">{t('drawer.kicker')}</p>
                            <h2 className="lv-dossier-title text-2xl mt-1 truncate">{u?.fullName || u?.email || t('loading')}</h2>
                            {u?.fullName && <p className="text-sm text-on-surface-variant truncate">{u.email}</p>}
                        </div>
                        <button onClick={onClose} aria-label={t('drawer.close')} className="shrink-0 w-9 h-9 rounded-full hover:bg-surface-container-low inline-flex items-center justify-center">
                            <Icon name="close" />
                        </button>
                    </div>
                    {u && (
                        <div className="mt-3 flex flex-wrap items-center gap-2">
                            <Stamp tone={toneOf(u.status)}>{t(`users.status.${u.status}`, { defaultValue: u.status })}</Stamp>
                            <Stamp tone={u.planCode === 'FREE' ? 'muted' : 'info'} icon="workspace_premium" flat>{u.planCode}</Stamp>
                            {u.dmsStatus && <Stamp tone={u.dmsStatus === 'ACTIVE' ? 'info' : toneOf(u.dmsStatus)} flat>{t(`users.dms.${u.dmsStatus}`, { defaultValue: u.dmsStatus })}</Stamp>}
                        </div>
                    )}
                </div>

                <div className="px-6 pb-10">
                    <div className="mt-4"><Alert>{error}</Alert><Alert type="success">{success}</Alert></div>

                    {u && (
                        <>
                            {/* Thao tác nhanh */}
                            <div className="mt-4 flex flex-wrap gap-2">
                                {!isMe && u.status === 'ACTIVE' && <button className={`${btn} text-error`} onClick={() => setDialog('lock')}><Icon name="lock" className="text-[16px]" />{t('users.actions.lock')}</button>}
                                {!isMe && (u.status === 'LOCKED' || isTemporarilyLocked(u)) && <button className={btn} onClick={() => setDialog('unlock')}><Icon name="lock_open" className="text-[16px]" />{t('users.actions.unlock')}</button>}
                                {isOwner && u.dmsStatus === 'ACTIVE' && <button className={btn} onClick={() => setDialog('suspend')}><Icon name="pause_circle" className="text-[16px]" />{t('users.actions.suspend')}</button>}
                                {isOwner && u.dmsStatus === 'SUSPENDED' && <button className={btn} onClick={() => setDialog('resume')}><Icon name="play_circle" className="text-[16px]" />{t('users.actions.resume')}</button>}
                                {demoTools && isOwner && u.dmsStatus === 'ACTIVE' && u.vaults?.length > 0 && (
                                    <button className={`${btn} border-dashed text-error`} title={t('users.actions.simulateOverdueHint')}
                                            onClick={() => setDialog('simulateOverdue')}>
                                        <Icon name="science" className="text-[16px]" />{t('users.actions.simulateOverdue')}
                                    </button>
                                )}
                                {isOwner && <button className={btn} onClick={() => setGranting(true)}><Icon name="workspace_premium" className="text-[16px]" />{t('users.actions.grantVip')}</button>}
                                <Link to={`/admin/audit-logs?actorEmail=${encodeURIComponent(u.email)}`} className={btn}><Icon name="history" className="text-[16px]" />{t('drawer.allLogs')}</Link>
                                <Link to={`/admin/payments?email=${encodeURIComponent(u.email)}`} className={btn}><Icon name="receipt_long" className="text-[16px]" />{t('drawer.payments')}</Link>
                            </div>

                            <Section title={t('drawer.profile')}>
                                <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
                                    <Field label={t('drawer.fields.roles')}>{(u.roles || []).filter((r) => !HIDDEN_ROLES.includes(r)).map((r) => t(`common:roles.${r}`, { defaultValue: r })).join(', ') || '—'}</Field>
                                    <Field label={t('drawer.fields.phone')}>{u.phoneNumber}</Field>
                                    <Field label={t('drawer.fields.joined')}>{formatDate(u.createdAt)}</Field>
                                    <Field label={t('drawer.fields.lastLogin')}>{formatDate(u.lastLoginAt, true)}</Field>
                                    <Field label={t('drawer.fields.mfa')}>{u.mfaEnabled ? t('drawer.on') : t('drawer.off')}</Field>
                                    <Field label={t('drawer.fields.failedLogins')}>{u.failedLoginAttempts}{isTemporarilyLocked(u) && <span className="block text-xs font-normal text-error">{t('users.tempLocked', { time: formatDate(u.lockedUntil, true) })}</span>}</Field>
                                    <Field label={t('drawer.fields.plan')}>{u.planName || u.planCode}{u.planExpiresAt && <span className="block text-xs font-normal text-outline">{t('drawer.until', { date: formatDate(u.planExpiresAt) })}</span>}</Field>
                                    {u.dmsNextCheckinDueAt && <Field label={t('drawer.fields.nextCheckin')}>{formatDate(u.dmsNextCheckinDueAt, true)}</Field>}
                                </dl>
                            </Section>

                            <Section title={t('drawer.vaults')} count={u.vaults.length}>
                                {u.vaults.length === 0 && <p className="text-sm text-on-surface-variant">{t('drawer.noVaults')}</p>}
                                <ul className="lv-todo">
                                    {u.vaults.map((v) => (
                                        <li key={v.id} className="py-2 flex items-center justify-between gap-3">
                                            <div className="min-w-0">
                                                <p className="text-sm font-semibold truncate">{v.name}</p>
                                                <p className="text-[11px] text-outline">
                                                    {formatDate(v.createdAt)}{v.claimDeadlineAt && v.status === 'UNLOCKED' && <> · {t('overview.todo.deadlineAt', { date: formatDate(v.claimDeadlineAt) })}</>}
                                                </p>
                                            </div>
                                            <Stamp tone={v.status === 'ACTIVE' ? 'info' : v.status === 'UNLOCKED' ? 'warn' : toneOf(v.status)} flat>{t(`vaults.status.${v.status}`, { defaultValue: v.status })}</Stamp>
                                        </li>
                                    ))}
                                </ul>
                            </Section>

                            <Section title={t('drawer.assetVerifications')} count={(u.assetVerifications || []).length}>
                                {(u.assetVerifications || []).length === 0 && <p className="text-sm text-on-surface-variant">{t('drawer.noAssetVerifications')}</p>}
                                <ul className="lv-todo">
                                    {(u.assetVerifications || []).map((a) => (
                                        <li key={a.assetId} className="py-2 flex items-center justify-between gap-3">
                                            <div className="min-w-0">
                                                <p className="text-sm font-semibold truncate">{tEnum('assetType', a.assetType)}</p>
                                                <p className="text-[11px] text-outline truncate">
                                                    {a.vaultName}{a.expiresAt && <> · {t('drawer.until', { date: formatDate(a.expiresAt) })}</>}
                                                </p>
                                            </div>
                                            <VerificationBadge status={a.status} method={a.method} />
                                        </li>
                                    ))}
                                </ul>
                            </Section>

                            <Section title={t('drawer.alerts')} count={u.alerts.length}>
                                {u.alerts.length === 0 && <p className="text-sm text-on-surface-variant">{t('drawer.noAlerts')}</p>}
                                <ul className="lv-todo">
                                    {u.alerts.map((a) => (
                                        <li key={a.id} className="py-2">
                                            <div className="flex items-center gap-2">
                                                <Stamp tone={toneOf(a.severity)}>{t(`alerts.severity.${a.severity}`, { defaultValue: a.severity })}</Stamp>
                                                <span className="text-sm font-semibold">{t(`alerts.rules.${a.rule}`, { defaultValue: a.rule })}</span>
                                            </div>
                                            <p className="text-[11px] text-outline mt-1">{formatDate(a.lastSeenAt, true)} · {t(`alerts.tabs.${a.status === 'OPEN' ? 'open' : 'acknowledged'}`)}</p>
                                        </li>
                                    ))}
                                </ul>
                            </Section>

                            <Section title={t('drawer.logs')} count={u.recentLogs.length}>
                                {u.recentLogs.length === 0 && <p className="text-sm text-on-surface-variant">{t('drawer.noLogs')}</p>}
                                <ul className="lv-todo">
                                    {u.recentLogs.map((l) => (
                                        <li key={l.id} className="py-2 flex items-start justify-between gap-3">
                                            <div className="min-w-0">
                                                <p className="lv-mono text-xs font-semibold">{l.action}</p>
                                                {l.detail && <p className="text-xs text-on-surface-variant truncate">{l.detail}</p>}
                                            </div>
                                            <span className="text-[11px] text-outline whitespace-nowrap">{formatDate(l.createdAt, true)}</span>
                                        </li>
                                    ))}
                                </ul>
                            </Section>
                        </>
                    )}
                </div>
            </aside>

            <GrantVipDialog open={granting && !!u} email={u?.email} busy={busy}
                            onConfirm={confirmGrant} onCancel={() => setGranting(false)} />

            <ReasonDialog
                open={!!dialog}
                title={dialog && u ? t(`users.dialog.${dialog}.title`) : ''}
                message={dialog && u ? t(`users.dialog.${dialog}.message`, { email: u.email }) : ''}
                confirmLabel={dialog ? t(`users.dialog.${dialog}.confirm`) : ''}
                danger={dialog === 'lock' || dialog === 'suspend' || dialog === 'simulateOverdue'}
                askReason={dialog === 'lock' || dialog === 'suspend'}
                busy={busy}
                onConfirm={confirm}
                onCancel={() => setDialog(null)}
            />
        </>
    );
}
