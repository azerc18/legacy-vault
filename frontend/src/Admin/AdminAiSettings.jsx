// Cấu hình AI: Admin bật / tắt từng chức năng dùng AI và chọn model chính / dự phòng trong danh sách cho phép.
// GET /api/admin/ai-settings, PUT /api/admin/ai-settings/{feature}. KHÔNG có ô nhập API key (key chỉ ở biến môi trường).
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert, btnPrimary, formatDate, Icon, inputClass, LoadingSkeleton } from '../Owner/ownerUi.jsx';
import { AdminLayout, Dossier, Stamp } from './adminUi.jsx';

const FEATURE_ICON = { ASSISTANT: 'smart_toy', DEATH_CERTIFICATE_OCR: 'description', CITIZEN_ID_OCR: 'badge' };

const draftOf = (f) => ({ enabled: f.enabled, primaryModel: f.primaryOverride || '', backupModel: f.backupOverride || '' });

export default function AdminAiSettings() {
    const { t } = useTranslation('admin');
    const [data, setData] = useState(null);
    const [drafts, setDrafts] = useState({});
    const [busy, setBusy] = useState(null);       // feature đang lưu
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const apply = (res) => {
        setData(res);
        setDrafts(Object.fromEntries(res.features.map((f) => [f.feature, draftOf(f)])));
    };

    const load = useCallback(() => {
        setError('');
        adminApi.getAiSettings().then(apply).catch((e) => setError(getErrorMessage(e, t('aiSettings.loadError'))));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => { load(); }, [load]);

    const setDraft = (feature, patch) => setDrafts((d) => ({ ...d, [feature]: { ...d[feature], ...patch } }));

    // Model đang có hiệu lực của bản nháp (trống = mặc định), để kiểm trùng ngay trên form
    const effective = (draft, key) => draft[key] || (key === 'primaryModel' ? data.defaultPrimaryModel : data.defaultBackupModel);

    const save = async (f, draft) => {
        setBusy(f.feature); setError(''); setSuccess('');
        try {
            const updated = await adminApi.updateAiFeature(f.feature, {
                enabled: draft.enabled,
                primaryModel: draft.primaryModel || null,
                backupModel: draft.backupModel || null,
            });
            setData((d) => ({ ...d, features: d.features.map((x) => (x.feature === updated.feature ? updated : x)) }));
            setDraft(f.feature, draftOf(updated));
            setSuccess(t('aiSettings.saved', { name: t(`aiSettings.features.${f.feature}.name`) }));
        } catch (err) {
            setError(getErrorMessage(err, t('aiSettings.saveError')));
        } finally {
            setBusy(null);
        }
    };

    return (
        <AdminLayout title={t('aiSettings.title')} subtitle={t('aiSettings.subtitle')}>
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            {!data && !error && <LoadingSkeleton label={t('loading')} />}

            {data && (
                <>
                    <Dossier title={t('aiSettings.status.title')} className="mb-6">
                        <div className="flex flex-wrap gap-x-8 gap-y-3 text-sm">
                            <p>{t('aiSettings.status.provider')}: <span className="lv-mono font-semibold">{data.provider}</span></p>
                            <p className="flex items-center gap-2">{t('aiSettings.status.apiKey')}:
                                <Stamp tone={data.apiKeyConfigured ? 'ok' : 'danger'}>
                                    {data.apiKeyConfigured ? t('aiSettings.status.set') : t('aiSettings.status.notSet')}
                                </Stamp>
                            </p>
                            <p className="flex items-center gap-2">{t('aiSettings.status.backupKey')}:
                                <Stamp tone={data.backupApiKeyConfigured ? 'ok' : 'muted'}>
                                    {data.backupApiKeyConfigured ? t('aiSettings.status.set') : t('aiSettings.status.notSet')}
                                </Stamp>
                            </p>
                        </div>
                        {data.provider !== 'gemini' && <p className="text-sm text-on-surface-variant mt-3">{t('aiSettings.status.mockNote')}</p>}
                        {data.provider === 'gemini' && !data.apiKeyConfigured && <p className="text-sm text-error mt-3">{t('aiSettings.status.noKeyNote')}</p>}
                        <p className="text-xs text-on-surface-variant flex gap-1.5 mt-3"><Icon name="key" className="text-[16px]" />{t('aiSettings.status.keyHint')}</p>
                    </Dossier>

                    <div className="grid gap-6">
                        {data.features.map((f) => {
                            const draft = drafts[f.feature] || draftOf(f);
                            const same = effective(draft, 'primaryModel') === effective(draft, 'backupModel');
                            const dirty = JSON.stringify(draft) !== JSON.stringify(draftOf(f));
                            return (
                                <Dossier key={f.feature} title={t(`aiSettings.features.${f.feature}.name`)}
                                         right={<Stamp tone={f.enabled ? 'ok' : 'muted'} icon={FEATURE_ICON[f.feature]}>
                                             {f.enabled ? t('aiSettings.enabled') : t('aiSettings.disabled')}
                                         </Stamp>}>
                                    <p className="text-sm text-on-surface-variant mb-4">{t(`aiSettings.features.${f.feature}.desc`)}</p>

                                    <div className="grid gap-3 sm:grid-cols-[auto_1fr_1fr] items-end">
                                        <label className="text-sm font-semibold flex items-center gap-2 sm:pb-2.5">
                                            <input type="checkbox" checked={draft.enabled}
                                                   onChange={(e) => setDraft(f.feature, { enabled: e.target.checked })}
                                                   className="h-4 w-4" />
                                            {draft.enabled ? t('aiSettings.enabled') : t('aiSettings.disabled')}
                                        </label>
                                        <label className="text-sm font-semibold">{t('aiSettings.primary')}
                                            <select value={draft.primaryModel} disabled={!draft.enabled}
                                                    onChange={(e) => setDraft(f.feature, { primaryModel: e.target.value })}
                                                    className={`${inputClass} mt-1 lv-mono`}>
                                                <option value="">{t('aiSettings.useDefault', { model: data.defaultPrimaryModel })}</option>
                                                {data.allowedModels.map((m) => <option key={m} value={m}>{m}</option>)}
                                            </select>
                                        </label>
                                        <label className="text-sm font-semibold">{t('aiSettings.backup')}
                                            <select value={draft.backupModel} disabled={!draft.enabled}
                                                    onChange={(e) => setDraft(f.feature, { backupModel: e.target.value })}
                                                    className={`${inputClass} mt-1 lv-mono`}>
                                                <option value="">{t('aiSettings.useDefault', { model: data.defaultBackupModel })}</option>
                                                {data.allowedModels.map((m) => <option key={m} value={m}>{m}</option>)}
                                            </select>
                                        </label>
                                    </div>
                                    <p className="text-xs text-on-surface-variant mt-2">{t('aiSettings.backupHint')}</p>
                                    {same && <p className="text-xs text-error mt-1">{t('aiSettings.sameModel', { model: effective(draft, 'backupModel') })}</p>}

                                    <div className="flex flex-wrap items-center justify-between gap-3 mt-4">
                                        <p className="text-xs text-outline">
                                            {f.updatedAt
                                                ? t('aiSettings.updated', { when: formatDate(f.updatedAt), who: f.updatedByEmail || '—' })
                                                : t('aiSettings.neverUpdated')}
                                        </p>
                                        <button onClick={() => save(f, draft)} disabled={!dirty || same || busy === f.feature} className={btnPrimary}>
                                            <Icon name="save" className="text-[18px]" /> {busy === f.feature ? t('wait') : t('aiSettings.save')}
                                        </button>
                                    </div>
                                </Dossier>
                            );
                        })}
                    </div>

                    <p className="text-xs text-on-surface-variant flex gap-1.5 mt-4"><Icon name="info" className="text-[16px]" />{t('aiSettings.modelsHint')}</p>
                    <p className="text-xs text-on-surface-variant flex gap-1.5 mt-1"><Icon name="history" className="text-[16px]" />{t('aiSettings.auditHint')}</p>
                </>
            )}
        </AdminLayout>
    );
}
