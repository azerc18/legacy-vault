// Nút chọn file dùng chung thay cho <input type="file"> mặc định của trình duyệt.
// Component "có kiểm soát": cha giữ state `file`, đặt file = null là ô tự xoá (không cần form.reset()).
import React, { useEffect, useRef } from 'react';

const Icon = ({ name, className = '' }) => <span className={`material-symbols-outlined ${className}`}>{name}</span>;

const formatSize = (b) => (b < 1024 * 1024 ? `${Math.max(1, Math.round(b / 1024))} KB` : `${(b / 1024 / 1024).toFixed(1)} MB`);

/**
 * @param file      File đang chọn hoặc null
 * @param onChange  (File | null) => void
 * @param accept    vd. ".pdf,.png,.jpg"
 * @param label     chữ trên nút khi chưa chọn
 * @param hint      dòng gợi ý nhỏ (định dạng, dung lượng tối đa)
 * @param compact   true = chỉ nút icon (dùng trong ô soạn tin nhắn)
 */
export default function FilePicker({
    file, onChange, accept, label = 'Choose file', hint, disabled = false, required = false, compact = false, id,
}) {
    const inputRef = useRef(null);

    // Cha xoá file -> xoá luôn giá trị của input để chọn lại cùng file vẫn kích hoạt onChange
    useEffect(() => {
        if (!file && inputRef.current) inputRef.current.value = '';
    }, [file]);

    const pick = () => !disabled && inputRef.current?.click();

    return (
        <div className={compact ? 'flex items-center gap-2 min-w-0' : 'space-y-1.5'}>
            <input ref={inputRef} id={id} type="file" accept={accept} required={required && !file}
                   disabled={disabled} className="sr-only" tabIndex={-1}
                   onChange={(e) => onChange(e.target.files?.[0] ?? null)} />

            <div className="flex flex-wrap items-center gap-2 min-w-0">
                <button type="button" onClick={pick} disabled={disabled}
                        title={compact ? label : undefined} aria-label={label}
                        className={`${compact ? 'h-11 w-11 justify-center' : 'h-11 px-4'} shrink-0 rounded-xl border border-dashed border-outline-variant text-primary text-sm font-semibold inline-flex items-center gap-2 hover:bg-surface-container-low hover:border-primary disabled:opacity-60 disabled:cursor-not-allowed`}>
                    <Icon name={compact ? 'attach_file' : 'upload_file'} className="text-[20px]" />
                    {!compact && (file ? 'Change file' : label)}
                </button>

                {file && (
                    <span className="min-w-0 max-w-full h-9 pl-3 pr-1 rounded-full bg-secondary-fixed text-on-secondary-fixed text-sm inline-flex items-center gap-2">
                        <Icon name="description" className="text-[18px] shrink-0" />
                        <span className="truncate max-w-[220px]" title={file.name}>{file.name}</span>
                        <span className="text-xs opacity-70 shrink-0">{formatSize(file.size)}</span>
                        <button type="button" onClick={() => onChange(null)} disabled={disabled} aria-label="Remove file"
                                className="w-7 h-7 shrink-0 rounded-full hover:bg-black/10 inline-flex items-center justify-center">
                            <Icon name="close" className="text-[16px]" />
                        </button>
                    </span>
                )}
                {!file && !compact && <span className="text-sm text-on-surface-variant">No file selected</span>}
            </div>

            {hint && !compact && <p className="text-xs text-on-surface-variant">{hint}</p>}
        </div>
    );
}
