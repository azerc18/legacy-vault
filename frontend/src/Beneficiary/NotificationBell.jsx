// Chuông thông báo trong app, dùng chung cho mọi vai trò (API /api/notifications).
// Câu thông báo được dịch theo type + params (i18n common:notifications.types); thông báo cũ không có params
// thì hiện content (câu tiếng Anh lưu sẵn ở BE).
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { notificationApi } from '../api/notificationApi.js';
import { executorApi } from '../api/executorApi.js';
import { getUser } from '../auth/auth.js';

const POLL_MS = 60000;
const PAGE_SIZE = 20;

// icon + nhóm màu: info (xanh), success, warning, danger
const TYPE_META = {
    VAULT_UNLOCKED: ['lock_open', 'success'],
    EXECUTOR_ACTIVATED: ['badge', 'warning'],
    VERIFIER_REPLY: ['forum', 'info'],
    VERIFICATION_DECIDED: ['gavel', 'info'],
    VERIFICATION_REOPENED: ['restart_alt', 'warning'],
    ASSET_REASSIGNED: ['swap_horiz', 'info'],
    LEGACY_MESSAGE_DELIVERED: ['mark_email_unread', 'info'],
    EXECUTOR_TASK_DISPUTED: ['report', 'danger'],
    EXECUTOR_TASK_COMPLETED: ['task_alt', 'success'],
    ASSET_REVERIFY_NEEDED: ['update', 'warning'],
    ASSET_VERIFIED: ['verified', 'success'],
    BENEFICIARY_ASSIGNED: ['volunteer_activism', 'success'],
    BENEFICIARY_REMOVED: ['person_remove', 'warning'],
    EXECUTOR_ASSIGNED: ['assignment_ind', 'success'],
    EXECUTOR_REVOKED: ['person_off', 'warning'],
    DMS_CHECKIN_REMINDER: ['alarm', 'warning'],
    DMS_CONFIRMATION_PENDING: ['crisis_alert', 'danger'],
    DMS_CONFIRMATION_REQUESTED: ['contact_support', 'danger'],
    DMS_OWNER_CONFIRMED_SAFE: ['health_and_safety', 'success'],
    DMS_PAUSED: ['pause_circle', 'warning'],
    DMS_RESUMED: ['play_circle', 'info'],
    PAYMENT_SUCCEEDED: ['workspace_premium', 'success'],
    SUBSCRIPTION_EXPIRING: ['hourglass_bottom', 'warning'],
    SUBSCRIPTION_EXPIRED: ['hourglass_disabled', 'danger'],
    CLAIM_DEADLINE_REMINDER: ['event_upcoming', 'warning'],
    VAULT_ARCHIVED: ['inventory_2', 'warning'],
};
const TONE = {
    info: 'bg-secondary/10 text-secondary',
    success: 'bg-emerald-500/10 text-emerald-600',
    warning: 'bg-amber-500/15 text-amber-600',
    danger: 'bg-error/10 text-error',
};

const hasRole = (role) => {
    const u = getUser();
    const roles = Array.isArray(u?.roles) && u.roles.length ? u.roles : [u?.role];
    return roles.includes(role);
};
const ifRole = (role, path) => (hasRole(role) ? path : null);

// Trang hồ sơ của Executor điều hướng theo assignmentId nên phải tra từ hồ sơ xác minh / task
const executorCaseOfRequest = async (requestId) => {
    const assignmentId = await executorApi.findAssignmentOfRequest(requestId).catch(() => null);
    return assignmentId ? `/executor/cases/${assignmentId}` : '/executor/home';
};
const executorCaseOfTask = async (taskId) => {
    const active = (await executorApi.listMine().catch(() => [])).filter((a) => a.status === 'ACTIVE');
    for (const a of active) {
        const tasks = await executorApi.getTasks(a.assignmentId).catch(() => []);
        if (tasks.some((x) => x.id === taskId)) return `/executor/cases/${a.assignmentId}`;
    }
    return '/executor/home';
};

