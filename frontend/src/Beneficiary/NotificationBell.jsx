// Chuông thông báo trong app: Beneficiary (FR-16) và Executor (D6, FR-11 / FR-12 / FR-14)
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { beneficiaryApi } from '../api/beneficiaryApi.js';
import { executorApi } from '../api/executorApi.js';

const TYPE_ICON = {
    VAULT_UNLOCKED: 'lock_open', EXECUTOR_ACTIVATED: 'badge', VERIFIER_REPLY: 'forum', VERIFICATION_DECIDED: 'gavel',
};
const fmt = (v) => (v ? new Date(v).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '');

// Mỗi nguồn: lấy danh sách, đánh dấu đã đọc, và trang cần mở khi bấm (null = ở lại)
const SOURCES = {
    beneficiary: {
        list: () => beneficiaryApi.listNotifications(),
        markRead: (id) => beneficiaryApi.markNotificationRead(id),
        target: async (n) => (n.vaultId ? `/beneficiary/vaults/${n.vaultId}` : null),
    },
    // API chung /api/notifications: theo referenceType (quy ước ở NotificationResponse)
    executor: {
        list: () => executorApi.listNotifications(),
        count: () => executorApi.unreadCount(), // đúng cả khi có hơn 20 thông báo (danh sách chỉ lấy trang đầu)
        markRead: (id) => executorApi.markRead(id),
        target: async (n) => {
            if (n.referenceType === 'Vault') return `/beneficiary/vaults/${n.referenceId}`; // người vừa là Beneficiary
            if (n.referenceType !== 'LegalVerificationRequest') return null;
            const assignmentId = await executorApi.findAssignmentOfRequest(n.referenceId).catch(() => null);
            return assignmentId ? `/executor/cases/${assignmentId}` : '/executor/home';
        },
    },
};

export default function NotificationBell({ source = 'beneficiary' }) {
    const api = SOURCES[source];
    const navigate = useNavigate();
    const [items, setItems] = useState([]);
    const [serverUnread, setServerUnread] = useState(null);
    const [open, setOpen] = useState(false);
    const ref = useRef(null);

    // Tải lại mỗi lần mở để thấy thông báo mới mà không cần F5
    useEffect(() => {
        api.list().then(setItems).catch(() => setItems([]));
        api.count?.().then(setServerUnread).catch(() => setServerUnread(null));
    }, [api, open]);

    // Click ra ngoài thì đóng
    useEffect(() => {
        if (!open) return;
        const onClick = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
        document.addEventListener('mousedown', onClick);
        return () => document.removeEventListener('mousedown', onClick);
    }, [open]);

    const unread = serverUnread ?? items.filter((n) => !n.read).length;

    const openItem = async (n) => {
        if (!n.read) {
            setItems((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
            setServerUnread((c) => (c == null ? c : Math.max(0, c - 1)));
            api.markRead(n.id).catch(() => {});
        }
        setOpen(false);
        const to = await api.target(n);
        if (to) navigate(to);
    };

    return (
        <div className="relative" ref={ref}>
            <button onClick={() => setOpen(!open)} className="relative w-10 h-10 rounded-xl border border-outline-variant flex items-center justify-center hover:bg-surface-container-low" aria-label="Notifications">
                <span className="material-symbols-outlined text-[20px]">notifications</span>
                {unread > 0 && (
                    <span className="absolute -top-1 -right-1 min-w-5 h-5 px-1 rounded-full bg-error text-on-error text-[11px] font-bold flex items-center justify-center">
                        {unread > 9 ? '9+' : unread}
                    </span>
                )}
            </button>
            {open && (
                <div className="absolute right-0 mt-2 w-80 max-h-96 overflow-y-auto bg-surface-container-lowest rounded-2xl border border-outline-variant/30 shadow-lg z-30">
                    <p className="px-4 py-3 text-sm font-semibold border-b border-outline-variant/30">Notifications</p>
                    {items.length === 0 && <p className="px-4 py-6 text-sm text-on-surface-variant text-center">No notifications yet.</p>}
                    {items.map((n) => (
                        <button key={n.id} onClick={() => openItem(n)} className={`w-full text-left px-4 py-3 flex gap-3 hover:bg-surface-container-low ${n.read ? '' : 'bg-secondary-fixed/40'}`}>
                            <span className="material-symbols-outlined text-[20px] text-secondary">{TYPE_ICON[n.type] || 'notifications'}</span>
                            <span className="flex-1 min-w-0">
                                <span className={`block text-sm ${n.read ? 'text-on-surface-variant' : 'font-semibold'}`}>{n.content}</span>
                                <span className="block text-xs text-outline mt-0.5">{fmt(n.createdAt)}</span>
                            </span>
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
