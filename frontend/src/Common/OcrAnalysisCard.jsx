import React from 'react';
import { useTranslation } from 'react-i18next';

export default function OcrAnalysisCard({
    data,
    role = 'executor',
    onApplyNote,
    onSuggestRejectReason,
}) {
    const ns = role === 'verifier' ? 'verifier' : 'executor';
    const { t } = useTranslation(ns);
    if (!data) return null;

    const engineSource = data.engineSource || 'UNAVAILABLE';

    if (engineSource === 'UNAVAILABLE') {
        return (
            <div
                data-testid="ocr-analysis-card"
                className="mt-4 rounded-xl border border-amber-500/60 bg-amber-50/70 dark:bg-amber-950/30 p-4 shadow-sm"
            >
                <div
                    data-testid="ocr-unavailable-banner"
                    className="flex items-start gap-3 text-amber-950 dark:text-amber-200"
                >
                    <span className="material-symbols-outlined text-amber-600 text-2xl shrink-0">
                        warning
                    </span>
                    <div className="space-y-1 text-sm">
                        <div className="flex flex-wrap items-center gap-2">
                            <p className="font-bold">
                                {data.unavailableMessage || t('ocr.unavailableTitle')}
                            </p>
                            <span
                                data-testid="ocr-engine-source"
                                className="inline-flex items-center gap-1 rounded-full bg-amber-200/80 dark:bg-amber-900/60 px-2.5 py-0.5 text-[11px] font-semibold text-amber-900 dark:text-amber-200"
                            >
                                {t('ocr.sourceLabel')}: {t('ocr.sourceUnavailable')}
                            </span>
                        </div>
                        <p className="text-xs text-on-surface-variant">
                            {t('ocr.unavailableHint')}
                        </p>
                    </div>
                </div>
            </div>
        );
    }

    const unreadableText = t('ocr.unreadable');
    const matched = Boolean(data.ownerNameMatched);
    const hasSeal = Boolean(data.hasOfficialSeal);
    const category = data.documentCategory || 'UNRELATED_DOCUMENT';
    const isRelatedDoc = category === 'RELATED_SUPPORTING_DOCUMENT';
    const isUnrelatedDoc = category === 'UNRELATED_DOCUMENT';
    const isValidCert = category === 'VALID_DEATH_CERTIFICATE';
    const isSuspectedForgery = data.suggestedRejectionCode === 'SUSPECTED_FORGERY';
    const isFullyValid = isValidCert && matched && !isSuspectedForgery;
    const warnings = Array.isArray(data.warnings) ? data.warnings : [];

    const imageQualityScore = typeof data.imageQualityScore === 'number' ? data.imageQualityScore : null;
    const deathProofPercent = typeof data.deathProofRelevancePercent === 'number' ? data.deathProofRelevancePercent : null;
    const confidenceScore = typeof data.confidenceScore === 'number' ? data.confidenceScore : null;

    const roleSuggestion = role === 'verifier'
        ? (data.verifierSuggestion || data.executorSuggestion)
        : (data.executorSuggestion || data.verifierSuggestion);

    const isDemoSource = engineSource === 'DEMO_SIMULATION';
    const sourceBadgeText = isDemoSource ? t('ocr.sourceDemo') : t('ocr.sourceGemini');

    let badgeText = t('ocr.matchedBadge');
    let badgeClass = 'bg-emerald-600 text-white';
    let badgeIcon = 'verified';
    let containerClass = 'border-emerald-500/40 bg-emerald-50/40 dark:bg-emerald-950/20';

    if (!matched) {
        badgeText = t('ocr.mismatchBadge');
        badgeClass = 'bg-red-600 text-white';
        badgeIcon = 'warning';
        containerClass = 'border-red-500/50 bg-red-50/50 dark:bg-red-950/20';
    } else if (isSuspectedForgery) {
        badgeText = t('ocr.forgeryBadge');
        badgeClass = 'bg-red-600 text-white';
        badgeIcon = 'gpp_maybe';
        containerClass = 'border-red-500/50 bg-red-50/50 dark:bg-red-950/20';
    } else if (isRelatedDoc || (imageQualityScore !== null && imageQualityScore < 65)) {
        badgeText = isRelatedDoc ? t('ocr.relatedBadge') : t('ocr.blurryBadge');
        badgeClass = 'bg-amber-600 text-white';
        badgeIcon = 'info';
        containerClass = 'border-amber-500/50 bg-amber-50/50 dark:bg-amber-950/20';
    } else if (isUnrelatedDoc) {
        badgeText = t('ocr.unrelatedBadge');
        badgeClass = 'bg-red-600 text-white';
        badgeIcon = 'cancel';
        containerClass = 'border-red-500/50 bg-red-50/50 dark:bg-red-950/20';
    }

    const scoreBarColor = (score) => {
        if (score === null) return 'bg-outline-variant';
        if (score >= 85) return 'bg-emerald-600';
        if (score >= 65) return 'bg-amber-500';
        return 'bg-red-600';
    };

    const scoreTextColor = (score) => {
        if (score === null) return 'text-on-surface-variant';
        if (score >= 85) return 'text-emerald-700 dark:text-emerald-400';
        if (score >= 65) return 'text-amber-700 dark:text-amber-400';
        return 'text-red-700 dark:text-red-400';
    };

    const dobGenderVal = [data.dateOfBirth, data.gender].filter(Boolean).join(' • ');
    const issueSignerVal = [data.issueDate, data.signerName].filter(Boolean).join(' — ');

    const fields = [
        {
            label: t('ocr.docType'),
            value: data.documentType,
            highlight: isValidCert,
            warning: isRelatedDoc,
            danger: isUnrelatedDoc,
        },
        { label: t('ocr.certNo'), value: data.certificateNumber, mono: true, highlight: Boolean(data.certificateNumber) },
        { label: t('ocr.bookNo'), value: data.bookNumber, mono: true },
        {
            label: t('ocr.deceasedName'),
            value: data.deceasedFullName,
            highlight: matched,
            danger: !matched,
        },
        { label: t('ocr.expectedOwner'), value: data.expectedOwnerName, highlight: true },
        {
            label: t('ocr.dobGender'),
            value: dobGenderVal || null,
            danger: isSuspectedForgery,
        },
        {
            label: t('ocr.idNumber'),
            value: data.idNumber,
            mono: true,
            danger: isSuspectedForgery,
        },
        { label: t('ocr.deathTime'), value: data.dateOfDeath },
        {
            label: t('ocr.deathPlace'),
            value: data.placeOfDeath,
            warning: isSuspectedForgery,
        },
        { label: t('ocr.causeOfDeath'), value: data.causeOfDeath },
        { label: t('ocr.issuingAuthority'), value: data.issuingAuthority },
        { label: t('ocr.issueDateSigner'), value: issueSignerVal || null },
    ];

    return (
        <div
            data-testid="ocr-analysis-card"
            className={`mt-4 rounded-xl border p-4 shadow-sm transition-all ${containerClass}`}
        >
            {/* Header */}
            <div className="flex flex-wrap items-center justify-between gap-2 pb-3 mb-3 border-b border-outline-variant/40">
                <div>
                    <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-primary text-xl">auto_awesome</span>
                        <h4 className="text-sm font-bold text-on-surface">{t('ocr.title')}</h4>
                    </div>
                    <p className="text-[11px] text-on-surface-variant mt-0.5 flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px] text-primary">psychology</span>
                        {t('ocr.advisoryTag')}
                    </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    <span
                        data-testid="ocr-engine-source"
                        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                            isDemoSource
                                ? 'bg-amber-200/80 text-amber-900 dark:bg-amber-900/50 dark:text-amber-200'
                                : 'bg-surface-container-highest text-on-surface-variant'
                        }`}
                    >
                        {t('ocr.sourceLabel')}: <strong>{sourceBadgeText}</strong>
                    </span>
                    {confidenceScore !== null && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-surface-container-highest px-2.5 py-0.5 text-xs font-semibold text-on-surface-variant">
                            {t('ocr.confidence')}: <strong className="text-primary">{confidenceScore}/100</strong>
                        </span>
                    )}
                    <span
                        data-testid="ocr-match-badge"
                        className={`inline-flex items-center gap-1 rounded-full px-3 py-0.5 text-xs font-bold ${badgeClass}`}
                    >
                        <span className="material-symbols-outlined text-sm">{badgeIcon}</span>
                        {badgeText}
                    </span>
                </div>
            </div>

            {/* 4-Criteria Advisory Evaluation Panel */}
            <div
                data-testid="ocr-advisory-metrics"
                className="mb-4 rounded-xl bg-surface-container-lowest/95 border border-outline-variant/40 p-3.5 space-y-3"
            >
                <p className="text-xs font-bold uppercase tracking-wider text-primary flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-base">analytics</span>
                    {t('ocr.evalSectionTitle')}
                </p>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
                    {/* 1. Image Quality & Clarity */}
                    <div className="rounded-lg border border-outline-variant/30 bg-background/70 p-2.5">
                        <div className="flex items-center justify-between gap-2 mb-1">
                            <span className="font-semibold text-on-surface">{t('ocr.imageQualityTitle')}</span>
                            <span
                                data-testid="ocr-image-quality-score"
                                className={`font-bold text-sm ${scoreTextColor(imageQualityScore)}`}
                            >
                                {imageQualityScore !== null ? `${imageQualityScore}%` : unreadableText}
                            </span>
                        </div>
                        {imageQualityScore !== null && (
                            <div className="w-full h-2 rounded-full bg-surface-container-highest overflow-hidden mb-1.5">
                                <div
                                    className={`h-full rounded-full transition-all ${scoreBarColor(imageQualityScore)}`}
                                    style={{ width: `${Math.min(100, Math.max(5, imageQualityScore))}%` }}
                                />
                            </div>
                        )}
                        <p className="text-[11px] text-on-surface-variant leading-snug">
                            {data.imageClarityLabel || unreadableText}
                        </p>
                    </div>

                    {/* 2. Internal Header / Title Assessment */}
                    <div className="rounded-lg border border-outline-variant/30 bg-background/70 p-2.5">
                        <p className="font-semibold text-on-surface mb-1">{t('ocr.headerTitleLabel')}</p>
                        <p
                            data-testid="ocr-detected-header"
                            className="font-mono font-bold text-primary text-xs break-words mb-1"
                        >
                            {data.detectedHeaderTitle ? `“${data.detectedHeaderTitle}”` : unreadableText}
                        </p>
                        <p className="text-[11px] text-on-surface-variant leading-snug">
                            {data.titleAssessment || unreadableText}
                        </p>
                    </div>

                    {/* 3. Death Proof Relevance Percentage */}
                    <div className="rounded-lg border border-outline-variant/30 bg-background/70 p-2.5">
                        <div className="flex items-center justify-between gap-2 mb-1">
                            <span className="font-semibold text-on-surface">{t('ocr.deathProofTitle')}</span>
                            <span
                                data-testid="ocr-death-proof-percent"
                                className={`font-bold text-sm ${scoreTextColor(deathProofPercent)}`}
                            >
                                {deathProofPercent !== null ? `${deathProofPercent}%` : unreadableText}
                            </span>
                        </div>
                        {deathProofPercent !== null && (
                            <div className="w-full h-2 rounded-full bg-surface-container-highest overflow-hidden mb-1.5">
                                <div
                                    className={`h-full rounded-full transition-all ${scoreBarColor(deathProofPercent)}`}
                                    style={{ width: `${Math.min(100, Math.max(5, deathProofPercent))}%` }}
                                />
                            </div>
                        )}
                        <p className="text-[11px] text-on-surface-variant leading-snug">
                            {data.deathProofExplanation || unreadableText}
                        </p>
                    </div>
                </div>

                {/* 4. Role-specific Non-binding Advisory Suggestion */}
                {roleSuggestion && (
                    <div
                        data-testid="ocr-role-suggestion"
                        className="rounded-lg border border-primary/30 bg-primary/5 p-3 text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                    >
                        <div className="space-y-0.5">
                            <p className="font-bold text-primary flex items-center gap-1.5">
                                <span className="material-symbols-outlined text-base">tips_and_updates</span>
                                {role === 'verifier'
                                    ? t('ocr.suggestionVerifierTitle')
                                    : t('ocr.suggestionExecutorTitle')}
                            </p>
                            <p className="text-on-surface leading-relaxed">{roleSuggestion}</p>
                        </div>

                        {role === 'verifier' && data.suggestedRejectionCode && onSuggestRejectReason && (
                            <button
                                type="button"
                                data-testid="ocr-suggest-reject-btn"
                                onClick={() => onSuggestRejectReason(data.suggestedRejectionCode)}
                                className="shrink-0 inline-flex items-center gap-1.5 rounded-lg border border-amber-600/40 bg-amber-500/15 hover:bg-amber-500/25 text-amber-900 dark:text-amber-200 px-3 py-1.5 text-xs font-semibold transition-colors cursor-pointer"
                            >
                                <span className="material-symbols-outlined text-sm">auto_fix_high</span>
                                {t('ocr.suggestRejectBtn')}
                            </button>
                        )}
                    </div>
                )}
            </div>

            {/* Warnings if any */}
            {warnings.length > 0 && (
                <div
                    data-testid="ocr-warnings-box"
                    className={`mb-3 rounded-lg border p-3 text-xs ${
                        !isFullyValid && (isRelatedDoc || (imageQualityScore !== null && imageQualityScore < 65)) && matched
                            ? 'border-amber-500/60 bg-amber-100/80 text-amber-950 dark:bg-amber-900/30 dark:text-amber-200'
                            : 'border-red-400/60 bg-red-100/80 text-red-900 dark:bg-red-900/30 dark:text-red-200'
                    }`}
                >
                    <p className="font-bold flex items-center gap-1.5 mb-1">
                        <span className="material-symbols-outlined text-base">report</span>
                        {t('ocr.warningsTitle')}
                    </p>
                    <ul className="list-disc pl-5 space-y-0.5">
                        {warnings.map((w, idx) => (
                            <li key={idx}>{w}</li>
                        ))}
                    </ul>
                </div>
            )}

            {/* Extracted Fields Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5 text-xs">
                {fields.map((f, i) => (
                    <div
                        key={i}
                        className="rounded-lg bg-surface-container-lowest/90 p-2.5 border border-outline-variant/30"
                    >
                        <p className="text-[11px] text-on-surface-variant mb-0.5">{f.label}</p>
                        <p
                            className={`font-semibold break-words ${
                                f.danger
                                    ? 'text-red-600 dark:text-red-400 font-bold'
                                    : f.warning
                                    ? 'text-amber-700 dark:text-amber-400 font-bold'
                                    : f.highlight
                                    ? 'text-primary font-bold'
                                    : 'text-on-surface'
                            } ${f.mono ? 'font-mono' : ''}`}
                        >
                            {f.value || unreadableText}
                        </p>
                    </div>
                ))}
            </div>

            {/* Seal Status & Summary Footer */}
            <div className="mt-3 pt-3 border-t border-outline-variant/40 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center gap-2 text-xs">
                    <span
                        className={`material-symbols-outlined text-base ${
                            hasSeal ? 'text-emerald-600' : 'text-amber-600'
                        }`}
                    >
                        {hasSeal ? 'workspace_premium' : 'help'}
                    </span>
                    <span className="text-on-surface-variant">{t('ocr.officialSeal')}:</span>
                    <span className={`font-semibold ${hasSeal ? 'text-emerald-700 dark:text-emerald-400' : 'text-amber-700'}`}>
                        {hasSeal ? t('ocr.sealValid') : t('ocr.sealMissing')}
                    </span>
                </div>

                {onApplyNote && data.summaryNote && (
                    <button
                        type="button"
                        data-testid="ocr-apply-note-btn"
                        onClick={() => onApplyNote(data.summaryNote)}
                        className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-primary/10 hover:bg-primary/20 text-primary px-3 py-1.5 text-xs font-semibold transition-colors cursor-pointer"
                    >
                        <span className="material-symbols-outlined text-sm">edit_note</span>
                        {t('ocr.applyNoteBtn')}
                    </button>
                )}
            </div>
        </div>
    );
}
