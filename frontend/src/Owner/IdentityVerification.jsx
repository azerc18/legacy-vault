// KYC Owner: xác minh danh tính (CCCD + số điện thoại) để bật Dead Man's Switch.
// Luồng: đồng ý điều khoản -> chọn cách (Quét QR / Chụp ảnh / Tải ảnh / Nhập tay) -> hệ thống đọc thông tin
// -> xem lại và sửa -> xác minh số điện thoại bằng OTP -> lưu -> quay lại trang đã gọi (returnTo, vd. /owner/dms).
import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { identityApi } from '../api/identityApi.js';
import { getErrorCode, getErrorMessage } from '../api/http.js';
import { useToast } from '../Common/Toast.jsx';
import { displayVnPhone, formatPhoneInput, parseVnPhone } from '../Common/vnPhone.js';
import {
    Alert, Card, ConfirmDialog, Icon, LoadingSkeleton, OwnerLayout, btnOutline, btnPrimary, formatDate, inputClass,
} from './ownerUi.jsx';
import QrScanner from './identity/QrScanner.jsx';
import { decodeQrFromFile, looksLikeCitizenQr } from './identity/qrDecode.js';
import { digitsOnly, sameName, validateIdentity } from './identity/citizenId.js';

const EMPTY_FORM = {
    citizenId: '', oldIdNumber: '', fullName: '', dateOfBirth: '', gender: '', residenceProvince: '', source: 'MANUAL',
};

// Chỉ quay lại trang nội bộ của Owner (tránh chuyển hướng ra ngoài qua tham số returnTo)
const safeReturnTo = (v) => (v && /^\/owner\/[\w\-/]*$/.test(v) ? v : null);

const METHODS = [
    { key: 'qr', icon: 'qr_code_scanner' },
    { key: 'camera', icon: 'photo_camera' },
    { key: 'upload', icon: 'upload_file' },
    { key: 'manual', icon: 'edit_note' },
];

