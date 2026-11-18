// Chính sách hệ thống: Admin chỉnh mặc định / giới hạn cấu hình Dead Man's Switch của Owner, và xem (chỉ đọc)
// cách dữ liệu được mã hoá, nơi lưu tệp, sao lưu CSDL.
// GET/PUT /api/admin/system-policies/dms, GET /api/admin/system-policies/system-info. Không có ô nào nhận khoá hay mật khẩu.
import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnOutline, btnPrimary, formatDate, Icon, inputClass, LoadingSkeleton } from '../Owner/ownerUi.jsx';
import { AdminLayout, Dossier, Stamp } from './adminUi.jsx';

const RANGE_KEYS = ['checkinIntervalDays', 'gracePeriodDays', 'reminderCount'];
const RANGE_PARTS = ['min', 'defaultValue', 'max'];
// Ô một giá trị (không phải khoảng Owner chọn): số ngày chờ Executor dự phòng (BR-03) và số giờ chờ Trusted Confirmer.
// hardLimits của BE chỉ trả số tối đa cho 2 ô này; số tối thiểu khớp DmsPolicy.HARD_*_MIN.
const SINGLE_KEYS = ['backupActivationWaitDays', 'confirmerWaitHours'];
const SINGLE_MIN = { backupActivationWaitDays: 1, confirmerWaitHours: 12 };
// Giá trị trước khi có trang này; nút "Khôi phục mặc định" đưa về các số này
const ORIGINAL_SINGLE = { backupActivationWaitDays: 14, confirmerWaitHours: 72 };

const draftOf = (p) => ({
    ...Object.fromEntries(RANGE_KEYS.map((k) => [k, { ...p[k] }])),
    ...Object.fromEntries(SINGLE_KEYS.map((k) => [k, p[k] ?? ORIGINAL_SINGLE[k]])),
});

// Lỗi của một dòng, kiểm giống BE (DmsPolicyService.validate) để nút Lưu khoá sớm và chỉ đúng dòng sai
function rangeError(r, hard) {
    const v = RANGE_PARTS.map((k) => Number(r[k]));
    if (v.some((n) => !Number.isInteger(n))) return 'notInteger';
    const [min, def, max] = v;
    if (min < hard.min || max > hard.max) return 'outsideHard';
    if (min > max) return 'minAboveMax';
    if (def < min || def > max) return 'defaultOutside';
    return null;
}

