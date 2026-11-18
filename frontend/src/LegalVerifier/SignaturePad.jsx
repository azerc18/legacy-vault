import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

// Ký tay cho Legal Verifier: khung vẽ bằng chuột / ngón tay / bút (Pointer Events).
// Xuất PNG nền trong suốt, nét đen (BE đếm điểm ảnh tối để chặn khung trống), kích thước 600x200 x tỉ lệ màn hình (≤ 3).
// onChange(dataUrl | null): null khi chưa vẽ đủ nét hoặc vừa "Vẽ lại".
const WIDTH = 600;
const HEIGHT = 200;
const MIN_STROKE_PX = 120; // tổng độ dài nét tối thiểu (px logic) mới coi là đã ký, khớp với kiểm tra phía BE

export default function SignaturePad({ onChange, disabled = false }) {
    const { t } = useTranslation('verifier');
    const canvasRef = useRef(null);
    const drawing = useRef(false);
    const last = useRef(null);
    const length = useRef(0);
    const [hasInk, setHasInk] = useState(false);

    const setup = useCallback(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ratio = Math.min(Math.max(window.devicePixelRatio || 1, 1), 3);
        canvas.width = WIDTH * ratio;
        canvas.height = HEIGHT * ratio;
        const ctx = canvas.getContext('2d');
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.lineWidth = 2.6;
        ctx.strokeStyle = '#111827';
    }, []);

    useEffect(() => { setup(); }, [setup]);

    const point = (e) => {
        const rect = canvasRef.current.getBoundingClientRect();
        return {
            x: ((e.clientX - rect.left) / rect.width) * WIDTH,
            y: ((e.clientY - rect.top) / rect.height) * HEIGHT,
        };
    };

    const start = (e) => {
        if (disabled) return;
        e.preventDefault();
        canvasRef.current.setPointerCapture?.(e.pointerId);
        drawing.current = true;
        last.current = point(e);
        const ctx = canvasRef.current.getContext('2d');
        ctx.beginPath();
        ctx.arc(last.current.x, last.current.y, 1.2, 0, Math.PI * 2);
        ctx.fillStyle = '#111827';
        ctx.fill();
    };

    const move = (e) => {
        if (!drawing.current || disabled) return;
        e.preventDefault();
        const p = point(e);
        const ctx = canvasRef.current.getContext('2d');
        ctx.beginPath();
        ctx.moveTo(last.current.x, last.current.y);
        ctx.lineTo(p.x, p.y);
        ctx.stroke();
        length.current += Math.hypot(p.x - last.current.x, p.y - last.current.y);
        last.current = p;
    };

    const end = () => {
        if (!drawing.current) return;
        drawing.current = false;
        const enough = length.current >= MIN_STROKE_PX;
        setHasInk(enough);
        onChange?.(enough ? canvasRef.current.toDataURL('image/png') : null);
    };

    const clear = () => {
        length.current = 0;
        setHasInk(false);
        setup();
        onChange?.(null);
    };

    return (
        <div className="space-y-2" data-testid="signature-pad">
            <div className="relative rounded-xl border-2 border-dashed border-outline-variant bg-white overflow-hidden">
                <canvas
                    ref={canvasRef}
                    role="img"
                    aria-label={t('detail.handwritten.canvasLabel')}
                    className={`block w-full touch-none ${disabled ? 'cursor-not-allowed opacity-60' : 'cursor-crosshair'}`}
                    style={{ aspectRatio: `${WIDTH} / ${HEIGHT}` }}
                    onPointerDown={start}
                    onPointerMove={move}
                    onPointerUp={end}
                    onPointerCancel={end}
                    onPointerLeave={end}
                />
                {/* Đường kẻ ký + chữ mờ hướng dẫn, chỉ là trang trí, không nằm trong ảnh xuất ra */}
                <div className="pointer-events-none absolute left-6 right-6 bottom-10 border-b border-slate-300" />
                {!hasInk && (
                    <p className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-slate-400">
                        {t('detail.handwritten.placeholder')}
                    </p>
                )}
            </div>
            <div className="flex items-center justify-between gap-2">
                <p className="text-xs text-on-surface-variant">{t('detail.handwritten.hint')}</p>
                <button
                    type="button"
                    onClick={clear}
                    disabled={disabled}
                    className="text-sm font-semibold text-secondary disabled:text-outline shrink-0"
                >
                    {t('detail.handwritten.clear')}
                </button>
            </div>
        </div>
    );
}
