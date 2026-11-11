import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CountingNumber } from '@animate-ui/react/primitives/texts/counting-number';
import { AutoHeight } from '@animate-ui/react/primitives/effects/auto-height';
import { ReducedMotionConfig, useMotionTokens } from '@animate-ui/react/utils/motion';
import { ownerApi, PROTECTION_CHANGED } from '../api/ownerApi.js';
import { Icon } from './ownerUi.jsx';

// Nhãn hạng lấy từ readiness.json: grade.<GRADE>
const GRADE_META = {
    EXCELLENT: {
        badgeCls: 'bg-[#8ee9b0]/20 text-[#8ee9b0]',
        strokeCls: 'text-[#8ee9b0]',
    },
    GOOD: {
        badgeCls: 'bg-[#b9c9ff]/20 text-[#b9c9ff]',
        strokeCls: 'text-[#8fa8ff]',
    },
    FAIR: {
        badgeCls: 'bg-[#ffd27a]/20 text-[#ffd27a]',
        strokeCls: 'text-[#ffd27a]',
    },
    AT_RISK: {
        badgeCls: 'bg-[#ffb4ab]/20 text-[#ffb4ab]',
        strokeCls: 'text-[#ffb4ab]',
    },
};

export default function EstateReadinessCard() {
    const { t } = useTranslation('readiness');
    const { t: tv } = useTranslation('verification');
    const { spring } = useMotionTokens();
    const [readiness, setReadiness] = useState(null);
    const [expanded, setExpanded] = useState(false);

    useEffect(() => {
        const loadReadiness = () => {
            ownerApi
                .getReadiness()
                .then((data) => {
                    if (data) setReadiness(data);
                })
                .catch(() => { });
        };
        loadReadiness();
        window.addEventListener(PROTECTION_CHANGED, loadReadiness);
        return () => window.removeEventListener(PROTECTION_CHANGED, loadReadiness);
    }, []);

    if (!readiness) return null;

    const score = readiness.score ?? 0;
    const maxScore = readiness.maxScore || 100;
    const grade = GRADE_META[readiness.grade] ? readiness.grade : 'FAIR';
    const meta = GRADE_META[grade];
    const radius = 36;
    const circumference = 2 * Math.PI * radius;
    const dashOffset = circumference - (Math.min(maxScore, Math.max(0, score)) / maxScore) * circumference;

    const items = readiness.items || [];
    const pendingItems = items.filter((item) => !item.done);
    const visibleItems = expanded ? items : pendingItems.slice(0, 3);

    return (
        <ReducedMotionConfig remount>
            <div className="mb-6 rounded-3xl p-7 text-white lv-page-hero lv-rise">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                    <div className="flex items-center gap-5">
                        <div className="relative w-24 h-24 shrink-0 flex items-center justify-center">
                            <svg className="w-24 h-24 -rotate-90" viewBox="0 0 88 88">
                                <circle
                                    cx="44"
                                    cy="44"
                                    r={radius}
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="8"
                                    className="text-white/15"
                                />
                                <circle
                                    cx="44"
                                    cy="44"
                                    r={radius}
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="8"
                                    strokeDasharray={circumference}
                                    strokeDashoffset={dashOffset}
                                    strokeLinecap="round"
                                    className={`${meta.strokeCls} transition-[stroke-dashoffset,color] duration-500`}
                                />
                            </svg>
                            <div className="absolute inset-0 flex flex-col items-center justify-center">
                                <CountingNumber
                                    number={score}
                                    transition={spring.number}
                                    className="font-headline-sm text-headline-sm leading-none tabular-nums"
                                />
                                <span className="text-[11px] text-white/60">{t('outOf', { max: maxScore })}</span>
                            </div>
                        </div>

                        <div>
                            <div className="flex flex-wrap items-center gap-2.5">
                                <h2 className="font-title-lg text-title-lg">{t('title')}</h2>
                                <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${meta.badgeCls}`}>
                                    {t(`grade.${grade}`)}
                                </span>
                            </div>
                            <p className="mt-1 text-sm text-white/70">
                                {t('progress', { completed: readiness.completedItems, count: readiness.totalItems })}{' '}
                                {pendingItems.length > 0 ? t('pendingHint') : t('doneHint')}
                            </p>
                        </div>
                    </div>

                    <button
                        type="button"
                        onClick={() => setExpanded((prev) => !prev)}
                        className="self-start md:self-center shrink-0 inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-white/30 text-xs font-label-lg text-white hover:bg-white/10 transition-colors"
                    >
                        <Icon name={expanded ? 'expand_less' : 'checklist'} className="text-[16px]" />
                        {expanded ? t('showFewer') : t('viewAll', { count: items.length })}
                    </button>
                </div>

                {(readiness.assetsNeedReverification > 0 || readiness.assetsExpiringSoon > 0) && (
                    <div className="mt-5 pt-4 border-t border-white/15 flex flex-wrap items-center justify-between gap-3">
                        <div className="flex items-start gap-2.5 text-sm text-white">
                            <Icon name="update" className="text-[20px] text-[#ffd27a]" />
                            <div>
                                {readiness.assetsNeedReverification > 0 && (
                                    <p className="font-semibold">{tv('readiness.reverify', { n: readiness.assetsNeedReverification })}</p>
                                )}
                                {readiness.assetsExpiringSoon > 0 && (
                                    <p className="text-white/70">{tv('readiness.expiringSoon', { n: readiness.assetsExpiringSoon })}</p>
                                )}
                            </div>
                        </div>
                        <Link to="/owner/vaults" className="inline-flex items-center gap-1 text-xs font-semibold text-[#b9c9ff] hover:underline">
                            {tv('readiness.action')}
                            <Icon name="arrow_forward" className="text-[14px]" />
                        </Link>
                    </div>
                )}

                <AutoHeight transition={spring.height}>
                    {visibleItems.length > 0 && (
                        <div className="pt-5">
                            <div className="pt-4 border-t border-white/15 grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
                                {visibleItems.map((item) => (
                                    <div
                                        key={item.key}
                                        className={`p-3.5 rounded-xl border flex flex-col justify-between gap-2 transition-colors ${
                                            item.done
                                                ? 'bg-white/5 border-white/10'
                                                : 'bg-white/10 border-white/20'
                                        }`}
                                    >
                                        <div className="flex items-start gap-2.5">
                                            <Icon
                                                name={item.done ? 'check_circle' : 'radio_button_unchecked'}
                                                className={`text-[18px] mt-0.5 ${
                                                    item.done ? 'text-[#8ee9b0]' : 'text-white/60'
                                                }`}
                                            />
                                            <div className="min-w-0 flex-1">
                                                <p
                                                    className={`text-xs font-medium leading-snug ${
                                                        item.done ? 'text-white/50 line-through' : 'text-white'
                                                    }`}
                                                >
                                                    {t(item.titleKey, { defaultValue: item.title })}
                                                </p>
                                                {/* Mục 0 điểm (vd. xác minh danh tính) chỉ để nhắc, không hiện "+0 điểm" */}
                                                {item.points > 0 && (
                                                    <span className="inline-block mt-1 text-[11px] font-semibold text-[#b9c9ff]">
                                                        {t('points', { count: item.points })}
                                                    </span>
                                                )}
                                            </div>
                                        </div>

                                        {!item.done && item.fixUrl && (
                                            <div className="flex justify-end">
                                                <Link
                                                    to={item.fixUrl}
                                                    className="inline-flex items-center gap-1 text-xs font-semibold text-[#b9c9ff] hover:underline"
                                                >
                                                    {t('fixNow')}
                                                    <Icon name="arrow_forward" className="text-[14px]" />
                                                </Link>
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </AutoHeight>
            </div>
        </ReducedMotionConfig>
    );
}