export default function AdminSystemPolicies() {
    const { t } = useTranslation('admin');
    const [data, setData] = useState(null);      // AdminDmsPolicyResponse
    const [info, setInfo] = useState(null);      // SystemInfoResponse
    const [draft, setDraft] = useState(null);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const apply = (res) => {
        setData(res);
        setDraft(draftOf(res.policy));
    };

    useEffect(() => {
        adminApi.getDmsPolicy().then(apply).catch((e) => setError(getErrorMessage(e, t('systemPolicies.loadError'))));
        // Thông tin chỉ đọc tải riêng: lỗi ở đây không chặn việc sửa chính sách
        adminApi.getSystemInfo().then(setInfo).catch(() => setInfo(false));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps -- t chỉ dùng cho thông báo lỗi

    const errors = useMemo(() => {
        if (!draft || !data) return {};
        const out = {};
        RANGE_KEYS.forEach((k) => {
            const e = rangeError(draft[k], data.hardLimits[k]);
            if (e) out[k] = e;
        });
        SINGLE_KEYS.forEach((k) => {
            const w = Number(draft[k]);
            const max = data.hardLimits[k] ?? ORIGINAL_SINGLE[k];
            if (!Number.isInteger(w) || w < SINGLE_MIN[k] || w > max) out[k] = 'outsideHard';
        });
        return out;
    }, [draft, data]);

    const draftKey = draft ? JSON.stringify(normalize(draft)) : '';
    const dirty = Boolean(data && draft) && draftKey !== JSON.stringify(normalize(draftOf(data.policy)));
    const invalid = Object.keys(errors).length > 0;

    // Xem trước trước khi lưu: số DMS đang chạy sẽ nằm ngoài khoảng đang nhập. Chờ 400 ms sau lần gõ cuối;
    // kết quả gắn với đúng bản nháp đã hỏi (key) nên số cũ không hiện cho bản nháp mới.
    const [impact, setImpact] = useState(null);   // { key, count } — count null = không tính được
    useEffect(() => {
        if (!dirty || invalid) return undefined;
        let alive = true;
        const timer = setTimeout(() => {
            adminApi.previewDmsPolicyImpact(JSON.parse(draftKey))
                .then((r) => { if (alive) setImpact({ key: draftKey, count: r.configsOutsideLimits }); })
                .catch(() => { if (alive) setImpact({ key: draftKey, count: null }); });
        }, 400);
        return () => { alive = false; clearTimeout(timer); };
    }, [draftKey, dirty, invalid]);
    const preview = dirty && !invalid ? (impact?.key === draftKey ? impact : 'pending') : null;

    const setPart = (key, part, value) => setDraft((d) => ({ ...d, [key]: { ...d[key], [part]: value } }));

    const resetToOriginal = () => setDraft({
        ...Object.fromEntries(RANGE_KEYS.map((k) => [k, { ...data.hardLimits[k] }])),
        ...ORIGINAL_SINGLE,
    });

    const save = async () => {
        setSaving(true); setError(''); setSuccess('');
        try {
            apply(await adminApi.updateDmsPolicy(normalize(draft)));
            setSuccess(t('systemPolicies.saved'));
        } catch (err) {
            setError(getErrorMessage(err, t('systemPolicies.saveError')));
        } finally {
            setSaving(false);
        }
    };

    return (
        <AdminLayout title={t('systemPolicies.title')} subtitle={t('systemPolicies.subtitle')}>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            {!data && !error && <LoadingSkeleton label={t('loading')} />}

            {data && draft && (
                <Dossier title={t('systemPolicies.dms.title')} className="mb-6"
                         right={<Stamp tone="info" icon="schedule">{t('systemPolicies.dms.appliesNext')}</Stamp>}>
                    <p className="text-sm text-on-surface-variant mb-4">{t('systemPolicies.dms.desc')}</p>

                    <div className="overflow-x-auto">
                        <table className="w-full text-sm" data-testid="dms-policy-table">
                            <thead>
                                <tr className="text-left text-outline">
                                    <th className="py-2 pr-3 font-semibold">{t('systemPolicies.dms.setting')}</th>
                                    {RANGE_PARTS.map((p) => <th key={p} className="py-2 px-2 font-semibold w-28">{t(`systemPolicies.dms.${p}`)}</th>)}
                                </tr>
                            </thead>
                            <tbody>
                                {RANGE_KEYS.map((k) => (
                                    <tr key={k} className="border-t border-outline-variant/40 align-top">
                                        <td className="py-3 pr-3">
                                            <p className="font-semibold">{t(`systemPolicies.dms.fields.${k}`)}</p>
                                            <p className="text-xs text-outline">
                                                {t('systemPolicies.dms.hardLimit', { min: data.hardLimits[k].min, max: data.hardLimits[k].max })}
                                            </p>
                                            {errors[k] && <p className="text-xs text-error mt-1">{t(`systemPolicies.dms.errors.${errors[k]}`, { min: data.hardLimits[k].min, max: data.hardLimits[k].max })}</p>}
                                        </td>
                                        {RANGE_PARTS.map((p) => (
                                            <td key={p} className="py-3 px-2">
                                                <input type="number" inputMode="numeric" aria-label={`${t(`systemPolicies.dms.fields.${k}`)} – ${t(`systemPolicies.dms.${p}`)}`}
                                                       min={data.hardLimits[k].min} max={data.hardLimits[k].max}
                                                       value={draft[k][p]} onChange={(e) => setPart(k, p, e.target.value)}
                                                       className={`${inputClass} ${errors[k] ? 'border-error' : ''}`} />
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                                {SINGLE_KEYS.map((k) => (
                                    <SingleRow key={k} t={t} k={k} min={SINGLE_MIN[k]} max={data.hardLimits[k]}
                                               hint={t(`systemPolicies.dms.hints.${k}`, { min: SINGLE_MIN[k], max: data.hardLimits[k] })}
                                               error={errors[k]} value={draft[k]}
                                               onChange={(v) => setDraft((d) => ({ ...d, [k]: v }))} />
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {/* Chưa sửa: số DMS ngoài khoảng ĐANG lưu. Đang sửa: xem trước theo khoảng đang nhập */}
                    {!dirty && data.configsOutsideLimits > 0 && <OutsideBox testId="dms-policy-outside" text={t('systemPolicies.dms.outside', { count: data.configsOutsideLimits })} />}
                    {preview === 'pending' && (
                        <p className="mt-4 text-sm text-outline flex gap-2" aria-live="polite" data-testid="dms-policy-impact-pending">
                            <Icon name="hourglass_top" className="text-[18px]" />{t('systemPolicies.dms.impactPending')}
                        </p>
                    )}
                    {preview && preview !== 'pending' && preview.count > 0 && (
                        <OutsideBox testId="dms-policy-impact" text={t('systemPolicies.dms.impact', { count: preview.count })} />
                    )}
                    {preview && preview !== 'pending' && preview.count === 0 && (
                        <p className="mt-4 text-sm text-on-surface-variant flex gap-2" aria-live="polite" data-testid="dms-policy-impact-none">
                            <Icon name="check_circle" className="text-[18px] text-primary" />{t('systemPolicies.dms.impactNone')}
                        </p>
                    )}
                    {preview && preview !== 'pending' && preview.count == null && (
                        <p className="mt-4 text-sm text-outline flex gap-2" data-testid="dms-policy-impact-error">
                            <Icon name="info" className="text-[18px]" />{t('systemPolicies.dms.impactError')}
                        </p>
                    )}

                    <ul className="text-xs text-on-surface-variant mt-4 space-y-1">
                        <li className="flex gap-1.5"><Icon name="person" className="text-[16px]" />{t('systemPolicies.dms.noteOwner')}</li>
                        <li className="flex gap-1.5"><Icon name="lock_clock" className="text-[16px]" />{t('systemPolicies.dms.noteRunning')}</li>
                        <li className="flex gap-1.5"><Icon name="group" className="text-[16px]" />{t('systemPolicies.dms.noteBackup')}</li>
                        <li className="flex gap-1.5"><Icon name="verified_user" className="text-[16px]" />{t('systemPolicies.dms.noteConfirmer')}</li>
                    </ul>

                    <div className="flex flex-wrap items-center justify-between gap-3 mt-5">
                        <p className="text-xs text-outline">
                            {data.updatedAt
                                ? t('systemPolicies.updated', { when: formatDate(data.updatedAt), who: data.updatedByEmail || '—' })
                                : t('systemPolicies.neverUpdated')}
                        </p>
                        <div className="flex gap-2">
                            <button type="button" onClick={resetToOriginal} className={btnOutline}>
                                <Icon name="restart_alt" className="text-[18px]" /> {t('systemPolicies.reset')}
                            </button>
                            <button type="button" onClick={save} disabled={!dirty || invalid || saving} className={btnPrimary} data-testid="dms-policy-save">
                                <Icon name="save" className="text-[18px]" /> {saving ? t('wait') : t('systemPolicies.save')}
                            </button>
                        </div>
                    </div>
                </Dossier>
            )}

            {info === false && <Alert>{t('systemPolicies.info.loadError')}</Alert>}
            {info && <SystemInfo t={t} info={info} />}

            <p className="text-xs text-on-surface-variant flex gap-1.5 mt-4"><Icon name="history" className="text-[16px]" />{t('systemPolicies.auditHint')}</p>
        </AdminLayout>
    );
}

function OutsideBox({ text, testId }) {
    return (
        <div className="mt-4 rounded-xl border border-warning/25 bg-warning-container/85 text-on-warning-container px-4 py-3 text-sm flex gap-2"
             aria-live="polite" data-testid={testId}>
            <Icon name="warning" className="text-[18px]" />
            <span>{text}</span>
        </div>
    );
}

// Ô nhập trả chuỗi; gửi BE và so sánh "đã sửa chưa" theo số
function normalize(d) {
    return {
        ...Object.fromEntries(RANGE_KEYS.map((k) => [k, Object.fromEntries(RANGE_PARTS.map((p) => [p, Number(d[k][p])]))])),
        ...Object.fromEntries(SINGLE_KEYS.map((k) => [k, Number(d[k])])),
    };
}

function SingleRow({ t, k, min, max, hint, error, value, onChange }) {
    return (
        <tr className="border-t border-outline-variant/40 align-top">
            <td className="py-3 pr-3">
                <p className="font-semibold">{t(`systemPolicies.dms.fields.${k}`)}</p>
                <p className="text-xs text-outline">{hint}</p>
                {error && <p className="text-xs text-error mt-1">{t('systemPolicies.dms.errors.outsideHard', { min, max })}</p>}
            </td>
            <td className="py-3 px-2" />
            <td className="py-3 px-2">
                <input type="number" inputMode="numeric" aria-label={t(`systemPolicies.dms.fields.${k}`)}
                       min={min} max={max} value={value} onChange={(e) => onChange(e.target.value)}
                       className={`${inputClass} ${error ? 'border-error' : ''}`} />
            </td>
            <td className="py-3 px-2" />
        </tr>
    );
}

// Thông tin chỉ đọc: mã hoá, lưu trữ, sao lưu. Chỉ có "đã đặt / chưa đặt", không bao giờ có giá trị khoá.
function SystemInfo({ t, info }) {
    const { crypto, storage, backup } = info;
    const yesNo = (ok, okKey = 'set', noKey = 'notSet', noTone = 'danger') => (
        <Stamp tone={ok ? 'ok' : noTone}>{t(`systemPolicies.info.${ok ? okKey : noKey}`)}</Stamp>
    );
    return (
        <div className="grid gap-6 lg:grid-cols-3">
            <Dossier title={t('systemPolicies.info.crypto.title')} right={<Icon name="encrypted" className="text-[20px] text-primary" />}>
                <dl className="text-sm space-y-3">
                    <Row label={t('systemPolicies.info.crypto.data')} value={<span className="lv-mono font-semibold">{crypto.dataEncryption}</span>} />
                    <Row label={t('systemPolicies.info.crypto.masterKey')} value={yesNo(crypto.masterKeyConfigured)} />
                    <Row label={t('systemPolicies.info.crypto.password')} value={<span className="lv-mono font-semibold">{crypto.passwordHashing}</span>} />
                    <Row label={t('systemPolicies.info.crypto.otp')} value={<span className="lv-mono font-semibold">{crypto.otpHashing}</span>} />
                    <Row label={t('systemPolicies.info.crypto.otpKey')} value={yesNo(crypto.otpKeySeparate, 'separate', 'derived', 'muted')} />
                    <Row label={t('systemPolicies.info.crypto.identityKey')} value={yesNo(crypto.identityKeySeparate, 'separate', 'derived', 'muted')} />
                </dl>
                <p className="text-xs text-on-surface-variant mt-4 flex gap-1.5"><Icon name="key" className="text-[16px]" />{t('systemPolicies.info.crypto.hint')}</p>
            </Dossier>

            <Dossier title={t('systemPolicies.info.storage.title')} right={<Icon name="cloud" className="text-[20px] text-primary" />}>
                <dl className="text-sm space-y-3">
                    <Row label={t('systemPolicies.info.storage.provider')}
                         value={<span className="font-semibold">{t(`systemPolicies.info.storage.providers.${storage.provider}`, { defaultValue: storage.provider })}</span>} />
                    {storage.bucket && <Row label={t('systemPolicies.info.storage.bucket')} value={<span className="lv-mono font-semibold">{storage.bucket}</span>} />}
                    <Row label={t('systemPolicies.info.storage.encrypted')} value={yesNo(storage.encryptedAtRest, 'yes', 'no')} />
                    <Row label={t('systemPolicies.info.schemaVersion')} value={<span className="lv-mono font-semibold">{info.schemaVersion || '—'}</span>} />
                </dl>
                <p className="text-xs text-on-surface-variant mt-4 flex gap-1.5"><Icon name="info" className="text-[16px]" />{t('systemPolicies.info.storage.hint')}</p>
            </Dossier>

            <Dossier title={t('systemPolicies.info.backup.title')} right={<Icon name="backup" className="text-[20px] text-primary" />}>
                {backup.provider ? (
                    <dl className="text-sm space-y-3">
                        <Row label={t('systemPolicies.info.backup.provider')} value={<span className="font-semibold">{backup.provider}</span>} />
                        <Row label={t('systemPolicies.info.backup.retention')}
                             value={<span className="font-semibold">{backup.retentionDays ? t('systemPolicies.info.backup.days', { count: backup.retentionDays }) : '—'}</span>} />
                        {backup.note && <p className="text-sm text-on-surface-variant">{backup.note}</p>}
                    </dl>
                ) : (
                    <div className="text-sm flex items-center gap-2"><Stamp tone="warn">{t('systemPolicies.info.backup.notDeclared')}</Stamp></div>
                )}
                <p className="text-xs text-on-surface-variant mt-4 flex gap-1.5"><Icon name="settings" className="text-[16px]" />{t('systemPolicies.info.backup.hint')}</p>
            </Dossier>
        </div>
    );
}

function Row({ label, value }) {
    return (
        <div className="flex items-center justify-between gap-3">
            <dt className="text-outline">{label}</dt>
            <dd className="text-right">{value}</dd>
        </div>
    );
}