// Trang cần mở khi bấm một thông báo (null = ở lại trang hiện tại)
async function targetOf(n) {
    const ref = n.referenceType;
    const id = n.referenceId;
    switch (n.type) {
        // Người thụ hưởng
        case 'BENEFICIARY_ASSIGNED':
        case 'VAULT_ARCHIVED':
            return ifRole('BENEFICIARY', '/beneficiary');
        case 'ASSET_REASSIGNED':
        case 'LEGACY_MESSAGE_DELIVERED':
        case 'CLAIM_DEADLINE_REMINDER':
        case 'EXECUTOR_TASK_COMPLETED':
            return ifRole('BENEFICIARY', id ? `/beneficiary/vaults/${id}` : '/beneficiary');
        // Cùng type cho hai vai trò: phân biệt bằng referenceType
        case 'VAULT_UNLOCKED':
            if (ref === 'Vault') return ifRole('BENEFICIARY', `/beneficiary/vaults/${id}`);
            return hasRole('EXECUTOR') ? executorCaseOfRequest(id) : null;
        // Executor
        case 'VERIFIER_REPLY':
        case 'VERIFICATION_DECIDED':
        case 'VERIFICATION_REOPENED':
            return hasRole('EXECUTOR') ? executorCaseOfRequest(id) : null;
        case 'EXECUTOR_TASK_DISPUTED':
            return hasRole('EXECUTOR') ? executorCaseOfTask(id) : null;
        case 'EXECUTOR_ASSIGNED':
        case 'EXECUTOR_ACTIVATED':
            return ifRole('EXECUTOR', '/executor/home');
        // Owner
        case 'DMS_CHECKIN_REMINDER':
        case 'DMS_CONFIRMATION_PENDING':
        case 'DMS_OWNER_CONFIRMED_SAFE':
        case 'DMS_PAUSED':
        case 'DMS_RESUMED':
            return ifRole('OWNER', '/owner/dms');
        case 'ASSET_VERIFIED':
            return ifRole('OWNER', id ? `/owner/vaults/${id}` : '/owner/vaults');
        case 'ASSET_REVERIFY_NEEDED':
            return ifRole('OWNER', n.params?.vaultId ? `/owner/vaults/${n.params.vaultId}` : '/owner/vaults');
        case 'PAYMENT_SUCCEEDED':
        case 'SUBSCRIPTION_EXPIRING':
            return ifRole('OWNER', '/billing');
        case 'SUBSCRIPTION_EXPIRED':
            return ifRole('OWNER', '/pricing');
        // BENEFICIARY_REMOVED, EXECUTOR_REVOKED: không còn quyền xem; DMS_CONFIRMATION_REQUESTED: link nằm trong email
        default:
            return null;
    }
}

const fmtDateTime = (v, lng) => new Date(v).toLocaleString(lng === 'vi' ? 'vi-VN' : 'en-GB', { dateStyle: 'medium', timeStyle: 'short' });
const fmtDate = (v, lng) => new Date(v).toLocaleDateString(lng === 'vi' ? 'vi-VN' : 'en-GB', { dateStyle: 'medium' });

// "5 phút trước"; quá 7 ngày thì hiện ngày giờ đầy đủ
function timeAgo(value, lng, justNow) {
    if (!value) return '';
    const diff = (new Date(value).getTime() - Date.now()) / 1000;
    const abs = Math.abs(diff);
    if (Number.isNaN(abs)) return '';
    if (abs < 60) return justNow;
    if (abs >= 7 * 86400) return fmtDateTime(value, lng);
    const rtf = new Intl.RelativeTimeFormat(lng === 'vi' ? 'vi' : 'en', { numeric: 'auto' });
    if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute');
    if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour');
    return rtf.format(Math.round(diff / 86400), 'day');
}

// Câu thông báo theo ngôn ngữ đang chọn. Khóa: types.TYPE hoặc types.TYPE_VARIANT (params.variant);
// tham số kết thúc bằng "At" là thời điểm ISO có múi giờ. Thông báo cũ không có params: câu dịch còn sót {{...}}
// thì dùng content (câu tiếng Anh lưu sẵn ở BE).
function messageOf(n, t, lng) {
    const params = n.params || {};
    const values = {};
    for (const [k, v] of Object.entries(params)) {
        // BE gửi thời điểm có múi giờ (…Z); chuỗi không có 'T' là ngày thuần
        values[k] = /At$/.test(k) && v ? (v.includes('T') ? fmtDateTime(v, lng) : fmtDate(v, lng)) : v;
    }
    if (params.role) values.role = t(`notifications.executorRole.${params.role}`, { defaultValue: params.role });
    const fallback = n.content || t('notifications.generic');
    const keys = [];
    if (params.variant) keys.push(`notifications.types.${n.type}_${params.variant}`);
    keys.push(`notifications.types.${n.type}`);
    const text = t(keys, { ...values, defaultValue: fallback });
    return text.includes('{{') ? fallback : text;
}

