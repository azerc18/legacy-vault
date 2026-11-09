import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'motion/react';
import { AutoHeight } from '@animate-ui/react/primitives/effects/auto-height';
import {
    ReducedMotionConfig,
    useMotionTokens,
    useShouldReduceMotion,
} from '@animate-ui/react/utils/motion';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import FilePicker from '../Common/FilePicker.jsx';
import GoogleSignInButton from '../Common/GoogleSignInButton.jsx';
import VerificationBadge from '../Common/VerificationBadge.jsx';
import { Icon, Alert, formatDate, inputClass, btnPrimary, btnOutline } from './ownerUi.jsx';
import { schemaOf } from './AssetFields.jsx';

// Cách xác thực mà FE biết vẽ. kind quyết định ô nhập ở bước hoàn tất.
// Bao phủ toàn bộ 18 giá trị trong enum AssetVerificationMethod của BE.
const METHOD_KIND = {
    OTP_EMAIL: 'code',
    SMS_CODE_MOCK: 'code',
    PAYPAL_LOGIN: 'code',
    MICRO_DEPOSIT_MOCK: 'code',
    EXCHANGE_EMAIL_CODE: 'code',
    API_READONLY_MOCK: 'code',
    SECOND_PERSON_CONFIRM: 'code',
    GOOGLE_LOGIN: 'google',
    ADDRESS_CHECKSUM: 'auto',
    BIP39_CHECK: 'auto',
    PUBLIC_BALANCE: 'auto',
    BANK_NAME_LOOKUP_MOCK: 'auto',
    BANK_FORMAT_CHECK: 'auto',
    WALLET_SIGNATURE: 'signature',
    BIO_CHALLENGE_MOCK: 'bio',
    SCREENSHOT_UPLOAD: 'file',
    FILE_HASH: 'file',
    DOCUMENT_TIMESTAMP: 'file',
    STATEMENT_UPLOAD: 'file',
};
const IS_VERIFIED = ['VERIFIED', 'VERIFIED_SANDBOX'];
const SCREENSHOT_TYPES = '.png,.jpg,.jpeg';
const DOC_TYPES = '.pdf,.doc,.docx,.png,.jpg,.jpeg';
// Định dạng tệp theo từng cách (khớp loại tệp BE nhận ở /documents/upload: PDF, DOC/DOCX, JPG/PNG)
const FILE_ACCEPT = { SCREENSHOT_UPLOAD: SCREENSHOT_TYPES, FILE_HASH: DOC_TYPES,
    DOCUMENT_TIMESTAMP: DOC_TYPES, STATEMENT_UPLOAD: '.pdf,.png,.jpg,.jpeg' };
const MAX_SIZE = 10 * 1024 * 1024;
const EMPTY_INPUT = { code: '', signature: '', bio: '', file: null };

