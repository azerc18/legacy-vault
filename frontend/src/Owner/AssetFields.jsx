import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ownerApi } from '../api/ownerApi.js';
import { Icon, inputClass } from './ownerUi.jsx';
import i18n from '../i18n/index.js';

// N3: schema các loại tài sản lấy từ BE (GET /api/asset-types), cache trong bộ nhớ
let cache = null;

export function useAssetSchemas() {
    const [schemas, setSchemas] = useState(cache);
    useEffect(() => {
        if (cache) return;
        let alive = true;
        ownerApi.getAssetTypes().then((list) => {
            cache = list;
            if (alive) setSchemas(list);
        }).catch(() => { if (alive) setSchemas([]); });
        return () => { alive = false; };
    }, []);
    return schemas;   // null = đang tải, [] = lỗi
}

export const schemaOf = (schemas, type) => (schemas || []).find((s) => s.assetType === type);

export const MECHANISM_TEXT = {
    LEGAL_CLAIM_DOSSIER: 'Beneficiary completes a legal claim with this dossier.',
    CRYPTOGRAPHIC_SPLIT_KEY: 'Secret goes to exactly one beneficiary.',
    EXECUTOR_DISPOSITION: 'The Executor handles it following your wish.',
};

// Nhãn dễ đọc cho giá trị SELECT (BE trả giá trị thô)
export const OPTION_LABEL = {
    MEMORIALIZE: 'Memorialize the account',
    DELETE: 'Delete the account',
    TRANSFER_DATA: 'Download data for the family',
};

const NUMBER_RE = /^\d+(\.\d+)?$/;

// Lỗi của MỘT ô (rỗng = hợp lệ hoặc chưa nhập): kiểu dữ liệu số và pattern từ BE
export function fieldError(f, raw) {
    const t = i18n.getFixedT(null, 'vault');
    const v = (raw || '').trim();
    if (!v) return '';
    const label = t(`assetFields.labels.${f.key}`, { defaultValue: f.label });
    if (f.type === 'NUMBER' && !NUMBER_RE.test(v)) return t('assetFields.errors.numberInvalid', { label });
    if (f.pattern && !new RegExp(f.pattern).test(v)) return t('assetFields.errors.fieldInvalid', { label });
    return '';
}

// Trả về thông báo lỗi đầu tiên hoặc ''
export function validateFields(schema, values, isEdit) {
    const t = i18n.getFixedT(null, 'vault');
    // Ví crypto cần seed phrase hoặc private key (khi sửa, để trống = giữ nguyên bí mật cũ)
    if (schema.assetType === 'CRYPTO_WALLET' && !isEdit
        && !(values.seedPhrase || '').trim() && !(values.privateKey || '').trim()) {
        return t('assetFields.errors.cryptoSecretRequired');
    }
    for (const f of schema.fields) {
        const v = (values[f.key] || '').trim();
        const fieldLabel = t(`assetFields.labels.${f.key}`, { defaultValue: f.label });
        if (!v && f.required && !(isEdit && f.sensitive)) return t('assetFields.errors.fieldRequired', { label: fieldLabel });
        const bad = fieldError(f, v);
        if (bad) return bad;
    }
    return '';
}

// Chỉ gửi trường có giá trị
export function cleanFields(values) {
    const out = {};
    Object.entries(values).forEach(([k, v]) => { if (v && String(v).trim()) out[k] = String(v).trim(); });
    return out;
}

const MECHANISM_ICON = {
    LEGAL_CLAIM_DOSSIER: 'gavel',
    CRYPTOGRAPHIC_SPLIT_KEY: 'key',
    EXECUTOR_DISPOSITION: 'assignment_ind',
};

