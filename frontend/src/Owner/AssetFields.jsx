import React, { useEffect, useState } from 'react';
import { ownerApi } from '../api/ownerApi.js';
import { Icon, inputClass } from './ownerUi.jsx';

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

// Trả về thông báo lỗi đầu tiên hoặc ''
export function validateFields(schema, values, isEdit) {
    for (const f of schema.fields) {
        const v = (values[f.key] || '').trim();
        if (!v && f.required && !(isEdit && f.sensitive)) return `${f.label} is required.`;
        if (v && f.pattern && !new RegExp(f.pattern).test(v)) return `${f.label} has an invalid format.`;
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
    const [shown, setShown] = useState({});
    return (
        <div className="space-y-3">
            {schema.mechanism && (
                <p className="text-xs text-on-surface-variant bg-surface-container-low rounded-lg p-3">
                    {MECHANISM_TEXT[schema.mechanism]}
                </p>
            )}
            {schema.fields.map((f) => {
                const label = `${f.label}${f.required ? ' *' : ''}`;
                const set = (e) => onChange(f.key, e.target.value);
                const v = values[f.key] || '';
                return (
                    <label key={f.key} className="block">
                        <span className="text-sm font-semibold text-on-surface-variant">{label}</span>
                        {f.type === 'SELECT' && (
                            <select value={v} onChange={set} className={inputClass}>
                                <option value="">Select...</option>
                                {f.options.map((o) => <option key={o} value={o}>{o}</option>)}
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
                                       placeholder={isEdit ? 'Leave empty to keep current' : ''} />
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
    const [shown, setShown] = useState({});
    return (
        <div className="mt-4 space-y-3">
            {schema.fields.filter((f) => fields[f.key]).map((f) => (
                <div key={f.key}>
                    <p className="text-sm font-semibold text-on-surface-variant">{f.label}</p>
                    {f.type === 'SECRET' ? (
                        <div className="mt-1 flex items-center gap-2 p-3 rounded-xl bg-surface-container-low font-mono text-sm break-all">
                            <span className="flex-1">{shown[f.key] ? fields[f.key] : '••••••••••••'}</span>
                            <button onClick={() => setShown((s) => ({ ...s, [f.key]: !s[f.key] }))}>
                                <Icon name={shown[f.key] ? 'visibility_off' : 'visibility'} className="text-[20px] text-outline" />
                            </button>
                        </div>
                    ) : (
                        <p className="mt-1 text-sm whitespace-pre-wrap break-words">{fields[f.key]}</p>
                    )}
                </div>
            ))}
        </div>
    );
}
