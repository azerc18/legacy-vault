import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { Card, Icon, Alert, ConfirmDialog, formatDate, inputClass, btnPrimary, btnOutline } from './ownerUi.jsx';
import FilePicker from '../Common/FilePicker.jsx';
import EmptyState from '../Common/EmptyState.jsx';

const textareaCls = 'w-full mt-1 px-4 py-2.5 rounded-xl border border-outline-variant/40 dark:border-white/15 bg-surface-container-lowest dark:bg-[#131c31] text-on-surface dark:text-white placeholder:text-on-surface-variant/50 dark:placeholder:text-white/40 focus:outline-none focus:border-primary';

export default function LegacyMessageSection({ vaultId, beneficiaries = [], editable }) {
    const { t } = useTranslation(['vault', 'common']);
    const [messages, setMessages] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [formOpen, setFormOpen] = useState(false);
    const [viewing, setViewing] = useState(null);
    const [editing, setEditing] = useState(null);
    const [deleting, setDeleting] = useState(null);

    // Form state
    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const [recipientType, setRecipientType] = useState('ALL_BENEFICIARIES');
    const [recipientBeneficiaryId, setRecipientBeneficiaryId] = useState('');
    const [deliveryTrigger, setDeliveryTrigger] = useState('ON_CLAIM');
    const [deliverDate, setDeliverDate] = useState('');
    const [file, setFile] = useState(null);
    const [keepAttachment, setKeepAttachment] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState('');

    const loadMessages = useCallback(() => {
        setLoading(true);
        ownerApi.listLegacyMessages(vaultId)
            .then(setMessages)
            .catch((e) => setError(getErrorMessage(e, t('detail.messages.errors.load'))))
            .finally(() => setLoading(false));
    }, [vaultId, t]);

    useEffect(() => { loadMessages(); }, [loadMessages]);

    const openCreate = () => {
        setEditing(null);
        setTitle('');
        setContent('');
        setRecipientType('ALL_BENEFICIARIES');
        setRecipientBeneficiaryId(beneficiaries[0]?.id || '');
        setDeliveryTrigger('ON_CLAIM');
        setDeliverDate('');
        setFile(null);
        setKeepAttachment(true);
        setFormError('');
        setFormOpen(true);
    };

    const openEdit = (msg) => {
        setEditing(msg);
        setTitle(msg.title || '');
        setContent(msg.content || '');
        setRecipientType(msg.recipientType || 'ALL_BENEFICIARIES');
        setRecipientBeneficiaryId(msg.recipientBeneficiaryId || beneficiaries[0]?.id || '');
        setDeliveryTrigger(msg.deliveryTrigger || 'ON_CLAIM');
        setDeliverDate(msg.deliverDate || '');
        setFile(null);
        setKeepAttachment(true);
        setFormError('');
        setFormOpen(true);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        setFormError('');

        if (deliveryTrigger === 'SPECIFIC_DATE' && !deliverDate) {
            setFormError(t('detail.messages.errors.dateRequired'));
            return;
        }
        if (recipientType === 'SPECIFIC_BENEFICIARY' && !recipientBeneficiaryId) {
            setFormError(t('detail.messages.errors.recipientRequired'));
            return;
        }

        const data = new FormData();
        data.append('title', title);
        data.append('content', content);
        data.append('recipientType', recipientType);
        if (recipientType === 'SPECIFIC_BENEFICIARY' && recipientBeneficiaryId) {
            data.append('recipientBeneficiaryId', recipientBeneficiaryId);
        }
        data.append('deliveryTrigger', deliveryTrigger);
        if (deliveryTrigger === 'SPECIFIC_DATE' && deliverDate) {
            data.append('deliverDate', deliverDate);
        }
        if (file) {
            data.append('attachment', file);
        }

        setSubmitting(true);
        try {
            if (editing) {
                data.append('keepExistingAttachment', String(keepAttachment));
                await ownerApi.updateLegacyMessage(vaultId, editing.id, data);
            } else {
                await ownerApi.createLegacyMessage(vaultId, data);
            }
            setFormOpen(false);
            loadMessages();
        } catch (err) {
            setFormError(getErrorMessage(err, t('detail.messages.errors.save')));
        } finally {
            setSubmitting(false);
        }
    };

    const handleDelete = async () => {
        if (!deleting) return;
        setSubmitting(true);
        try {
            await ownerApi.deleteLegacyMessage(vaultId, deleting.id);
            setDeleting(null);
            loadMessages();
        } catch (err) {
            setError(getErrorMessage(err, t('detail.messages.errors.delete')));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Card title={t('detail.messages.title', { count: messages.length })} icon="mark_email_unread">
            <Alert>{error}</Alert>
            <p className="text-sm text-on-surface-variant mb-4">
                {t('detail.messages.subtitle')}
            </p>

            {messages.length === 0 && !loading && (
                <EmptyState compact icon="mail" title={t('detail.messages.emptyTitle')}
                    text={editable ? t('detail.messages.emptyEditable') : t('detail.messages.emptyLocked')} />
            )}

            {messages.length > 0 && (
                <ul className="divide-y divide-outline-variant/30 mb-4">
                    {messages.map((m) => (
                        <li key={m.id} className="py-3 flex flex-wrap items-center justify-between gap-3">
                            <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2 flex-wrap">
                                    <span className="font-semibold text-on-surface truncate">{m.title}</span>
                                    {m.recipientType === 'ALL_BENEFICIARIES' ? (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-primary-container text-on-primary-container inline-flex items-center gap-1">
                                            <Icon name="groups" className="text-[14px]" /> {t('detail.messages.familyBadge')}
                                        </span>
                                    ) : (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-tertiary-container text-on-tertiary-container inline-flex items-center gap-1">
                                            <Icon name="person" className="text-[14px]" /> {m.recipientName || m.recipientEmail || t('detail.messages.beneficiaryFallback')}
                                        </span>
                                    )}
                                    {m.deliveryTrigger === 'ON_CLAIM' ? (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-surface-container-high text-on-surface-variant inline-flex items-center gap-1">
                                            <Icon name="lock_open" className="text-[14px]" /> {t('detail.messages.onClaimBadge')}
                                        </span>
                                    ) : (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-secondary-container text-on-secondary-container inline-flex items-center gap-1">
                                            <Icon name="schedule" className="text-[14px]" /> {m.deliverDate ? formatDate(m.deliverDate) : t('detail.messages.scheduledBadge')}
                                        </span>
                                    )}
                                    {m.hasAttachment && (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-surface-container-low text-primary inline-flex items-center gap-1" title={m.attachmentName}>
                                            <Icon name="attachment" className="text-[14px]" /> {m.attachmentName || t('detail.messages.mediaFallback')}
                                        </span>
                                    )}
                                </div>
                                <p className="text-xs text-on-surface-variant truncate mt-1 max-w-lg">{m.content || t('detail.messages.encryptedFallback')}</p>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                                <button onClick={() => setViewing(m)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">{t('ui.view')}</button>
                                {editable && (
                                    <>
                                        <button onClick={() => openEdit(m)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">{t('ui.edit')}</button>
                                        <button onClick={() => setDeleting(m)} className="h-9 px-3 rounded-lg text-sm font-semibold text-error hover:bg-error-container">{t('ui.delete')}</button>
                                    </>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            {editable && (
                <button onClick={openCreate} className={btnOutline}>
                    <Icon name="add" className="text-[18px]" /> {t('detail.messages.newButton')}
                </button>
            )}

            {/* Modal soạn / sửa */}
            {formOpen && (
                <div className="lv-modal-backdrop fixed inset-0 z-50 bg-black/40 dark:bg-black/70 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200">
                    <div className="lv-surface-secure bg-surface-container-lowest text-on-surface border border-outline-variant/30 dark:border-white/15 rounded-2xl sm:rounded-3xl max-w-lg w-full p-6 shadow-2xl max-h-[90vh] overflow-y-auto space-y-4 relative animate-in zoom-in-95 duration-200">
                        <div className="flex justify-between items-center pb-3 border-b border-outline-variant/20 dark:border-white/10">
                            <h3 className="font-title-lg text-lg font-bold text-on-surface dark:text-white">
                                {editing ? t('detail.messages.editTitle') : t('detail.messages.createTitle')}
                            </h3>
                            <button
                                type="button"
                                onClick={() => setFormOpen(false)}
                                className="w-8 h-8 rounded-full flex items-center justify-center bg-black/5 dark:bg-white/10 hover:bg-black/10 dark:hover:bg-white/20 text-on-surface-variant dark:text-white/70 transition-colors"
                            >
                                <Icon name="close" className="text-[18px]" />
                            </button>
                        </div>
                        <Alert>{formError}</Alert>

                        <form onSubmit={handleSave} className="space-y-4 text-sm">
                            <div>
                                <label className="block font-medium mb-1">{t('detail.messages.titleLabel')}</label>
                                <input required maxLength={255} className={`${inputClass} bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/40 dark:border-white/15 text-on-surface dark:text-white placeholder:text-on-surface-variant/50 dark:placeholder:text-white/40`} value={title}
                                    placeholder={t('detail.messages.titlePlaceholder')}
                                    onChange={(e) => setTitle(e.target.value)} />
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <div>
                                    <label className="block font-medium mb-1">{t('detail.messages.recipientLabel')}</label>
                                    <select className={`${inputClass} bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/40 dark:border-white/15 text-on-surface dark:text-white`} value={recipientType}
                                        onChange={(e) => setRecipientType(e.target.value)}>
                                        <option value="ALL_BENEFICIARIES">{t('detail.messages.allFamilyOption')}</option>
                                        <option value="SPECIFIC_BENEFICIARY" disabled={beneficiaries.length === 0}>
                                            {t('detail.messages.specificBeneficiaryOption')}
                                        </option>
                                    </select>
                                </div>
                                {recipientType === 'SPECIFIC_BENEFICIARY' && (
                                    <div>
                                        <label className="block font-medium mb-1">{t('detail.messages.chooseRecipientLabel')}</label>
                                        <select className={`${inputClass} bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/40 dark:border-white/15 text-on-surface dark:text-white`} value={recipientBeneficiaryId}
                                            onChange={(e) => setRecipientBeneficiaryId(e.target.value)}>
                                            {beneficiaries.map((b) => (
                                                <option key={b.id} value={b.id}>
                                                    {b.fullName ? `${b.fullName} (${b.email})` : b.email}
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                )}
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <div>
                                    <label className="block font-medium mb-1">{t('detail.messages.triggerLabel')}</label>
                                    <select className={`${inputClass} bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/40 dark:border-white/15 text-on-surface dark:text-white`} value={deliveryTrigger}
                                        onChange={(e) => setDeliveryTrigger(e.target.value)}>
                                        <option value="ON_CLAIM">{t('detail.messages.onClaimOption')}</option>
                                        <option value="SPECIFIC_DATE">{t('detail.messages.specificDateOption')}</option>
                                    </select>
                                </div>
                                {deliveryTrigger === 'SPECIFIC_DATE' && (
                                    <div>
                                        <label className="block font-medium mb-1">{t('detail.messages.scheduleDateLabel')}</label>
                                        <input type="date" required className={`${inputClass} bg-surface-container-lowest dark:bg-[#131c31] border border-outline-variant/40 dark:border-white/15 text-on-surface dark:text-white`} value={deliverDate}
                                            min={new Date(Date.now() + 86400000).toISOString().split('T')[0]}
                                            onChange={(e) => setDeliverDate(e.target.value)} />
                                    </div>
                                )}
                            </div>

                            <div>
                                <label className="block font-medium mb-1">{t('detail.messages.contentLabel')}</label>
                                <textarea required rows={5} className={textareaCls} value={content}
                                    placeholder={t('detail.messages.contentPlaceholder')}
                                    onChange={(e) => setContent(e.target.value)} />
                            </div>

                            <div>
                                <label className="block font-medium mb-1">{t('detail.messages.attachmentLabel')}</label>
                                {editing?.hasAttachment && (
                                    <div className="mb-2 flex items-center gap-2 text-xs text-on-surface-variant">
                                        <input type="checkbox" id="keepAtt" checked={keepAttachment}
                                            onChange={(e) => setKeepAttachment(e.target.checked)} />
                                        <label htmlFor="keepAtt">{t('detail.messages.keepExistingFile', { name: editing.attachmentName })}</label>
                                    </div>
                                )}
                                <FilePicker file={file} onChange={setFile}
                                    accept="image/*,video/*,audio/*,application/pdf"
                                    maxSize={10 * 1024 * 1024} />
                            </div>

                            <div className="flex justify-end gap-2 pt-3 border-t border-outline-variant/20 dark:border-white/10">
                                <button type="button" onClick={() => setFormOpen(false)} className={btnOutline}>{t('common:actions.cancel')}</button>
                                <button type="submit" disabled={submitting} className={btnPrimary}>
                                    {submitting ? t('ui.saving') : t('detail.messages.saveButton')}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal xem chi tiết */}
            {viewing && (
                <div className="lv-modal-backdrop fixed inset-0 z-50 bg-black/40 dark:bg-black/70 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in duration-200" onClick={() => setViewing(null)}>
                    <div className="lv-surface-secure bg-surface-container-lowest text-on-surface border border-outline-variant/30 dark:border-white/15 rounded-2xl sm:rounded-3xl max-w-lg w-full p-6 shadow-2xl space-y-4 relative animate-in zoom-in-95 duration-200" onClick={(e) => e.stopPropagation()}>
                        <div className="flex justify-between items-center pb-3 border-b border-outline-variant/20 dark:border-white/10">
                            <h3 className="font-title-lg text-lg font-bold flex items-center gap-2 text-on-surface dark:text-white">
                                <Icon name="mail" className="text-primary" /> {viewing.title}
                            </h3>
                            <button
                                type="button"
                                onClick={() => setViewing(null)}
                                className="w-8 h-8 rounded-full flex items-center justify-center bg-black/5 dark:bg-white/10 hover:bg-black/10 dark:hover:bg-white/20 text-on-surface-variant dark:text-white/70 transition-colors"
                            >
                                <Icon name="close" className="text-[18px]" />
                            </button>
                        </div>
                        <div className="flex flex-wrap gap-2 text-xs">
                            <span className="px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant font-medium">
                                {t('detail.messages.viewRecipient', {
                                    recipient: viewing.recipientType === 'ALL_BENEFICIARIES'
                                        ? t('detail.messages.viewAllFamily')
                                        : (viewing.recipientName || viewing.recipientEmail)
                                })}
                            </span>
                            <span className="px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant font-medium">
                                {t('detail.messages.viewTrigger', {
                                    trigger: viewing.deliveryTrigger === 'ON_CLAIM'
                                        ? t('detail.messages.viewOnClaim')
                                        : t('detail.messages.viewScheduledFor', { date: formatDate(viewing.deliverDate) })
                                })}
                            </span>
                        </div>
                        <div className="p-4 rounded-xl bg-surface-container-low/70 dark:bg-[#131c31] border border-outline-variant/25 dark:border-white/15 text-sm whitespace-pre-wrap font-body max-h-60 overflow-y-auto text-on-surface dark:text-white/90">
                            {viewing.content}
                        </div>
                        {viewing.hasAttachment && (
                            <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low/70 dark:bg-[#131c31] border border-outline-variant/25 dark:border-white/15 text-xs text-on-surface dark:text-white">
                                <div className="flex items-center gap-2 truncate">
                                    <Icon name="attachment" className="text-primary" />
                                    <span className="truncate font-medium">{viewing.attachmentName || t('detail.messages.attachmentFallback')}</span>
                                    {viewing.attachmentSize && <span className="text-outline">({Math.round(viewing.attachmentSize / 1024)} KB)</span>}
                                </div>
                                <button onClick={() => ownerApi.downloadLegacyMessageAttachment(vaultId, viewing.id, viewing.attachmentName)}
                                    className="px-3 py-1.5 rounded-lg bg-primary text-on-primary font-semibold hover:bg-primary-container">
                                    {t('ui.download')}
                                </button>
                            </div>
                        )}
                        <div className="flex justify-end pt-3 border-t border-outline-variant/20 dark:border-white/10">
                            <button onClick={() => setViewing(null)} className={btnOutline}>{t('ui.close')}</button>
                        </div>
                    </div>
                </div>
            )}

            {/* Confirm xóa */}
            <ConfirmDialog
                open={deleting !== null}
                danger
                title={t('detail.messages.deleteTitle')}
                message={t('detail.messages.deleteMessage', { title: deleting?.title })}
                confirmLabel={t('ui.delete')}
                busy={submitting}
                onConfirm={handleDelete}
                onCancel={() => setDeleting(null)}
            />
        </Card>
    );
}