// Form nhập theo schema: phần nhận diện (công khai) xếp lưới 2 cột, phần bí mật gom vào khung "niêm phong".
// Mỗi ô có nhãn, ví dụ (placeholder) và gợi ý lấy từ i18n assetFields.examples / assetFields.hints (không có thì bỏ trống).
export function AssetFieldsForm({ schema, values, onChange, isEdit, idPrefix = 'asset-field' }) {
    const { t } = useTranslation('vault');
    const [shown, setShown] = useState({});
    const [touched, setTouched] = useState({});
    const publicFields = schema.fields.filter((f) => f.type !== 'SECRET');
    const secretFields = schema.fields.filter((f) => f.type === 'SECRET');

    const renderField = (f) => {
        const id = `${idPrefix}-${f.key}`;
        const translatedLabel = t(`assetFields.labels.${f.key}`, { defaultValue: f.label });
        const example = t(`assetFields.examples.${f.key}`, { defaultValue: '' });
        const hint = t(`assetFields.hints.${f.key}`, { defaultValue: '' });
        const hintId = hint ? `${id}-hint` : undefined;
        const set = (e) => onChange(f.key, e.target.value);
        const v = values[f.key] || '';
        // Không dùng thuộc tính required của trình duyệt: hàng tài sản để trống phải được bỏ qua, validateFields lo phần kiểm tra
        // Báo lỗi kiểu dữ liệu ngay khi gõ sai (ô đã có giá trị) hoặc khi rời ô
        const err = (touched[f.key] || v) ? fieldError(f, v) : '';
        const errId = err ? `${id}-err` : undefined;
        const common = {
            id, value: v, onChange: set,
            onBlur: () => setTouched((s) => ({ ...s, [f.key]: true })),
            'aria-describedby': [hintId, errId].filter(Boolean).join(' ') || undefined,
            'aria-invalid': err ? true : undefined,
            'aria-required': f.required && !(isEdit && f.sensitive),
        };
        const errCls = err ? ' lv-field-invalid' : '';
        return (
            <div key={f.key} className={f.type === 'TEXTAREA' ? 'sm:col-span-2' : ''}>
                <label htmlFor={id} className="block text-sm font-semibold text-on-surface mb-1.5">
                    {translatedLabel}{f.required && <span className="text-primary"> *</span>}
                </label>
                {f.type === 'SELECT' && (
                    <select {...common} className={inputClass}>
                        <option value="">{t('ui.select')}</option>
                        {f.options.map((o) => (
                            <option key={o} value={o}>
                                {t(`assetFields.options.${o}`, { defaultValue: OPTION_LABEL[o] || o })}
                            </option>
                        ))}
                    </select>
                )}
                {f.type === 'TEXTAREA' && (
                    <textarea {...common} rows={3} maxLength={2000} placeholder={example}
                              className={`${inputClass} h-auto min-h-24 py-3 leading-relaxed`} />
                )}
                {(f.type === 'TEXT' || f.type === 'NUMBER') && (
                    <input {...common} type="text"
                           inputMode={f.type === 'NUMBER' ? 'decimal' : undefined}
                           maxLength={500} placeholder={example} className={inputClass + errCls} />
                )}
                {f.type === 'SECRET' && (
                    <div className="relative">
                        <input {...common} type={shown[f.key] ? 'text' : 'password'} maxLength={2000}
                               autoComplete="new-password" className={`${inputClass} pr-12 bg-surface-container-lowest`}
                               placeholder={isEdit ? t('assetFields.keepCurrentPlaceholder') : example} />
                        <button type="button" onClick={() => setShown((s) => ({ ...s, [f.key]: !s[f.key] }))}
                                aria-label={translatedLabel} aria-pressed={!!shown[f.key]}
                                className="absolute right-1 top-1/2 -translate-y-1/2 w-10 h-10 rounded-lg flex items-center justify-center text-outline hover:text-on-surface">
                            <Icon name={shown[f.key] ? 'visibility_off' : 'visibility'} className="text-[20px]" />
                        </button>
                    </div>
                )}
                {err
                    ? <p id={errId} role="alert" className="mt-1.5 text-xs font-semibold text-error">{err}</p>
                    : hint && <p id={hintId} className="mt-1.5 text-xs text-on-surface-variant">{hint}</p>}
            </div>
        );
    };

    return (
        <div className="space-y-4">
            {schema.mechanism && (
                <div className="flex items-start gap-3 rounded-lg border border-secondary/30 bg-tertiary-fixed/40 p-3">
                    <Icon name={MECHANISM_ICON[schema.mechanism] || 'info'} className="text-[20px] text-secondary shrink-0" />
                    <p className="text-sm text-on-surface">
                        {t(`assetFields.mechanisms.${schema.mechanism}`, { defaultValue: MECHANISM_TEXT[schema.mechanism] })}
                    </p>
                </div>
            )}
            {publicFields.length > 0 && (
                <div className="grid sm:grid-cols-2 gap-x-3 gap-y-4">{publicFields.map(renderField)}</div>
            )}
            {secretFields.length > 0 && (
                <div className="rounded-xl border border-dashed border-primary/40 bg-surface-container-low p-4 space-y-4">
                    <div className="flex items-start gap-3">
                        <span className="w-9 h-9 shrink-0 rounded-full border-2 border-primary text-primary flex items-center justify-center">
                            <Icon name="lock" className="text-[18px]" />
                        </span>
                        <div>
                            <p className="font-semibold text-on-surface">{t('assetFields.sections.sealed')}</p>
                            <p className="text-xs text-on-surface-variant">
                                {schema.assetType === 'CRYPTO_WALLET'
                                    ? t('assetFields.errors.cryptoSecretRequired')
                                    : t('assetFields.sections.sealedHint')}
                            </p>
                        </div>
                    </div>
                    {secretFields.map(renderField)}
                </div>
            )}
        </div>
    );
}

