import React, { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, ASSET_TYPES, inputClass, btnPrimary, btnOutline } from './ownerUi.jsx';

const emptyAsset = () => ({ assetType: 'BANK_ACCOUNT', assetName: '', secret: '', notes: '' });

export default function CreateVault() {
    const user = getUser();
    const navigate = useNavigate();
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [assets, setAssets] = useState([emptyAsset()]);
    const [showSecret, setShowSecret] = useState({});
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    if (!user) return <Navigate to="/login" replace />;

    const updateAsset = (i, field, value) =>
        setAssets((list) => list.map((a, idx) => (idx === i ? { ...a, [field]: value } : a)));
    const removeAsset = (i) => setAssets((list) => list.filter((_, idx) => idx !== i));

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        // BE không validate name/secret nên FE phải chặn trước
        if (!name.trim()) return setError('Please enter a vault name.');
        const filled = assets.filter((a) => a.assetName.trim() || a.secret.trim());
        const invalid = filled.find((a) => !a.assetName.trim() || !a.secret.trim());
        if (invalid) return setError('Each asset needs a name and a secret.');

        setLoading(true);
        try {
            const vault = await ownerApi.createVault({
                name: name.trim(),
                description: description.trim() || null,
                assets: filled.map((a) => ({
                    assetType: a.assetType,
                    assetName: a.assetName.trim(),
                    secret: a.secret,
                    notes: a.notes.trim() || null,
                })),
            });
            navigate(`/owner/vaults/${vault.id}`, { state: { created: true } });
        } catch (err) {
            setError(getErrorMessage(err, 'Could not create the vault.'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <OwnerLayout title="New vault" subtitle="Secrets are encrypted on the server before they are stored.">
            <form onSubmit={handleSubmit} className="space-y-6">
                <Alert>{error}</Alert>

                <Card title="Vault details" icon="inventory_2">
                    <div className="space-y-4">
                        <label className="block">
                            <span className="text-sm font-semibold text-on-surface-variant">Name *</span>
                            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Family finances" className={`${inputClass} mt-1`} />
                        </label>
                        <label className="block">
                            <span className="text-sm font-semibold text-on-surface-variant">Description</span>
                            <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)}
                                className="w-full mt-1 px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary" />
                        </label>
                    </div>
                </Card>

                <Card title={`Assets (${assets.length})`} icon="key">
                    <div className="space-y-4">
                        {assets.map((a, i) => (
                            <div key={i} className="p-4 rounded-xl border border-outline-variant/50 space-y-3">
                                <div className="flex justify-between items-center">
                                    <span className="text-sm font-semibold text-outline">Asset {i + 1}</span>
                                    {assets.length > 1 && (
                                        <button type="button" onClick={() => removeAsset(i)} className="text-sm text-error font-semibold flex items-center gap-1">
                                            <Icon name="delete" className="text-[18px]" /> Remove
                                        </button>
                                    )}
                                </div>
                                <div className="grid sm:grid-cols-2 gap-3">
                                    <select value={a.assetType} onChange={(e) => updateAsset(i, 'assetType', e.target.value)} className={inputClass}>
                                        {ASSET_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                                    </select>
                                    <input value={a.assetName} onChange={(e) => updateAsset(i, 'assetName', e.target.value)} placeholder="Asset name (e.g. Vietcombank)" className={inputClass} />
                                </div>
                                <div className="relative">
                                    <input
                                        type={showSecret[i] ? 'text' : 'password'}
                                        value={a.secret}
                                        onChange={(e) => updateAsset(i, 'secret', e.target.value)}
                                        placeholder="Secret (password, seed phrase, account number...)"
                                        className={`${inputClass} pr-12`}
                                        autoComplete="new-password"
                                    />
                                    <button type="button" onClick={() => setShowSecret((s) => ({ ...s, [i]: !s[i] }))} className="absolute right-3 top-1/2 -translate-y-1/2 text-outline">
                                        <Icon name={showSecret[i] ? 'visibility_off' : 'visibility'} className="text-[20px]" />
                                    </button>
                                </div>
                                <input value={a.notes} onChange={(e) => updateAsset(i, 'notes', e.target.value)} placeholder="Notes for the beneficiary (optional)" className={inputClass} />
                            </div>
                        ))}
                        <button type="button" onClick={() => setAssets((l) => [...l, emptyAsset()])} className={btnOutline}>
                            <Icon name="add" className="text-[18px]" /> Add asset
                        </button>
                    </div>
                </Card>

                <div className="flex justify-end gap-3">
                    <button type="button" onClick={() => navigate('/owner/vaults')} className={btnOutline}>Cancel</button>
                    <button type="submit" disabled={loading} className={btnPrimary}>
                        {loading ? 'Creating...' : 'Create vault'}
                    </button>
                </div>
            </form>
        </OwnerLayout>
    );
}
