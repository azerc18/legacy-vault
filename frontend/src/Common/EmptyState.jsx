// Trạng thái trống dùng chung: icon + tiêu đề + câu gợi ý bước tiếp theo + nút (tuỳ chọn).
import React from 'react';
import { Link } from 'react-router-dom';

const Icon = ({ name, className = '' }) => <span className={`material-symbols-outlined ${className}`}>{name}</span>;

/**
 * @param action   { label, to } (Link) hoặc { label, onClick } (button)
 * @param compact  true = nhỏ gọn, dùng bên trong Card / khung chat
 * @param bordered true = có khung viền nét đứt (dùng khi đứng một mình trên trang)
 */
export default function EmptyState({ icon = 'inbox', title, text, action, compact = false, bordered = false }) {
    const btn = 'mt-4 h-10 px-4 rounded-xl bg-primary text-on-primary text-sm font-bold inline-flex items-center gap-2 hover:opacity-90';
    return (
        <div className={`flex flex-col items-center text-center ${compact ? 'py-6 px-4' : 'py-12 px-6'} ${
            bordered ? 'rounded-2xl border border-dashed border-outline-variant bg-surface-container-lowest' : ''}`}>
            <span className={`${compact ? 'w-12 h-12' : 'w-16 h-16'} rounded-full bg-surface-container flex items-center justify-center text-outline`}>
                <Icon name={icon} className={compact ? 'text-[26px]' : 'text-[34px]'} />
            </span>
            <p className={`${compact ? 'mt-3 text-sm' : 'mt-4'} font-semibold text-on-surface`}>{title}</p>
            {text && <p className="mt-1 text-sm text-on-surface-variant max-w-md">{text}</p>}
            {action && (action.to
                ? <Link to={action.to} className={btn}>{action.icon && <Icon name={action.icon} className="text-[18px]" />}{action.label}</Link>
                : <button type="button" onClick={action.onClick} className={btn}>{action.icon && <Icon name={action.icon} className="text-[18px]" />}{action.label}</button>)}
        </div>
    );
}