// Xem chi tiết: hiện từng trường dạng thẻ gọn gàng, tách biệt trường công khai và phần niêm phong bảo mật
export function AssetFieldsView({ schema, fields }) {
    const { t } = useTranslation('vault');
    const [shown, setShown] = useState({});

    const activeFields = schema.fields.filter((f) => fields[f.key]);
    const publicFields = activeFields.filter((f) => f.type !== 'SECRET');
    const secretFields = activeFields.filter((f) => f.type === 'SECRET');

    return (
        <div className="space-y-4 my-4">
            {publicFields.length > 0 && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {publicFields.map((f) => (
                        <div key={f.key} className="p-3 rounded-xl bg-surface-container-low/70 dark:bg-white/[0.04] border border-outline-variant/25 dark:border-white/10">
                            <p className="text-[11px] font-bold uppercase tracking-wider text-on-surface-variant/75 dark:text-white/60 mb-1">
                                {t(`assetFields.labels.${f.key}`, { defaultValue: f.label })}
                            </p>
                            <p className="text-sm font-semibold text-on-surface dark:text-white break-words whitespace-pre-wrap">
                                {t(`assetFields.options.${fields[f.key]}`, { defaultValue: OPTION_LABEL[fields[f.key]] || fields[f.key] })}
                            </p>
                        </div>
                    ))}
                </div>
            )}

            {secretFields.length > 0 && (
                <div className="rounded-2xl border border-primary/30 bg-primary/5 dark:bg-primary/10 p-4 space-y-3">
                    <div className="flex items-center gap-1.5 text-primary font-bold text-xs uppercase tracking-wider mb-1">
                        <Icon name="lock" className="text-[16px]" />
                        <span>{t('assetFields.sections.sealed', { defaultValue: 'Phần niêm phong bảo mật' })}</span>
                    </div>
                    {secretFields.map((f) => (
                        <div key={f.key}>
                            <p className="text-xs font-semibold text-on-surface-variant dark:text-white/70 mb-1">
                                {t(`assetFields.labels.${f.key}`, { defaultValue: f.label })}
                            </p>
                            <div className="flex items-center gap-2 p-3 rounded-xl bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/30 dark:border-white/15 font-mono text-sm break-all">
                                <span className="flex-1 font-semibold text-on-surface dark:text-white">
                                    {shown[f.key] ? fields[f.key] : '••••••••••••'}
                                </span>
                                <button
                                    type="button"
                                    onClick={() => setShown((s) => ({ ...s, [f.key]: !s[f.key] }))}
                                    className="w-8 h-8 rounded-lg flex items-center justify-center text-outline hover:text-on-surface dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 transition-colors"
                                >
                                    <Icon name={shown[f.key] ? 'visibility_off' : 'visibility'} className="text-[20px]" />
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
