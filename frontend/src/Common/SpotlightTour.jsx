import React, { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';
import { FEATURE_TOURS, getFeatureTourForPath } from './featureToursConfig.js';

export const ONBOARDING_STATUS_KEY = 'lv_onboarding_status';
export const ONBOARDING_STEP_KEY = 'lv_tour_step';
export const START_SPOTLIGHT_EVENT = 'lv_start_spotlight_tour';
export const OPEN_PROMPT_EVENT = 'lv_open_first_time_prompt';
export const START_FEATURE_TOUR_EVENT = 'lv_start_feature_tour';

/**
 * Kích hoạt trực tiếp Tour tạo két sắt 6 bước
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
 * Kích hoạt hoặc xem lại một feature tour cụ thể (vd: 'owner_activity', 'owner_dms')
 */
export function startFeatureTour(featureId) {
    window.dispatchEvent(new CustomEvent(START_FEATURE_TOUR_EVENT, { detail: { featureId } }));
}

/**
 * Đặt lại toàn bộ trạng thái để test hoặc xem lại từ đầu
 */
export function resetAllTours() {
    try {
        localStorage.removeItem(ONBOARDING_STATUS_KEY);
        localStorage.removeItem(ONBOARDING_STEP_KEY);
        FEATURE_TOURS.forEach((ft) => {
            localStorage.removeItem(`lv_seen_feature_${ft.id}`);
        });
    } catch {
        // ignore
    }
}

export default function SpotlightTour() {
    const { t } = useTranslation(['onboarding', 'common']);
    const location = useLocation();
    const navigate = useNavigate();

    const [showPrompt, setShowPrompt] = useState(false);
    // activeTour: null | { type: 'vault_creation' | 'feature', config: object }
    const [activeTour, setActiveTour] = useState(null);
    const [currentStep, setCurrentStep] = useState(1);
    const [targetRect, setTargetRect] = useState(null);
    const [showCongrats, setShowCongrats] = useState(false);
    const retryTimerRef = useRef(null);
    const tooltipRef = useRef(null);

    // Cấu hình 6 bước khởi tạo két sắt (Deep Happy Path Tour)
    const vaultTourConfig = useMemo(() => ({
        id: 'vault_creation',
        badge: t('welcomeTitle'),
        steps: [
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
                placement: 'top',
                title: t('tour.steps.step6.title'),
                instruction: t('tour.steps.step6.instruction'),
                tip: t('tour.steps.step6.tip'),
                nextLabel: t('tour.finishBtn'),
                isLast: true,
            },
        ],
    }), [t]);

    // Danh sách các bước của tour đang hoạt động
    const currentStepsList = activeTour?.type === 'vault_creation'
        ? vaultTourConfig.steps
        : (activeTour?.config?.steps || []);

    const totalSteps = currentStepsList.length;
    const currentStepItem = currentStepsList[currentStep - 1] || currentStepsList[0];

    // Cập nhật toạ độ bounding box của phần tử mục tiêu
    const updateTargetRect = useCallback(() => {
        if (!activeTour) return;
        const steps = activeTour.type === 'vault_creation' ? vaultTourConfig.steps : (activeTour.config?.steps || []);
        const cfg = steps[currentStep - 1];
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
    }, [activeTour, currentStep, vaultTourConfig]);

    // 1. Kiểm tra hiển thị câu hỏi lần đầu khi người dùng đăng nhập
    useEffect(() => {
        const user = getUser();
        if (!user) return undefined;

        // Bỏ qua nếu đang ở trang auth công khai hoặc trang admin
        const publicPaths = ['/', '/login', '/register', '/forgot-password', '/reset-password', '/verify-email'];
        if (publicPaths.includes(location.pathname) || location.pathname.startsWith('/admin')) return undefined;

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
                // Chỉ kích hoạt lại tour tạo két nếu đang ở đúng trang tạo két (/owner/vaults hoặc /owner/vaults/new)
                if (location.pathname === '/owner/vaults' || location.pathname === '/owner/vaults/new') {
                    const savedStep = parseInt(localStorage.getItem(ONBOARDING_STEP_KEY) || '1', 10);
                    setCurrentStep(savedStep >= 1 && savedStep <= 6 ? savedStep : 1);
                    setActiveTour({ type: 'vault_creation', config: vaultTourConfig });
                }
            }
        } catch {
            // ignore
        }
        return undefined;
    }, [location.pathname]);

    // 2. Tự động kích hoạt Feature Tour khi người dùng mở một tính năng mới lần đầu
    useEffect(() => {
        const user = getUser();
        if (!user) return undefined;
        // Bỏ qua nếu admin hoặc đang chạy prompt / tour khác
        if (location.pathname.startsWith('/admin') || showPrompt || activeTour || showCongrats) return undefined;

        const feature = getFeatureTourForPath(location.pathname);
        if (!feature) return undefined;

        try {
            const seen = localStorage.getItem(`lv_seen_feature_${feature.id}`);
            if (!seen) {
                const timer = setTimeout(() => {
                    setCurrentStep(1);
                    setActiveTour({ type: 'feature', config: feature });
                }, 450);
                return () => clearTimeout(timer);
            }
        } catch {
            // ignore
        }
        return undefined;
    }, [location.pathname, showPrompt, activeTour, showCongrats]);

    // 3. Lắng nghe các sự kiện toàn cục
    useEffect(() => {
        const handleStartVaultTour = () => {
            setShowPrompt(false);
            setShowCongrats(false);
            setCurrentStep(1);
            setActiveTour({ type: 'vault_creation', config: vaultTourConfig });
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
            setActiveTour(null);
            setShowPrompt(true);
        };

        const handleStartFeatureTour = (e) => {
            const featureId = e.detail?.featureId;
            const feature = FEATURE_TOURS.find((f) => f.id === featureId) || getFeatureTourForPath(location.pathname);
            if (feature) {
                setShowPrompt(false);
                setShowCongrats(false);
                setCurrentStep(1);
                setActiveTour({ type: 'feature', config: feature });
            }
        };

        window.addEventListener(START_SPOTLIGHT_EVENT, handleStartVaultTour);
        window.addEventListener(OPEN_PROMPT_EVENT, handleOpenPrompt);
        window.addEventListener(START_FEATURE_TOUR_EVENT, handleStartFeatureTour);
        return () => {
            window.removeEventListener(START_SPOTLIGHT_EVENT, handleStartVaultTour);
            window.removeEventListener(OPEN_PROMPT_EVENT, handleOpenPrompt);
            window.removeEventListener(START_FEATURE_TOUR_EVENT, handleStartFeatureTour);
        };
    }, [location.pathname, navigate, vaultTourConfig]);

    // 4. Cuộn và canh nét phần tử khi chuyển bước
    useEffect(() => {
        if (!activeTour) return undefined;
        const steps = activeTour.type === 'vault_creation' ? vaultTourConfig.steps : (activeTour.config?.steps || []);
        const cfg = steps[currentStep - 1];
        if (!cfg) return undefined;

        // Nếu cần đổi route (đối với vault creation tour)
        if (cfg.route && location.pathname !== cfg.route) {
            navigate(cfg.route);
        }

        let retries = 0;
        let cancelled = false;
        const findAndFocus = () => {
            if (cancelled) return;
            const el = document.querySelector(cfg.selector);
            if (el) {
                const rect = el.getBoundingClientRect();
                const isMobile = window.innerWidth < 640;
                const estTooltipHeight = tooltipRef.current?.offsetHeight || 220;
                const gap = 16;
                const headerOffset = 75;

                if (isMobile) {
                    if (cfg.placement === 'top') {
                        // Nút submit ở cuối: cuộn lên nửa trên màn hình để không bị bottom sheet che
                        const targetY = window.scrollY + rect.top - 120;
                        window.scrollTo({ top: Math.max(0, targetY), behavior: 'smooth' });
                    } else {
                        // Trên mobile: cuộn đỉnh khối xuống dưới header (~64px) để toàn bộ phần đầu khối lộ rõ phía trên bottom sheet
                        const targetY = window.scrollY + rect.top - 64;
                        window.scrollTo({ top: Math.max(0, targetY), behavior: 'smooth' });
                    }
                } else if (cfg.placement === 'top') {
                    // Cần khoảng trống PHÍA TRÊN khối cho tooltip: cuộn sao cho khối nằm ở nửa dưới màn hình
                    const targetY = window.scrollY + rect.top - (window.innerHeight - 120);
                    window.scrollTo({ top: Math.max(0, targetY), behavior: 'smooth' });
                } else {
                    const neededBelow = rect.height + estTooltipHeight + gap + headerOffset;
                    if (neededBelow <= window.innerHeight) {
                        // Đủ chỗ đặt tooltip ở phía dưới: cuộn đỉnh khối lên sát header (~75px)
                        const targetY = window.scrollY + rect.top - 75;
                        window.scrollTo({ top: Math.max(0, targetY), behavior: 'smooth' });
                    } else {
                        // Không đủ chỗ ở phía dưới (khối quá cao): cuộn sao cho đầu khối nằm ngay dưới tooltip (đặt tooltip ở trên)
                        const targetY = window.scrollY + rect.top - (headerOffset + estTooltipHeight + gap);
                        window.scrollTo({ top: Math.max(0, targetY), behavior: 'smooth' });
                    }
                }

                const start = performance.now();
                const pollScroll = () => {
                    if (cancelled) return;
                    updateTargetRect();
                    if (performance.now() - start < 800) {
                        requestAnimationFrame(pollScroll);
                    }
                };
                requestAnimationFrame(pollScroll);
            } else if (retries < 15) {
                retries += 1;
                retryTimerRef.current = setTimeout(findAndFocus, 150);
            }
        };

        findAndFocus();
        return () => {
            cancelled = true;
            if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
        };
    }, [activeTour, currentStep, location.pathname, navigate, updateTargetRect, vaultTourConfig.steps]);

    // 5. Scroll & resize listener
    useEffect(() => {
        if (!activeTour) return undefined;
        const handleScrollOrResize = () => updateTargetRect();
        window.addEventListener('resize', handleScrollOrResize);
        window.addEventListener('scroll', handleScrollOrResize, true);
        return () => {
            window.removeEventListener('resize', handleScrollOrResize);
            window.removeEventListener('scroll', handleScrollOrResize, true);
        };
    }, [activeTour, updateTargetRect]);




    // 6. Click trực tiếp vào nút Tạo két ở Bước 1
    useEffect(() => {
        if (!activeTour || activeTour.type !== 'vault_creation' || currentStep !== 1) return undefined;
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
    }, [activeTour, currentStep]);

    // 7. ESC handler
    useEffect(() => {
        if (!activeTour && !showPrompt && !showCongrats) return undefined;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                if (showPrompt) setShowPrompt(false);
                if (activeTour) handleDismissActiveTour();
                if (showCongrats) setShowCongrats(false);
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [activeTour, showPrompt, showCongrats]);

    // Xử lý lựa chọn câu hỏi lần đầu
    const handlePromptNewbie = () => {
        setShowPrompt(false);
        setCurrentStep(1);
        setActiveTour({ type: 'vault_creation', config: vaultTourConfig });
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

    // Đóng / Bỏ qua tour đang chạy
    const handleDismissActiveTour = () => {
        if (!activeTour) return;
        if (activeTour.type === 'feature') {
            try {
                localStorage.setItem(`lv_seen_feature_${activeTour.config.id}`, 'true');
            } catch {
                // ignore
            }
        } else {
            try {
                localStorage.setItem(ONBOARDING_STATUS_KEY, 'skipped');
            } catch {
                // ignore
            }
        }
        setActiveTour(null);
    };

    // Chuyển bước kế tiếp
    const handleNextStep = () => {
        if (currentStep < totalSteps) {
            const next = currentStep + 1;
            setCurrentStep(next);
            if (activeTour?.type === 'vault_creation') {
                try {
                    localStorage.setItem(ONBOARDING_STEP_KEY, String(next));
                } catch {
                    // ignore
                }
            }
        } else {
            handleCompleteActiveTour();
        }
    };

    // Lùi bước trước
    const handlePrevStep = () => {
        if (currentStep > 1) {
            const prev = currentStep - 1;
            setCurrentStep(prev);
            if (activeTour?.type === 'vault_creation') {
                try {
                    localStorage.setItem(ONBOARDING_STEP_KEY, String(prev));
                } catch {
                    // ignore
                }
            }
        }
    };

    // Hoàn thành tour
    const handleCompleteActiveTour = () => {
        if (!activeTour) return;
        if (activeTour.type === 'feature') {
            try {
                localStorage.setItem(`lv_seen_feature_${activeTour.config.id}`, 'true');
            } catch {
                // ignore
            }
            setActiveTour(null);
        } else {
            try {
                localStorage.setItem(ONBOARDING_STATUS_KEY, 'completed');
                localStorage.removeItem(ONBOARDING_STEP_KEY);
            } catch {
                // ignore
            }
            setActiveTour(null);
            setShowCongrats(true);
        }
    };

    // Định vị Popover Tooltip (không bao giờ che phần tử mục tiêu, ghim đáy trên mobile)
    const getTooltipStyle = () => {
        if (!targetRect) {
            return {
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
            };
        }

        // Trên điện thoại (màn hình < 640px): ghim dạng bottom sheet ở đáy màn hình
        if (window.innerWidth < 640) {
            return {
                bottom: '12px',
                left: '12px',
                right: '12px',
            };
        }

        const tooltipWidth = tooltipRef.current?.offsetWidth || 380;
        const tooltipHeight = tooltipRef.current?.offsetHeight || 220;
        const gap = 12;
        const margin = 16;

        const spaceBelow = window.innerHeight - targetRect.bottom;
        const spaceAbove = targetRect.top;
        const spaceRight = window.innerWidth - targetRect.right;
        const spaceLeft = targetRect.left;

        const placement = currentStepItem?.placement;
        let top;
        let left;

        if (placement === 'top') {
            if (spaceAbove >= tooltipHeight + gap) {
                top = targetRect.top - tooltipHeight - gap;
            } else {
                top = targetRect.bottom + gap;
            }
        } else {
            if (spaceBelow >= tooltipHeight + gap) {
                top = targetRect.bottom + gap;
            } else if (spaceAbove >= tooltipHeight + gap) {
                top = targetRect.top - tooltipHeight - gap;
            } else if (spaceRight >= tooltipWidth + gap) {
                top = Math.max(margin, targetRect.top);
                left = targetRect.right + gap;
            } else if (spaceLeft >= tooltipWidth + gap) {
                top = Math.max(margin, targetRect.top);
                left = targetRect.left - tooltipWidth - gap;
            } else if (spaceBelow >= spaceAbove) {
                top = targetRect.bottom + gap;
            } else {
                top = targetRect.top - tooltipHeight - gap;
            }
        }

        // Đảm bảo tuyệt đối không đè lên phần tử mục tiêu
        if (top !== undefined && top < targetRect.bottom && top + tooltipHeight > targetRect.top) {
            if (targetRect.top >= tooltipHeight + gap + margin) {
                top = targetRect.top - tooltipHeight - gap;
            } else {
                top = targetRect.bottom + gap;
            }
        }

        if (left === undefined) {
            if (targetRect.right > window.innerWidth / 2) {
                left = targetRect.right - tooltipWidth;
            } else {
                left = targetRect.left;
            }
        }

        left = Math.max(margin, Math.min(window.innerWidth - tooltipWidth - margin, left));

        return {
            top: top + 'px',
            left: left + 'px',
        };
    };

    // Trích xuất tiêu đề & diễn giải cho bước hiện tại
    const isFeatureTour = activeTour?.type === 'feature';
    const stepTitle = isFeatureTour
        ? t(currentStepItem?.titleKey, { defaultValue: currentStepItem?.title })
        : currentStepItem?.title;
    const stepInstruction = isFeatureTour
        ? t(currentStepItem?.instructionKey, { defaultValue: currentStepItem?.instruction })
        : currentStepItem?.instruction;
    const stepTip = isFeatureTour
        ? t(currentStepItem?.tipKey, { defaultValue: currentStepItem?.tip })
        : currentStepItem?.tip;
    const isLastStep = currentStep === totalSteps;

    return (
        <>
            {/* 1. HỘP THOẠI HỎI NGƯỜI DÙNG LẦN ĐẦU */}
            {showPrompt && (
                <div
                    className="fixed inset-0 z-[10000] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="first-time-prompt-title"
                >
                    <div className="relative w-full max-w-lg bg-[#0e1629] border border-white/20 rounded-3xl shadow-2xl overflow-hidden text-white p-6 sm:p-8 animate-in zoom-in-95 duration-200">
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

            {/* 2. SPOTLIGHT OVERLAY & POPOVER TOOLTIP */}
            {activeTour && (
                <>
                    {targetRect ? (
                        <>
                            {/* 4 Mảnh bóng tối bao quanh */}
                            <div
                                className="fixed z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all duration-300 pointer-events-auto"
                                style={{ top: 0, left: 0, right: 0, height: `${targetRect.top}px` }}
                            />
                            <div
                                className="fixed z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all duration-300 pointer-events-auto"
                                style={{ top: `${targetRect.bottom}px`, left: 0, right: 0, bottom: 0 }}
                            />
                            <div
                                className="fixed z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all duration-300 pointer-events-auto"
                                style={{ top: `${targetRect.top}px`, left: 0, width: `${targetRect.left}px`, height: `${targetRect.height}px` }}
                            />
                            <div
                                className="fixed z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all duration-300 pointer-events-auto"
                                style={{ top: `${targetRect.top}px`, left: `${targetRect.right}px`, right: 0, height: `${targetRect.height}px` }}
                            />

                            {/* Vòng viền phát sáng */}
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
                        <div className="fixed inset-0 z-[9990] bg-[#070b14]/85 backdrop-blur-[2px] transition-all pointer-events-auto" />
                    )}

                    {/* Popover Tooltip */}
                    <div
                        ref={tooltipRef}
                        id="spotlight-tour-tooltip"
                        className="fixed z-[9999] w-[calc(100vw-24px)] sm:w-[380px] max-h-[calc(100vh-32px)] overflow-y-auto bg-[#0e1629] border border-amber-400/50 rounded-2xl p-4 sm:p-5 shadow-2xl text-white pointer-events-auto animate-in zoom-in-95 duration-200"
                        style={getTooltipStyle()}
                    >
                        <div className="flex items-center justify-between gap-2 border-b border-white/10 pb-3">
                            <div className="flex items-center gap-2">
                                <span className="px-2.5 py-0.5 rounded-full text-[10px] font-extrabold uppercase bg-amber-400 text-slate-950">
                                    {t('tour.stepBadge', { current: currentStep, total: totalSteps })}
                                </span>
                                {isFeatureTour && (
                                    <span className="text-[10px] font-bold text-amber-300/80 uppercase tracking-wider truncate max-w-[170px]">
                                        {t(activeTour.config.badgeKey, { defaultValue: 'HƯỚNG DẪN NHANH' })}
                                    </span>
                                )}
                            </div>
                            <button
                                type="button"
                                onClick={handleDismissActiveTour}
                                className="text-xs text-white/60 hover:text-white transition-colors"
                                title={t('tour.skipTour')}
                            >
                                {t('tour.skipTour')}
                            </button>
                        </div>

                        <div className="mt-3 space-y-2">
                            <h4 className="text-base font-bold text-white flex items-center gap-1.5">
                                <span className="material-symbols-outlined text-[18px] text-amber-400">arrow_forward</span>
                                <span>{stepTitle}</span>
                            </h4>
                            <p className="text-xs sm:text-[13px] text-white/85 leading-relaxed">
                                {stepInstruction}
                            </p>
                            {stepTip && (
                                <div className="p-2.5 rounded-xl bg-amber-400/10 border border-amber-400/25 text-[11px] sm:text-xs text-amber-200 flex items-start gap-1.5">
                                    <span className="material-symbols-outlined text-[16px] text-amber-400 shrink-0">lightbulb</span>
                                    <span>{stepTip}</span>
                                </div>
                            )}
                        </div>

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
                                    isLastStep
                                        ? 'bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 hover:brightness-110 shadow-md shadow-amber-400/20'
                                        : 'bg-white text-slate-950 hover:bg-white/90 shadow'
                                }`}
                            >
                                <span>
                                    {isLastStep
                                        ? (isFeatureTour ? t('featureTours.gotIt', { defaultValue: 'Đã hiểu' }) : t('tour.finishBtn'))
                                        : (currentStepItem?.nextLabel || t('tour.nextBtn'))}
                                </span>
                                <span className="material-symbols-outlined text-[16px]">
                                    {isLastStep ? 'check_circle' : 'arrow_forward'}
                                </span>
                            </button>
                        </div>
                    </div>
                </>
            )}

            {/* 3. MODAL CHÚC MỪNG HOÀN TẤT (CHO TOUR KHỞI TẠO KÉT) */}
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
                        <h3 id="congrats-modal-title" className="text-xl font-bold mt-4 text-white">
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
