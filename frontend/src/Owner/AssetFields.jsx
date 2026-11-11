import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
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

// NUMBER: số nguyên không âm (tiền VND không có phần thập phân). Chỉ bỏ dấu ngăn nghìn khi chuỗi ĐÚNG dạng nhóm 3 chữ số
// ("10.000.000", "10,000,000", "10 000 000"). Dạng khác giữ nguyên để báo lỗi rõ thay vì đổi số im lặng:
// "1.5" / "0.5" / "12,5" bị báo lỗi, không biến thành 15 / 5 / 125. FE và BE (AssetFieldsValidator) cùng một luật số nguyên.
const INT_RE = /^\d+$/;
// Giá trị tiền hợp lệ: số nguyên dương, không bắt đầu bằng 0 (chặn "0000000"), tối đa 15 chữ số. BE (AssetFieldsValidator) cùng luật.
const VALUE_RE = /^[1-9]\d{0,14}$/;
const GROUPED_RE = /^\d{1,3}([.,\s]\d{3})+$/;
export const normalizeNumber = (raw) => {
    const v = (raw || '').trim();
    return GROUPED_RE.test(v) ? v.replace(/[.,\s]/g, '') : v;
};

// Hiển thị số nguyên theo nhóm 3 chữ số cho dễ đọc: 98543000 -> "98 543 000". Chuỗi không phải số nguyên (vd "1.5") giữ nguyên để báo lỗi.
export const formatGrouped = (raw) => {
    const n = normalizeNumber(raw);
    return INT_RE.test(n) ? n.replace(/\B(?=(\d{3})+(?!\d))/g, ' ') : (raw || '');
};
// Giá trị lưu trong state: chỉ có chữ số và khoảng trắng thì bỏ khoảng trắng (khoảng trắng do chính ô này thêm vào);
// còn lại (dán "10.000.000", gõ "1.5") đi qua normalizeNumber như cũ.
const toStored = (raw) => (/^[\d\s]*$/.test(raw) ? raw.replace(/\s/g, '') : normalizeNumber(raw));

// Ô nhập số: tự chèn khoảng trắng ngăn nhóm 3 chữ số khi gõ, con trỏ giữ nguyên vị trí theo số chữ số đứng trước nó
function NumberInput({ value, onChange, ...rest }) {
    const ref = useRef(null);
    const pending = useRef(null);
    const shown = formatGrouped(value);
    useLayoutEffect(() => {
        const want = pending.current;
        pending.current = null;
        const el = ref.current;
        if (!want || !el || document.activeElement !== el || want.raw === shown) return;   // chữ không đổi: trình duyệt đã đặt đúng chỗ
        let pos = 0;
        let seen = 0;
        while (pos < shown.length && seen < want.digits) { if (/\d/.test(shown[pos++])) seen++; }
        el.setSelectionRange(pos, pos);
    });
    return (
        <input {...rest} ref={ref} type="text" inputMode="numeric" value={shown}
               onChange={(e) => {
                   const raw = e.target.value;
                   pending.current = { raw, digits: raw.slice(0, e.target.selectionStart ?? raw.length).replace(/\D/g, '').length };
                   onChange(toStored(raw));
               }} />
    );
}

// Lỗi của MỘT ô (rỗng = hợp lệ hoặc chưa nhập): kiểu dữ liệu số và pattern từ BE
export function fieldError(f, raw) {
    const t = i18n.getFixedT(null, 'vault');
    const v = (raw || '').trim();
    if (!v) return '';
    const label = t(`assetFields.labels.${f.key}`, { defaultValue: f.label });
    if (f.type === 'NUMBER') {
        const n = normalizeNumber(v);
        if (!INT_RE.test(n)) return t('assetFields.errors.numberInvalid', { label });
        if (!VALUE_RE.test(n)) return t('assetFields.errors.numberRange', { label });
    }
    if (f.pattern && !new RegExp(f.pattern).test(v)) return t('assetFields.errors.fieldInvalid', { label });
    return '';
}

// Lỗi của từng ô (key -> thông báo), gồm cả "bắt buộc" của ô còn trống. Dùng để báo lỗi ngay tại ô.
export function fieldProblems(schema, values, isEdit) {
    const t = i18n.getFixedT(null, 'vault');
    const out = {};
    for (const f of schema.fields) {
        const v = (values[f.key] || '').trim();
        if (!v) {
            if (f.required && !(isEdit && f.sensitive)) {
                out[f.key] = t('assetFields.errors.fieldRequired', { label: t(`assetFields.labels.${f.key}`, { defaultValue: f.label }) });
            }
            continue;
        }
        const bad = fieldError(f, v);
        if (bad) out[f.key] = bad;
    }
    return out;
}

// Cuộn tới ô lỗi đầu tiên (aria-invalid) và đưa con trỏ vào đó, để người dùng không phải tự tìm chỗ sai
export function focusFirstInvalid(root = document) {
    requestAnimationFrame(() => {
        const el = root.querySelector('[aria-invalid="true"]');
        if (!el) return;
        el.scrollIntoView({ block: 'center', behavior: 'smooth' });
        if (typeof el.focus === 'function') el.focus({ preventScroll: true });
    });
}

