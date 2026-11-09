import React, {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Navigate, useNavigate} from 'react-router-dom';
import {getUser} from '../auth/auth.js';
import {ownerApi} from '../api/ownerApi.js';
import {getErrorMessage} from '../api/http.js';
import {OwnerLayout, Card, Icon, Alert, ASSET_TYPES, inputClass, btnPrimary, btnOutline} from './ownerUi.jsx';
import {AssetTypeIcon} from './assetArt.jsx';
import {AssetFieldsForm, cleanFields, schemaOf, useAssetSchemas, validateFields} from './AssetFields.jsx';

const emptyAsset = () => ({assetType: 'BANK_ACCOUNT', assetName: '', secret: '', notes: '', fields: {}});
const hasFieldValues = (fields) => Object.values(fields || {}).some((v) => v && String(v).trim());
const emptyBeneficiary = () => ({email: '', fullName: '', relationship: ''});

export default function CreateVault() {
    const {t} = useTranslation(['vault', 'enums', 'common']);
    const user = getUser();
    const navigate = useNavigate();
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [assets, setAssets] = useState([emptyAsset()]);
    const [beneficiaries, setBeneficiaries] = useState([emptyBeneficiary()]);
    const [showSecret, setShowSecret] = useState({});
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    // Két đã tạo nhưng một số tài sản theo schema lưu lỗi: không cho gửi lại (tránh tạo trùng két)
    const [partial, setPartial] = useState(null);
    const schemas = useAssetSchemas();   // null = đang tải, [] = lỗi -> dùng form cũ (1 ô bí mật)

    if (!user) return <Navigate to="/login" replace/>;

    const updateAsset = (i, field, value) =>
        setAssets((list) => list.map((a, idx) => (idx === i ? {...a, [field]: value} : a)));
    const removeAsset = (i) => setAssets((list) => list.filter((_, idx) => idx !== i));
    const changeAssetType = (i, assetType) =>
        setAssets((list) => list.map((a, idx) => (idx === i ? {...a, assetType, fields: {}} : a)));
    const setAssetField = (i, key, value) =>
        setAssets((list) => list.map((a, idx) => (idx === i ? {...a, fields: {...a.fields, [key]: value}} : a)));
    const updateBeneficiary = (i, field, value) =>
        setBeneficiaries((list) => list.map((b, idx) => (idx === i ? {...b, [field]: value} : b)));
    const removeBeneficiaryRow = (i) => setBeneficiaries((list) => list.filter((_, idx) => idx !== i));

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        // BE không validate name/secret nên FE phải chặn trước
        if (!name.trim()) return setError(t('create.errors.nameRequired'));
        // Tài sản có schema (N3) nhập theo từng trường; không có schema thì dùng 1 ô bí mật như cũ
        const filled = assets
            .map((a, i) => ({...a, index: i + 1, schema: schemaOf(schemas, a.assetType)}))
            .filter((a) => a.assetName.trim() || a.secret.trim() || hasFieldValues(a.fields));
        for (const a of filled) {
            if (!a.assetName.trim()) return setError(t('create.errors.assetIncomplete'));
            if (a.schema) {
                const msg = validateFields(a.schema, a.fields, false);
                if (msg) return setError(`${t('create.assetItem', {index: a.index})}: ${msg}`);
            } else if (!a.secret.trim()) return setError(t('create.errors.assetIncomplete'));
        }
        const typed = filled.filter((a) => a.schema);
        const legacy = filled.filter((a) => !a.schema);
        // N1: người thụ hưởng bắt buộc, không trùng nhau, không trùng chính Owner
        const emails = beneficiaries.map((b) => b.email.trim().toLowerCase()).filter(Boolean);
        if (emails.length === 0) return setError(t('create.errors.beneficiaryRequired'));
        if (new Set(emails).size !== emails.length) return setError(t('create.errors.duplicateEmail'));
        if (emails.includes((user.email || '').toLowerCase())) return setError(t('create.errors.selfBeneficiary'));

        setLoading(true);
        try {
            const vault = await ownerApi.createVault({
                name: name.trim(),
                description: description.trim() || null,
                beneficiaries: beneficiaries
                    .filter((b) => b.email.trim())
                    .map((b) => ({
                        email: b.email.trim(),
                        fullName: b.fullName.trim() || null,
                        relationship: b.relationship.trim() || null,
                    })),
                assets: legacy.map((a) => ({
                    assetType: a.assetType,
                    assetName: a.assetName.trim(),
                    secret: a.secret,
                    notes: a.notes.trim() || null,
                })),
            });
            // API tạo két chỉ nhận secret thô, nên tài sản theo schema được thêm ngay sau khi két có id
            let failed = 0;
            for (const a of typed) {
                try {
                    await ownerApi.addAsset(vault.id, {
                        assetType: a.assetType,
                        assetName: a.assetName.trim(),
                        fields: cleanFields(a.fields),
                        notes: a.notes.trim() || null,
                    });
                } catch {
                    failed += 1;
                }
            }
            if (failed > 0) {
                setPartial({vaultId: vault.id, failed});
                return;
            }
            navigate(`/owner/vaults/${vault.id}`, {state: {created: true}});
        } catch (err) {
            setError(getErrorMessage(err, t('create.errors.createFailed')));
        } finally {
            setLoading(false);
        }
    };

    return (
        <OwnerLayout title={t('create.title')} subtitle={t('create.subtitle')}>
            <form onSubmit={handleSubmit} className="space-y-6">
                <Alert>{error}</Alert>

                <div data-tour="vault-info">
                <Card title={t('create.detailsTitle')} icon="inventory_2">
                    <div className="space-y-4">
                        <label className="block">
                            <span className="text-sm font-semibold text-on-surface-variant">{t('create.nameLabel')}</span>
                            <input value={name} maxLength={100} onChange={(e) => setName(e.target.value)}
                                   placeholder={t('create.namePlaceholder')} className={`${inputClass} mt-1`}/>
                        </label>
                        <label className="block">
                            <span className="text-sm font-semibold text-on-surface-variant">{t('create.descriptionLabel')}</span>
                            <textarea rows={2} maxLength={500} value={description}
                                      onChange={(e) => setDescription(e.target.value)}
                                      className="w-full mt-1 px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary"/>
                        </label>
                    </div>
                </Card>
                </div>

                <div data-tour="beneficiary-section">
                <Card title={t('create.beneficiariesTitle', {count: beneficiaries.length})} icon="family_restroom">
                    <div className="space-y-4">
                        {beneficiaries.map((b, i) => (
                            <div key={i} className="p-4 rounded-xl border border-outline-variant/50 space-y-3">
                                <div className="flex justify-between items-center">
                                    <span className="text-sm font-semibold text-outline">{t('create.beneficiaryItem', {index: i + 1})}</span>
                                    {beneficiaries.length > 1 && (
                                        <button type="button" onClick={() => removeBeneficiaryRow(i)} className="text-sm text-error font-semibold flex items-center gap-1">
                                            <Icon name="delete" className="text-[18px]" /> {t('ui.remove')}
                                        </button>
                                    )}
                                </div>
                                <input type="email" value={b.email} maxLength={255} onChange={(e) => updateBeneficiary(i, 'email', e.target.value)}
                                       placeholder={t('create.emailPlaceholder')} className={inputClass} />
                                <div className="grid sm:grid-cols-2 gap-3">
                                    <input value={b.fullName} maxLength={255} onChange={(e) => updateBeneficiary(i, 'fullName', e.target.value)}
                                           placeholder={t('create.fullNamePlaceholder')} className={inputClass} />
                                    <input value={b.relationship} maxLength={50} onChange={(e) => updateBeneficiary(i, 'relationship', e.target.value)}
                                           placeholder={t('create.relationshipPlaceholder')} className={inputClass} />
                                </div>
                            </div>
                        ))}
                        <button type="button" disabled={beneficiaries.length >= 10} onClick={() => setBeneficiaries((l) => [...l, emptyBeneficiary()])} className={btnOutline}>
                            <Icon name="add" className="text-[18px]" /> {t('create.addBeneficiary')}
                        </button>
                    </div>
                </Card>
                </div>

                <Card title={t('create.assetsTitle', {count: assets.length})} icon="key">
                    <div className="space-y-5">
                        {assets.map((a, i) => (
                            <fieldset key={i} className="rounded-xl border border-outline-variant bg-surface-container-lowest overflow-hidden">
                                <legend className="sr-only">{t('create.assetItem', {index: i + 1})}</legend>
                                <div className="flex items-center gap-3 px-4 py-3 bg-surface-container-low border-b border-dashed border-outline-variant">
                                    <span className="w-11 h-11 shrink-0 rounded-full border-2 border-primary text-primary flex items-center justify-center ring-2 ring-offset-2 ring-offset-surface-container-low ring-primary/20">
                                        <AssetTypeIcon type={a.assetType} className="w-[22px] h-[22px]"/>
                                    </span>
                                    <div className="min-w-0">
                                        <div className="text-xs font-semibold uppercase tracking-wider text-secondary">
                                            {t('create.assetItem', {index: i + 1})}
                                        </div>
                                        <div className="font-title-lg text-lg leading-snug text-on-surface truncate">
                                            {a.assetName.trim() || t(`enums:assetType.${a.assetType}`)}
                                        </div>
                                    </div>
                                    {assets.length > 1 && (
                                        <button type="button" onClick={() => removeAsset(i)}
                                                className="ml-auto h-11 px-3 rounded-lg text-sm text-error font-semibold flex items-center gap-1 hover:bg-error-container">
                                            <Icon name="delete" className="text-[18px]"/> {t('ui.remove')}
                                        </button>
                                    )}
                                </div>

                                <div className="p-4 space-y-4">
                                    <div data-tour="asset-type-select" role="radiogroup" aria-labelledby={`asset-type-label-${i}`}>
                                        <div id={`asset-type-label-${i}`} className="text-sm font-semibold text-on-surface mb-2">
                                            {t('create.assetTypeLabel')}
                                        </div>
                                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                                            {ASSET_TYPES.map((typeItem) => (
                                                <label key={typeItem.value}
                                                       className="relative cursor-pointer min-h-11 px-3 py-2 rounded-lg border border-outline-variant text-sm text-on-surface-variant flex items-center gap-2 transition-colors hover:border-primary hover:text-on-surface has-[:checked]:border-primary has-[:checked]:bg-primary-fixed has-[:checked]:text-on-primary-fixed has-[:checked]:font-semibold has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary">
                                                    <input type="radio" name={`asset-type-${i}`} value={typeItem.value}
                                                           checked={a.assetType === typeItem.value}
                                                           onChange={() => changeAssetType(i, typeItem.value)}
                                                           className="sr-only"/>
                                                    <AssetTypeIcon type={typeItem.value} className="w-5 h-5 shrink-0"/>
                                                    <span className="leading-tight">{t(`enums:assetType.${typeItem.value}`, {defaultValue: typeItem.label})}</span>
                                                </label>
                                            ))}
                                        </div>
                                    </div>

                                    <div>
                                        <label htmlFor={`asset-name-${i}`} className="block text-sm font-semibold text-on-surface mb-1.5">
                                            {t('detail.assets.assetNamePlaceholder')}
                                        </label>
                                        <input id={`asset-name-${i}`} value={a.assetName} maxLength={200}
                                               onChange={(e) => updateAsset(i, 'assetName', e.target.value)}
                                               placeholder={t('create.assetNamePlaceholder')} className={inputClass}/>
                                    </div>

                                    <div data-tour="asset-fields">
                                    {schemas === null ? (
                                        <div className="h-24 rounded-xl bg-surface-container-low animate-pulse" aria-hidden="true"/>
                                    ) : schemaOf(schemas, a.assetType) ? (
                                        <AssetFieldsForm schema={schemaOf(schemas, a.assetType)} values={a.fields}
                                                         onChange={(k, v) => setAssetField(i, k, v)} isEdit={false}
                                                         idPrefix={`asset-${i}`}/>
                                    ) : (
                                        <div>
                                            <label htmlFor={`asset-secret-${i}`} className="block text-sm font-semibold text-on-surface mb-1.5">
                                                {t('detail.assets.secretLabel')}
                                            </label>
                                            <div className="relative">
                                                <Icon name="lock" className="absolute left-3.5 top-1/2 -translate-y-1/2 text-[20px] text-secondary pointer-events-none"/>
                                                <input
                                                    id={`asset-secret-${i}`}
                                                    type={showSecret[i] ? 'text' : 'password'}
                                                    value={a.secret}
                                                    maxLength={5000}
                                                    onChange={(e) => updateAsset(i, 'secret', e.target.value)}
                                                    placeholder={t('create.secretPlaceholder')}
                                                    className={`${inputClass} pl-11 pr-12 bg-surface-container-low`}
                                                    autoComplete="new-password"
                                                    aria-describedby={`asset-secret-hint-${i}`}
                                                />
                                                <button type="button" onClick={() => setShowSecret((s) => ({...s, [i]: !s[i]}))}
                                                        aria-pressed={!!showSecret[i]} aria-label={t('detail.assets.secretLabel')}
                                                        className="absolute right-1 top-1/2 -translate-y-1/2 w-10 h-10 rounded-lg flex items-center justify-center text-outline hover:text-on-surface">
                                                    <Icon name={showSecret[i] ? 'visibility_off' : 'visibility'}
                                                          className="text-[20px]"/>
                                                </button>
                                            </div>
                                            <p id={`asset-secret-hint-${i}`} className="mt-1.5 text-xs text-on-surface-variant flex items-center gap-1">
                                                <Icon name="verified_user" className="text-[16px] text-success"/> {t('create.secretHint')}
                                            </p>
                                        </div>
                                    )}

                                    </div>
                                    <div>
                                        <label htmlFor={`asset-notes-${i}`} className="block text-sm font-semibold text-on-surface mb-1.5">
                                            {t('detail.assets.notesLabel')}
                                        </label>
                                        <input id={`asset-notes-${i}`} value={a.notes} maxLength={2000}
                                               onChange={(e) => updateAsset(i, 'notes', e.target.value)}
                                               placeholder={t('create.notesPlaceholder')} className={inputClass}/>
                                    </div>
                                </div>
                            </fieldset>
                        ))}
                        <button type="button" disabled={assets.length >= 50}
                                onClick={() => setAssets((l) => [...l, emptyAsset()])}
                                className="w-full min-h-14 rounded-xl border-2 border-dashed border-outline-variant text-primary font-bold inline-flex items-center justify-center gap-2 transition-colors hover:border-primary hover:bg-primary-fixed/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-60 disabled:cursor-not-allowed">
                            <Icon name="add_circle" className="text-[22px]"/> {t('create.addAsset')}
                            <span className="text-sm font-normal text-outline tabular-nums">{assets.length}/50</span>
                        </button>
                    </div>
                </Card>

                {partial && (
                    <div className="p-4 rounded-xl bg-warning-container text-on-warning-container flex flex-wrap items-center justify-between gap-3" role="alert">
                        <span className="text-sm">{t('create.assetsPartial', {count: partial.failed})}</span>
                        <button type="button" onClick={() => navigate(`/owner/vaults/${partial.vaultId}`, {state: {created: true}})} className={btnPrimary}>
                            {t('create.openVault')}
                        </button>
                    </div>
                )}

                <div className="flex justify-end gap-3">
                    <button type="button" onClick={() => navigate('/owner/vaults')} className={btnOutline}>
                        {t('common:actions.cancel')}
                    </button>
                    <button type="submit" data-tour="submit-vault-btn" disabled={loading || !!partial} className={btnPrimary}>
                        {loading ? t('create.creating') : t('create.submit')}
                    </button>
                </div>
            </form>
        </OwnerLayout>
    );
}
