import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ownerApi, PROTECTION_CHANGED } from '../api/ownerApi.js';
import { Icon } from './ownerUi.jsx';

// Nhãn hạng lấy từ readiness.json: grade.<GRADE>
const GRADE_META = {
    EXCELLENT: {
        badgeCls: 'bg-success-container text-on-success-container',
        strokeCls: 'text-primary',
    },
    GOOD: {
        badgeCls: 'bg-secondary-fixed text-on-secondary-fixed',
        strokeCls: 'text-primary',
    },
    FAIR: {
        badgeCls: 'bg-warning-container text-on-warning-container',
        strokeCls: 'text-amber-600',
    },
    AT_RISK: {
        badgeCls: 'bg-error-container text-on-error-container',
        strokeCls: 'text-error',
    },
};

export default function EstateReadinessCard() {
    const { t } = useTranslation('readiness');
    const [readiness, setReadiness] = useState(null);
    const [expanded, setExpanded] = useState(false);

    useEffect(() => {
        const loadReadiness = () => {
            ownerApi
                .getReadiness()
                .then((data) => {
                    if (data) setReadiness(data);
                })
                .catch(() => {});
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
        <div className="mb-6 bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
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
                                className="text-surface-container-high"
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
                                className={`${meta.strokeCls} transition-all duration-500`}
                            />
                        </svg>
                        <div className="absolute inset-0 flex flex-col items-center justify-center">
                            <span className="font-headline-sm text-headline-sm leading-none">{score}</span>
                            <span className="text-[11px] text-on-surface-variant">{t('outOf', { max: maxScore })}</span>
                        </div>
                    </div>

                    <div>
                        <div className="flex flex-wrap items-center gap-2.5">
                            <h2 className="font-title-lg text-title-lg">{t('title')}</h2>
                            <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${meta.badgeCls}`}>
                                {t(`grade.${grade}`)}
                            </span>
                        </div>
                        <p className="mt-1 text-sm text-on-surface-variant">
                            {t('progress', { completed: readiness.completedItems, count: readiness.totalItems })}{' '}
                            {pendingItems.length > 0 ? t('pendingHint') : t('doneHint')}
                        </p>
                    </div>
                </div>

                <button
                    type="button"
                    onClick={() => setExpanded((prev) => !prev)}
                    className="self-start md:self-center shrink-0 inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-outline-variant text-xs font-label-lg text-on-surface hover:bg-surface-container-low"
                >
                    <Icon name={expanded ? 'expand_less' : 'checklist'} className="text-[16px]" />
                    {expanded ? t('showFewer') : t('viewAll', { count: items.length })}
                </button>
            </div>

            {visibleItems.length > 0 && (
                <div className="mt-5 pt-4 border-t border-outline-variant/20 grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
                    {visibleItems.map((item) => (
                        <div
                            key={item.key}
                            className={`p-3.5 rounded-xl border flex flex-col justify-between gap-2 ${
                                item.done
                                    ? 'bg-surface-container-low/50 border-outline-variant/20'
                                    : 'bg-surface border-outline-variant/40'
                            }`}
                        >
                            <div className="flex items-start gap-2.5">
                                <Icon
                                    name={item.done ? 'check_circle' : 'radio_button_unchecked'}
                                    className={`text-[18px] mt-0.5 ${
                                        item.done ? 'text-primary' : 'text-on-surface-variant'
                                    }`}
                                />
                                <div className="min-w-0 flex-1">
                                    <p
                                        className={`text-xs font-medium leading-snug ${
                                            item.done ? 'text-on-surface-variant line-through' : 'text-on-surface'
                                        }`}
                                    >
                                        {t(item.titleKey, { defaultValue: item.title })}
                                    </p>
                                    <span className="inline-block mt-1 text-[11px] font-semibold text-primary">
                                        {t('points', { count: item.points })}
                                    </span>
                                </div>
                            </div>

                            {!item.done && item.fixUrl && (
                                <div className="flex justify-end">
                                    <Link
                                        to={item.fixUrl}
                                        className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:underline"
                                    >
                                        {t('fixNow')}
                                        <Icon name="arrow_forward" className="text-[14px]" />
                                    </Link>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

