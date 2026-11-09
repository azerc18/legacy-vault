import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';

export const ONBOARDING_STATUS_KEY = 'lv_onboarding_status';
export const ONBOARDING_STEP_KEY = 'lv_tour_step';
export const START_SPOTLIGHT_EVENT = 'lv_start_spotlight_tour';
export const OPEN_PROMPT_EVENT = 'lv_open_first_time_prompt';

/**
 * Kích hoạt trực tiếp Tour tương tác 6 bước
 */
export function startSpotlightTour() {
    window.dispatchEvent(new CustomEvent(START_SPOTLIGHT_EVENT));
}

/**
 * Mở hộp thoại hỏi người dùng lần đầu
 */
export function openFirstTimePrompt() {
    window.dispatchEvent(new CustomEvent(OPEN_PROMPT_EVENT));
}

/**
 * Đặt lại trạng thái để test hoặc xem lại từ đầu
 */
export function resetOnboardingStatus() {
    try {
        localStorage.removeItem(ONBOARDING_STATUS_KEY);
        localStorage.removeItem(ONBOARDING_STEP_KEY);
    } catch {
        // ignore
    }
}

export default function SpotlightTour() {
    const { t } = useTranslation(['onboarding', 'common']);
    const location = useLocation();
    const navigate = useNavigate();

    const [showPrompt, setShowPrompt] = useState(false);
    const [tourActive, setTourActive] = useState(false);
    const [currentStep, setCurrentStep] = useState(1);
    const [targetRect, setTargetRect] = useState(null);
    const [showCongrats, setShowCongrats] = useState(false);
    const retryTimerRef = useRef(null);

    const stepsConfig = [
        {
            step: 1,
            route: '/owner/vaults',
            selector: '[data-tour="new-vault-btn"]',
            padding: 8,
            title: t('tour.steps.step1.title'),
            instruction: t('tour.steps.step1.instruction'),
            tip: t('tour.steps.step1.tip'),
            nextLabel: t('tour.goToCreate'),
        },
        {
            step: 2,
            route: '/owner/vaults/new',
            selector: '[data-tour="vault-info"]',
            padding: 10,
            title: t('tour.steps.step2.title'),
            instruction: t('tour.steps.step2.instruction'),
            tip: t('tour.steps.step2.tip'),
            nextLabel: t('tour.nextBtn'),
        },
        {
            step: 3,
            route: '/owner/vaults/new',
            selector: '[data-tour="beneficiary-section"]',
            padding: 10,
            title: t('tour.steps.step3.title'),
            instruction: t('tour.steps.step3.instruction'),
            tip: t('tour.steps.step3.tip'),
            nextLabel: t('tour.nextBtn'),
        },
        {
            step: 4,
            route: '/owner/vaults/new',
            selector: '[data-tour="asset-type-select"]',
            padding: 10,
            title: t('tour.steps.step4.title'),
            instruction: t('tour.steps.step4.instruction'),
            tip: t('tour.steps.step4.tip'),
            nextLabel: t('tour.nextBtn'),
        },
        {
            step: 5,
            route: '/owner/vaults/new',
            selector: '[data-tour="asset-fields"]',
            padding: 10,
            title: t('tour.steps.step5.title'),
            instruction: t('tour.steps.step5.instruction'),
            tip: t('tour.steps.step5.tip'),
            nextLabel: t('tour.nextBtn'),
        },
        {
            step: 6,
            route: '/owner/vaults/new',
            selector: '[data-tour="submit-vault-btn"]',
            padding: 8,
            title: t('tour.steps.step6.title'),
            instruction: t('tour.steps.step6.instruction'),
            tip: t('tour.steps.step6.tip'),
            nextLabel: t('tour.finishBtn'),
            isLast: true,
        },
    ];

    const totalSteps = stepsConfig.length;
    const currentConfig = stepsConfig[currentStep - 1] || stepsConfig[0];

    // Cập nhật toạ độ bounding box của phần tử mục tiêu
    const updateTargetRect = useCallback(() => {
        if (!tourActive) return;
        const cfg = stepsConfig[currentStep - 1];
        if (!cfg) return;

        const el = document.querySelector(cfg.selector);
        if (el) {
            const raw = el.getBoundingClientRect();
            const pad = cfg.padding || 8;
            setTargetRect({
                top: Math.max(0, raw.top - pad),
                left: Math.max(0, raw.left - pad),
                right: Math.min(window.innerWidth, raw.right + pad),
                bottom: Math.min(window.innerHeight, raw.bottom + pad),
                width: raw.width + pad * 2,
                height: raw.height + pad * 2,
            });
        }
    }, [tourActive, currentStep, stepsConfig]);

    // Kiểm tra hiển thị câu hỏi lần đầu khi người dùng đăng nhập
    useEffect(() => {
        const user = getUser();
        if (!user) return undefined;

        // Bỏ qua nếu đang ở trang auth công khai
        const publicPaths = ['/', '/login', '/register', '/forgot-password', '/reset-password', '/verify-email'];
        if (publicPaths.includes(location.pathname)) return undefined;

        try {
            const status = localStorage.getItem(ONBOARDING_STATUS_KEY);
            if (!status) {
                // Lần đầu đăng nhập: hiển thị câu hỏi sau 600ms
                const timer = setTimeout(() => {
                    setShowPrompt(true);
                }, 600);
                return () => clearTimeout(timer);
            }
            if (status === 'tour_active') {
                const savedStep = parseInt(localStorage.getItem(ONBOARDING_STEP_KEY) || '1', 10);
                setCurrentStep(savedStep >= 1 && savedStep <= totalSteps ? savedStep : 1);
                setTourActive(true);
            }
        } catch {
            // ignore
        }
        return undefined;
    }, [location.pathname, totalSteps]);

    // Lắng nghe sự kiện toàn cục để khởi động lại tour hoặc mở câu hỏi
    useEffect(() => {
        const handleStartTour = () => {
            setShowPrompt(false);
            setShowCongrats(false);
            setCurrentStep(1);
            setTourActive(true);
            try {
                localStorage.setItem(ONBOARDING_STATUS_KEY, 'tour_active');
                localStorage.setItem(ONBOARDING_STEP_KEY, '1');
            } catch {
                // ignore
            }
            if (location.pathname !== '/owner/vaults') {
                navigate('/owner/vaults');
            }
        };

        const handleOpenPrompt = () => {
            setTourActive(false);
            setShowPrompt(true);
        };

        window.addEventListener(START_SPOTLIGHT_EVENT, handleStartTour);
        window.addEventListener(OPEN_PROMPT_EVENT, handleOpenPrompt);
        return () => {
            window.removeEventListener(START_SPOTLIGHT_EVENT, handleStartTour);
            window.removeEventListener(OPEN_PROMPT_EVENT, handleOpenPrompt);
        };
    }, [location.pathname, navigate]);

    // Điều hướng và cuộn tới phần tử khi chuyển bước
    useEffect(() => {
        if (!tourActive) return undefined;
        const cfg = stepsConfig[currentStep - 1];
        if (!cfg) return undefined;

        // Nếu cần đổi route
        if (cfg.route && location.pathname !== cfg.route) {
            navigate(cfg.route);
        }

        // Tìm và cuộn mượt tới phần tử mục tiêu (có retry nếu DOM chưa kịp render)
        let retries = 0;
        const findAndFocus = () => {
            const el = document.querySelector(cfg.selector);
            if (el) {
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                setTimeout(updateTargetRect, 250);
            } else if (retries < 12) {
                retries += 1;
                retryTimerRef.current = setTimeout(findAndFocus, 180);
            }
        };

        const initialTimer = setTimeout(findAndFocus, 150);

        return () => {
            clearTimeout(initialTimer);
            if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
        };
    }, [tourActive, currentStep, location.pathname, navigate, updateTargetRect]);

    // Bắt sự kiện scroll / resize để cập nhật toạ độ spotlight
    useEffect(() => {
        if (!tourActive) return undefined;
        const handleScrollOrResize = () => {
            updateTargetRect();
        };

        window.addEventListener('resize', handleScrollOrResize);
        window.addEventListener('scroll', handleScrollOrResize, true);
        return () => {
            window.removeEventListener('resize', handleScrollOrResize);
            window.removeEventListener('scroll', handleScrollOrResize, true);
        };
    }, [tourActive, updateTargetRect]);

    // Tự động chuyển bước khi người dùng click vào nút "+ Tạo két mới" ở Bước 1
    useEffect(() => {
        if (!tourActive || currentStep !== 1) return undefined;

        const btn = document.querySelector('[data-tour="new-vault-btn"]');
        if (!btn) return undefined;

        const onTargetClick = () => {
            setCurrentStep(2);
            try {
                localStorage.setItem(ONBOARDING_STEP_KEY, '2');
            } catch {
                // ignore
            }
        };

        btn.addEventListener('click', onTargetClick);
        return () => btn.removeEventListener('click', onTargetClick);
    }, [tourActive, currentStep, targetRect]);

    // Phím tắt ESC để thoát tour
    useEffect(() => {
        if (!tourActive && !showPrompt && !showCongrats) return undefined;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                if (showPrompt) setShowPrompt(false);
                if (tourActive) handleSkipTour();
                if (showCongrats) setShowCongrats(false);
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [tourActive, showPrompt, showCongrats]);

    // Xử lý lựa chọn ở câu hỏi lần đầu
    const handlePromptNewbie = () => {
        setShowPrompt(false);
        setCurrentStep(1);
        setTourActive(true);
        try {
            localStorage.setItem(ONBOARDING_STATUS_KEY, 'tour_active');
            localStorage.setItem(ONBOARDING_STEP_KEY, '1');
        } catch {
            // ignore
        }
        if (location.pathname !== '/owner/vaults') {
            navigate('/owner/vaults');
        }
    };

    const handlePromptExperienced = () => {
        setShowPrompt(false);
        try {
            localStorage.setItem(ONBOARDING_STATUS_KEY, 'experienced');
        } catch {
            // ignore
        }
    };

    // Tiến bước tiếp theo
    const handleNextStep = () => {
        if (currentStep < totalSteps) {
            const next = currentStep + 1;
            setCurrentStep(next);
            try {
                localStorage.setItem(ONBOARDING_STEP_KEY, String(next));
            } catch {
                // ignore
            }
        } else {
            handleFinishTour();
        }
    };

    // Lùi bước trước
    const handlePrevStep = () => {
        if (currentStep > 1) {
            const prev = currentStep - 1;
            setCurrentStep(prev);
            try {
                localStorage.setItem(ONBOARDING_STEP_KEY, String(prev));
            } catch {
                // ignore
            }
        }
    };

    // Bỏ qua tour
    const handleSkipTour = () => {
        setTourActive(false);
        try {
            localStorage.setItem(ONBOARDING_STATUS_KEY, 'skipped');
        } catch {
            // ignore
        }
    };

    // Hoàn tất tour thành công
    const handleFinishTour = () => {
        setTourActive(false);
        try {
            localStorage.setItem(ONBOARDING_STATUS_KEY, 'completed');
            localStorage.removeItem(ONBOARDING_STEP_KEY);
        } catch {
            // ignore
        }
        setShowCongrats(true);
    };

    // Tính toạ độ hiển thị tooltip popover
    const getTooltipStyle = () => {
        if (!targetRect) {
            return {
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
            };
        }

        const tooltipWidth = 380;
        const tooltipHeight = 240;
        const gap = 14;

        let left = targetRect.left;
        if (left + tooltipWidth > window.innerWidth - 20) {
            left = window.innerWidth - tooltipWidth - 20;
        }
        if (left < 20) left = 20;

        const spaceBelow = window.innerHeight - targetRect.bottom;
        const spaceAbove = targetRect.top;

        let top;
        if (spaceBelow >= tooltipHeight + gap) {
            top = targetRect.bottom + gap;
        } else if (spaceAbove >= tooltipHeight + gap) {
            top = targetRect.top - tooltipHeight - gap;
        } else {
            top = window.innerHeight - tooltipHeight - 20;
        }

        top = Math.max(20, Math.min(window.innerHeight - tooltipHeight - 20, top));

        return {
            top: `${top}px`,
            left: `${left}px`,
        };
    };

    return (
        <>
            {/* 1. HỘP THOẠI HỎI NGƯỜI DÙNG LẦN ĐẦU (FIRST-TIME QUESTION DIALOG) */}
            {showPrompt && (
                <div
                    className="fixed inset-0 z-[10000] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="first-time-prompt-title"
                >
                    <div className="relative w-full max-w-lg bg-[#0e1629] border border-white/20 rounded-3xl shadow-2xl overflow-hidden text-white p-6 sm:p-8 animate-in zoom-in-95 duration-200">
                        {/* Viền hào quang chuyển sắc */}
                        <div className="absolute top-0 inset-x-0 h-1.5 bg-gradient-to-r from-amber-400 via-orange-500 to-indigo-500" />

                        <div className="flex items-start justify-between gap-4">
                            <div className="w-13 h-13 rounded-2xl bg-gradient-to-br from-amber-400 to-orange-500 text-slate-950 flex items-center justify-center shadow-lg shadow-amber-400/20 shrink-0">
                                <span className="material-symbols-outlined text-[28px] font-bold">help_outline</span>
                            </div>
                            <button
                                type="button"
                                onClick={handlePromptExperienced}
                                className="w-8 h-8 rounded-full flex items-center justify-center text-white/50 hover:text-white hover:bg-white/10 transition-colors"
                                title={t('common:actions.close', { defaultValue: 'Đóng' })}
                            >
                                <span className="material-symbols-outlined text-[20px]">close</span>
                            </button>
                        </div>

                        <div className="mt-4">
                            <span className="inline-block px-2.5 py-0.5 rounded text-[11px] font-extrabold uppercase tracking-wider bg-amber-400/20 text-amber-300 border border-amber-400/30">
                                {t('firstTimePrompt.badge')}
                            </span>
                            <h3 id="first-time-prompt-title" className="text-xl sm:text-2xl font-bold mt-2 text-white leading-snug">
                                {t('firstTimePrompt.question')}
                            </h3>
                            <p className="text-sm text-white/80 mt-2 leading-relaxed">
                                {t('firstTimePrompt.subtitle')}
                            </p>
                        </div>

                        {/* 2 Nút bấm lựa chọn rõ ràng */}
                        <div className="mt-6 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                            <button
                                type="button"
                                onClick={handlePromptNewbie}
                                className="h-12 px-5 rounded-xl bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 font-bold hover:brightness-110 shadow-lg shadow-amber-400/20 flex items-center justify-center gap-2 transition-all"
                            >
                                <span className="material-symbols-outlined text-[20px]">rocket_launch</span>
                                <span>{t('firstTimePrompt.newbieAction')}</span>
                            </button>
                            <button
                                type="button"
                                onClick={handlePromptExperienced}
                                className="h-12 px-5 rounded-xl border border-white/20 text-white/80 hover:bg-white/10 font-semibold flex items-center justify-center transition-colors"
                            >
                                <span>{t('firstTimePrompt.experiencedAction')}</span>
                            </button>
                        </div>

                        <p className="text-[11px] text-white/50 mt-4 flex items-center gap-1.5">
                            <span className="material-symbols-outlined text-[15px] text-amber-400">info</span>
                            <span>{t('firstTimePrompt.helper')}</span>
                        </p>
                    </div>
                </div>
            )}

            {/* 2. SPOTLIGHT INTERACTIVE GUIDED TOUR */}
            {tourActive && (
                <>
                    {/* 4 Mảnh bóng tối bao quanh (Masking overlay with transparent cutout) */}
                    {targetRect ? (
                        <>
                            {/* Khối phía trên */}
                            <div
                                className="fixed z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all duration-300 pointer-events-auto"
                                style={{
                                    top: 0,
                                    left: 0,
                                    right: 0,
                                    height: `${targetRect.top}px`,
                                }}
                            />
                            {/* Khối phía dưới */}
                            <div
                                className="fixed z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all duration-300 pointer-events-auto"
                                style={{
                                    top: `${targetRect.bottom}px`,
                                    left: 0,
                                    right: 0,
                                    bottom: 0,
                                }}
                            />
                            {/* Khối bên trái */}
                            <div
                                className="fixed z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all duration-300 pointer-events-auto"
                                style={{
                                    top: `${targetRect.top}px`,
                                    left: 0,
                                    width: `${targetRect.left}px`,
                                    height: `${targetRect.height}px`,
                                }}
                            />
                            {/* Khối bên phải */}
                            <div
                                className="fixed z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all duration-300 pointer-events-auto"
                                style={{
                                    top: `${targetRect.top}px`,
                                    left: `${targetRect.right}px`,
                                    right: 0,
                                    height: `${targetRect.height}px`,
                                }}
                            />

                            {/* Vòng viền phát sáng bao quanh nút/khối được highlight */}
                            <div
                                className="fixed z-[9992] pointer-events-none rounded-2xl border-2 border-amber-400 ring-4 ring-amber-400/35 shadow-[0_0_30px_rgba(251,191,36,0.5)] transition-all duration-300 animate-pulse"
                                style={{
                                    top: `${targetRect.top}px`,
                                    left: `${targetRect.left}px`,
                                    width: `${targetRect.width}px`,
                                    height: `${targetRect.height}px`,
                                }}
                            />
                        </>
                    ) : (
                        /* Đang tải hoặc đang cuộn: phủ mờ toàn màn hình */
                        <div className="fixed inset-0 z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all pointer-events-auto" />
                    )}

                    {/* Khung Popover Tooltip hướng dẫn tại vị trí mục tiêu */}
                    <div
                        className="fixed z-[9999] w-[360px] sm:w-[380px] bg-[#0e1629] border border-amber-400/50 rounded-2xl p-5 shadow-2xl text-white pointer-events-auto animate-in zoom-in-95 duration-200"
                        style={getTooltipStyle()}
                    >
                        {/* Header của Tooltip */}
                        <div className="flex items-center justify-between gap-2 border-b border-white/10 pb-3">
                            <div className="flex items-center gap-2">
                                <span className="px-2.5 py-0.5 rounded-full text-[10px] font-extrabold uppercase bg-amber-400 text-slate-950">
                                    {t('tour.stepBadge', { current: currentStep, total: totalSteps })}
                                </span>
                            </div>
                            <button
                                type="button"
                                onClick={handleSkipTour}
                                className="text-xs text-white/60 hover:text-white transition-colors"
                                title={t('tour.skipTour')}
                            >
                                {t('tour.skipTour')}
                            </button>
                        </div>

                        {/* Tiêu đề & Nội dung hướng dẫn */}
                        <div className="mt-3 space-y-2">
                            <h4 className="text-base font-bold text-white flex items-center gap-1.5">
                                <span className="material-symbols-outlined text-[18px] text-amber-400">arrow_forward</span>
                                <span>{currentConfig.title}</span>
                            </h4>
                            <p className="text-xs sm:text-[13px] text-white/85 leading-relaxed">
                                {currentConfig.instruction}
                            </p>
                            {currentConfig.tip && (
                                <div className="p-2.5 rounded-xl bg-amber-400/10 border border-amber-400/25 text-[11px] sm:text-xs text-amber-200 flex items-start gap-1.5">
                                    <span className="material-symbols-outlined text-[16px] text-amber-400 shrink-0">lightbulb</span>
                                    <span>{currentConfig.tip}</span>
                                </div>
                            )}
                        </div>

                        {/* Thanh điều khiển bước */}
                        <div className="mt-4 pt-3 border-t border-white/10 flex items-center justify-between gap-2">
                            {currentStep > 1 ? (
                                <button
                                    type="button"
                                    onClick={handlePrevStep}
                                    className="h-8 px-3 rounded-lg border border-white/20 text-xs font-semibold text-white/75 hover:bg-white/10 transition-colors"
                                >
                                    {t('tour.prevBtn')}
                                </button>
                            ) : (
                                <span />
                            )}
                            <button
                                type="button"
                                onClick={handleNextStep}
                                className={`h-8 px-4 rounded-lg text-xs font-bold flex items-center gap-1 transition-all ${
                                    currentConfig.isLast
                                        ? 'bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 hover:brightness-110 shadow-md shadow-amber-400/20'
                                        : 'bg-white text-slate-950 hover:bg-white/90 shadow'
                                }`}
                            >
                                <span>{currentConfig.nextLabel || t('tour.nextBtn')}</span>
                                <span className="material-symbols-outlined text-[16px]">
                                    {currentConfig.isLast ? 'check_circle' : 'arrow_forward'}
                                </span>
                            </button>
                        </div>
                    </div>
                </>
            )}

            {/* 3. MODAL CHÚC MỪNG HOÀN TẤT TOUR (CONGRATS MODAL) */}
            {showCongrats && (
                <div
                    className="fixed inset-0 z-[10000] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200"
                    role="dialog"
                    aria-modal="true"
                >
                    <div className="relative w-full max-w-md bg-[#0e1629] border border-amber-400/40 rounded-3xl shadow-2xl overflow-hidden text-white p-6 sm:p-7 text-center">
                        <div className="absolute top-0 inset-x-0 h-1.5 bg-gradient-to-r from-emerald-400 via-amber-400 to-orange-500" />
                        <div className="w-16 h-16 mx-auto rounded-3xl bg-gradient-to-br from-amber-400 to-emerald-500 text-slate-950 flex items-center justify-center shadow-lg shadow-amber-400/30">
                            <span className="material-symbols-outlined text-[34px]">verified</span>
                        </div>
                        <h3 className="text-xl font-bold mt-4 text-white">
                            {t('tour.congrats.title')}
                        </h3>
                        <p className="text-sm font-medium text-amber-300 mt-1">
                            {t('tour.congrats.subtitle')}
                        </p>
                        <p className="text-xs sm:text-sm text-white/75 mt-3 leading-relaxed">
                            {t('tour.congrats.text')}
                        </p>
                        <button
                            type="button"
                            onClick={() => setShowCongrats(false)}
                            className="mt-6 w-full h-11 rounded-xl bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 font-bold hover:brightness-110 shadow-lg shadow-amber-400/20 transition-all flex items-center justify-center gap-2"
                        >
                            <span>{t('tour.congrats.action')}</span>
                            <span className="material-symbols-outlined text-[18px]">rocket_launch</span>
                        </button>
                    </div>
                </div>
            )}
        </>
    );
}
