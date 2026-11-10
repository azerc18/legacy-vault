// Dấu "?" nhỏ giải thích thuật ngữ. Rê chuột / focus bàn phím thì hiện; trên cảm ứng thì chạm để mở / đóng.
// Nội dung nằm ở i18n/locales/*/glossary.json: <InfoTip term="dms" /> -> glossary.terms.dms.{title,text}.
import React, { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';

export default function InfoTip({ term, className = '' }) {
    const { t } = useTranslation('glossary');
    const id = useId();
    const [open, setOpen] = useState(false);
    const [shift, setShift] = useState(0);

    const title = t(`terms.${term}.title`, { defaultValue: '' });
    const text = t(`terms.${term}.text`, { defaultValue: '' });
    if (!text) return null;   // thiếu bản dịch thì không hiện dấu ? rỗng

    return (
        <span className={`relative group inline-flex align-middle ${className}`}>
            <button type="button"
                    aria-label={t('aboutTerm', { term: title })}
                    aria-describedby={id}
                    aria-expanded={open}
                    onClick={(e) => {
                        // Nút sát mép phải thì đẩy khung sang trái để không tràn màn hình
                        const screen = document.documentElement.clientWidth;
                        const width = Math.min(256, window.innerWidth * 0.8);
                        setShift(Math.max(0, e.currentTarget.getBoundingClientRect().left + width + 12 - screen));
                        setOpen((v) => !v);
                    }}
                    onBlur={() => setOpen(false)}
                    onKeyDown={(e) => { if (e.key === 'Escape') setOpen(false); }}
                    className="inline-flex items-center justify-center rounded-full text-outline hover:text-primary focus:text-primary focus:outline-none focus-visible:ring-2 focus-visible:ring-primary">
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">help</span>
            </button>
            <span id={id} role="tooltip" style={{ left: -shift }}
                  className={`lv-infotip${open ? ' is-open' : ''} pointer-events-none !absolute left-0 top-full mt-2 w-64 max-w-[80vw] rounded-xl lv-glass-frosted bg-surface-container-lowest/95 text-on-surface border border-outline-variant/50 text-xs font-normal leading-relaxed px-3.5 py-2.5 shadow-xl z-30 origin-top-left transition-all duration-150 group-hover:opacity-100 group-hover:scale-100 group-focus-within:opacity-100 group-focus-within:scale-100 ${open ? 'opacity-100 scale-100' : 'opacity-0 scale-95'}`}>
                {title && <strong className="block mb-1 text-on-surface font-semibold">{title}</strong>}
                <span className="text-on-surface-variant">{text}</span>
            </span>
        </span>
    );
}
