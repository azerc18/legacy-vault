import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getSignature } from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import { formatDate } from './verifierUi.js';

// V3: thẻ "Chữ ký xác nhận" của hồ sơ đã ký. Gọi BE kiểm lại mỗi lần mở / bấm "Kiểm tra lại",
// hiện 3 điều kiện (chữ ký, nội dung, giấy chứng tử) và phần kỹ thuật để người ngoài tự kiểm bằng OpenSSL.
const STATUS_STYLE = {
    VALID: { icon: 'verified', cls: 'bg-success-container text-on-success-container' },
    INVALID: { icon: 'gpp_bad', cls: 'bg-error-container text-on-error-container' },
    DOCUMENT_UNAVAILABLE: { icon: 'cloud_off', cls: 'bg-surface-container-high text-on-surface' },
    LEGACY: { icon: 'history', cls: 'bg-surface-container-high text-on-surface' },
};

export default function SignatureSealCard({ requestId }) {
    const { t } = useTranslation('verifier');
    const [seal, setSeal] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [copied, setCopied] = useState('');

    // Chỉ nhận kết quả của lần gọi mới nhất: đổi hồ sơ hoặc bấm "Kiểm tra lại" liên tục thì
    // phản hồi cũ về muộn sẽ bị bỏ qua (review #293 n2)
    const latestCall = useRef(0);
    const fetchSeal = useCallback(() => {
        const call = ++latestCall.current;
        const isLatest = () => call === latestCall.current;
        return getSignature(requestId)
            .then((data) => { if (isLatest()) { setSeal(data); setError(''); } })
            .catch((e) => { if (isLatest()) setError(getErrorMessage(e, t('seal.loadError'))); })
            .finally(() => { if (isLatest()) setLoading(false); });
    }, [requestId]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        fetchSeal();
        return () => { latestCall.current += 1; }; // rời trang / đổi hồ sơ: bỏ kết quả đang bay về
    }, [fetchSeal]);

    // Bấm "Kiểm tra lại": BE đọc lại file và kiểm lại chữ ký từ đầu
    const recheck = () => { setLoading(true); fetchSeal(); };

    const copy = async (key, text) => {
        try {
            await navigator.clipboard.writeText(text);
            setCopied(key);
            setTimeout(() => setCopied(''), 1500);
        } catch { /* trình duyệt chặn clipboard: bỏ qua, người dùng vẫn chọn chữ được */ }
    };

    const style = STATUS_STYLE[seal?.status] ?? STATUS_STYLE.LEGACY;

    return (
        <section data-testid="signature-seal" className="bg-surface-container-lowest rounded-2xl p-6 md:p-8 shadow-sm border border-outline-variant/30">
            <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
                <h2 className="font-title-lg text-on-surface font-bold flex items-center gap-3">
                    <span className="material-symbols-outlined text-secondary">approval</span>
                    {t('seal.title')}
                </h2>
                <button
                    type="button"
                    onClick={recheck}
                    disabled={loading}
                    className="h-10 px-4 rounded-xl border border-outline-variant text-primary font-semibold hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-2"
                >
                    <span className={`material-symbols-outlined text-[20px] ${loading ? 'animate-spin' : ''}`}>
                        {loading ? 'progress_activity' : 'refresh'}
                    </span>
                    {loading ? t('seal.checking') : t('seal.recheck')}
                </button>
            </div>

            {error && <div role="alert" className="p-3 rounded-lg bg-error-container text-on-error-container text-sm">{error}</div>}

            {seal && (
                <div className="space-y-5">
                    <div className={`p-4 rounded-xl flex items-start gap-3 ${style.cls}`} role="status">
                        <span className="material-symbols-outlined text-[28px] shrink-0">{style.icon}</span>
                        <div className="min-w-0">
                            <p className="font-bold">{t(`seal.status.${seal.status}.title`)}</p>
                            <p className="text-sm mt-1">{t(`seal.status.${seal.status}.text`)}</p>
                        </div>
                    </div>

                    {seal.status !== 'LEGACY' && (
                        <ul className="grid grid-cols-1 md:grid-cols-3 gap-3">
                            <CheckItem ok={seal.signatureValid} label={t('seal.check.signature')} t={t} />
                            <CheckItem ok={seal.payloadMatches} label={t('seal.check.payload')} t={t} />
                            <CheckItem ok={seal.documentMatches} label={t('seal.check.document')} t={t} />
                        </ul>
                    )}

                    <dl className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-4 text-sm">
                        <Item label={t('seal.signedBy')} value={[seal.verifierName, seal.verifierEmail].filter(Boolean).join(' · ')} />
                        <Item label={t('seal.signedAt')} value={formatDate(seal.signedAt, true)} />
                        <Item label={t('seal.method')} value={t(`seal.methods.${seal.method}`, { defaultValue: seal.method })} />
                        {seal.algorithm && <Item label={t('seal.algorithm')} value={`${seal.algorithm} (P-256) · ${seal.keyRef}`} />}
                        {seal.keyFingerprint && <Item label={t('seal.fingerprint')} value={seal.keyFingerprint} mono />}
                        {seal.documentSha256 && <Item label={t('seal.documentHash')} value={seal.documentSha256} mono />}
                        {seal.documentMatches === false && seal.currentDocumentSha256 && (
                            <Item label={t('seal.currentDocumentHash')} value={seal.currentDocumentSha256} mono />
                        )}
                        {seal.status === 'LEGACY' && seal.signatureHash && <Item label={t('seal.legacyHash')} value={seal.signatureHash} mono />}
                    </dl>

                    {seal.signedPayload && (
                        <details className="rounded-xl border border-outline-variant/50 bg-surface-container-low p-4">
                            <summary className="cursor-pointer font-semibold text-sm">{t('seal.technical')}</summary>
                            <p className="text-xs text-on-surface-variant mt-3">{t('seal.technicalHint')}</p>
                            {[
                                ['payload', t('seal.payload'), seal.signedPayload],
                                ['signature', t('seal.signatureValue'), seal.signatureValue],
                                ['publicKey', t('seal.publicKey'), seal.publicKeyPem],
                            ].map(([key, label, value]) => (
                                <div key={key} className="mt-4">
                                    <div className="flex items-center justify-between gap-2 mb-1">
                                        <span className="text-xs font-semibold text-on-surface-variant">{label}</span>
                                        <button type="button" onClick={() => copy(key, value)} className="text-xs font-semibold text-secondary">
                                            {copied === key ? t('seal.copied') : t('seal.copy')}
                                        </button>
                                    </div>
                                    <pre className="text-[11px] leading-snug font-mono whitespace-pre-wrap break-all p-3 rounded-lg bg-surface-container-highest max-h-48 overflow-auto">{value}</pre>
                                </div>
                            ))}
                            <p className="text-xs text-on-surface-variant mt-4">{t('seal.opensslHint')}</p>
                            <pre className="text-[11px] font-mono whitespace-pre-wrap break-all p-3 rounded-lg bg-surface-container-highest mt-1">{'base64 -d sig.b64 > sig.der\nopenssl dgst -sha256 -verify public.pem -signature sig.der payload.txt'}</pre>
                        </details>
                    )}

                    <p className="text-xs text-on-surface-variant">{t('seal.disclaimer')}</p>
                </div>
            )}
        </section>
    );
}

function CheckItem({ ok, label, t }) {
    const state = ok === true ? 'ok' : ok === false ? 'fail' : 'unknown';
    const icon = { ok: 'check_circle', fail: 'cancel', unknown: 'help' }[state];
    const cls = { ok: 'text-success', fail: 'text-error', unknown: 'text-outline' }[state];
    return (
        <li className="flex items-center gap-2 p-3 rounded-xl bg-surface-container-low border border-outline-variant/40 text-sm">
            <span className={`material-symbols-outlined text-[22px] ${cls}`}>{icon}</span>
            <span className="min-w-0">
                <span className="font-semibold block">{label}</span>
                <span className={`text-xs ${cls}`}>{t(`seal.check.${state}`)}</span>
            </span>
        </li>
    );
}

function Item({ label, value, mono }) {
    return (
        <div className="min-w-0">
            <dt className="text-on-surface-variant">{label}</dt>
            <dd className={`mt-1 font-medium ${mono ? 'font-mono text-xs break-all' : ''}`}>{value || '—'}</dd>
        </div>
    );
}
