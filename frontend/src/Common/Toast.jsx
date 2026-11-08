// Thông báo nhỏ tự tắt ở góc màn hình (toast). Dùng: const toast = useToast(); toast.success('Đã lưu').
// Bọc <ToastProvider> một lần ở main.jsx. Màu lấy từ token của theme nên sáng / tối đều đúng.
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

const ToastContext = createContext(null);

const DURATION = { success: 4000, info: 4000, error: 7000 };   // lỗi để lâu hơn cho kịp đọc
const MAX_VISIBLE = 4;

const STYLE = {
    success: { box: 'bg-success-container text-on-success-container', icon: 'check_circle' },
    error: { box: 'bg-error-container text-on-error-container', icon: 'error' },
    info: { box: 'bg-secondary-fixed text-on-secondary-fixed', icon: 'info' },
};

function ToastItem({ toast, onClose }) {
    const { t } = useTranslation('common');
    const style = STYLE[toast.type] ?? STYLE.info;

    useEffect(() => {
        const timer = setTimeout(() => onClose(toast.id), DURATION[toast.type] ?? DURATION.info);
        return () => clearTimeout(timer);
    }, [toast.id, toast.type, onClose]);

    return (
        <div role={toast.type === 'error' ? 'alert' : 'status'}
             className={`pointer-events-auto flex items-start gap-3 rounded-xl px-4 py-3 shadow-lg border border-outline-variant/30 text-sm ${style.box}`}>
            <span className="material-symbols-outlined text-[20px] shrink-0" aria-hidden="true">{style.icon}</span>
            <span className="flex-1 min-w-0 break-words">{toast.message}</span>
            <button type="button" onClick={() => onClose(toast.id)} aria-label={t('toast.close', 'Close')}
                    className="shrink-0 opacity-70 hover:opacity-100">
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">close</span>
            </button>
        </div>
    );
}

export function ToastProvider({ children }) {
    const [items, setItems] = useState([]);
    const nextId = useRef(1);

    const close = useCallback((id) => setItems((list) => list.filter((x) => x.id !== id)), []);

    const push = useCallback((type, message) => {
        if (!message) return;
        const id = nextId.current++;
        setItems((list) => [...list.filter((x) => x.message !== message), { id, type, message }].slice(-MAX_VISIBLE));
    }, []);

    const api = useMemo(() => ({
        success: (message) => push('success', message),
        error: (message) => push('error', message),
        info: (message) => push('info', message),
    }), [push]);

    return (
        <ToastContext.Provider value={api}>
            {children}
            <div className="pointer-events-none fixed z-[100] inset-x-3 bottom-3 sm:inset-x-auto sm:right-4 sm:top-4 sm:bottom-auto sm:w-96 flex flex-col gap-2">
                {items.map((item) => <ToastItem key={item.id} toast={item} onClose={close} />)}
            </div>
        </ToastContext.Provider>
    );
}

// Không có Provider (ví dụ test) thì không làm gì thay vì văng lỗi
const NOOP = { success: () => {}, error: () => {}, info: () => {} };

export function useToast() {
    return useContext(ToastContext) ?? NOOP;
}