// Tên tài sản tự đặt cho các loại không phải "Khác": "<Loại> – <giá trị ô văn bản bắt buộc đầu tiên>", ví dụ "Tài khoản ngân hàng – Vietcombank"
export function deriveAssetName(typeLabel, schema, fields) {
    const key = (schema?.fields || []).find((f) => f.type === 'TEXT' && f.required)?.key;
    const v = key ? (fields?.[key] || '').trim() : '';
    return (v ? `${typeLabel} – ${v}` : typeLabel).slice(0, 200);
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

// Chỉ gửi trường có giá trị; trường NUMBER gửi chuỗi chữ số đã bỏ dấu ngăn nghìn
export function cleanFields(values, schema) {
    const numberKeys = new Set((schema?.fields || []).filter((f) => f.type === 'NUMBER').map((f) => f.key));
    const out = {};
    Object.entries(values).forEach(([k, v]) => {
        if (!v || !String(v).trim()) return;
        out[k] = numberKeys.has(k) ? normalizeNumber(String(v).trim()) : String(v).trim();
    });
    return out;
}

const MECHANISM_ICON = {
    LEGAL_CLAIM_DOSSIER: 'gavel',
    CRYPTOGRAPHIC_SPLIT_KEY: 'key',
    EXECUTOR_DISPOSITION: 'assignment_ind',
};

// Form nhập theo schema: phần nhận diện (công khai) xếp lưới 2 cột, phần bí mật gom vào khung "niêm phong".
// Mỗi ô có nhãn, ví dụ (placeholder) và gợi ý lấy từ i18n assetFields.examples / assetFields.hints (không có thì bỏ trống).
// part: 'all' (mặc định) | 'main' (ô công khai) | 'sealed' (khung niêm phong) để hộp thoại rộng chia hai cột
export function AssetFieldsForm({ schema, values, onChange, isEdit, idPrefix = 'asset-field', part = 'all', showErrors = false }) {
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
        const needed = f.required && !(isEdit && f.sensitive);
        const err = (touched[f.key] || v || showErrors) ? (fieldError(f, v) || (!v.trim() && needed && (touched[f.key] || showErrors)
            ? t('assetFields.errors.fieldRequired', { label: translatedLabel }) : '')) : '';
        const errId = err ? `${id}-err` : undefined;
        // Chỉ mô tả bằng phần tử đang hiển thị: lỗi thay chỗ gợi ý
        const describedBy = err ? errId : (hint ? hintId : undefined);
        const common = {
            id, value: v, onChange: set,
            onBlur: () => setTouched((s) => ({ ...s, [f.key]: true })),
            'aria-describedby': describedBy,
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
                    <select {...common} className={inputClass + errCls}>
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
                              className={`${inputClass} h-auto min-h-24 py-3 leading-relaxed${errCls}`} />
                )}
                {f.type === 'TEXT' && (
                    <input {...common} type="text" maxLength={500} placeholder={example} className={inputClass + errCls} />
                )}
                {f.type === 'NUMBER' && (
                    <NumberInput {...common} onChange={(next) => onChange(f.key, next)}
                                 maxLength={500} placeholder={example} className={inputClass + errCls} />
                )}
                {f.type === 'SECRET' && (
                    <div className="relative">
                        <input {...common} type={shown[f.key] ? 'text' : 'password'} maxLength={2000}
                               autoComplete="new-password" className={`${inputClass} pr-12 bg-surface-container-lowest${errCls}`}
                               placeholder={isEdit ? t('assetFields.keepCurrentPlaceholder') : example} />
                        <button type="button" onClick={() => setShown((s) => ({ ...s, [f.key]: !s[f.key] }))}
                                aria-label={translatedLabel} aria-pressed={!!shown[f.key]}
                                className="absolute right-1 top-1/2 -translate-y-1/2 w-10 h-10 rounded-lg flex items-center justify-center text-outline hover:text-on-surface">
                            <Icon name={shown[f.key] ? 'visibility_off' : 'visibility'} className="text-[20px]" />
                        </button>
                    </div>
                )}
                {err
                    // role="alert" chỉ khi đã rời ô; đang gõ thì aria-live polite để trình đọc màn hình không đọc lại từng phím
                    ? <p id={errId} role={touched[f.key] || showErrors ? 'alert' : undefined} aria-live={touched[f.key] || showErrors ? undefined : 'polite'}
                         className="mt-1.5 text-xs font-semibold text-error">{err}</p>
                    : hint && <p id={hintId} className="mt-1.5 text-xs text-on-surface-variant">{hint}</p>}
            </div>
        );
    };

    // Khung giải thích cơ chế: màn rộng (hai cột) nằm ở cột phải cho cột trái ngắn lại, màn hẹp nằm ngay trên các ô như cũ
    const mechanismBanner = (cls) => schema.mechanism && (
        <div className={`flex items-start gap-3 rounded-lg border border-secondary/30 bg-tertiary-fixed/40 p-3 ${cls}`.trim()}>
            <Icon name={MECHANISM_ICON[schema.mechanism] || 'info'} className="text-[20px] text-secondary shrink-0" />
            <p className="text-sm text-on-surface">
                {t(`assetFields.mechanisms.${schema.mechanism}`, { defaultValue: MECHANISM_TEXT[schema.mechanism] })}
            </p>
        </div>
    );

    return (
        <div className="space-y-4">
            {part !== 'sealed' && mechanismBanner(part === 'main' ? 'lg:hidden' : '')}
            {part === 'sealed' && mechanismBanner('hidden lg:flex')}
            {part !== 'sealed' && publicFields.length > 0 && (
                <div className="grid sm:grid-cols-2 gap-x-3 gap-y-4">{publicFields.map(renderField)}</div>
            )}
            {part !== 'main' && secretFields.length > 0 && (
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
                                {f.type === 'NUMBER'
                                    ? formatGrouped(fields[f.key])
                                    : t(`assetFields.options.${fields[f.key]}`, { defaultValue: OPTION_LABEL[fields[f.key]] || fields[f.key] })}
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