export default function NotificationBell() {
    const { t, i18n } = useTranslation('common');
    const navigate = useNavigate();
    const [items, setItems] = useState([]);
    const [page, setPage] = useState(0);
    const [last, setLast] = useState(true);
    const [loading, setLoading] = useState(false);
    const [unread, setUnread] = useState(0);
    const [onlyUnread, setOnlyUnread] = useState(false);
    const [open, setOpen] = useState(false);
    const ref = useRef(null);
    const btnRef = useRef(null);
    const panelRef = useRef(null);
    const [pos, setPos] = useState(null);

    const refreshCount = useCallback(() => {
        notificationApi.unreadCount().then(setUnread).catch(() => {});
    }, []);

    // Số chưa đọc: hỏi lại mỗi phút và khi quay lại tab, để thông báo mới hiện mà không cần F5
    useEffect(() => {
        refreshCount();
        const tick = () => { if (document.visibilityState === 'visible') refreshCount(); };
        const timer = setInterval(tick, POLL_MS);
        document.addEventListener('visibilitychange', tick);
        window.addEventListener('focus', tick);
        return () => {
            clearInterval(timer);
            document.removeEventListener('visibilitychange', tick);
            window.removeEventListener('focus', tick);
        };
    }, [refreshCount]);

    // Tải lại trang đầu mỗi lần mở hoặc đổi tab; seq bỏ kết quả của lần tải cũ về muộn
    const seq = useRef(0);
    const reload = (unreadOnly) => {
        const mine = ++seq.current;
        setLoading(true);
        setItems([]);
        notificationApi.list({ page: 0, size: PAGE_SIZE, unreadOnly })
            .then((r) => { if (seq.current === mine) { setItems(r.items); setLast(r.last); setPage(0); } })
            .catch(() => { if (seq.current === mine) { setItems([]); setLast(true); } })
            .finally(() => { if (seq.current === mine) setLoading(false); });
        refreshCount();
    };
    const toggleOpen = () => {
        if (!open) reload(onlyUnread);
        setOpen(!open);
    };
    const switchTab = (unreadOnly) => {
        if (unreadOnly === onlyUnread) return;
        setOnlyUnread(unreadOnly);
        reload(unreadOnly);
    };

    const loadMore = () => {
        const next = page + 1;
        const mine = ++seq.current;
        setLoading(true);
        notificationApi.list({ page: next, size: PAGE_SIZE, unreadOnly: onlyUnread })
            .then((r) => {
                if (seq.current !== mine) return;
                setItems((list) => [...list, ...r.items.filter((x) => !list.some((y) => y.id === x.id))]);
                setLast(r.last);
                setPage(next);
            })
            .catch(() => {})
            .finally(() => { if (seq.current === mine) setLoading(false); });
    };

    // Click ra ngoài hoặc bấm Esc thì đóng
    useEffect(() => {
        if (!open) return;
        const onClick = (e) => { if (ref.current?.contains(e.target) || panelRef.current?.contains(e.target)) return;
            setOpen(false); };
        const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
        document.addEventListener('mousedown', onClick);
        document.addEventListener('keydown', onKey);
        return () => { document.removeEventListener('mousedown', onClick); document.removeEventListener('keydown', onKey); };
    }, [open]);

    // Dropdown dùng position: fixed để không bị cắt bởi thanh bên (overflow) và không tràn màn hình
    useEffect(() => {
        if (!open || !btnRef.current) return;
        const place = () => {
            const r = btnRef.current.getBoundingClientRect();
            const W = 384, gap = 8, margin = 12;
            const left = Math.max(margin, Math.min(r.right + gap, window.innerWidth - W - margin));
            const belowFits = r.bottom + gap + 200 < window.innerHeight / 2 + 200 && r.top < window.innerHeight / 2;
            setPos(belowFits
                ? { left, top: r.bottom + gap, maxHeight: Math.min(560, window.innerHeight - r.bottom - gap - margin) }
                : { left, bottom: window.innerHeight - r.bottom, maxHeight: Math.min(560, r.bottom - margin) });
        };
        place();
        window.addEventListener('resize', place);
        window.addEventListener('scroll', place, true);
        return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true); };
    }, [open]);

    const markOne = (n) => {
        if (n.read) return;
        setItems((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
        setUnread((c) => Math.max(0, c - 1));
        notificationApi.markRead(n.id).catch(() => {});
    };

    const markAll = () => {
        setItems((list) => list.map((x) => ({ ...x, read: true })));
        setUnread(0);
        notificationApi.markAllRead().catch(() => refreshCount());
    };

    const openItem = async (n) => {
        markOne(n);
        const to = await targetOf(n).catch(() => null);
        if (to) {
            setOpen(false);
            navigate(to);
        }
    };

    const tab = (active) => `h-7 px-3 rounded-full text-xs font-semibold transition-colors ${active
        ? 'bg-secondary text-on-secondary' : 'text-on-surface-variant hover:bg-surface-container-low'}`;

    return (
        <div className="relative" ref={ref}>
            <button ref={btnRef} onClick={toggleOpen} aria-haspopup="dialog" aria-expanded={open}
                    className="relative w-10 h-10 rounded-xl border border-outline-variant flex items-center justify-center hover:bg-surface-container-low"
                    aria-label={unread > 0 ? t('notifications.titleUnread', { count: unread }) : t('notifications.title')}
                    title={t('notifications.title')}>
                <span className="material-symbols-outlined text-[20px]">{unread > 0 ? 'notifications_active' : 'notifications'}</span>
                {unread > 0 && (
                    <span className="absolute -top-1 -right-1 min-w-5 h-5 px-1 rounded-full bg-error text-on-error text-[11px] font-bold flex items-center justify-center">
                        {unread > 9 ? '9+' : unread}
                    </span>
                )}
            </button>
            {/* Portal ra body + z-[60]: thanh bên và các thẻ kính (backdrop-filter) tạo stacking context riêng nên z-30 bị nền trang đè lên */}
            {open && pos && createPortal(
                <div ref={panelRef} style={pos} role="dialog" aria-label={t('notifications.title')}
                     className="fixed w-96 max-w-[calc(100vw-24px)] flex flex-col bg-surface-container-lowest rounded-2xl border border-outline-variant/30 shadow-lg z-[60] lv-pop text-on-surface">
                    <div className="px-4 pt-3 pb-2 border-b border-outline-variant/30 shrink-0">
                        <div className="flex items-center justify-between gap-3">
                            <p className="text-sm font-semibold">{t('notifications.title')}</p>
                            <button type="button" onClick={markAll} disabled={unread === 0}
                                    className="text-xs font-semibold text-secondary hover:underline disabled:text-outline disabled:no-underline disabled:cursor-default">
                                {t('notifications.markAllRead')}
                            </button>
                        </div>
                        <div className="mt-2 flex items-center gap-1.5">
                            <button type="button" className={tab(!onlyUnread)} onClick={() => switchTab(false)}>{t('notifications.all')}</button>
                            <button type="button" className={tab(onlyUnread)} onClick={() => switchTab(true)}>
                                {t('notifications.unread')}{unread > 0 ? ` (${unread > 99 ? '99+' : unread})` : ''}
                            </button>
                        </div>
                    </div>
                    <div className="overflow-y-auto min-h-0">
                        {items.length === 0 && !loading && (
                            <div className="px-4 py-8 text-center">
                                <span className="material-symbols-outlined text-[32px] text-outline">notifications_off</span>
                                <p className="mt-1 text-sm text-on-surface-variant">
                                    {onlyUnread ? t('notifications.emptyUnread') : t('notifications.empty')}
                                </p>
                            </div>
                        )}
                        {items.map((n) => {
                            const [icon, tone] = TYPE_META[n.type] || ['notifications', 'info'];
                            return (
                                <button key={n.id} onClick={() => openItem(n)}
                                        className={`w-full text-left px-4 py-3 flex gap-3 items-start border-b border-outline-variant/15 last:border-b-0 hover:bg-surface-container-low ${n.read ? '' : 'bg-secondary-fixed/40'}`}>
                                    <span className={`shrink-0 w-9 h-9 rounded-full flex items-center justify-center ${TONE[tone]}`}>
                                        <span className="material-symbols-outlined text-[20px]" style={{ color: 'inherit' }}>{icon}</span>
                                    </span>
                                    <span className="flex-1 min-w-0">
                                        <span className={`block text-sm break-words ${n.read ? 'text-on-surface-variant' : 'font-semibold'}`}>{messageOf(n, t, i18n.language)}</span>
                                        <span className="block text-xs text-outline mt-0.5" title={n.createdAt ? fmtDateTime(n.createdAt, i18n.language) : ''}>
                                            {timeAgo(n.createdAt, i18n.language, t('notifications.justNow'))}
                                        </span>
                                    </span>
                                    {!n.read && <span className="shrink-0 mt-1.5 w-2 h-2 rounded-full bg-secondary" aria-label={t('notifications.unread')} />}
                                </button>
                            );
                        })}
                        {loading && <p className="px-4 py-3 text-xs text-outline text-center">{t('notifications.loading')}</p>}
                        {!loading && !last && (
                            <button type="button" onClick={loadMore}
                                    className="w-full py-2.5 text-xs font-semibold text-secondary hover:bg-surface-container-low">
                                {t('notifications.loadMore')}
                            </button>
                        )}
                    </div>
                </div>,
                document.body
            )}
        </div>
    );
}
