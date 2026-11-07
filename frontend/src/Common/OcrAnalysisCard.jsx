import React from 'react';
import { useTranslation } from 'react-i18next';

export default function OcrAnalysisCard({
    data,
    role = 'executor',
    onApplyNote,
    onSuggestRejectReason,
}) {
    const { i18n } = useTranslation();
    if (!data) return null;

    const isVi = (i18n?.language || 'vi').startsWith('vi');
    const matched = Boolean(data.ownerNameMatched);
    const hasSeal = Boolean(data.hasOfficialSeal);
    const category = data.documentCategory || 'VALID_DEATH_CERTIFICATE';
    const isRelatedDoc = category === 'RELATED_SUPPORTING_DOCUMENT';
    const isUnrelatedDoc = category === 'UNRELATED_DOCUMENT';
    const isValidCert = category === 'VALID_DEATH_CERTIFICATE';
    const isSuspectedForgery = data.suggestedRejectionCode === 'SUSPECTED_FORGERY';
    const isFullyValid = isValidCert && matched && !isSuspectedForgery;
    const warnings = Array.isArray(data.warnings) ? data.warnings : [];

    const imageQualityScore = typeof data.imageQualityScore === 'number' ? data.imageQualityScore : 96;
    const deathProofPercent = typeof data.deathProofRelevancePercent === 'number' ? data.deathProofRelevancePercent : (matched ? 98 : 15);
    const roleSuggestion = role === 'verifier'
        ? (data.verifierSuggestion || data.executorSuggestion)
        : (data.executorSuggestion || data.verifierSuggestion);

    const labels = isVi ? {
        title: 'Kết quả bóc tách & Cố vấn thẩm định AI (OCR Pháp lý)',
        advisoryTag: 'Trợ lý AI Cố vấn — Chỉ gợi ý tham khảo, quyết định cuối cùng thuộc về bạn',
        confidence: 'Điểm tổng hợp AI',
        matchedBadge: 'Khớp 100% Chủ két & Đúng mẫu',
        relatedBadge: 'Tài liệu liên quan — Chưa phải Trích lục khai tử',
        unrelatedBadge: 'Tài liệu không hợp lệ',
        mismatchBadge: 'Cảnh báo: Sai lệch họ tên Chủ két',
        evalSectionTitle: '4 Tiêu chí Đánh giá Chuyên sâu của AI',
        imageQualityTitle: '1. Chất lượng ảnh & Độ rõ nét',
        headerTitleLabel: '2. Tiêu đề bên trong tài liệu',
        deathProofTitle: '3. Mức độ chứng minh sự qua đời',
        suggestionTitle: role === 'verifier'
            ? '4. Gợi ý tham khảo cho Thẩm định viên (Legal Verifier)'
            : '4. Gợi ý tham khảo cho Người chấp hành (Executor)',
        docType: 'Phân loại văn bản',
        certNo: 'Số hiệu văn bản / Trích lục',
        bookNo: 'Số vào sổ / Hồ sơ',
        deceasedName: 'Họ và tên trên tài liệu',
        expectedOwner: 'Họ và tên Chủ két (hệ thống)',
        dobGender: 'Ngày sinh / Giới tính',
        idNumber: 'Số CCCD / Định danh',
        deathTime: 'Thời điểm tử vong / Sự kiện',
        deathPlace: 'Địa điểm ghi nhận',
        causeOfDeath: 'Nguyên nhân / Nội dung chính',
        issuingAuthority: 'Cơ quan cấp',
        issueDateSigner: 'Ngày cấp & Người ký',
        officialSeal: 'Con dấu pháp nhân',
        sealValid: 'Đã nhận diện dấu đỏ hợp lệ',
        sealMissing: 'Chưa nhận diện rõ dấu đỏ',
        warningsTitle: 'Lưu ý & Phân tích chi tiết từ AI',
        applyNoteBtn: 'Chèn tóm tắt gợi ý AI vào Ghi chú nộp hồ sơ',
        suggestRejectBtn: 'Áp dụng nhanh lý do từ chối gợi ý vào biểu mẫu (Tùy chọn)',
    } : {
        title: 'AI Legal OCR Extraction & Advisory Assessment',
        advisoryTag: 'AI Advisory Assistant — Suggestions only, final decision is yours',
        confidence: 'Overall AI Score',
        matchedBadge: '100% Owner Match & Valid Form',
        relatedBadge: 'Related Document — Not an Official Death Certificate',
        unrelatedBadge: 'Invalid Document Type',
        mismatchBadge: 'Warning: Owner Name Mismatch',
        evalSectionTitle: '4 In-Depth AI Assessment Criteria',
        imageQualityTitle: '1. Image Quality & Clarity',
        headerTitleLabel: '2. Internal Document Header / Title',
        deathProofTitle: '3. Death Proof Relevance (%)',
        suggestionTitle: role === 'verifier'
            ? '4. Advisory Suggestion for Legal Verifier'
            : '4. Advisory Suggestion for Executor',
        docType: 'Document Classification',
        certNo: 'Certificate / Document No.',
        bookNo: 'Registry / Dossier No.',
        deceasedName: 'Full Name (Extracted)',
        expectedOwner: 'Vault Owner Name (System)',
        dobGender: 'Date of Birth / Gender',
        idNumber: 'National ID Number',
        deathTime: 'Date & Time of Death / Event',
        deathPlace: 'Recorded Location',
        causeOfDeath: 'Cause of Death / Summary',
        issuingAuthority: 'Issuing Authority',
        issueDateSigner: 'Issue Date & Signer',
        officialSeal: 'Official Red Seal',
        sealValid: 'Valid official seal detected',
        sealMissing: 'Official seal unclear/missing',
        warningsTitle: 'Detailed AI Notes & Warnings',
        applyNoteBtn: 'Insert AI summary into submission notes',
        suggestRejectBtn: 'Pre-select suggested rejection reason (Optional)',
    };

    let badgeText = labels.matchedBadge;
    let badgeClass = 'bg-emerald-600 text-white';
    let badgeIcon = 'verified';
    let containerClass = 'border-emerald-500/40 bg-emerald-50/40 dark:bg-emerald-950/20';

    if (!matched) {
        badgeText = labels.mismatchBadge;
        badgeClass = 'bg-red-600 text-white';
        badgeIcon = 'warning';
        containerClass = 'border-red-500/50 bg-red-50/50 dark:bg-red-950/20';
    } else if (isSuspectedForgery) {
        badgeText = isVi
            ? 'Cảnh báo: Đúng tên nhưng Mâu thuẫn Năm sinh ↔ CCCD'
            : 'Warning: Name Matches but DOB ↔ ID Mismatch';
        badgeClass = 'bg-red-600 text-white';
        badgeIcon = 'gpp_maybe';
        containerClass = 'border-red-500/50 bg-red-50/50 dark:bg-red-950/20';
    } else if (isRelatedDoc || imageQualityScore < 65) {
        badgeText = isRelatedDoc ? labels.relatedBadge : (isVi ? 'Cảnh báo: Ảnh mờ / khó đọc' : 'Warning: Blurry / Low Clarity');
        badgeClass = 'bg-amber-600 text-white';
        badgeIcon = 'info';
        containerClass = 'border-amber-500/50 bg-amber-50/50 dark:bg-amber-950/20';
    } else if (isUnrelatedDoc) {
        badgeText = labels.unrelatedBadge;
        badgeClass = 'bg-red-600 text-white';
        badgeIcon = 'cancel';
        containerClass = 'border-red-500/50 bg-red-50/50 dark:bg-red-950/20';
    }

    const scoreBarColor = (score) => {
        if (score >= 85) return 'bg-emerald-600';
        if (score >= 65) return 'bg-amber-500';
        return 'bg-red-600';
    };

    const scoreTextColor = (score) => {
        if (score >= 85) return 'text-emerald-700 dark:text-emerald-400';
        if (score >= 65) return 'text-amber-700 dark:text-amber-400';
        return 'text-red-700 dark:text-red-400';
    };

    const fields = [
        {
            label: labels.docType,
            value: data.documentType,
            highlight: isValidCert,
            warning: isRelatedDoc,
            danger: isUnrelatedDoc,
        },
        { label: labels.certNo, value: data.certificateNumber, mono: true, highlight: true },
        { label: labels.bookNo, value: data.bookNumber, mono: true },
        {
            label: labels.deceasedName,
            value: data.deceasedFullName,
            highlight: matched,
            danger: !matched,
        },
        { label: labels.expectedOwner, value: data.expectedOwnerName, highlight: true },
        {
            label: labels.dobGender,
            value: [data.dateOfBirth, data.gender].filter(Boolean).join(' • '),
            danger: isSuspectedForgery,
        },
        {
            label: labels.idNumber,
            value: data.idNumber,
            mono: true,
            danger: isSuspectedForgery,
        },
        { label: labels.deathTime, value: data.dateOfDeath },
        {
            label: labels.deathPlace,
            value: data.placeOfDeath,
            warning: isSuspectedForgery,
        },
        { label: labels.causeOfDeath, value: data.causeOfDeath },
        { label: labels.issuingAuthority, value: data.issuingAuthority },
        { label: labels.issueDateSigner, value: [data.issueDate, data.signerName].filter(Boolean).join(' — ') },
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
                        <h4 className="text-sm font-bold text-on-surface">{labels.title}</h4>
                    </div>
                    <p className="text-[11px] text-on-surface-variant mt-0.5 flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px] text-primary">psychology</span>
                        {labels.advisoryTag}
                    </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center gap-1 rounded-full bg-surface-container-highest px-2.5 py-0.5 text-xs font-semibold text-on-surface-variant">
                        {labels.confidence}: <strong className="text-primary">{data.confidenceScore ?? 98}/100</strong>
                    </span>
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
                    {labels.evalSectionTitle}
                </p>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
                    {/* 1. Image Quality & Clarity */}
                    <div className="rounded-lg border border-outline-variant/30 bg-background/70 p-2.5">
                        <div className="flex items-center justify-between gap-2 mb-1">
                            <span className="font-semibold text-on-surface">{labels.imageQualityTitle}</span>
                            <span
                                data-testid="ocr-image-quality-score"
                                className={`font-bold text-sm ${scoreTextColor(imageQualityScore)}`}
                            >
                                {imageQualityScore}%
                            </span>
                        </div>
                        <div className="w-full h-2 rounded-full bg-surface-container-highest overflow-hidden mb-1.5">
                            <div
                                className={`h-full rounded-full transition-all ${scoreBarColor(imageQualityScore)}`}
                                style={{ width: `${Math.min(100, Math.max(5, imageQualityScore))}%` }}
                            />
                        </div>
                        <p className="text-[11px] text-on-surface-variant leading-snug">
                            {data.imageClarityLabel || 'Rõ nét, chữ và con dấu dễ đọc'}
                        </p>
                    </div>

                    {/* 2. Internal Header / Title Assessment */}
                    <div className="rounded-lg border border-outline-variant/30 bg-background/70 p-2.5">
                        <p className="font-semibold text-on-surface mb-1">{labels.headerTitleLabel}</p>
                        <p
                            data-testid="ocr-detected-header"
                            className="font-mono font-bold text-primary text-xs break-words mb-1"
                        >
                            “{data.detectedHeaderTitle || data.documentType || 'TRÍCH LỤC KHAI TỬ'}”
                        </p>
                        <p className="text-[11px] text-on-surface-variant leading-snug">
                            {data.titleAssessment || 'Tiêu đề chuẩn mẫu Trích lục khai tử hộ tịch'}
                        </p>
                    </div>

                    {/* 3. Death Proof Relevance Percentage */}
                    <div className="rounded-lg border border-outline-variant/30 bg-background/70 p-2.5">
                        <div className="flex items-center justify-between gap-2 mb-1">
                            <span className="font-semibold text-on-surface">{labels.deathProofTitle}</span>
                            <span
                                data-testid="ocr-death-proof-percent"
                                className={`font-bold text-sm ${scoreTextColor(deathProofPercent)}`}
                            >
                                {deathProofPercent}%
                            </span>
                        </div>
                        <div className="w-full h-2 rounded-full bg-surface-container-highest overflow-hidden mb-1.5">
                            <div
                                className={`h-full rounded-full transition-all ${scoreBarColor(deathProofPercent)}`}
                                style={{ width: `${Math.min(100, Math.max(5, deathProofPercent))}%` }}
                            />
                        </div>
                        <p className="text-[11px] text-on-surface-variant leading-snug">
                            {data.deathProofExplanation || 'Chứng minh trực tiếp sự qua đời của Chủ két'}
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
                                {labels.suggestionTitle}
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
                                {labels.suggestRejectBtn}
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
                        !isFullyValid && (isRelatedDoc || imageQualityScore < 65) && matched
                            ? 'border-amber-500/60 bg-amber-100/80 text-amber-950 dark:bg-amber-900/30 dark:text-amber-200'
                            : 'border-red-400/60 bg-red-100/80 text-red-900 dark:bg-red-900/30 dark:text-red-200'
                    }`}
                >
                    <p className="font-bold flex items-center gap-1.5 mb-1">
                        <span className="material-symbols-outlined text-base">report</span>
                        {labels.warningsTitle}
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
                            {f.value || '—'}
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
                    <span className="text-on-surface-variant">{labels.officialSeal}:</span>
                    <span className={`font-semibold ${hasSeal ? 'text-emerald-700 dark:text-emerald-400' : 'text-amber-700'}`}>
                        {hasSeal ? labels.sealValid : labels.sealMissing}
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
                        {labels.applyNoteBtn}
                    </button>
                )}
            </div>
        </div>
    );
}
