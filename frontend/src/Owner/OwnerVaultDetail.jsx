import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import {
    OwnerLayout, Card, Icon, Alert, ConfirmDialog, formatDate, humanize,
    ASSET_TYPES, DOCUMENT_TYPES, inputClass, btnPrimary, btnOutline, btnDanger,
} from './ownerUi.jsx';
import FilePicker from '../Common/FilePicker.jsx';
import EmptyState from '../Common/EmptyState.jsx';
import LegacyMessageSection from './LegacyMessageSection.jsx';
import VerificationBadge from '../Common/VerificationBadge.jsx';
import VerifyAssetDialog from './VerifyAssetDialog.jsx';
import { AssetFieldsForm, AssetFieldsView, cleanFields, schemaOf, useAssetSchemas, validateFields } from './AssetFields.jsx';
import { AllocationFields, allocationBody, allocationFromAsset, allocationSummary, emptyAllocation, validateAllocation } from './AllocationFields.jsx';

const textareaCls = 'w-full mt-1 px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary';

// Gom tài sản theo loại, theo thứ tự ASSET_TYPES (loại lạ do BE thêm sau xếp cuối); trong nhóm giữ thứ tự BE trả về
function groupAssetsByType(assets) {
    const order = ASSET_TYPES.map((a) => a.value);
    const byType = new Map();
    for (const a of assets) {
        if (!byType.has(a.assetType)) byType.set(a.assetType, []);
        byType.get(a.assetType).push(a);
    }
    const rank = (type) => (order.includes(type) ? order.indexOf(type) : order.length);
    return [...byType.entries()]
        .sort((x, y) => rank(x[0]) - rank(y[0]))
        .map(([type, items]) => ({ type, items }));
}

export default function OwnerVaultDetail() {
    const { t } = useTranslation(['vault', 'enums', 'common', 'verification']);
    const { vaultId } = useParams();
    const location = useLocation();
    const user = getUser();
    const [vault, setVault] = useState(null);      // VaultDetailResponse
    const [error, setError] = useState('');

    const reload = useCallback(() => {
        ownerApi.getVault(vaultId)
            .then(setVault)
            .catch((e) => setError(getErrorMessage(e, t('detail.notFound'))));
    }, [vaultId, t]);

    useEffect(() => { reload(); }, [reload]);

    if (!user) return <Navigate to="/login" replace />;

    // FR-27: BE quyết định có sửa được không (ACTIVE, DMS chưa bàn giao, không có hồ sơ xác minh mở)
    const editable = vault?.modifiable ?? (vault?.status === 'ACTIVE');

    return (
        <OwnerLayout
            title={vault?.name || t('detail.defaultTitle')}
            subtitle={vault ? t('detail.subtitle', {
                date: formatDate(vault.createdAt),
                assets: vault.totalAssets,
                docs: vault.totalDocuments
            }) : ''}
            actions={vault && (
                <span className="text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                    {t(`status.${vault.status}`, { defaultValue: vault.status })}
                </span>
            )}
        >
            {location.state?.created && <Alert type="success">{t('detail.createdSuccess')}</Alert>}
            <Alert>{error}</Alert>
            {!vault && !error && <p className="text-on-surface-variant">{t('ui.loading')}</p>}
            {vault && !editable && (
                <Alert type="warning">{vault.lockReason || t('detail.lockedNotice', { status: t(`status.${vault.status}`).toLowerCase() })}</Alert>
            )}

            {vault && (
                <div className="space-y-6">
                    <div data-tour="vault-detail-info">
                        <DetailsSection vault={vault} editable={editable} onSaved={reload} />
                    </div>
                    <div data-tour="vault-detail-assets">
                        <AssetSection vaultId={vaultId} beneficiaries={vault.beneficiaries || []} editable={editable} onChanged={reload} />
                    </div>
                    <div data-tour="vault-detail-beneficiaries">
                        <BeneficiarySection vaultId={vaultId} beneficiaries={vault.beneficiaries || []} editable={editable} onSaved={reload} />
                    </div>
                    <DocumentSection vaultId={vaultId} editable={editable} onChanged={reload} />
                    <LegacyMessageSection vaultId={vaultId} beneficiaries={vault.beneficiaries || []} editable={editable} />
                    <ActivitySection vaultId={vaultId} />
                    {editable && <DeleteSection vaultId={vaultId} />}
                </div>
            )}
        </OwnerLayout>
    );
}

/* ---------- Tên / mô tả (FR-27) ---------- */
function DetailsSection({ vault, editable, onSaved }) {
    const { t } = useTranslation(['vault', 'common']);
    const [editing, setEditing] = useState(false);
    const [name, setName] = useState(vault.name);
    const [description, setDescription] = useState(vault.description || '');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const save = async (e) => {
        e.preventDefault();
        if (!name.trim()) return setError(t('detail.details.nameRequired'));
        setError(''); setLoading(true);
        try {
            await ownerApi.updateVault(vault.id, { name: name.trim(), description: description.trim() || null });
            setEditing(false);
            onSaved();
        } catch (err) {
            setError(getErrorMessage(err, t('detail.details.saveError')));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title={t('detail.details.title')} icon="info">
            <Alert>{error}</Alert>
            {!editing ? (
                <div className="flex flex-wrap justify-between gap-4">
                    <dl className="grid sm:grid-cols-2 gap-x-8 gap-y-3 text-sm flex-1">
                        <div className="sm:col-span-2">
                            <dt className="text-outline">{t('detail.details.descriptionLabel')}</dt>
                            <dd className="whitespace-pre-wrap">{vault.description || '—'}</dd>
                        </div>
                        <div><dt className="text-outline">{t('detail.details.lastUpdated')}</dt><dd>{formatDate(vault.updatedAt, true)}</dd></div>
                        {vault.unlockedAt && <div><dt className="text-outline">{t('detail.details.unlocked')}</dt><dd>{formatDate(vault.unlockedAt, true)}</dd></div>}
                        {vault.claimDeadlineAt && <div><dt className="text-outline">{t('detail.details.claimDeadline')}</dt><dd>{formatDate(vault.claimDeadlineAt)}</dd></div>}
                    </dl>
                    {editable && (
                        <button onClick={() => setEditing(true)} className={btnOutline}><Icon name="edit" className="text-[18px]" /> {t('ui.edit')}</button>
                    )}
                </div>
            ) : (
                <form onSubmit={save} className="space-y-4">
                    <label className="block">
                        <span className="text-sm font-semibold text-on-surface-variant">{t('detail.details.nameLabel')}</span>
                        <input value={name} maxLength={100} onChange={(e) => setName(e.target.value)} className={`${inputClass} mt-1`} />
                    </label>
                    <label className="block">
                        <span className="text-sm font-semibold text-on-surface-variant">{t('detail.details.descriptionField')}</span>
                        <textarea rows={3} maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} className={textareaCls} />
                    </label>
                    <div className="flex gap-3">
                        <button type="button" onClick={() => setEditing(false)} className={btnOutline}>{t('common:actions.cancel')}</button>
                        <button type="submit" disabled={loading} className={btnPrimary}>{loading ? t('ui.saving') : t('common:actions.save')}</button>
                    </div>
                </form>
            )}
        </Card>
    );
}

