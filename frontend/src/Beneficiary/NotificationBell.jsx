// Chuông thông báo trong app cho Beneficiary (FR-16)
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { beneficiaryApi } from '../api/beneficiaryApi.js';

const TYPE_ICON = { VAULT_UNLOCKED: 'lock_open', EXECUTOR_ACTIVATED: 'badge' };
const fmt = (v) => (v ? new Date(v).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '');

export default function NotificationBell() {
    const navigate = useNavigate();
    const [items, setItems] = useState([]);
    const [open, setOpen] = useState(false);
    const ref = useRef(null);

    useEffect(() => {
        beneficiaryApi.listNotifications().then(setItems).catch(() => setItems([]));
    }, []);

    // Click ra ngoài thì đóng
    useEffect(() => {
        if (!open) return;
        const onClick = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
        document.addEventListener('mousedown', onClick);
        return () => document.removeEventListener('mousedown', onClick);
    }, [open]);

    const unread = items.filter((n) => !n.read).length;

    const openItem = async (n) => {
        if (!n.read) {
            setItems((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
            beneficiaryApi.markNotificationRead(n.id).catch(() => {});
        }
        setOpen(false);
        if (n.vaultId) navigate(`/beneficiary/vaults/${n.vaultId}`);
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
