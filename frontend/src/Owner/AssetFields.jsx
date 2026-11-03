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
        if (v && f.pattern && !new RegExp(f.pattern).test(v)) return t('assetFields.errors.fieldInvalid', { label: fieldLabel });
    }
    return '';
}

// Chỉ gửi trường có giá trị
export function cleanFields(values) {
    const out = {};
    Object.entries(values).forEach(([k, v]) => { if (v && String(v).trim()) out[k] = String(v).trim(); });
    return out;
}

export function AssetFieldsForm({ schema, values, onChange, isEdit }) {
    const { t } = useTranslation('vault');
    const [shown, setShown] = useState({});
    return (
        <div className="space-y-3">
            {schema.mechanism && (
                <p className="text-xs text-on-surface-variant bg-surface-container-low rounded-lg p-3">
                    {t(`assetFields.mechanisms.${schema.mechanism}`, { defaultValue: MECHANISM_TEXT[schema.mechanism] })}
                </p>
            )}
            {schema.fields.map((f) => {
                const translatedLabel = t(`assetFields.labels.${f.key}`, { defaultValue: f.label });
                const label = `${translatedLabel}${f.required ? ' *' : ''}`;
                const set = (e) => onChange(f.key, e.target.value);
                const v = values[f.key] || '';
                return (
                    <label key={f.key} className="block">
                        <span className="text-sm font-semibold text-on-surface-variant">{label}</span>
                        {f.type === 'SELECT' && (
                            <select value={v} onChange={set} className={inputClass}>
                                <option value="">{t('ui.select')}</option>
                                {f.options.map((o) => (
                                    <option key={o} value={o}>
                                        {t(`assetFields.options.${o}`, { defaultValue: OPTION_LABEL[o] || o })}
                                    </option>
                                ))}
                            </select>
                        )}
                        {f.type === 'TEXTAREA' && (
                            <textarea rows={3} maxLength={2000} value={v} onChange={set} className={inputClass} />
                        )}
                        {(f.type === 'TEXT' || f.type === 'NUMBER') && (
                            <input type={f.type === 'NUMBER' ? 'number' : 'text'} min={f.type === 'NUMBER' ? 0 : undefined}
                                   maxLength={500} value={v} onChange={set} className={inputClass} />
                        )}
                        {f.type === 'SECRET' && (
                            <div className="relative">
                                <input type={shown[f.key] ? 'text' : 'password'} value={v} onChange={set} maxLength={2000}
                                       autoComplete="new-password" className={`${inputClass} pr-12`}
                                       placeholder={isEdit ? t('assetFields.keepCurrentPlaceholder') : ''} />
                                <button type="button" onClick={() => setShown((s) => ({ ...s, [f.key]: !s[f.key] }))}
                                        className="absolute right-3 top-1/2 -translate-y-1/2 text-outline">
                                    <Icon name={shown[f.key] ? 'visibility_off' : 'visibility'} className="text-[20px]" />
                                </button>
                            </div>
                        )}
                    </label>
                );
            })}
        </div>
    );
}

// Xem chi tiết: hiện từng trường, trường bí mật bị che cho tới khi bấm mắt
export function AssetFieldsView({ schema, fields }) {
    const { t } = useTranslation('vault');
    const [shown, setShown] = useState({});
    return (
        <div className="mt-4 space-y-3">
            {schema.fields.filter((f) => fields[f.key]).map((f) => (
                <div key={f.key}>
                    <p className="text-sm font-semibold text-on-surface-variant">
                        {t(`assetFields.labels.${f.key}`, { defaultValue: f.label })}
                    </p>
                    {f.type === 'SECRET' ? (
                        <div className="mt-1 flex items-center gap-2 p-3 rounded-xl bg-surface-container-low font-mono text-sm break-all">
                            <span className="flex-1">{shown[f.key] ? fields[f.key] : '••••••••••••'}</span>
                            <button onClick={() => setShown((s) => ({ ...s, [f.key]: !s[f.key] }))}>
                                <Icon name={shown[f.key] ? 'visibility_off' : 'visibility'} className="text-[20px] text-outline" />
                            </button>
                        </div>
                    ) : (
                        <p className="mt-1 text-sm whitespace-pre-wrap break-words">
                            {t(`assetFields.options.${fields[f.key]}`, { defaultValue: OPTION_LABEL[fields[f.key]] || fields[f.key] })}
                        </p>
                    )}
                </div>
            ))}
        </div>
    );
}