/* ---------- Tài sản số (FR-27) ---------- */
function AssetSection({ vaultId, beneficiaries, editable, onChanged }) {
    const { t } = useTranslation(['vault', 'enums', 'common']);
    const schemas = useAssetSchemas();
    const [assets, setAssets] = useState([]);
    const [error, setError] = useState('');
    const [viewing, setViewing] = useState(null);   // chi tiết đã giải mã
    const [form, setForm] = useState(null);         // null | { id?, assetType, assetName, secret, notes }
    const [deleting, setDeleting] = useState(null);
    const [busy, setBusy] = useState(false);
    const [verifying, setVerifying] = useState(null);   // tài sản đang mở hộp thoại xác thực

    const getAssetLabel = (type) => {
        const fallback = ASSET_TYPES.find((a) => a.value === type)?.label || type;
        return t(`enums:assetType.${type}`, { defaultValue: fallback });
    };
    const groups = useMemo(() => groupAssetsByType(assets), [assets]);

    const load = useCallback(() => {
        ownerApi.listAssets(vaultId).then(setAssets).catch((e) => setError(getErrorMessage(e, t('detail.assets.errors.load'))));
    }, [vaultId, t]);

    useEffect(() => { load(); }, [load]);

    const view = async (a) => {
        setError('');
        try { setViewing(await ownerApi.getAsset(vaultId, a.id)); }
        catch (e) { setError(getErrorMessage(e, t('detail.assets.errors.open'))); }
    };

    // Sửa: lấy chi tiết để điền sẵn tên/ghi chú; secret để trống = giữ nguyên
    const edit = async (a) => {
        setError('');
        try {
            const d = await ownerApi.getAsset(vaultId, a.id);
            // N3: tài sản theo schema -> bỏ giá trị trường bí mật (để trống = giữ nguyên)
            let fields = null;
            if (d.fields) {
                const sec = new Set((schemaOf(schemas, d.assetType)?.fields || []).filter((x) => x.sensitive).map((x) => x.key));
                fields = Object.fromEntries(Object.entries(d.fields).filter(([k]) => !sec.has(k)));
            }
            setForm({ id: a.id, assetType: d.assetType, assetName: d.assetName, secret: '', notes: d.notes || '', fields, allocation: allocationFromAsset(a) });
        } catch (e) {
            setError(getErrorMessage(e, t('detail.assets.errors.open')));
        }
    };

    const remove = async () => {
        setBusy(true); setError('');
        try {
            await ownerApi.deleteAsset(vaultId, deleting.id);
            setDeleting(null);
            load(); onChanged();
        } catch (e) {
            setError(getErrorMessage(e, t('detail.assets.errors.delete')));
            setDeleting(null);
        } finally {
            setBusy(false);
        }
    };

    return (
        <Card title={t('detail.assets.title', { count: assets.length })} icon="key">
            <Alert>{error}</Alert>
            {assets.length === 0 && (
                <EmptyState compact icon="key" title={t('detail.assets.emptyTitle')}
                            text={editable ? t('detail.assets.emptyEditable') : t('detail.assets.emptyLocked')} />
            )}
            {assets.length > 0 && (
                <div className="mb-4 space-y-5">
                    {groups.map((g) => (
                        <section key={g.type} aria-label={getAssetLabel(g.type)}>
                            <h4 className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant mb-1">
                                {getAssetLabel(g.type)} <span className="text-outline">({g.items.length})</span>
                            </h4>
                            <ul className="divide-y divide-outline-variant/30">
                                {g.items.map((a) => (
                                    <li key={a.id} className="py-3 flex items-center gap-3">
                                        <div className="flex-1 min-w-0">
                                            <p className="font-semibold truncate">{a.assetName}</p>
                                            <p className="text-xs text-outline">{t('detail.assets.updatedOn', { date: formatDate(a.updatedAt) })}</p>
                                            <div className="mt-1">
                                                <VerificationBadge status={a.verificationStatus || 'SELF_DECLARED'} method={a.verificationMethod} />
                                            </div>
                                            {beneficiaries.length >= 2 && (
                                                <p className="text-xs text-on-surface-variant">{allocationSummary(a, beneficiaries, t)}</p>
                                            )}
                                        </div>
                                        <button onClick={() => view(a)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">{t('ui.view')}</button>
                                        {editable && (
                                            <button onClick={() => setVerifying(a)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">
                                                {['VERIFIED', 'VERIFIED_SANDBOX'].includes(a.verificationStatus)
                                                    ? t('verification:button.details')
                                                    : a.verificationStatus === 'EXPIRED' ? t('verification:button.reverify') : t('verification:button.verify')}
                                            </button>
                                        )}
                                        {editable && (
                                            <>
                                                <button onClick={() => edit(a)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">{t('ui.edit')}</button>
                                                <button onClick={() => setDeleting(a)} className="h-9 px-3 rounded-lg text-sm font-semibold text-error hover:bg-error-container">{t('ui.delete')}</button>
                                            </>
                                        )}
                                    </li>
                                ))}
                            </ul>
                        </section>
                    ))}
                </div>
            )}
            {editable && (
                <button disabled={schemas === null} onClick={() => setForm({ assetType: 'BANK_ACCOUNT', assetName: '', secret: '', notes: '', fields: {}, allocation: emptyAllocation() })} className={btnOutline}>
                    <Icon name="add" className="text-[18px]" /> {t('detail.assets.addAsset')}
                </button>
            )}

            {viewing && <AssetViewModal asset={viewing} schemas={schemas} onClose={() => setViewing(null)} />}
            {verifying && (
                <VerifyAssetDialog
                    vaultId={vaultId}
                    asset={verifying}
                    schemas={schemas}
                    onClose={() => { setVerifying(null); load(); }}
                    onChanged={load}
                />
            )}
            {form && (
                <AssetFormModal
                    vaultId={vaultId}
                    beneficiaries={beneficiaries}
                    initial={form}
                    schemas={schemas}
                    onClose={() => setForm(null)}
                    onSaved={() => { setForm(null); load(); onChanged(); }}
                />
            )}
            <ConfirmDialog
                open={!!deleting}
                danger
                title={t('detail.assets.deleteTitle')}
                message={t('detail.assets.deleteMessage', { name: deleting?.assetName })}
                confirmLabel={t('ui.delete')}
                busy={busy}
                onConfirm={remove}
                onCancel={() => setDeleting(null)}
            />
        </Card>
    );
}

// Render ra document.body: nếu một thẻ cha có transform/filter (hiệu ứng vào trang) thì "fixed" bị tính theo
// thẻ đó, hộp thoại lệch giữa trang dài và bị cắt ở đáy thay vì nằm giữa màn hình.
// dismissOnBackdrop=false cho hộp thoại có form: bấm ra ngoài không được làm mất dữ liệu đang nhập,
// chỉ đóng bằng Cancel, nút X hoặc Save.
function Modal({ children, onClose, dismissOnBackdrop = true }) {
    return createPortal(
        <div className="lv-modal-backdrop fixed inset-0 z-50 bg-black/40 dark:bg-black/70 backdrop-blur-md flex items-start sm:items-center justify-center p-4 overflow-y-auto animate-in fade-in duration-200" onClick={dismissOnBackdrop ? onClose : undefined}>
            <div className="lv-surface-secure bg-surface-container-lowest text-on-surface border border-outline-variant/30 dark:border-white/15 rounded-2xl sm:rounded-3xl p-6 w-full max-w-lg max-h-[calc(100dvh-2rem)] overflow-y-auto overscroll-contain my-auto shadow-2xl relative animate-in zoom-in-95 duration-200" onClick={(e) => e.stopPropagation()}>
                {children}
            </div>
        </div>,
        document.body
    );
}

function AssetViewModal({ asset, schemas, onClose }) {
    const { t } = useTranslation(['vault', 'enums', 'common']);
    const [reveal, setReveal] = useState(false);
    const schema = asset.fields ? schemaOf(schemas, asset.assetType) : null;
    const fallbackLabel = ASSET_TYPES.find((a) => a.value === asset.assetType)?.label || asset.assetType;
    const typeLabel = t(`enums:assetType.${asset.assetType}`, { defaultValue: fallbackLabel });
    const iconName = ASSET_ICON[asset.assetType] || 'inventory_2';

    return (
        <Modal onClose={onClose}>
            <div className="flex justify-between items-start gap-3 pb-3 border-b border-outline-variant/20 dark:border-white/10">
                <div className="min-w-0">
                    <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-primary/10 text-primary dark:bg-primary/20 dark:text-primary-container mb-1.5">
                        <Icon name={iconName} className="text-[14px]" />
                        <span>{typeLabel}</span>
                    </div>
                    <h3 className="font-title-lg text-xl font-bold text-on-surface dark:text-white truncate">{asset.assetName}</h3>
                </div>
                <button
                    type="button"
                    onClick={onClose}
                    aria-label={t('common:actions.close', { defaultValue: 'Đóng' })}
                    className="w-8 h-8 rounded-full flex items-center justify-center bg-black/5 dark:bg-white/10 hover:bg-black/10 dark:hover:bg-white/20 text-on-surface-variant dark:text-white/70 transition-colors shrink-0"
                >
                    <Icon name="close" className="text-[18px]" />
                </button>
            </div>

            {schema ? <AssetFieldsView schema={schema} fields={asset.fields} /> : (
                <div className="my-4">
                    <p className="text-xs font-bold uppercase tracking-wider text-on-surface-variant/75 dark:text-white/60 mb-1">{t('detail.assets.secretLabel')}</p>
                    <div className="flex items-center gap-2 p-3 rounded-xl bg-surface-container-low dark:bg-[#131c31] border border-outline-variant/30 dark:border-white/15 font-mono text-sm break-all">
                        <span className="flex-1 font-semibold text-on-surface dark:text-white">{reveal ? asset.secret : '••••••••••••'}</span>
                        <button
                            type="button"
                            onClick={() => setReveal(!reveal)}
                            className="w-8 h-8 rounded-lg flex items-center justify-center text-outline hover:text-on-surface dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 transition-colors"
                        >
                            <Icon name={reveal ? 'visibility_off' : 'visibility'} className="text-[20px]" />
                        </button>
                    </div>
                </div>
            )}

            {asset.notes && (
                <div className="p-3.5 rounded-xl bg-surface-container-low/70 dark:bg-white/[0.04] border border-outline-variant/25 dark:border-white/10 my-4">
                    <p className="text-[11px] font-bold uppercase tracking-wider text-on-surface-variant/75 dark:text-white/60 mb-1">{t('detail.assets.notesLabel')}</p>
                    <p className="text-sm whitespace-pre-wrap text-on-surface dark:text-white">{asset.notes}</p>
                </div>
            )}

            <div className="flex items-center justify-between pt-3 mt-4 border-t border-outline-variant/20 dark:border-white/10 text-xs">
                <p className="flex items-center gap-1.5 text-on-surface-variant/70 dark:text-white/50">
                    <Icon name="verified_user" className="text-[15px] text-primary" />
                    <span>{t('detail.assets.auditNotice')}</span>
                </p>
                <button
                    type="button"
                    onClick={onClose}
                    className="h-8 px-4 rounded-xl border border-outline-variant/40 dark:border-white/20 text-on-surface dark:text-white text-xs font-semibold hover:bg-surface-container-low dark:hover:bg-white/10 transition-colors"
                >
                    {t('common:actions.close', { defaultValue: 'Đóng' })}
                </button>
            </div>
        </Modal>
    );
}

function AssetFormModal({ vaultId, beneficiaries, initial, schemas, onClose, onSaved }) {
    const { t } = useTranslation(['vault', 'enums', 'common']);
    const isEdit = !!initial.id;
    const [f, setF] = useState(initial);
    const [show, setShow] = useState(false);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }));
    const schema = schemaOf(schemas, f.assetType);
    // Tài sản cũ (không có fields) giữ form kiểu cũ khi sửa; tài sản mới / đã theo schema dùng form động
    const dynamic = !!schema && (!isEdit || initial.fields != null);
    // Sửa tài sản đã theo schema mà không tải được schema -> báo lỗi, không rơi về form cũ (sẽ làm hỏng dữ liệu)
    const schemaMissing = isEdit && initial.fields != null && !schema;
    const showAllocation = beneficiaries.length >= 2;
    const allowProportional = schema?.mechanism === 'LEGAL_CLAIM_DOSSIER';   // BE chỉ cho chia % với tài sản cần hồ sơ pháp lý
    const changeType = (e) => setF((x) => {
        const type = e.target.value;
        const keepProportional = schemaOf(schemas, type)?.mechanism === 'LEGAL_CLAIM_DOSSIER';
        // Loại mới không chia % được thì bỏ PROPORTIONAL, tránh option bị disable mà vẫn đang chọn
        const allocation = x.allocation.mode === 'PROPORTIONAL' && !keepProportional ? emptyAllocation() : x.allocation;
        return { ...x, assetType: type, fields: {}, allocation };
    });
    const setField = (k, v) => setF((x) => ({ ...x, fields: { ...x.fields, [k]: v } }));

    const submit = async (e) => {
        e.preventDefault();
        setError('');
        if (!f.assetName.trim()) return setError(t('detail.assets.errors.nameRequired'));
        if (schemaMissing) return setError(t('detail.assets.errors.schemaMissing'));
        if (dynamic) {
            const msg = validateFields(schema, f.fields || {}, isEdit);
            if (msg) return setError(msg);
        } else if (!isEdit && !f.secret.trim()) return setError(t('detail.assets.errors.secretRequired'));
        if (showAllocation) {
            const allocMsg = validateAllocation(f.allocation, allowProportional, t);
            if (allocMsg) return setError(allocMsg);
        }
        setLoading(true);
        try {
            const body = {
                assetType: f.assetType,
                assetName: f.assetName.trim(),
                notes: isEdit ? f.notes.trim() : (f.notes.trim() || null), // sửa: "" = xoá ghi chú
            };
            if (dynamic) body.fields = cleanFields(f.fields || {}, schema);
            else body.secret = f.secret ? f.secret : null;     // sửa: để trống = giữ nguyên secret cũ
            if (showAllocation) {
                const allocation = allocationBody(f.allocation, initial.allocation, isEdit);
                if (allocation) body.allocation = allocation;   // không gửi = BE giữ nguyên phân bổ cũ
            }
            if (isEdit) await ownerApi.updateAsset(vaultId, f.id, body);
            else await ownerApi.addAsset(vaultId, body);
            onSaved();
        } catch (err) {
            setError(getErrorMessage(err, t('detail.assets.errors.save')));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal onClose={onClose} dismissOnBackdrop={false}>
            <div className="flex justify-between items-center mb-4 pb-2 border-b border-white/10">
                <h3 className="font-title-lg text-lg font-bold text-white">{isEdit ? t('detail.assets.editTitle') : t('detail.assets.addTitle')}</h3>
                <button
                    type="button"
                    onClick={onClose}
                    className="w-8 h-8 rounded-full flex items-center justify-center bg-black/5 dark:bg-white/10 hover:bg-black/10 dark:hover:bg-white/20 text-on-surface-variant dark:text-white/70 transition-colors"
                >
                    <Icon name="close" className="text-[20px]" />
                </button>
            </div>
            <Alert>{error}</Alert>
            <form onSubmit={submit} className="space-y-3">
                <select value={f.assetType} onChange={changeType} className={`${inputClass} bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/40 dark:border-white/15 text-on-surface dark:text-white`}>
                    {ASSET_TYPES.map((typeItem) => (
                        <option key={typeItem.value} value={typeItem.value}>
                            {t(`enums:assetType.${typeItem.value}`, { defaultValue: typeItem.label })}
                        </option>
                    ))}
                </select>
                <input value={f.assetName} maxLength={200} onChange={set('assetName')} placeholder={t('detail.assets.assetNamePlaceholder')} className={`${inputClass} bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/40 dark:border-white/15 text-on-surface dark:text-white placeholder:text-on-surface-variant/50 dark:placeholder:text-white/40`} />
                {dynamic ? (
                    <AssetFieldsForm schema={schema} values={f.fields || {}} onChange={setField} isEdit={isEdit} />
                ) : (
                    <div className="relative">
                        <input
                            type={show ? 'text' : 'password'} value={f.secret} maxLength={5000} onChange={set('secret')} autoComplete="new-password"
                            placeholder={isEdit ? t('detail.assets.newSecretPlaceholder') : t('detail.assets.secretPlaceholder')} className={`${inputClass} pr-12`}
                        />
                        <button type="button" onClick={() => setShow(!show)} className="absolute right-3 top-1/2 -translate-y-1/2 text-outline">
                            <Icon name={show ? 'visibility_off' : 'visibility'} className="text-[20px]" />
                        </button>
                    </div>
                )}
                <textarea rows={3} maxLength={2000} value={f.notes} onChange={set('notes')} placeholder={t('detail.assets.notesPlaceholder')} className={`${textareaCls} bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/40 dark:border-white/15 text-on-surface dark:text-white placeholder:text-on-surface-variant/50 dark:placeholder:text-white/40`} />
                {showAllocation && (
                    <AllocationFields
                        beneficiaries={beneficiaries}
                        value={f.allocation}
                        onChange={(allocation) => setF((x) => ({ ...x, allocation }))}
                        allowProportional={allowProportional}
                    />
                )}
                <div className="flex justify-end gap-3 pt-2">
                    <button type="button" onClick={onClose} className={btnOutline}>{t('common:actions.cancel')}</button>
                    <button type="submit" disabled={loading} className={btnPrimary}>{loading ? t('ui.saving') : t('common:actions.save')}</button>
                </div>
            </form>
        </Modal>
    );
}

/* ---------- FR-02 / N2-owner: danh sách người thụ hưởng ---------- */
const emptyBeneficiaryForm = { email: '', fullName: '', relationship: '' };

function BeneficiarySection({ vaultId, beneficiaries, editable, onSaved }) {
    const { t } = useTranslation('vault');
    const [form, setForm] = useState(emptyBeneficiaryForm);
    const [error, setError] = useState('');
    const [removing, setRemoving] = useState(null);   // beneficiary đang chờ xác nhận xoá
    const [editing, setEditing] = useState(null);     // { id, fullName, relationship } đang sửa
    const [loading, setLoading] = useState(false);
    const onlyOne = beneficiaries.length <= 1;          // BE chặn xoá người cuối cùng (LAST_BENEFICIARY)

    const setField = (field, value) => setForm((f) => ({ ...f, [field]: value }));

    const handleAdd = async (e) => {
        e.preventDefault();
        if (!form.email.trim()) return;
        setError(''); setLoading(true);
        try {
            await ownerApi.addBeneficiary(vaultId, {
                email: form.email.trim(),
                fullName: form.fullName.trim() || null,
                relationship: form.relationship.trim() || null,
            });
            setForm(emptyBeneficiaryForm);
            onSaved();
        } catch (err) {
            setError(getErrorMessage(err, t('detail.beneficiaries.errors.add')));
        } finally {
            setLoading(false);
        }
    };

    const handleRemove = async () => {
        setLoading(true); setError('');
        try {
            await ownerApi.removeBeneficiaryById(vaultId, removing.id);
            setRemoving(null);
            onSaved();
        } catch (err) {
            // BENEFICIARY_IN_USE / LAST_BENEFICIARY đã có bản dịch trong errors.json
            setError(getErrorMessage(err, t('detail.beneficiaries.errors.remove')));
            setRemoving(null);
        } finally {
            setLoading(false);
        }
    };

    const startEdit = (b) => {
        setError('');
        setEditing({ id: b.id, fullName: b.fullName || '', relationship: b.relationship || '' });
    };

    const handleUpdate = async (e) => {
        e.preventDefault();
        setLoading(true); setError('');
        try {
            await ownerApi.updateBeneficiary(vaultId, editing.id, {
                fullName: editing.fullName.trim() || null,
                relationship: editing.relationship.trim() || null,
            });
            setEditing(null);
            onSaved();
        } catch (err) {
            setError(getErrorMessage(err, t('detail.beneficiaries.errors.update')));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title={t('detail.beneficiaries.title', { count: beneficiaries.length })} icon="family_restroom">
            <Alert>{error}</Alert>
            {beneficiaries.length === 0 ? (
                <p className="text-sm mb-4">{t('detail.beneficiaries.empty')}</p>
            ) : (
                <ul className="mb-4 space-y-2">
                    {beneficiaries.map((b) => (editing?.id === b.id ? (
                        <li key={b.id} className="p-3 rounded-xl border border-primary/40">
                            <form onSubmit={handleUpdate} className="space-y-3">
                                <div className="text-sm">
                                    <b className="break-all">{b.email}</b>
                                    <div className="text-xs text-outline">{t('detail.beneficiaries.emailLocked')}</div>
                                </div>
                                <div className="grid sm:grid-cols-2 gap-3">
                                    <input value={editing.fullName} maxLength={255} autoFocus
                                           aria-label={t('detail.beneficiaries.fullNamePlaceholder')}
                                           onChange={(e) => setEditing((f) => ({ ...f, fullName: e.target.value }))}
                                           placeholder={t('detail.beneficiaries.fullNamePlaceholder')} className={inputClass} />
                                    <input value={editing.relationship} maxLength={50}
                                           aria-label={t('detail.beneficiaries.relationshipPlaceholder')}
                                           onChange={(e) => setEditing((f) => ({ ...f, relationship: e.target.value }))}
                                           placeholder={t('detail.beneficiaries.relationshipPlaceholder')} className={inputClass} />
                                </div>
                                <div className="flex gap-2">
                                    <button type="submit" disabled={loading} className={btnPrimary}>
                                        {loading ? t('ui.saving') : t('detail.beneficiaries.saveEdit')}
                                    </button>
                                    <button type="button" disabled={loading} onClick={() => setEditing(null)} className={btnOutline}>
                                        {t('detail.beneficiaries.cancelEdit')}
                                    </button>
                                </div>
                            </form>
                        </li>
                    ) : (
                        <li key={b.id} className="flex items-center justify-between gap-3 p-3 rounded-xl border border-outline-variant/50">
                            <div className="text-sm min-w-0">
                                <b className="break-all">{b.email}</b>
                                {b.fullName && <span> · {b.fullName}</span>}
                                {b.relationship && <span className="text-on-surface-variant"> ({b.relationship})</span>}
                                <div className="text-xs text-outline">
                                    {t(`detail.beneficiaries.status.${b.status}`, { defaultValue: humanize(b.status) })}
                                </div>
                            </div>
                            {editable && (
                                <div className="flex items-center gap-4 shrink-0">
                                    <button type="button" disabled={loading || !!editing} onClick={() => startEdit(b)}
                                            className="text-sm text-primary font-semibold disabled:opacity-50">
                                        {t('ui.edit')}
                                    </button>
                                    {/* Chỉ còn 1 người: nút mờ, rê chuột / focus thì hiện lý do (dùng aria-disabled để tooltip vẫn hiện) */}
                                    <span className="relative group inline-flex">
                                        <button type="button"
                                                aria-disabled={onlyOne || loading || undefined}
                                                aria-describedby={onlyOne ? `last-bnf-${b.id}` : undefined}
                                                onClick={() => { if (!onlyOne && !loading) setRemoving(b); }}
                                                className={`text-sm text-error font-semibold ${onlyOne ? 'opacity-40 cursor-not-allowed' : ''}`}>
                                            {t('ui.remove')}
                                        </button>
                                        {onlyOne && (
                                            <span id={`last-bnf-${b.id}`} role="tooltip"
                                                  className="pointer-events-none absolute right-0 bottom-full mb-2 w-60 rounded-lg bg-on-surface text-background text-xs px-3 py-2 shadow-lg opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 z-10">
                                                {t('detail.beneficiaries.lastOneHint')}
                                            </span>
                                        )}
                                    </span>
                                </div>
                            )}
                        </li>
                    )))}
                </ul>
            )}
            {editable && (
                <form onSubmit={handleAdd} className="space-y-3">
                    <input type="email" required value={form.email} maxLength={255} onChange={(e) => setField('email', e.target.value)}
                           placeholder={t('detail.beneficiaries.emailPlaceholder')} className={inputClass} />
                    <div className="grid sm:grid-cols-2 gap-3">
                        <input value={form.fullName} maxLength={255} onChange={(e) => setField('fullName', e.target.value)}
                               placeholder={t('detail.beneficiaries.fullNamePlaceholder')} className={inputClass} />
                        <input value={form.relationship} maxLength={50} onChange={(e) => setField('relationship', e.target.value)}
                               placeholder={t('detail.beneficiaries.relationshipPlaceholder')} className={inputClass} />
                    </div>
                    <button type="submit" disabled={loading || beneficiaries.length >= 10} className={btnPrimary}>
                        {loading ? t('ui.saving') : t('detail.beneficiaries.addButton')}
                    </button>
                </form>
            )}
            <ConfirmDialog
                open={!!removing}
                danger
                title={t('detail.beneficiaries.removeTitle')}
                message={t('detail.beneficiaries.removeMessage', { email: removing?.email })}
                confirmLabel={t('ui.remove')}
                busy={loading}
                onConfirm={handleRemove}
                onCancel={() => setRemoving(null)}
            />
        </Card>
    );
}


/* ---------- FR-04 & X5: tài liệu pháp lý + quản lý phiên bản ---------- */
const ALLOWED = ['.pdf', '.doc', '.docx', '.jpg', '.jpeg', '.png'];
const MAX_SIZE = 10 * 1024 * 1024;

function DocumentSection({ vaultId, editable, onChanged }) {
    const { t } = useTranslation(['vault', 'enums', 'common']);
    const [docs, setDocs] = useState([]);
    const [type, setType] = useState('WILL');
    const [file, setFile] = useState(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [deleting, setDeleting] = useState(null);
    const [updatingDoc, setUpdatingDoc] = useState(null);
    const [newVersionFile, setNewVersionFile] = useState(null);
    const [historyDoc, setHistoryDoc] = useState(null);
    const [versions, setVersions] = useState([]);
    const [historyLoading, setHistoryLoading] = useState(false);

    const getDocTypeLabel = (docType) => t(`enums:documentType.${docType}`, { defaultValue: humanize(docType) });

    const load = useCallback(() => {
        ownerApi.listDocuments(vaultId).then(setDocs).catch((e) => setError(getErrorMessage(e, t('detail.documents.errors.load'))));
    }, [vaultId, t]);

    useEffect(() => { load(); }, [load]);

    const validateFile = (f) => {
        if (!f) return t('detail.documents.errors.chooseFile');
        if (!ALLOWED.some((ext) => f.name.toLowerCase().endsWith(ext))) return t('detail.documents.errors.invalidType');
        if (f.size > MAX_SIZE) return t('detail.documents.errors.tooLarge');
        return '';
    };

    const upload = async (e) => {
        e.preventDefault();
        setError('');
        const msg = validateFile(file);
        if (msg) return setError(msg);
        setLoading(true);
        try {
            await ownerApi.uploadDocument(vaultId, type, file);
            setFile(null);
            load(); onChanged();
        } catch (err) {
            setError(getErrorMessage(err, t('detail.documents.errors.upload')));
        } finally {
            setLoading(false);
        }
    };

    const uploadNewVersion = async (e) => {
        e.preventDefault();
        if (!updatingDoc) return;
        setError('');
        const msg = validateFile(newVersionFile);
        if (msg) return setError(msg);
        setLoading(true);
        try {
            await ownerApi.uploadDocument(vaultId, updatingDoc.documentType, newVersionFile, updatingDoc.documentGroupId);
            setUpdatingDoc(null);
            setNewVersionFile(null);
            load(); onChanged();
        } catch (err) {
            setError(getErrorMessage(err, t('detail.documents.errors.uploadVersion')));
        } finally {
            setLoading(false);
        }
    };

    const openHistory = async (d) => {
        setError('');
        setHistoryDoc(d);
        setVersions([]);
        setHistoryLoading(true);
        try {
            const list = await ownerApi.getDocumentVersions(vaultId, d.documentGroupId);
            setVersions(list);
        } catch (err) {
            setError(getErrorMessage(err, t('detail.documents.errors.history')));
            setHistoryDoc(null);
        } finally {
            setHistoryLoading(false);
        }
    };

    const download = async (d) => {
        setError('');
        try { await ownerApi.downloadDocument(d.id, d.fileName); }
        catch (err) { setError(getErrorMessage(err, t('detail.documents.errors.download'))); }
    };

    const remove = async () => {
        setLoading(true); setError('');
        try {
            await ownerApi.deleteDocument(deleting.id);
            setDeleting(null);
            load(); onChanged();
        } catch (err) {
            setError(getErrorMessage(err, t('detail.documents.errors.delete'))); // 409: đang nằm trong hồ sơ xác minh
            setDeleting(null);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title={t('detail.documents.title', { count: docs.length })} icon="description">
            <Alert>{error}</Alert>
            {docs.length > 0 && (
                <ul className="divide-y divide-outline-variant/30 mb-5">
                    {docs.map((d) => (
                        <li key={d.id} className="py-3 flex flex-wrap items-center gap-3 text-sm">
                            <Icon name="description" className="text-secondary" />
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2">
                                    <p className="font-semibold truncate">{d.fileName}</p>
                                    {d.totalVersions > 1 && (
                                        <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed shrink-0">
                                            v{d.version}
                                        </span>
                                    )}
                                </div>
                                <p className="text-xs text-outline">{getDocTypeLabel(d.documentType)} · {formatDate(d.uploadedAt, true)}</p>
                            </div>
                            <button type="button" onClick={() => download(d)} className="h-9 px-3 rounded-lg font-semibold text-primary hover:bg-surface-container-low flex items-center gap-1" title={t('ui.download')}>
                                <Icon name="download" className="text-[18px]" />
                            </button>
                            {d.documentGroupId && (
                                <button type="button" onClick={() => openHistory(d)} className="h-9 px-3 rounded-lg font-semibold text-secondary hover:bg-surface-container-low">
                                    {t('detail.documents.history')}
                                </button>
                            )}
                            {editable && d.documentGroupId && (
                                <button type="button" onClick={() => { setUpdatingDoc(d); setNewVersionFile(null); setError(''); }} className="h-9 px-3 rounded-lg font-semibold text-primary hover:bg-surface-container-low">
                                    {t('detail.documents.uploadNewVersion')}
                                </button>
                            )}
                            {editable && (
                                <button type="button" onClick={() => setDeleting(d)} className="h-9 px-3 rounded-lg font-semibold text-error hover:bg-error-container">{t('ui.delete')}</button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
            {editable && (
                <>
                    <p className="text-sm text-on-surface-variant mb-3">{t('detail.documents.hint')}</p>
                    <form onSubmit={upload} className="grid sm:grid-cols-[180px_1fr_auto] gap-3 items-center">
                        <select value={type} onChange={(e) => setType(e.target.value)} className={inputClass}>
                            {DOCUMENT_TYPES.map((tItem) => (
                                <option key={tItem.value} value={tItem.value}>
                                    {t(`enums:documentType.${tItem.value}`, { defaultValue: tItem.label })}
                                </option>
                            ))}
                        </select>
                        <FilePicker file={file} onChange={setFile} accept={ALLOWED.join(',')} disabled={loading} />
                        <button type="submit" disabled={loading} className={btnPrimary}>{loading ? t('ui.uploading') : t('ui.upload')}</button>
                    </form>
                </>
            )}
            {updatingDoc && (
                <Modal onClose={() => { setUpdatingDoc(null); setNewVersionFile(null); }}>
                    <div className="flex justify-between items-start pb-3 border-b border-outline-variant/20 dark:border-white/10 mb-3">
                        <h3 className="font-title-lg text-title-lg font-bold text-on-surface dark:text-white">{t('detail.documents.uploadNewVersionTitle')}</h3>
                        <button
                            type="button"
                            onClick={() => { setUpdatingDoc(null); setNewVersionFile(null); }}
                            className="w-8 h-8 rounded-full flex items-center justify-center bg-black/5 dark:bg-white/10 hover:bg-black/10 dark:hover:bg-white/20 text-on-surface-variant dark:text-white/70 transition-colors shrink-0"
                        >
                            <Icon name="close" className="text-[18px]" />
                        </button>
                    </div>
                    <p className="text-sm text-on-surface-variant mb-4">
                        {t('detail.documents.replacingDesc', {
                            fileName: updatingDoc.fileName,
                            type: getDocTypeLabel(updatingDoc.documentType),
                            version: updatingDoc.version || 1
                        })}
                    </p>
                    <form onSubmit={uploadNewVersion} className="space-y-4">
                        <FilePicker file={newVersionFile} onChange={setNewVersionFile} accept={ALLOWED.join(',')} disabled={loading} />
                        <div className="flex justify-end gap-3">
                            <button type="button" onClick={() => { setUpdatingDoc(null); setNewVersionFile(null); }} className={btnOutline}>{t('common:actions.cancel')}</button>
                            <button type="submit" disabled={loading || !newVersionFile} className={btnPrimary}>
                                {loading ? t('ui.uploading') : t('detail.documents.uploadVersionBtn', { version: (updatingDoc.version || 1) + 1 })}
                            </button>
                        </div>
                    </form>
                </Modal>
            )}
            {historyDoc && (
                <Modal onClose={() => setHistoryDoc(null)}>
                    <div className="flex justify-between items-start pb-3 border-b border-outline-variant/20 dark:border-white/10 mb-3">
                        <div>
                            <h3 className="font-title-lg text-title-lg font-bold text-on-surface dark:text-white">{t('detail.documents.historyTitle')}</h3>
                            <p className="text-xs text-outline">{getDocTypeLabel(historyDoc.documentType)}</p>
                        </div>
                        <button
                            type="button"
                            onClick={() => setHistoryDoc(null)}
                            className="w-8 h-8 rounded-full flex items-center justify-center bg-black/5 dark:bg-white/10 hover:bg-black/10 dark:hover:bg-white/20 text-on-surface-variant dark:text-white/70 transition-colors shrink-0"
                        >
                            <Icon name="close" className="text-[18px]" />
                        </button>
                    </div>
                    {historyLoading ? (
                        <p className="text-sm text-on-surface-variant">{t('ui.loading')}</p>
                    ) : (
                        <ul className="divide-y divide-outline-variant/30 mb-4 max-h-72 overflow-y-auto">
                            {versions.map((v) => (
                                <li key={v.id} className="py-2.5 flex items-center justify-between gap-3 text-sm">
                                    <div className="min-w-0">
                                        <div className="flex items-center gap-2">
                                            <span className="font-semibold">v{v.version}</span>
                                            <span className="truncate">{v.fileName}</span>
                                            {v.isCurrent && (
                                                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                                                    {t('detail.documents.currentBadge')}
                                                </span>
                                            )}
                                        </div>
                                        <p className="text-xs text-outline">{formatDate(v.uploadedAt, true)}</p>
                                    </div>
                                    <button type="button" onClick={() => download(v)} className="h-8 px-2.5 rounded-lg font-semibold text-primary hover:bg-surface-container-low flex items-center gap-1 shrink-0">
                                        <Icon name="download" className="text-[18px]" />
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                    <div className="flex justify-end">
                        <button type="button" onClick={() => setHistoryDoc(null)} className={btnOutline}>{t('ui.close')}</button>
                    </div>
                </Modal>
            )}
            <ConfirmDialog
                open={!!deleting}
                danger
                title={t('detail.documents.deleteTitle')}
                message={t('detail.documents.deleteMessage', { name: deleting?.fileName })}
                confirmLabel={t('ui.delete')}
                busy={loading}
                onConfirm={remove}
                onCancel={() => setDeleting(null)}
            />
        </Card>
    );
}

/* ---------- Nhật ký hoạt động (mặc định 30 ngày gần nhất) ---------- */
function ActivitySection({ vaultId }) {
    const { t } = useTranslation('vault');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        setError('');
        ownerApi.getActivityLogs(vaultId, { page, size: 10 })
            .then(setData)
            .catch((err) => setError(getErrorMessage(err, t('detail.activity.loadError'))));
    }, [vaultId, page, t]);

    const rows = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;

    return (
        <Card title={t('detail.activity.title')} icon="history">
            <Alert>{error}</Alert>
            {!data && !error && <p className="text-sm text-on-surface-variant">{t('ui.loading')}</p>}
            {data && rows.length === 0 && (
                <EmptyState compact icon="history" title={t('detail.activity.emptyTitle')} text={t('detail.activity.emptyText')} />
            )}
            {rows.length > 0 && (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm text-left">
                        <thead className="text-on-surface-variant">
                        <tr>
                            <th className="py-2 pr-4">{t('detail.activity.timeCol')}</th>
                            <th className="py-2 pr-4">{t('detail.activity.actionCol')}</th>
                            <th className="py-2 pr-4">{t('detail.activity.byCol')}</th>
                            <th className="py-2">{t('detail.activity.detailsCol')}</th>
                        </tr>
                        </thead>
                        <tbody>
                        {rows.map((r) => (
                            <tr key={r.id} className="border-t border-outline-variant/20">
                                <td className="py-2 pr-4 whitespace-nowrap">{formatDate(r.createdAt, true)}</td>
                                <td className="py-2 pr-4 font-semibold">{humanize(r.action)}</td>
                                <td className="py-2 pr-4">{r.actorName || '—'}</td>
                                <td className="py-2 text-on-surface-variant">{r.description || '—'}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            {totalPages > 1 && (
                <div className="flex justify-between items-center mt-4 text-sm">
                    <button onClick={() => setPage((p) => p - 1)} disabled={page === 0} className="font-semibold text-secondary disabled:text-outline">{t('detail.activity.previous')}</button>
                    <span className="text-on-surface-variant">{t('detail.activity.pageInfo', { page: page + 1, total: totalPages })}</span>
                    <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= totalPages} className="font-semibold text-secondary disabled:text-outline">{t('detail.activity.next')}</button>
                </div>
            )}
        </Card>
    );
}

/* ---------- Xoá vault ---------- */
function DeleteSection({ vaultId }) {
    const { t } = useTranslation('vault');
    const navigate = useNavigate();
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const remove = async () => {
        setLoading(true); setError('');
        try {
            await ownerApi.deleteVault(vaultId);
            navigate('/owner/vaults');
        } catch (err) {
            setError(getErrorMessage(err, t('detail.danger.deleteError')));
            setOpen(false);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title={t('detail.danger.title')} icon="warning">
            <Alert>{error}</Alert>
            <p className="text-sm text-on-surface-variant mb-4">{t('detail.danger.description')}</p>
            <button onClick={() => setOpen(true)} className={btnDanger}><Icon name="delete" className="text-[18px]" /> {t('detail.danger.deleteButton')}</button>
            <ConfirmDialog
                open={open}
                danger
                title={t('detail.danger.confirmTitle')}
                message={t('detail.danger.confirmMessage')}
                confirmLabel={t('ui.delete')}
                busy={loading}
                onConfirm={remove}
                onCancel={() => setOpen(false)}
            />
            <p className="mt-4 text-xs text-outline"><Link to="/owner/vaults" className="text-secondary font-semibold">{t('detail.danger.backToVaults')}</Link></p>
        </Card>
    );
}