export default function IdentityVerification() {
    const { t } = useTranslation(['identity', 'common']);
    const toast = useToast();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const returnTo = safeReturnTo(params.get('returnTo'));

    const [status, setStatus] = useState(null);
    const [loading, setLoading] = useState(true);
    const [step, setStep] = useState('summary');   // summary | consent | method | scan | review
    const [dataConsent, setDataConsent] = useState(false);
    const [aiConsent, setAiConsent] = useState(false);
    const [form, setForm] = useState(EMPTY_FORM);
    const [warnings, setWarnings] = useState([]);
    // Tỉnh gợi ý theo 3 số đầu CCCD khi không đọc được tỉnh từ địa chỉ: chỉ đưa lên đầu danh sách, không chọn sẵn.
    // Giữ kèm 3 số đầu lúc nhận kết quả để bỏ gợi ý khi người dùng sửa số CCCD sang mã tỉnh khác.
    const [suggestion, setSuggestion] = useState({ province: '', prefix: '' });
    const [reading, setReading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [nameDialog, setNameDialog] = useState(false);

    // Số điện thoại + OTP
    const [phone, setPhone] = useState('');
    const [otp, setOtp] = useState(null);          // PhoneOtpResponse
    const [code, setCode] = useState('');
    const [phoneBusy, setPhoneBusy] = useState(false);
    const [phoneError, setPhoneError] = useState('');
    const [cooldown, setCooldown] = useState(0);
    const [phoneConfirmedNow, setPhoneConfirmedNow] = useState(false);

    const cameraInput = useRef(null);
    const uploadInput = useRef(null);

    useEffect(() => {
        identityApi.getStatus()
            .then((s) => {
                setStatus(s);
                setPhone(displayVnPhone(s.phoneNumber));
                setStep(s.verified ? 'summary' : 'consent');
            })
            .catch((err) => setError(getErrorMessage(err, t('errors.load'))))
            .finally(() => setLoading(false));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps -- t chỉ dùng cho câu lỗi

    useEffect(() => {
        if (cooldown <= 0) return undefined;
        const id = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(id);
    }, [cooldown]);

    const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

    const fillFrom = (data) => {
        setForm({
            citizenId: data.citizenId || '',
            oldIdNumber: data.oldIdNumber || '',
            fullName: data.fullName || '',
            dateOfBirth: data.dateOfBirth || '',
            gender: data.gender || '',
            residenceProvince: data.residenceProvince || '',
            source: data.source || 'MANUAL',
        });
        setWarnings(data.warnings || []);
        setSuggestion(data.residenceProvince || !data.suggestedProvince
            ? { province: '', prefix: '' }
            : { province: data.suggestedProvince, prefix: (data.citizenId || '').replace(/\D/g, '').slice(0, 3) });
        setStep('review');
    };

    const readQr = async (text) => {
        setError(''); setReading(true);
        try {
            fillFrom(await identityApi.extractFromQr(text));
        } catch (err) {
            setError(getErrorMessage(err, t('errors.qr')));
            setStep('method');
        } finally {
            setReading(false);
        }
    };

    // Chụp / tải ảnh: thử đọc QR ngay trên máy trước (không cần AI); không thấy QR mới gửi ảnh cho AI đọc
    const onImage = async (e) => {
        const file = e.target.files?.[0];
        e.target.value = '';
        if (!file) return;
        setError(''); setReading(true);
        try {
            const qr = await decodeQrFromFile(file);
            if (qr && looksLikeCitizenQr(qr)) {
                fillFrom(await identityApi.extractFromQr(qr));
                toast.success(t('method.qrFoundInPhoto'));
                return;
            }
            if (!aiConsent) {
                setError(t('method.needAiConsent'));
                return;
            }
            fillFrom(await identityApi.extractFromImage(file, true));
        } catch (err) {
            setError(getErrorMessage(err, t('errors.image')));
        } finally {
            setReading(false);
        }
    };

    const chooseMethod = (key) => {
        setError('');
        if (key === 'qr') setStep('scan');
        else if (key === 'camera') cameraInput.current?.click();
        else if (key === 'upload') uploadInput.current?.click();
        else fillFrom({ ...EMPTY_FORM, fullName: status?.accountFullName || '' });
    };

    const startUpdate = () => {
        setDataConsent(false);
        setPhoneConfirmedNow(false);
        setOtp(null);
        setStep('consent');
    };

    /* ---------- Số điện thoại ---------- */
    const parsedPhone = parseVnPhone(phone);
    const phoneMatchesVerified = status?.phoneVerified && parsedPhone.ok
        && parseVnPhone(status.phoneNumber).ok && parseVnPhone(status.phoneNumber).e164 === parsedPhone.e164;
    // Cập nhật thông tin đã xác minh thì phải xác minh lại số (xác thực lại); lần đầu thì số đã xác minh là đủ
    const phoneReady = phoneConfirmedNow || (!status?.verified && phoneMatchesVerified);

    const phoneProblem = (p) => (p.problem === 'LEGACY_11_DIGIT' && p.suggestion
        ? t('phone.errors.LEGACY_11_DIGIT_SUGGEST', { suggestion: displayVnPhone(p.suggestion) })
        : t(`phone.errors.${p.problem}`));

    const sendOtp = async () => {
        setPhoneError('');
        if (!parsedPhone.ok) return setPhoneError(phoneProblem(parsedPhone));
        setPhoneBusy(true);
        try {
            const res = await identityApi.sendPhoneOtp(parsedPhone.e164);
            setOtp(res);
            setCode('');
            setCooldown(res.resendAfterSeconds || 60);
        } catch (err) {
            const retry = err?.response?.data?.data?.retryAfterSeconds;
            if (retry) setCooldown(Number(retry));
            setPhoneError(getErrorMessage(err, t('phone.errors.send')));
        } finally {
            setPhoneBusy(false);
        }
    };

    const verifyOtp = async () => {
        setPhoneError('');
        if (!/^\d{6}$/.test(code)) return setPhoneError(t('phone.errors.codeFormat'));
        setPhoneBusy(true);
        try {
            const s = await identityApi.verifyPhoneOtp(code);
            setStatus(s);
            setPhone(displayVnPhone(s.phoneNumber));
            setOtp(null);
            setPhoneConfirmedNow(true);
            toast.success(t('phone.verified'));
        } catch (err) {
            setPhoneError(getErrorMessage(err, t('phone.errors.verify')));
        } finally {
            setPhoneBusy(false);
        }
    };

    /* ---------- Lưu ---------- */
    const submit = async (updateAccountName = false) => {
        setError('');
        const problem = validateIdentity(form);
        if (problem) return setError(t(`review.errors.${problem}`));
        if (!phoneReady) return setError(t('review.errors.phoneFirst'));
        if (!updateAccountName && !sameName(form.fullName, status?.accountFullName)) {
            setNameDialog(true);
            return;
        }
        setNameDialog(false);
        setSaving(true);
        try {
            const old = digitsOnly(form.oldIdNumber);
            const s = await identityApi.submit({
                citizenId: digitsOnly(form.citizenId),
                oldIdNumber: old || null,
                fullName: form.fullName.trim(),
                dateOfBirth: form.dateOfBirth,
                gender: form.gender,
                residenceProvince: form.residenceProvince,
                source: form.source,
                dataConsent,
                aiOcrConsent: form.source === 'OCR' ? aiConsent : false,
                updateAccountName,
            });
            setStatus(s);
            toast.success(t('saved'));
            if (returnTo) navigate(returnTo);
            else setStep('summary');
        } catch (err) {
            if (getErrorCode(err) === 'IDENTITY_NAME_MISMATCH') setNameDialog(true);
            else setError(getErrorMessage(err, t('errors.save')));
        } finally {
            setSaving(false);
        }
    };

    const provinceName = (code) => status?.provinces?.find((p) => p.code === code)?.name || code;
    // Gợi ý chỉ còn đúng khi số CCCD vẫn bắt đầu bằng 3 số lúc đọc thẻ (OCR có thể đọc sai và người dùng sửa lại)
    const suggestedProvince = suggestion.province
        && form.citizenId.replace(/\D/g, '').startsWith(suggestion.prefix) ? suggestion.province : '';
    const provinceOptions = (status?.provinces || []).map((p) => <option key={p.code} value={p.code}>{p.name}</option>);

    return (
        <OwnerLayout title={t('title')} subtitle={t('subtitle')} back={returnTo ? { to: returnTo } : undefined}>
            <Alert>{error}</Alert>
            {status?.locked && <Alert type="warning">{t('locked')}</Alert>}

            {loading ? <LoadingSkeleton label={t('loading')} /> : (
                <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-6 items-start">
                    <div className="space-y-6">
                        {step === 'summary' && status?.verified && (
                            <Card title={t('summary.title')} icon="verified_user">
                                <dl className="grid sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
                                    <Row label={t('fields.citizenId')} value={status.citizenIdMasked} />
                                    <Row label={t('fields.fullName')} value={status.fullName} />
                                    <Row label={t('fields.dateOfBirth')} value={formatDate(status.dateOfBirth)} />
                                    <Row label={t('fields.gender')} value={t(`gender.${status.gender}`)} />
                                    <Row label={t('fields.province')} value={provinceName(status.residenceProvince)} />
                                    <Row label={t('fields.phone')} value={displayVnPhone(status.phoneNumber)} />
                                    <Row label={t('summary.source')} value={t(`source.${status.source}`)} />
                                    <Row label={t('summary.verifiedAt')} value={formatDate(status.verifiedAt, true)} />
                                </dl>
                                <div className="mt-6 flex flex-wrap gap-3 justify-end">
                                    {!status.locked && (
                                        <button type="button" onClick={startUpdate} className={btnOutline}>
                                            <Icon name="edit" className="text-[18px]" /> {t('summary.update')}
                                        </button>
                                    )}
                                    {returnTo && (
                                        <button type="button" onClick={() => navigate(returnTo)} className={btnPrimary}>
                                            {t('actions.continue')} <Icon name="arrow_forward" className="text-[18px]" />
                                        </button>
                                    )}
                                </div>
                            </Card>
                        )}

                        {step === 'consent' && (
                            <Card title={t('consent.title')} icon="privacy_tip">
                                <ul className="space-y-2 text-sm text-on-surface-variant list-disc pl-5">
                                    <li>{t('consent.purpose')}</li>
                                    <li>{t('consent.stored')}</li>
                                    <li>{t('consent.notStored')}</li>
                                    <li>{t('consent.rights')}</li>
                                </ul>
                                <label className="mt-5 flex items-start gap-3 text-sm cursor-pointer">
                                    <input type="checkbox" checked={dataConsent} onChange={(e) => setDataConsent(e.target.checked)}
                                           className="mt-0.5 w-4 h-4 accent-primary" />
                                    <span>{t('consent.agree')}</span>
                                </label>
                                <div className="mt-6 flex justify-end gap-3">
                                    {status?.verified && (
                                        <button type="button" onClick={() => setStep('summary')} className={btnOutline}>{t('common:actions.cancel')}</button>
                                    )}
                                    <button type="button" disabled={!dataConsent} onClick={() => setStep('method')} className={btnPrimary}>
                                        {t('actions.continue')} <Icon name="arrow_forward" className="text-[18px]" />
                                    </button>
                                </div>
                            </Card>
                        )}

                        {step === 'method' && (
                            <Card title={t('method.title')} icon="badge">
                                <p className="text-sm text-on-surface-variant mb-4">{t('method.hint')}</p>
                                <div className="grid sm:grid-cols-2 gap-3">
                                    {METHODS.map((m) => (
                                        <button key={m.key} type="button" disabled={reading} onClick={() => chooseMethod(m.key)}
                                                className="text-left p-4 rounded-2xl border border-outline-variant hover:border-primary hover:bg-surface-container-low disabled:opacity-60 transition">
                                            <span className="flex items-center gap-2 font-semibold">
                                                <Icon name={m.icon} className="text-secondary" /> {t(`method.${m.key}.label`)}
                                                {m.key === 'qr' && <span className="ml-auto text-xs px-2 py-0.5 rounded-full bg-success-container text-on-success-container">{t('method.recommended')}</span>}
                                            </span>
                                            <span className="block mt-1 text-sm text-on-surface-variant">{t(`method.${m.key}.desc`)}</span>
                                        </button>
                                    ))}
                                </div>
                                <label className="mt-5 flex items-start gap-3 text-sm cursor-pointer">
                                    <input type="checkbox" checked={aiConsent} onChange={(e) => setAiConsent(e.target.checked)}
                                           className="mt-0.5 w-4 h-4 accent-primary" />
                                    <span>{t('method.aiConsent')}</span>
                                </label>
                                {reading && <p className="mt-4 text-sm text-on-surface-variant flex items-center gap-2"><Icon name="hourglass_top" className="text-[18px]" /> {t('method.reading')}</p>}
                                {/* capture="environment": điện thoại mở thẳng camera sau; máy tính mở hộp chọn file */}
                                <input ref={cameraInput} type="file" accept="image/*" capture="environment" className="hidden" onChange={onImage} />
                                <input ref={uploadInput} type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={onImage} />
                            </Card>
                        )}

                        {step === 'scan' && (
                            <Card title={t('method.qr.label')} icon="qr_code_scanner">
                                {reading
                                    ? <p className="text-sm text-on-surface-variant">{t('method.reading')}</p>
                                    : <QrScanner onResult={readQr} onCancel={() => setStep('method')} />}
                            </Card>
                        )}

                        {step === 'review' && (
                            <>
                                <Card title={t('review.title')} icon="fact_check">
                                    <p className="text-sm text-on-surface-variant mb-4">{t(`review.hint.${form.source}`)}</p>
                                    {warnings.filter((w) => w !== 'REVIEW_AI_RESULT').map((w) => (
                                        <Alert key={w} type="warning">
                                            {/* Đã sửa số CCCD sang mã tỉnh khác: gợi ý cũ không còn đúng, quay về câu "chưa xác định" */}
                                            {w === 'PROVINCE_SUGGESTED_FROM_ID' && !suggestedProvince
                                                ? t('review.warnings.PROVINCE_NOT_DETECTED')
                                                : t(`review.warnings.${w}`, { province: provinceName(suggestedProvince) })}
                                        </Alert>
                                    ))}
                                    <div className="grid sm:grid-cols-2 gap-4">
                                        <Field label={t('fields.citizenId')} hint={t('fields.citizenIdHint')}>
                                            <input value={form.citizenId} onChange={set('citizenId')} inputMode="numeric" maxLength={15}
                                                   autoComplete="off" placeholder="0792 0112 3456" className={inputClass} />
                                        </Field>
                                        <Field label={t('fields.oldId')} hint={t('fields.oldIdHint')}>
                                            <input value={form.oldIdNumber} onChange={set('oldIdNumber')} inputMode="numeric" maxLength={12}
                                                   autoComplete="off" className={inputClass} />
                                        </Field>
                                        <Field label={t('fields.fullName')}>
                                            <input value={form.fullName} onChange={set('fullName')} maxLength={100} autoComplete="name" className={inputClass} />
                                        </Field>
                                        <Field label={t('fields.dateOfBirth')}>
                                            <input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} className={inputClass} />
                                        </Field>
                                        <Field label={t('fields.gender')}>
                                            <select value={form.gender} onChange={set('gender')} className={inputClass}>
                                                <option value="">{t('fields.choose')}</option>
                                                <option value="MALE">{t('gender.MALE')}</option>
                                                <option value="FEMALE">{t('gender.FEMALE')}</option>
                                            </select>
                                        </Field>
                                        <Field label={t('fields.province')} hint={t('fields.provinceHint')}>
                                            <select value={form.residenceProvince} onChange={set('residenceProvince')} className={inputClass}>
                                                <option value="">{t('fields.choose')}</option>
                                                {suggestedProvince && (
                                                    <optgroup label={t('fields.suggested')}>
                                                        <option value={suggestedProvince}>{provinceName(suggestedProvince)}</option>
                                                    </optgroup>
                                                )}
                                                {suggestedProvince
                                                    ? <optgroup label={t('fields.allProvinces')}>{provinceOptions}</optgroup>
                                                    : provinceOptions}
                                            </select>
                                        </Field>
                                    </div>
                                </Card>

                                <Card title={t('phone.title')} icon="smartphone">
                                    <Alert>{phoneError}</Alert>
                                    {phoneReady ? (
                                        <p className="text-sm flex items-center gap-2">
                                            <Icon name="check_circle" className="text-success" /> {t('phone.ready', { phone: displayVnPhone(status?.phoneNumber) })}
                                        </p>
                                    ) : (
                                        <>
                                            {status?.verified && <p className="text-sm text-on-surface-variant mb-3">{t('phone.reverify')}</p>}
                                            <div className="flex flex-col sm:flex-row gap-3">
                                                <input type="tel" inputMode="tel" autoComplete="tel" value={phone}
                                                       onChange={(e) => setPhone(formatPhoneInput(e.target.value))}
                                                       placeholder="0912 345 678" className={inputClass} />
                                                <button type="button" onClick={sendOtp} disabled={phoneBusy || cooldown > 0} className={`${btnOutline} shrink-0`}>
                                                    {cooldown > 0 ? t('phone.resendIn', { seconds: cooldown }) : (otp ? t('phone.resend') : t('phone.send'))}
                                                </button>
                                            </div>
                                            {phone && parsedPhone.ok && <p className="mt-1 text-xs text-on-surface-variant">{t('phone.normalized', { phone: parsedPhone.display })}</p>}
                                            {otp && (
                                                <div className="mt-4">
                                                    <p className="text-sm text-on-surface-variant">{t('phone.sent', { phone: otp.maskedPhone, carrier: otp.carrier })}</p>
                                                    {otp.sandboxCode && <p className="mt-1 text-xs text-on-surface-variant">{t('phone.sandbox', { code: otp.sandboxCode })}</p>}
                                                    <div className="mt-3 flex flex-col sm:flex-row gap-3">
                                                        <input value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                                               inputMode="numeric" autoComplete="one-time-code" placeholder="123456" className={inputClass} />
                                                        <button type="button" onClick={verifyOtp} disabled={phoneBusy} className={`${btnPrimary} shrink-0`}>
                                                            {t('phone.verify')}
                                                        </button>
                                                    </div>
                                                </div>
                                            )}
                                        </>
                                    )}
                                </Card>

                                <div className="flex flex-wrap justify-end gap-3">
                                    <button type="button" onClick={() => setStep('method')} className={btnOutline}>{t('actions.back')}</button>
                                    <button type="button" onClick={() => submit(false)} disabled={saving || status?.locked} className={btnPrimary}>
                                        <Icon name="verified_user" className="text-[18px]" /> {saving ? t('actions.saving') : t('actions.save')}
                                    </button>
                                </div>
                            </>
                        )}
                    </div>

                    <aside className="space-y-4">
                        <Card title={t('why.title')} icon="help">
                            <ul className="space-y-2 text-sm text-on-surface-variant list-disc pl-5">
                                <li>{t('why.dms')}</li>
                                <li>{t('why.deathCertificate')}</li>
                                <li>{t('why.encrypted')}</li>
                            </ul>
                        </Card>
                    </aside>
                </div>
            )}

            <ConfirmDialog
                open={nameDialog}
                title={t('nameMismatch.title')}
                message={t('nameMismatch.message', { card: form.fullName.trim(), account: status?.accountFullName })}
                confirmLabel={t('nameMismatch.confirm')}
                busy={saving}
                onConfirm={() => submit(true)}
                onCancel={() => setNameDialog(false)}
            />
        </OwnerLayout>
    );
}

function Field({ label, hint, children }) {
    return (
        <label className="block">
            <span className="block text-sm font-semibold mb-1.5">{label}</span>
            {children}
            {hint && <span className="block mt-1 text-xs text-on-surface-variant">{hint}</span>}
        </label>
    );
}

function Row({ label, value }) {
    return (
        <div>
            <dt className="text-on-surface-variant">{label}</dt>
            <dd className="font-semibold">{value || '—'}</dd>
        </div>
    );
}
