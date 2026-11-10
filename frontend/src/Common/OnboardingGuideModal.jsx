import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { startSpotlightTour } from './SpotlightTour.jsx';
import LanguageSwitch from './LanguageSwitch.jsx';

export const ONBOARDING_STORAGE_KEY = 'lv_onboarding_seen_v1';
export const OPEN_ONBOARDING_EVENT = 'lv_open_onboarding';

export function openOnboardingGuide() {
    window.dispatchEvent(new CustomEvent(OPEN_ONBOARDING_EVENT));
}

const STEP_ICONS = [
    { icon: 'inventory_2', color: 'from-blue-500 to-indigo-600', ring: 'ring-blue-400/40' },
    { icon: 'diversity_3', color: 'from-teal-400 to-emerald-600', ring: 'ring-emerald-400/40' },
    { icon: 'favorite', color: 'from-rose-500 to-red-600', ring: 'ring-rose-400/40' },
    { icon: 'verified', color: 'from-amber-400 to-orange-500', ring: 'ring-amber-400/50' },
];

export default function OnboardingGuideModal({ defaultOpen = false, onDismiss }) {
    const { t } = useTranslation('onboarding');
    const [open, setOpen] = useState(defaultOpen);
    const [currentStep, setCurrentStep] = useState(0);
    const [dontShowAgain, setDontShowAgain] = useState(true);

    const steps = t('steps', { returnObjects: true });
    const stepList = Array.isArray(steps) ? steps : [];
    const totalSteps = stepList.length || 4;

    // Lắng nghe sự kiện mở từ bất kỳ đâu trên trang (Header, Banner, Card)
    useEffect(() => {
        const handleOpen = () => {
            setCurrentStep(0);
            setOpen(true);
        };
        window.addEventListener(OPEN_ONBOARDING_EVENT, handleOpen);
        return () => window.removeEventListener(OPEN_ONBOARDING_EVENT, handleOpen);
    }, []);

    // Hỗ trợ phím tắt ESC và phím mũi tên
    useEffect(() => {
        if (!open) return undefined;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                handleClose();
            } else if (e.key === 'ArrowRight') {
                setCurrentStep((s) => Math.min(totalSteps - 1, s + 1));
            } else if (e.key === 'ArrowLeft') {
                setCurrentStep((s) => Math.max(0, s - 1));
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [open, totalSteps, dontShowAgain]);

    if (!open) return null;

    const handleClose = () => {
        if (dontShowAgain) {
            try {
                localStorage.setItem(ONBOARDING_STORAGE_KEY, 'true');
            } catch {
                // ignore
            }
        }
        setOpen(false);
        if (onDismiss) onDismiss();
    };

    const handleNext = () => {
        if (currentStep < totalSteps - 1) {
            setCurrentStep((s) => s + 1);
        } else {
            handleClose();
        }
    };

    const handlePrev = () => {
        setCurrentStep((s) => Math.max(0, s - 1));
    };

    const step = stepList[currentStep] || {};
    const iconMeta = STEP_ICONS[currentStep] || STEP_ICONS[0];
    const isLast = currentStep === totalSteps - 1;

    return createPortal(
        <div
            className="lv-modal-backdrop fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-in fade-in duration-200"
            role="dialog"
            aria-modal="true"
            aria-labelledby="onboarding-title"
            onClick={handleClose}
        >
            <div
                className="relative w-full max-w-2xl bg-[#0e1629] border border-white/15 rounded-3xl shadow-2xl overflow-hidden text-white flex flex-col max-h-[90vh]"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Thanh hào quang trang trí trên cùng */}
                <div className="absolute top-0 inset-x-0 h-1.5 bg-gradient-to-r from-blue-500 via-indigo-400 to-amber-400" />

                {/* Header Modal */}
                <div className="px-6 sm:px-8 pt-7 pb-4 border-b border-white/10 shrink-0">
                    <div className="flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2 min-w-0">
                            <span className="material-symbols-outlined text-amber-400 text-[20px]">school</span>
                            <span className="text-[11px] font-bold tracking-[0.2em] uppercase text-amber-300">
                                {t('welcomeTitle')}
                            </span>
                        </div>
                    <div className="flex items-center gap-2 shrink-0 -mr-2">
                        <LanguageSwitch variant="dark" />
                        <button
                            type="button"
                            onClick={handleClose}
                            title={t('skip')}
                            aria-label={t('skip')}
                            className="h-8 w-8 rounded-full flex items-center justify-center text-white/60 hover:text-white hover:bg-white/10 transition-colors"
                        >
                            <span className="material-symbols-outlined text-[20px]">close</span>
                        </button>
                    </div>
                    </div>
                    <h2 id="onboarding-title" className="text-xl sm:text-2xl font-bold mt-1 tracking-tight text-white">
                        {step.title}
                    </h2>
                </div>

                {/* Stepper Dots Navigation */}
                <div className="px-6 sm:px-8 py-3 bg-white/[0.03] border-b border-white/5 flex items-center justify-between gap-2 shrink-0">
                    <div className="flex items-center gap-2">
                        {Array.from({ length: totalSteps }).map((_, idx) => {
                            const active = idx === currentStep;
                            const visited = idx < currentStep;
                            return (
                                <button
                                    key={idx}
                                    type="button"
                                    onClick={() => setCurrentStep(idx)}
                                    title={`Chuyển tới bước ${idx + 1}`}
                                    className={`h-2.5 rounded-full transition-all duration-300 ${
                                        active
                                            ? 'w-8 bg-amber-400 shadow-sm shadow-amber-400/50'
                                            : visited
                                            ? 'w-2.5 bg-white/60 hover:bg-white/80'
                                            : 'w-2.5 bg-white/20 hover:bg-white/40'
                                    }`}
                                />
                            );
                        })}
                    </div>
                    <span className="text-xs font-semibold text-white/60">
                        {t('stepBadge', { current: currentStep + 1, total: totalSteps })}
                    </span>
                </div>

                {/* Nội dung Bước Hiện Tại */}
                <div className="px-6 sm:px-8 py-6 overflow-y-auto space-y-5">
                    {/* Tag & Subtitle */}
                    <div className="flex items-center gap-3">
                        <div className={`w-12 h-12 rounded-2xl bg-gradient-to-br ${iconMeta.color} flex items-center justify-center shadow-lg ring-4 ${iconMeta.ring} shrink-0`}>
                            <span className="material-symbols-outlined text-[26px] text-white">
                                {iconMeta.icon}
                            </span>
                        </div>
                        <div>
                            <span className="inline-block px-2.5 py-0.5 rounded-md text-[11px] font-extrabold tracking-wider uppercase bg-white/10 text-white/90 border border-white/10">
                                {step.tag}
                            </span>
                            <p className="text-sm font-medium text-white/70 mt-1">
                                {step.subtitle}
                            </p>
                        </div>
                    </div>

                    {/* Đoạn mô tả chính */}
                    <p className="text-sm sm:text-[15px] leading-relaxed text-white/85">
                        {step.description}
                    </p>

                    {/* Danh sách 3 điểm then chốt */}
                    {step.points && Array.isArray(step.points) && (
                        <div className="bg-white/[0.04] border border-white/10 rounded-2xl p-4 sm:p-5 space-y-3">
                            {step.points.map((pt, idx) => (
                                <div key={idx} className="flex items-start gap-3 text-sm text-white/90">
                                    <span className="material-symbols-outlined text-emerald-400 text-[18px] shrink-0 mt-0.5">
                                        check_circle
                                    </span>
                                    <span>{pt}</span>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Mẹo tân thủ (Pro-tip box) */}
                    {step.tip && (
                        <div className="p-3.5 rounded-xl bg-amber-400/10 border border-amber-400/25 flex items-start gap-2.5 text-xs sm:text-sm text-amber-200">
                            <span className="material-symbols-outlined text-amber-400 text-[18px] shrink-0">
                                lightbulb
                            </span>
                            <span>{step.tip}</span>
                        </div>
                    )}
                </div>

                {/* Footer Controls */}
                <div className="px-6 sm:px-8 py-4 bg-white/[0.02] border-t border-white/10 flex flex-col sm:flex-row items-center justify-between gap-4 shrink-0">
                    <div className="flex items-center gap-4 w-full sm:w-auto">
                        <label className="flex items-center gap-2.5 text-xs text-white/60 hover:text-white/80 cursor-pointer select-none">
                            <input
                                type="checkbox"
                                checked={dontShowAgain}
                                onChange={(e) => setDontShowAgain(e.target.checked)}
                                className="rounded border-white/30 bg-white/10 text-amber-400 focus:ring-amber-400/30"
                            />
                            <span>{t('dontShowAgain')}</span>
                        </label>
                        <button
                            type="button"
                            onClick={() => {
                                handleClose();
                                startSpotlightTour();
                            }}
                            className="hidden md:inline-flex h-9 px-3 rounded-lg border border-amber-400/40 text-amber-300 hover:bg-amber-400/10 text-xs font-bold items-center gap-1.5 transition-colors"
                        >
                            <span className="material-symbols-outlined text-[16px]">rocket_launch</span>
                            <span>{t('startInteractiveTour')}</span>
                        </button>
                    </div>

                    <div className="flex items-center gap-2.5 w-full sm:w-auto justify-end">
                        {currentStep > 0 && (
                            <button
                                type="button"
                                onClick={handlePrev}
                                className="h-10 px-4 rounded-xl border border-white/20 text-sm font-semibold text-white/80 hover:bg-white/10 transition-colors"
                            >
                                {t('prev')}
                            </button>
                        )}
                        <button
                            type="button"
                            onClick={handleNext}
                            className={`h-10 px-5 rounded-xl text-sm font-bold shadow-md transition-all flex items-center gap-1.5 ${
                                isLast
                                    ? 'bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 hover:brightness-110 shadow-amber-400/20'
                                    : 'bg-white text-slate-950 hover:bg-white/90 shadow-white/20'
                            }`}
                        >
                            <span>{isLast ? t('finish') : t('next')}</span>
                            <span className="material-symbols-outlined text-[18px]">
                                {isLast ? 'rocket_launch' : 'arrow_forward'}
                            </span>
                        </button>
                    </div>
                </div>
            </div>
        </div>,
        document.body
    );
}
