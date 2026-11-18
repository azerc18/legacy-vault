// Tích hợp: trạng thái các dịch vụ bên ngoài (eKYC, AI/OCR, ký xác nhận, thanh toán, email, CAPTCHA, đăng nhập Google)
// và cờ eKYC mô phỏng Admin bật / tắt được ngay (trước đây chỉ có biến môi trường APP_KYC_MOCK_ENABLED).
// GET /api/admin/integrations, PUT /api/admin/integrations/kyc-mock. KHÔNG có ô nhập key: key chỉ ở biến môi trường.
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnPrimary, formatDate, Icon, LoadingSkeleton } from '../Owner/ownerUi.jsx';
import { AdminLayout, Dossier, Stamp } from './adminUi.jsx';

// Lựa chọn eKYC mô phỏng: 'env' = theo biến môi trường (gửi null), 'on' / 'off' = ghi đè
const CHOICES = ['env', 'on', 'off'];
const choiceOf = (override) => (override == null ? 'env' : override ? 'on' : 'off');
const valueOf = (choice) => (choice === 'env' ? null : choice === 'on');
const MIN_REASON = 5;

export default function AdminIntegrations() {
    const { t } = useTranslation('admin');
    const [data, setData] = useState(null);
    const [choice, setChoice] = useState('env');
    const [reason, setReason] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const apply = (res) => {
        setData(res);
        setChoice(choiceOf(res.kyc.mockOverride));
    };

    useEffect(() => {
        adminApi.getIntegrations().then(apply).catch((e) => setError(getErrorMessage(e, t('integrations.loadError'))));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps -- t chỉ dùng cho thông báo lỗi

    const save = async () => {
        setSaving(true); setError(''); setSuccess('');
        try {
            apply(await adminApi.updateKycMock({ enabled: valueOf(choice), reason: reason.trim() }));
            setReason('');
            setSuccess(t('integrations.kyc.saved'));
        } catch (err) {
            setError(getErrorMessage(err, t('integrations.saveError')));
        } finally {
            setSaving(false);
        }
    };

    const ok = (cond, okKey = 'configured', noKey = 'notConfigured', noTone = 'muted') => (
        <Stamp tone={cond ? 'ok' : noTone}>{t(`integrations.${cond ? okKey : noKey}`)}</Stamp>
    );
    const mode = (sandbox) => <Stamp tone={sandbox ? 'warn' : 'ok'}>{t(sandbox ? 'integrations.sandbox' : 'integrations.live')}</Stamp>;

    return (
        <AdminLayout title={t('integrations.title')} subtitle={t('integrations.subtitle')}>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            {!data && !error && <LoadingSkeleton label={t('loading')} />}

            {data && (() => {
                const { kyc, ai, signature, payments, mail, captcha, googleLogin } = data;
                const dirty = choice !== choiceOf(kyc.mockOverride);
                // Hiệu lực SAU khi lưu lựa chọn đang chọn, để cảnh báo đúng lúc Admin sắp bật mock
                const willBeOn = choice === 'env' ? kyc.envDefault : choice === 'on';
                const reasonOk = reason.trim().length >= MIN_REASON;
                return (
                    <>
                        {/* eKYC: phần duy nhất chỉnh được */}
                        <Dossier title={t('integrations.kyc.title')} className="mb-6"
                                 right={<Stamp tone={kyc.mockEnabled ? 'warn' : 'muted'} icon="badge">
                                     {t(kyc.mockEnabled ? 'integrations.kyc.mockOn' : 'integrations.kyc.mockOff')}
                                 </Stamp>}>
                            <p className="text-sm text-on-surface-variant mb-4">{t('integrations.kyc.desc')}</p>

                            <fieldset className="grid gap-2 sm:grid-cols-3" data-testid="kyc-mock-choice">
                                <legend className="sr-only">{t('integrations.kyc.title')}</legend>
                                {CHOICES.map((c) => (
                                    <label key={c} className={`flex items-start gap-2 rounded-xl border px-3 py-2.5 cursor-pointer text-sm ${
                                        choice === c ? 'border-primary bg-primary/5' : 'border-outline-variant'}`}>
                                        <input type="radio" name="kyc-mock" value={c} checked={choice === c}
                                               onChange={() => setChoice(c)} className="mt-1" />
                                        <span>
                                            <span className="font-semibold block">{t(`integrations.kyc.choices.${c}`)}</span>
                                            <span className="text-xs text-outline">
                                                {c === 'env'
                                                    ? t('integrations.kyc.envNow', { state: t(kyc.envDefault ? 'integrations.on' : 'integrations.off') })
                                                    : t(`integrations.kyc.choiceHints.${c}`)}
                                            </span>
                                        </span>
                                    </label>
                                ))}
                            </fieldset>

                            {dirty && willBeOn && (
                                <div className="mt-4 rounded-xl border border-error/25 bg-error-container/85 text-on-error-container px-4 py-3 text-sm flex gap-2" data-testid="kyc-mock-warning">
                                    <Icon name="gpp_maybe" className="text-[18px]" />
                                    <span>{t('integrations.kyc.onWarning')}</span>
                                </div>
                            )}

                            {dirty && (
                                <label className="block mt-4 text-sm font-semibold">{t('integrations.kyc.reason')}
                                    <textarea rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)}
                                              placeholder={t('integrations.kyc.reasonPlaceholder')}
                                              className="lv-glass-inline w-full mt-1 px-4 py-2 rounded-xl border border-outline-variant bg-background font-normal focus:outline-none focus:border-primary" />
                                    {!reasonOk && <span className="text-xs text-outline font-normal">{t('integrations.kyc.reasonHint', { min: MIN_REASON })}</span>}
                                </label>
                            )}

                            <div className="grid gap-x-8 gap-y-2 sm:grid-cols-2 text-sm mt-4">
                                <Row label={t('integrations.kyc.provider')} value={<span className="font-semibold">{t('integrations.kyc.providerMock')}</span>} />
                                <Row label={t('integrations.kyc.sms')} value={<Stamp tone={kyc.smsSandbox ? 'warn' : 'ok'}>{t(kyc.smsSandbox ? 'integrations.kyc.smsSandbox' : 'integrations.kyc.smsReal')}</Stamp>} />
                            </div>

                            <div className="flex flex-wrap items-center justify-between gap-3 mt-5">
                                <p className="text-xs text-outline">
                                    {kyc.updatedAt
                                        ? t('integrations.updated', { when: formatDate(kyc.updatedAt), who: kyc.updatedByEmail || '—' })
                                        : t('integrations.kyc.neverUpdated')}
                                </p>
                                <button type="button" onClick={save} disabled={!dirty || !reasonOk || saving} className={btnPrimary} data-testid="kyc-mock-save">
                                    <Icon name="save" className="text-[18px]" /> {saving ? t('wait') : t('integrations.kyc.save')}
                                </button>
                            </div>
                        </Dossier>

                        {/* Chỉ đọc */}
                        <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
                            <Dossier title={t('integrations.ai.title')} right={<Icon name="document_scanner" className="text-[20px] text-primary" />}>
                                <dl className="text-sm space-y-3">
                                    <Row label={t('integrations.ai.provider')} value={<span className="lv-mono font-semibold">{ai.provider}</span>} />
                                    <Row label="Gemini" value={ok(ai.geminiKeyConfigured, 'configured', 'notConfigured', ai.provider === 'gemini' ? 'danger' : 'muted')} />
                                    <Row label={t('integrations.ai.geminiBackup')} value={ok(ai.geminiBackupKeyConfigured)} />
                                    <Row label="Groq" value={ok(ai.groqKeyConfigured)} />
                                    <Row label="DeepSeek" value={ok(ai.deepseekKeyConfigured)} />
                                    {ai.ocrDemoScenarios && <Row label={t('integrations.ai.demo')} value={<Stamp tone="warn">{t('integrations.on')}</Stamp>} />}
                                </dl>
                                {ai.provider !== 'gemini' && <p className="text-xs text-on-surface-variant mt-3">{t('integrations.ai.mockNote')}</p>}
                                <Link to="/admin/ai-settings" className="text-sm font-semibold text-primary inline-flex items-center gap-1 mt-4">
                                    {t('integrations.ai.link')} <Icon name="arrow_forward" className="text-[16px]" />
                                </Link>
                            </Dossier>

                            <Dossier title={t('integrations.signature.title')} right={<Icon name="draw" className="text-[20px] text-primary" />}>
                                <dl className="text-sm space-y-3">
                                    <Row label={t('integrations.signature.method')} value={<span className="font-semibold">{t(`integrations.signature.methods.${signature.verifierAuthMethod}`, { defaultValue: signature.verifierAuthMethod })}</span>} />
                                    <Row label={t('integrations.signature.ca')} value={ok(signature.caProviderIntegrated, 'integrated', 'notIntegrated')} />
                                </dl>
                                <p className="text-xs text-on-surface-variant mt-3">{t('integrations.signature.hint')}</p>
                            </Dossier>

                            <Dossier title={t('integrations.payments.title')} right={<Icon name="payments" className="text-[20px] text-primary" />}>
                                <dl className="text-sm space-y-3">
                                    <Row label="VNPay" value={<span className="flex gap-1.5">{ok(payments.vnpay.configured, 'configured', 'notConfigured', 'danger')}{payments.vnpay.configured && mode(payments.vnpay.sandbox)}</span>} />
                                    <Row label="PayPal" value={<span className="flex gap-1.5">{ok(payments.paypal.configured, 'configured', 'hidden')}{payments.paypal.configured && mode(payments.paypal.sandbox)}</span>} />
                                    <Row label={t('integrations.payments.reconcile')} value={<Stamp tone={payments.reconcileEnabled ? 'ok' : 'warn'}>{t(payments.reconcileEnabled ? 'integrations.on' : 'integrations.off')}</Stamp>} />
                                </dl>
                                <p className="text-xs text-on-surface-variant mt-3">{t('integrations.payments.hint')}</p>
                            </Dossier>

                            <Dossier title={t('integrations.mail.title')} right={<Icon name="mail" className="text-[20px] text-primary" />}>
                                <dl className="text-sm space-y-3">
                                    <Row label={t('integrations.mail.host')} value={<span className="lv-mono font-semibold">{mail.host || '—'}</span>} />
                                    <Row label={t('integrations.mail.account')} value={ok(mail.configured, 'configured', 'notConfigured', 'danger')} />
                                </dl>
                                <Link to="/admin/notifications" className="text-sm font-semibold text-primary inline-flex items-center gap-1 mt-4">
                                    {t('integrations.mail.link')} <Icon name="arrow_forward" className="text-[16px]" />
                                </Link>
                            </Dossier>

                            <Dossier title={t('integrations.captcha.title')} right={<Icon name="verified_user" className="text-[20px] text-primary" />}>
                                <dl className="text-sm space-y-3">
                                    <Row label={t('integrations.captcha.state')} value={<Stamp tone={captcha.enabled ? 'ok' : 'muted'}>{t(captcha.enabled ? 'integrations.on' : 'integrations.off')}</Stamp>} />
                                    <Row label={t('integrations.captcha.siteKey')} value={ok(captcha.siteKeyConfigured)} />
                                    <Row label={t('integrations.captcha.secretKey')} value={ok(captcha.secretKeyConfigured)} />
                                </dl>
                                {captcha.enabled && !(captcha.siteKeyConfigured && captcha.secretKeyConfigured) && (
                                    <p className="text-xs text-error mt-3">{t('integrations.captcha.missingKeys')}</p>
                                )}
                            </Dossier>

                            <Dossier title={t('integrations.google.title')} right={<Icon name="login" className="text-[20px] text-primary" />}>
                                <dl className="text-sm space-y-3">
                                    <Row label={t('integrations.google.clientId')} value={ok(googleLogin.configured, 'configured', 'hidden')} />
                                </dl>
                                <p className="text-xs text-on-surface-variant mt-3">{t('integrations.google.hint')}</p>
                            </Dossier>
                        </div>
                    </>
                );
            })()}

            <p className="text-xs text-on-surface-variant flex gap-1.5 mt-4"><Icon name="key" className="text-[16px]" />{t('integrations.keyHint')}</p>
            <p className="text-xs text-on-surface-variant flex gap-1.5 mt-1"><Icon name="history" className="text-[16px]" />{t('integrations.auditHint')}</p>
        </AdminLayout>
    );
}

function Row({ label, value }) {
    return (
        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
            <dt className="text-outline">{label}</dt>
            <dd className="text-right">{value}</dd>
        </div>
    );
}
