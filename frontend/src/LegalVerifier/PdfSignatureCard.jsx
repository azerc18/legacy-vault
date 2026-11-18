import React from 'react';
import { useTranslation } from 'react-i18next';
import { formatDate } from './verifierUi.js';

// V2: kết quả đọc chữ ký số trong giấy chứng tử PDF. Chỉ hiển thị, không quyết định thay Verifier.
const OVERALL_STYLE = {
    ALL_VALID: { icon: 'verified', cls: 'bg-success-container text-on-success-container' },
    HAS_INVALID: { icon: 'gpp_bad', cls: 'bg-error-container text-on-error-container' },
    HAS_UNVERIFIED: { icon: 'gpp_maybe', cls: 'bg-secondary-fixed text-on-secondary-fixed' },
    NO_SIGNATURE: { icon: 'draw', cls: 'bg-surface-container-high text-on-surface' },
    NOT_PDF: { icon: 'image', cls: 'bg-surface-container-high text-on-surface' },
    UNREADABLE: { icon: 'broken_image', cls: 'bg-surface-container-high text-on-surface' },
};

export default function PdfSignatureCard({ report }) {
    const { t } = useTranslation('verifier');
    if (!report) return null;
    const style = OVERALL_STYLE[report.overall] ?? OVERALL_STYLE.UNREADABLE;
    const signatures = report.signatures ?? [];

    return (
        <div data-testid="pdf-signatures" className="mt-5 space-y-4">
            <div className={`p-4 rounded-xl flex items-start gap-3 ${style.cls}`} role="status">
                <span className="material-symbols-outlined text-[26px] shrink-0">{style.icon}</span>
                <div className="min-w-0">
                    <p className="font-bold">{t(`pdfSig.overall.${report.overall}.title`, { count: report.signatureCount })}</p>
                    <p className="text-sm mt-1">{t(`pdfSig.overall.${report.overall}.text`)}</p>
                </div>
            </div>

            {signatures.map((s, i) => (
                <div key={s.fieldName || i} className="rounded-xl border border-outline-variant/50 bg-surface-container-low p-4 md:p-5">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                        <div className="min-w-0">
                            <p className="font-semibold break-words">{s.signerName || t('pdfSig.unknownSigner')}</p>
                            {s.signerOrganization && <p className="text-sm text-on-surface-variant break-words">{s.signerOrganization}</p>}
                        </div>
                        {s.selfSigned && (
                            <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-secondary-fixed text-on-secondary-fixed">
                                {t('pdfSig.selfSigned')}
                            </span>
                        )}
                    </div>

                    <ul className="grid grid-cols-1 sm:grid-cols-3 gap-2 mt-4">
                        <Check ok={s.integrityValid} label={t('pdfSig.check.integrity')} t={t} />
                        <Check ok={s.coversWholeDocument} label={t('pdfSig.check.whole')} t={t} />
                        <Check ok={s.certificateValidAtSigning} label={t('pdfSig.check.certTime')} t={t} />
                    </ul>

                    {s.problem && (
                        <p className="mt-3 text-sm text-error">{t(`pdfSig.problem.${s.problem}`, { defaultValue: s.problem })}</p>
                    )}

                    <dl className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-3 mt-4 text-sm">
                        <Item label={t('pdfSig.issuer')} value={s.issuer} />
                        <Item label={t('pdfSig.signedAt')} value={formatDate(s.signedAt, true)} />
                        <Item label={t('pdfSig.validity')} value={s.certificateNotBefore
                            ? `${formatDate(s.certificateNotBefore)} → ${formatDate(s.certificateNotAfter)}` : null} />
                        <Item label={t('pdfSig.algorithm')} value={[s.digestAlgorithm, s.subFilter].filter(Boolean).join(' · ')} />
                        {s.reason && <Item label={t('pdfSig.reason')} value={s.reason} />}
                        {s.location && <Item label={t('pdfSig.location')} value={s.location} />}
                        {s.serialNumber && <Item label={t('pdfSig.serial')} value={s.serialNumber} mono />}
                    </dl>
                </div>
            ))}

            <p className="text-xs text-on-surface-variant">{t('pdfSig.disclaimer')}</p>
        </div>
    );
}

function Check({ ok, label, t }) {
    const state = ok === true ? 'ok' : ok === false ? 'fail' : 'unknown';
    const icon = { ok: 'check_circle', fail: 'cancel', unknown: 'help' }[state];
    const cls = { ok: 'text-success', fail: 'text-error', unknown: 'text-outline' }[state];
    return (
        <li className="flex items-center gap-2 p-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/40 text-sm">
            <span className={`material-symbols-outlined text-[20px] ${cls}`}>{icon}</span>
            <span className="min-w-0">
                <span className="font-semibold block leading-tight">{label}</span>
                <span className={`text-xs ${cls}`}>{t(`pdfSig.check.${state}`)}</span>
            </span>
        </li>
    );
}

function Item({ label, value, mono }) {
    return (
        <div className="min-w-0">
            <dt className="text-on-surface-variant">{label}</dt>
            <dd className={`mt-0.5 font-medium break-words ${mono ? 'font-mono text-xs break-all' : ''}`}>{value || '—'}</dd>
        </div>
    );
}
