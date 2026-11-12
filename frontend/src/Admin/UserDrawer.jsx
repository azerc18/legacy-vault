// A3: hồ sơ người dùng (GET /api/admin/users/{id}), hộp thoại giữa màn hình. Mở từ trang Người dùng, Kho, Thanh toán, Cảnh báo.
// Thao tác nhanh dùng lại API sẵn có: khoá / mở khoá tài khoản, tạm dừng / tiếp tục DMS, nhắc Owner check-in.
// Bố cục 2 cột (màn hình rộng): trái = thông tin, kho, tài sản; phải = Dead Man's Switch, cảnh báo, nhật ký.
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
export default function UserDrawer({ userId, onClose, onChanged }) {
    const { t } = useTranslation('admin');
    const me = getUser();
    const [u, setU] = useState(null);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [warning, setWarning] = useState('');   // nhắc check-in: chuông đã gửi nhưng email lỗi
    const [dialog, setDialog] = useState(null);   // 'lock' | 'unlock' | 'suspend' | 'resume' | 'simulateOverdue' | 'remindCheckin'
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

    useEffect(() => { setU(null); setSuccess(''); setWarning(''); load(); }, [load]);

    // Esc để đóng
    useEffect(() => {
        if (!userId) return undefined;
        const onKey = (e) => { if (e.key === 'Escape' && !dialog) onClose(); };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [userId, dialog, onClose]);

    // Hết thời gian chờ nhắc check-in thì tải lại hồ sơ để mở lại nút (server trả số giây còn lại, không lệ thuộc múi giờ)
    const waitSeconds = u?.dmsAdminReminderAvailableInSeconds;
    useEffect(() => {
        if (!waitSeconds || waitSeconds <= 0) return undefined;
        const timer = setTimeout(load, Math.min(waitSeconds * 1000 + 1000, 2 ** 31 - 1));
        return () => clearTimeout(timer);
    }, [waitSeconds, load]);

    if (!userId) return null;

    const isMe = u && u.id === me?.id;
    const isOwner = u?.roles?.includes('OWNER');

    const confirm = async (reason) => {
        const kind = dialog;
        setBusy(true); setError(''); setSuccess(''); setWarning('');
        try {
            let res;
            if (kind === 'remindCheckin') {
                const r = await adminApi.remindCheckin(u.id);
                if (r?.emailSent) setSuccess(t('users.success.remindCheckin', { email: u.email }));
                else setWarning(t('users.success.remindCheckinNoEmail', { email: u.email }));
                load();
                return;
            }
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
        setBusy(true); setError(''); setSuccess(''); setWarning('');
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
    const dms = u?.dmsStatus;
    // Nhắc check-in: chỉ khi Owner còn check-in được (đang chạy / chờ xác nhận) và còn két
    const canRemind = isOwner && (dms === 'ACTIVE' || dms === 'PENDING_CONFIRMATION') && u.vaults?.length > 0;
    const remindWaitUntil = u?.dmsAdminReminderAvailableAt;   // server tính: null = được nhắc ngay
    const accountActions = u && (
        <>
            {!isMe && u.status === 'ACTIVE' && <button className={`${btn} text-error`} onClick={() => setDialog('lock')}><Icon name="lock" className="text-[16px]" />{t('users.actions.lock')}</button>}
            {!isMe && u.status === 'LOCKED' && <button className={btn} onClick={() => setDialog('unlock')}><Icon name="lock_open" className="text-[16px]" />{t('users.actions.unlock')}</button>}
            {isOwner && <button className={btn} onClick={() => setGranting(true)}><Icon name="workspace_premium" className="text-[16px]" />{t('users.actions.grantVip')}</button>}
            <Link to={`/admin/audit-logs?actorEmail=${encodeURIComponent(u.email)}`} className={btn}><Icon name="history" className="text-[16px]" />{t('drawer.allLogs')}</Link>
            <Link to={`/admin/payments?email=${encodeURIComponent(u.email)}`} className={btn}><Icon name="receipt_long" className="text-[16px]" />{t('drawer.payments')}</Link>
        </>
    );

    return (
        <>
            <div className="lv-modal-backdrop lv-drawer-backdrop" onClick={onClose} />
            <section className="lv-drawer lv-admin lv-dossier-modal" role="dialog" aria-modal="true" aria-label={t('drawer.title')}>
                <header className="shrink-0 px-5 sm:px-8 pt-6 pb-4 border-b border-outline-variant">
                    <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0">
                            <p className="lv-adm-kicker">{t('drawer.kicker')}</p>
                            <h2 className="lv-dossier-title text-2xl mt-1 truncate">{u?.fullName || u?.email || t('loading')}</h2>
                            {u?.fullName && <p className="text-sm text-on-surface-variant truncate">{u.email}</p>}
                            {u && (
                                <div className="mt-3 flex flex-wrap items-center gap-2">
                                    <Stamp tone={toneOf(u.status)}>{t(`users.status.${u.status}`, { defaultValue: u.status })}</Stamp>
                                    <Stamp tone={u.planCode === 'FREE' ? 'muted' : 'info'} icon="workspace_premium" flat>{u.planCode}</Stamp>
                                    {u.dmsStatus && <Stamp tone={u.dmsStatus === 'ACTIVE' ? 'info' : toneOf(u.dmsStatus)} flat>{t(`users.dms.${u.dmsStatus}`, { defaultValue: u.dmsStatus })}</Stamp>}
                                </div>
                            )}
                        </div>
                        <div className="shrink-0 flex items-start gap-3">
                            {/* Thao tác với tài khoản; thao tác DMS nằm trong thẻ Dead Man's Switch */}
                            {u && (
                                <div className="hidden md:flex flex-wrap justify-end gap-2 max-w-[520px]">
                                    {accountActions}
                                </div>
                            )}
                            <button onClick={onClose} aria-label={t('drawer.close')} className="w-9 h-9 rounded-full hover:bg-surface-container-low inline-flex items-center justify-center">
                                <Icon name="close" />
                            </button>
                        </div>
                    </div>
                    {u && <div className="md:hidden mt-3 flex flex-wrap gap-2">{accountActions}</div>}
                </header>

                <div className="lv-dossier-scroll px-5 sm:px-8 pb-8">
                    <div className="mt-4"><Alert>{error}</Alert><Alert type="success">{success}</Alert><Alert type="warning">{warning}</Alert></div>

                    {u && (
                        <div className="grid gap-x-8 gap-y-2 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
                            {/* Cột trái: hồ sơ và tài sản */}
                            <div className="min-w-0">
                                <Section title={t('drawer.profile')}>
                                    <dl className="grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-3">
                                        <Field label={t('drawer.fields.roles')}>{(u.roles || []).filter((r) => !HIDDEN_ROLES.includes(r)).map((r) => t(`common:roles.${r}`, { defaultValue: r })).join(', ') || '—'}</Field>
                                        <Field label={t('drawer.fields.phone')}>{u.phoneNumber}</Field>
                                        <Field label={t('drawer.fields.plan')}>{u.planName || u.planCode}{u.planExpiresAt && <span className="block text-xs font-normal text-outline">{t('drawer.until', { date: formatDate(u.planExpiresAt) })}</span>}</Field>
                                        <Field label={t('drawer.fields.joined')}>{formatDate(u.createdAt)}</Field>
                                        <Field label={t('drawer.fields.lastLogin')}>{formatDate(u.lastLoginAt, true)}</Field>
                                        <Field label={t('drawer.fields.mfa')}>{u.mfaEnabled ? t('drawer.on') : t('drawer.off')}</Field>
                                        <Field label={t('drawer.fields.failedLogins')}>{u.failedLoginAttempts}</Field>
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
                            </div>

                            {/* Cột phải: DMS và hoạt động */}
                            <div className="min-w-0">
                                {isOwner && (
                                    <div className="mt-6 lv-dossier-card">
                                        <h3 className="lv-dossier-title text-base flex items-center gap-2 pb-2 border-b border-dashed border-outline-variant">
                                            <Icon name="timer" className="text-[18px]" />{t('drawer.dmsTitle')}
                                        </h3>
                                        {!dms && <p className="mt-3 text-sm text-on-surface-variant">{t('drawer.noDms')}</p>}
                                        {dms && (
                                            <>
                                                <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3">
                                                    <Field label={t('drawer.fields.dmsStatus')}>{t(`users.dms.${dms}`, { defaultValue: dms })}</Field>
                                                    {u.dmsNextCheckinDueAt && dms !== 'TRIGGERED' && <Field label={t('drawer.fields.dmsDeadline')}>{formatDate(u.dmsNextCheckinDueAt, true)}</Field>}
                                                    {u.dmsLastAdminReminderAt && <Field label={t('drawer.fields.lastAdminReminder')}>{formatDate(u.dmsLastAdminReminderAt, true)}</Field>}
                                                </dl>
                                                <div className="mt-4 flex flex-wrap gap-2">
                                                    {canRemind && (
                                                        <button className={`${btn} bg-primary text-on-primary border-primary hover:bg-primary/90`}
                                                                disabled={!!remindWaitUntil || busy}
                                                                title={remindWaitUntil ? t('drawer.remindWait', { date: formatDate(remindWaitUntil, true) }) : t('drawer.remindHint')}
                                                                onClick={() => setDialog('remindCheckin')}>
                                                            <Icon name="notifications_active" className="text-[16px]" />{t('users.actions.remindCheckin')}
                                                        </button>
                                                    )}
                                                    {dms === 'ACTIVE' && <button className={btn} onClick={() => setDialog('suspend')}><Icon name="pause_circle" className="text-[16px]" />{t('users.actions.suspend')}</button>}
                                                    {dms === 'SUSPENDED' && <button className={btn} onClick={() => setDialog('resume')}><Icon name="play_circle" className="text-[16px]" />{t('users.actions.resume')}</button>}
                                                    {demoTools && dms === 'ACTIVE' && u.vaults?.length > 0 && (
                                                        <button className={`${btn} border-dashed text-error`} title={t('users.actions.simulateOverdueHint')}
                                                                onClick={() => setDialog('simulateOverdue')}>
                                                            <Icon name="science" className="text-[16px]" />{t('users.actions.simulateOverdue')}
                                                        </button>
                                                    )}
                                                </div>
                                                {canRemind && (
                                                    <p className="mt-2 text-xs text-on-surface-variant">
                                                        {remindWaitUntil ? t('drawer.remindWait', { date: formatDate(remindWaitUntil, true) }) : t('drawer.remindHint')}
                                                    </p>
                                                )}
                                            </>
                                        )}
                                    </div>
                                )}

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
                            </div>
                        </div>
                    )}
                </div>
            </section>

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
