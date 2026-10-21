import React, { useEffect } from 'react';

// Hộp xác nhận cho các thao tác không hoàn tác được (Approve / Reject / Sign).
// open=false -> không hiện gì. Nhấn Esc hoặc bấm nền = huỷ.
export default function ConfirmDialog({ open, title, message, confirmText, danger = false, busy = false, onConfirm, onCancel }) {
    useEffect(() => {
        if (!open) return;
        const onKey = (e) => { if (e.key === 'Escape' && !busy) onCancel(); };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [open, busy, onCancel]);

    if (!open) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-5"
            onClick={() => { if (!busy) onCancel(); }}
        >
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="confirm-title"
                className="w-full max-w-md rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30"
                onClick={(e) => e.stopPropagation()}
            >
                <h3 id="confirm-title" className="font-title-lg font-bold text-on-surface">{title}</h3>
                <p className="mt-2 text-sm text-on-surface-variant whitespace-pre-line">{message}</p>
                <div className="mt-6 flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={onCancel}
                        disabled={busy}
                        className="h-10 px-4 rounded-xl border border-outline-variant font-semibold text-on-surface hover:bg-surface-container-low disabled:opacity-50"
                    >
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={onConfirm}
                        disabled={busy}
                        className={`h-10 px-4 rounded-xl font-bold disabled:opacity-50 ${danger
                            ? 'bg-error text-on-error hover:opacity-90'
                            : 'bg-primary text-on-primary hover:bg-primary-container'}`}
                    >
                        {busy ? 'Processing...' : confirmText}
                    </button>
                </div>
            </div>
        </div>
    );
}
