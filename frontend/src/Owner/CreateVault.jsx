import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useNavigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, ASSET_TYPES, inputClass, btnPrimary, btnOutline } from './ownerUi.jsx';

const emptyAsset = () => ({ assetType: 'BANK_ACCOUNT', assetName: '', secret: '', notes: '' });
const emptyBeneficiary = () => ({ email: '', fullName: '', relationship: '' });

export default function CreateVault() {
    const { t } = useTranslation(['vault', 'enums']);
    const user = getUser();
    const navigate = useNavigate();
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [assets, setAssets] = useState([emptyAsset()]);
    const [beneficiaries, setBeneficiaries] = useState([emptyBeneficiary()]);
    const [showSecret, setShowSecret] = useState({});
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    if (!user) return <Navigate to="/login" replace />;

    const updateAsset = (i, field, value) =>
        setAssets((list) => list.map((a, idx) => (idx === i ? { ...a, [field]: value } : a)));
    const removeAsset = (i) => setAssets((list) => list.filter((_, idx) => idx !== i));
    const updateBeneficiary = (i, field, value) =>
        setBeneficiaries((list) => list.map((b, idx) => (idx === i ? { ...b, [field]: value } : b)));
    const removeBeneficiaryRow = (i) => setBeneficiaries((list) => list.filter((_, idx) => idx !== i));

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        // BE không validate name/secret nên FE phải chặn trước
        if (!name.trim()) return setError(t('create.errors.nameRequired'));
        const filled = assets.filter((a) => a.assetName.trim() || a.secret.trim());
        const invalid = filled.find((a) => !a.assetName.trim() || !a.secret.trim());
        if (invalid) return setError(t('create.errors.assetIncomplete'));
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
                assets: filled.map((a) => ({
                    assetType: a.assetType,
                    assetName: a.assetName.trim(),
                    secret: a.secret,
                    notes: a.notes.trim() || null,
                })),
            });
            navigate(`/owner/vaults/${vault.id}`, { state: { created: true } });
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

                <Card title={t('create.detailsTitle')} icon="inventory_2">
                    <div className="space-y-4">
                        <label className="block">
                            <span className="text-sm font-semibold text-on-surface-variant">{t('create.nameLabel')}</span>
                            <input value={name} maxLength={100} onChange={(e) => setName(e.target.value)}
                                   placeholder={t('create.namePlaceholder')} className={`${inputClass} mt-1`} />
                        </label>
                        <label className="block">
                            <span className="text-sm font-semibold text-on-surface-variant">{t('create.descriptionLabel')}</span>
                            <textarea rows={2} maxLength={500} value={description}
                                      onChange={(e) => setDescription(e.target.value)}
                                      className="w-full mt-1 px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary" />
                        </label>
                    </div>
                </Card>

                <Card title={t('create.beneficiariesTitle', { count: beneficiaries.length })} icon="family_restroom">
                    <div className="space-y-4">
                        {beneficiaries.map((b, i) => (
                            <div key={i} className="p-4 rounded-xl border border-outline-variant/50 space-y-3">
                                <div className="flex justify-between items-center">
                                    <span className="text-sm font-semibold text-outline">{t('create.beneficiaryItem', { index: i + 1 })}</span>
                                    {beneficiaries.length > 1 && (
                                        <button type="button" onClick={() => removeBeneficiaryRow(i)} className="text-sm text-error font-semibold flex items-center gap-1">
                                            <Icon name="delete" className="text-[18px]" /> {t('common.remove')}
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

                <Card title={t('create.assetsTitle', { count: assets.length })} icon="key">
                    <div className="space-y-4">
                        {assets.map((a, i) => (
                            <div key={i} className="p-4 rounded-xl border border-outline-variant/50 space-y-3">
                                <div className="flex justify-between items-center">
                                    <span className="text-sm font-semibold text-outline">{t('create.assetItem', { index: i + 1 })}</span>
                                    {assets.length > 1 && (
                                        <button type="button" onClick={() => removeAsset(i)}
                                                className="text-sm text-error font-semibold flex items-center gap-1">
                                            <Icon name="delete" className="text-[18px]" /> {t('common.remove')}
                                        </button>
                                    )}
                                </div>
                                <div className="grid sm:grid-cols-2 gap-3">
                                    <select value={a.assetType}
                                            onChange={(e) => updateAsset(i, 'assetType', e.target.value)}
                                            className={inputClass}>
                                        {ASSET_TYPES.map((typeItem) => (
                                            <option key={typeItem.value} value={typeItem.value}>
                                                {t(`enums:assetType.${typeItem.value}`, { defaultValue: typeItem.label })}
                                            </option>
                                        ))}
                                    </select>
                                    <input value={a.assetName} maxLength={200}
                                           onChange={(e) => updateAsset(i, 'assetName', e.target.value)}
                                           placeholder={t('create.assetNamePlaceholder')} className={inputClass} />
                                </div>
                                <div className="relative">
                                    <input
                                        type={showSecret[i] ? 'text' : 'password'}
                                        value={a.secret}
                                        maxLength={5000}
                                        onChange={(e) => updateAsset(i, 'secret', e.target.value)}
                                        placeholder={t('create.secretPlaceholder')}
                                        className={`${inputClass} pr-12`}
                                        autoComplete="new-password"
                                    />
                                    <button type="button" onClick={() => setShowSecret((s) => ({ ...s, [i]: !s[i] }))}
                                            className="absolute right-3 top-1/2 -translate-y-1/2 text-outline">
                                        <Icon name={showSecret[i] ? 'visibility_off' : 'visibility'}
                                              className="text-[20px]" />
                                    </button>
                                </div>
                                <input value={a.notes} maxLength={2000}
                                       onChange={(e) => updateAsset(i, 'notes', e.target.value)}
                                       placeholder={t('create.notesPlaceholder')} className={inputClass} />
                            </div>
                        ))}
                        <button type="button" disabled={assets.length >= 50}
                                onClick={() => setAssets((l) => [...l, emptyAsset()])} className={btnOutline}>
                            <Icon name="add" className="text-[18px]" /> {t('create.addAsset')}
                        </button>
                    </div>
                </Card>

                <div className="flex justify-end gap-3">
                    <button type="button" onClick={() => navigate('/owner/vaults')} className={btnOutline}>
                        {t('common.cancel')}
                    </button>
                    <button type="submit" disabled={loading} className={btnPrimary}>
                        {loading ? t('create.creating') : t('create.submit')}
                    </button>
                </div>
            </form>
        </OwnerLayout>
    );
}