function VerifyAssetDialogInner({ vaultId, asset, schemas, onClose, onChanged }) {
    const { t, i18n } = useTranslation('verification');
    const { spring, transition: motionTransition, scale, blur } = useMotionTokens();
    const reduce = useShouldReduceMotion();
    const [current, setCurrent] = useState(null);   // trạng thái hiện tại từ BE
    const [method, setMethod] = useState('');
    const [started, setStarted] = useState(null);   // kết quả bước start (có clientData)
    const [result, setResult] = useState(null);     // kết quả bước confirm
    const [input, setInput] = useState(EMPTY_INPUT);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [copied, setCopied] = useState(false);

    useEffect(() => {
        let alive = true;
        ownerApi.getAssetVerification(vaultId, asset.id)
            .then((r) => { if (alive) setCurrent(r); })
            .catch((e) => { if (alive) { setCurrent({}); setError(getErrorMessage(e, t('dialog.errors.load'))); } });
        return () => { alive = false; };
    }, [vaultId, asset.id, t]);

    const kind = METHOD_KIND[method];
    const methods = (current?.availableMethods?.length
        ? current.availableMethods
        : schemaOf(schemas, asset.assetType)?.verificationMethods || []
    ).filter((m) => METHOD_KIND[m]);
    const alreadyVerified = IS_VERIFIED.includes(current?.status);
    const canResume = current?.status === 'PENDING' && ['code', 'auto', 'file'].includes(METHOD_KIND[current?.method]);
    const cd = started?.clientData || {};
    const setIn = (k, v) => setInput((x) => ({ ...x, [k]: v }));

    const backToChoose = () => {
        setMethod(''); setStarted(null); setResult(null); setInput(EMPTY_INPUT); setError('');
    };

    const start = async (m) => {
        setBusy(true); setError('');
        try {
            const r = await ownerApi.startAssetVerification(vaultId, asset.id, m);
            setMethod(m); setStarted(r); setInput(EMPTY_INPUT);
        } catch (e) {
            setError(getErrorMessage(e, t('dialog.errors.start')));
        } finally {
            setBusy(false);
        }
    };

    const resume = () => { setMethod(current.method); setStarted({ clientData: {} }); setInput(EMPTY_INPUT); };

    const confirm = async (idToken) => {
        setError('');
        if (kind === 'file' && input.file && input.file.size > MAX_SIZE) return setError(t('dialog.errors.fileTooBig'));
        setBusy(true);
        try {
            let payload = {};
            if (kind === 'code') payload = { code: input.code.trim() };
            if (kind === 'signature') payload = { signature: input.signature.trim(), nonce: cd.nonce, challengeMessage: cd.challengeMessage };
            if (kind === 'bio') payload = { challengeCode: input.bio.trim() };
            if (kind === 'google') payload = { idToken };
            if (kind === 'file') {
                const doc = await ownerApi.uploadDocument(vaultId, 'OTHER', input.file);
                payload = { documentId: doc.id };
            }
            const r = await ownerApi.confirmAssetVerification(vaultId, asset.id, method, payload);
            setResult(r);
            onChanged();
        } catch (e) {
            setError(getErrorMessage(e, t('dialog.errors.confirm')));
        } finally {
            setBusy(false);
        }
    };

    const canSubmit = !busy && (
        kind === 'auto'
        || (kind === 'code' && input.code.trim())
        || (kind === 'signature' && input.signature.trim())
        || (kind === 'bio' && input.bio.trim())
        || (kind === 'file' && input.file)
    );
    const copy = (text) => {
        navigator.clipboard?.writeText(text).then(() => {
            setCopied(true);
            setTimeout(() => setCopied(false), 1800);
        }).catch(() => { });
    };
    const codeTarget = cd.maskedEmail || cd.maskedPhone || cd.expectedEmail;

    return (
        <motion.div
            initial={{ backgroundColor: 'rgba(0, 0, 0, 0)', backdropFilter: 'blur(0px)' }}
            animate={{ backgroundColor: 'rgba(0, 0, 0, 0.42)', backdropFilter: 'blur(3px)' }}
            transition={motionTransition.overlay}
            className="fixed inset-0 z-40 flex items-center justify-center px-4"
            onClick={onClose}
        >
            <motion.div
                initial={{
                    opacity: 0,
                    filter: blur.surface,
                    transform: reduce
                        ? 'scale(1)'
                        : `perspective(500px) rotateX(-12deg) scale(${scale.dialog})`,
                }}
                animate={{
                    opacity: 1,
                    filter: 'blur(0px)',
                    transform: 'perspective(500px) rotateX(0deg) scale(1)',
                }}
                transition={spring.dialog}
                className="bg-surface-container-lowest rounded-2xl p-6 w-full max-w-lg max-h-[90vh] overflow-y-auto border border-outline-variant/30 shadow-xl"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex justify-between items-start gap-3">
                    <div className="min-w-0">
                        <h3 className="font-title-lg text-title-lg">{t('dialog.title')}</h3>
                        <p className="text-sm text-on-surface-variant truncate">{asset.assetName}</p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        aria-label={t('dialog.close')}
                        className="w-8 h-8 rounded-full flex items-center justify-center hover:bg-surface-container-low transition-colors"
                    >
                        <Icon name="close" className="text-outline" />
                    </button>
                </div>

                <div className="mt-3">
                    <VerificationBadge
                        status={result?.status || current?.status || asset.verificationStatus || 'SELF_DECLARED'}
                        method={result?.method || current?.method}
                    />
                </div>

                <AutoHeight className="mt-4" transition={spring.height}>
                    <div>
                        <Alert>{error}</Alert>

                        {current === null && <p className="text-sm text-on-surface-variant">{t('dialog.loading')}</p>}

                        {/* Kết quả sau khi hoàn tất */}
                        {result && (
                            <div>
                                {IS_VERIFIED.includes(result.status) ? (
                                    <Alert type="success">
                                        {result.status === 'VERIFIED_SANDBOX' ? t('dialog.result.sandbox') : t('dialog.result.verified')}
                                        {result.expiresAt && ` ${t('dialog.result.expires', { date: formatDate(result.expiresAt) })}`}
                                    </Alert>
                                ) : (
                                    <Alert>{t('dialog.result.failed')}{result.message && ` ${result.message}`}</Alert>
                                )}
                                <div className="flex justify-end gap-2 mt-2">
                                    {!IS_VERIFIED.includes(result.status) && (
                                        <button type="button" onClick={backToChoose} className={btnOutline}>{t('dialog.retry')}</button>
                                    )}
                                    <button type="button" onClick={onClose} className={btnPrimary}>{t('dialog.close')}</button>
                                </div>
                            </div>
                        )}

                        {/* Đã xác thực và còn hiệu lực */}
                        {!result && current !== null && alreadyVerified && (
                            <Alert type="info">{t('dialog.alreadyVerified', { date: formatDate(current.expiresAt) })}</Alert>
                        )}

                        {/* Chọn cách xác thực */}
                        {!result && current !== null && !alreadyVerified && !started && (
                            methods.length === 0 ? (
                                <Alert type="info">{t('dialog.noMethods')}</Alert>
                            ) : (
                                <div>
                                    <p className="text-sm font-semibold text-on-surface-variant mb-2">{t('dialog.chooseHint')}</p>
                                    <div className="space-y-2">
                                        {methods.map((m) => (
                                            <motion.button
                                                key={m}
                                                type="button"
                                                disabled={busy}
                                                onClick={() => start(m)}
                                                whileTap={{ transform: `scale(${reduce ? 1 : scale.choice})` }}
                                                transition={spring.press}
                                                className="w-full text-left p-3.5 rounded-xl border border-outline-variant/60 hover:border-secondary hover:bg-surface-container-low transition-colors disabled:opacity-60 flex items-center justify-between gap-3"
                                            >
                                                <div className="min-w-0">
                                                    <p className="font-semibold text-on-surface">{t(`methods.${m}.label`, { defaultValue: m })}</p>
                                                    <p className="text-xs text-on-surface-variant mt-0.5">{t(`methods.${m}.desc`, { defaultValue: '' })}</p>
                                                </div>
                                                <Icon name="chevron_right" className="text-outline shrink-0" />
                                            </motion.button>
                                        ))}
                                    </div>
                                    {canResume && (
                                        <button type="button" onClick={resume} className={`${btnOutline} mt-3`}>{t('dialog.resume')}</button>
                                    )}
                                </div>
                            )
                        )}

                        {/* Nhập dữ liệu để hoàn tất */}
                        {!result && started && (
                            <div className="space-y-3">
                                <p className="font-semibold">{t(`methods.${method}.label`, { defaultValue: method })}</p>
                                {(cd.mode === 'SANDBOX') && <Alert type="info">{t('dialog.sandboxNotice')}</Alert>}

                                {kind === 'code' && (
                                    <>
                                        <p className="text-sm text-on-surface-variant">
                                            {codeTarget ? t('dialog.codeSent', { target: codeTarget }) : cd.amount ? t('dialog.microDeposit', { amount: new Intl.NumberFormat(i18n.language).format(Number(cd.amount)) }) : t('dialog.codeEnter')}
                                        </p>
                                        {cd.sandboxCode && <p className="text-sm font-mono p-2 rounded-lg bg-surface-container-low">{t('dialog.sandboxCode', { code: cd.sandboxCode })}</p>}
                                        <label className="block">
                                            <span className="text-sm font-semibold text-on-surface-variant">{t(cd.amount ? 'dialog.amountLabel' : 'dialog.codeLabel')}</span>
                                            <input value={input.code} maxLength={64} onChange={(e) => setIn('code', e.target.value)}
                                                placeholder={t(cd.amount ? 'dialog.amountPlaceholder' : 'dialog.codePlaceholder')} className={`${inputClass} mt-1`} autoComplete="one-time-code" />
                                        </label>
                                    </>
                                )}

                                {kind === 'signature' && (
                                    <>
                                        <div>
                                            <div className="flex items-center justify-between">
                                                <span className="text-sm font-semibold text-on-surface-variant">{t('dialog.challengeLabel')}</span>
                                                <button type="button" onClick={() => copy(cd.challengeMessage || '')} className="text-sm font-semibold text-primary inline-flex items-center gap-1">
                                                    <Icon name={copied ? 'check' : 'content_copy'} className="text-[15px]" />
                                                    {t('dialog.copy')}
                                                </button>
                                            </div>
                                            <pre className="mt-1 p-3 rounded-xl bg-surface-container-low text-xs whitespace-pre-wrap break-all">{cd.challengeMessage}</pre>
                                        </div>
                                        <label className="block">
                                            <span className="text-sm font-semibold text-on-surface-variant">{t('dialog.signatureLabel')}</span>
                                            <textarea rows={3} value={input.signature} onChange={(e) => setIn('signature', e.target.value)}
                                                placeholder={t('dialog.signaturePlaceholder')} className={`${inputClass} mt-1 h-auto py-2 font-mono text-xs`} />
                                        </label>
                                    </>
                                )}

                                {kind === 'bio' && (
                                    <>
                                        <p className="text-sm text-on-surface-variant">{t('dialog.bioIntro')}</p>
                                        <p className="text-lg font-mono font-bold p-3 rounded-xl bg-surface-container-low text-center">{cd.challengeCode}</p>
                                        <label className="block">
                                            <span className="text-sm font-semibold text-on-surface-variant">{t('dialog.bioLabel')}</span>
                                            <input value={input.bio} maxLength={500} onChange={(e) => setIn('bio', e.target.value)}
                                                placeholder={t('dialog.bioPlaceholder')} className={`${inputClass} mt-1`} />
                                        </label>
                                    </>
                                )}

                                {kind === 'file' && (
                                    <>
                                        <p className="text-sm text-on-surface-variant">{t(`methods.${method}.fileIntro`, { defaultValue: t('dialog.fileIntro') })}</p>
                                        <FilePicker file={input.file} onChange={(f) => setIn('file', f)} accept={FILE_ACCEPT[method] || SCREENSHOT_TYPES} />
                                    </>
                                )}

                                {kind === 'auto' && <p className="text-sm text-on-surface-variant">{t(`methods.${method}.autoIntro`, { defaultValue: t('dialog.autoIntro') })}</p>}

                                {kind === 'google' && (
                                    <>
                                        <p className="text-sm text-on-surface-variant">{t('dialog.googleIntro')}</p>
                                        {cd.expectedEmail && <p className="text-sm font-semibold">{cd.expectedEmail}</p>}
                                        <GoogleSignInButton onCredential={(token) => confirm(token)} disabled={busy} />
                                    </>
                                )}

                                <div className="flex flex-wrap justify-end gap-2 pt-1">
                                    <button type="button" onClick={backToChoose} disabled={busy} className={btnOutline}>{t('dialog.back')}</button>
                                    {kind !== 'google' && (
                                        <button type="button" onClick={() => confirm()} disabled={!canSubmit} className={btnPrimary}>
                                            {busy ? t('dialog.working') : kind === 'auto' ? t('dialog.check') : t('dialog.submit')}
                                        </button>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>
                </AutoHeight>
            </motion.div>
        </motion.div>
    );
}

export default function VerifyAssetDialog(props) {
    return (
        <ReducedMotionConfig>
            <VerifyAssetDialogInner {...props} />
        </ReducedMotionConfig>
    );
}
