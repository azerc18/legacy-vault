import React, { useCallback, useEffect, useState } from 'react';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { Card, Icon, Alert, ConfirmDialog, formatDate, inputClass, btnPrimary, btnOutline } from './ownerUi.jsx';
import FilePicker from '../Common/FilePicker.jsx';
import EmptyState from '../Common/EmptyState.jsx';

const textareaCls = 'w-full mt-1 px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary';

export default function LegacyMessageSection({ vaultId, beneficiaries = [], editable }) {
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
            .catch((e) => setError(getErrorMessage(e, 'Could not load messages.')))
            .finally(() => setLoading(false));
    }, [vaultId]);

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
            setFormError('Please select a scheduled date.');
            return;
        }
        if (recipientType === 'SPECIFIC_BENEFICIARY' && !recipientBeneficiaryId) {
            setFormError('Please choose a recipient beneficiary.');
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
            setFormError(getErrorMessage(err, 'Failed to save legacy message.'));
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
            setError(getErrorMessage(err, 'Failed to delete message.'));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Card title={`Legacy messages & Time-capsule (${messages.length})`} icon="mark_email_unread">
            <Alert>{error}</Alert>
            <p className="text-sm text-on-surface-variant mb-4">
                Leave heartfelt notes, videos or voice recordings for loved ones. Choose to deliver immediately upon vault claim or schedule a future anniversary (Time-Capsule).
            </p>

            {messages.length === 0 && !loading && (
                <EmptyState compact icon="mail" title="No legacy messages yet"
                    text={editable ? 'Compose personal wishes, memory videos or scheduled anniversary letters below.' : 'This vault has no legacy messages.'} />
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
                                            <Icon name="groups" className="text-[14px]" /> Family
                                        </span>
                                    ) : (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-tertiary-container text-on-tertiary-container inline-flex items-center gap-1">
                                            <Icon name="person" className="text-[14px]" /> {m.recipientName || m.recipientEmail || 'Beneficiary'}
                                        </span>
                                    )}
                                    {m.deliveryTrigger === 'ON_CLAIM' ? (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-surface-container-high text-on-surface-variant inline-flex items-center gap-1">
                                            <Icon name="lock_open" className="text-[14px]" /> On claim
                                        </span>
                                    ) : (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-secondary-container text-on-secondary-container inline-flex items-center gap-1">
                                            <Icon name="schedule" className="text-[14px]" /> {m.deliverDate ? formatDate(m.deliverDate) : 'Scheduled'}
                                        </span>
                                    )}
                                    {m.hasAttachment && (
                                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-surface-container-low text-primary inline-flex items-center gap-1" title={m.attachmentName}>
                                            <Icon name="attachment" className="text-[14px]" /> {m.attachmentName || 'Media'}
                                        </span>
                                    )}
                                </div>
                                <p className="text-xs text-on-surface-variant truncate mt-1 max-w-lg">{m.content || '[Encrypted]'}</p>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                                <button onClick={() => setViewing(m)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">View</button>
                                {editable && (
                                    <>
                                        <button onClick={() => openEdit(m)} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low">Edit</button>
                                        <button onClick={() => setDeleting(m)} className="h-9 px-3 rounded-lg text-sm font-semibold text-error hover:bg-error-container">Delete</button>
                                    </>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            {editable && (
                <button onClick={openCreate} className={btnOutline}>
                    <Icon name="add" className="text-[18px]" /> New legacy message
                </button>
            )}

            {/* Modal soạn / sửa */}
            {formOpen && (
                <div className="fixed inset-0 z-50 bg-scrim/40 flex items-center justify-center p-4">
                    <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-xl max-h-[90vh] overflow-y-auto space-y-4">
                        <div className="flex justify-between items-center">
                            <h3 className="font-title-lg text-lg font-bold">
                                {editing ? 'Edit legacy message' : 'New legacy message'}
                            </h3>
                            <button onClick={() => setFormOpen(false)} className="text-outline hover:text-on-surface">
                                <Icon name="close" />
                            </button>
                        </div>
                        <Alert>{formError}</Alert>

                        <form onSubmit={handleSave} className="space-y-4 text-sm">
                            <div>
                                <label className="block font-medium mb-1">Title *</label>
                                <input required maxLength={255} className={inputClass} value={title}
                                    placeholder="e.g. Words for my daughter on her wedding"
                                    onChange={(e) => setTitle(e.target.value)} />
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <div>
                                    <label className="block font-medium mb-1">Recipient *</label>
                                    <select className={inputClass} value={recipientType}
                                        onChange={(e) => setRecipientType(e.target.value)}>
                                        <option value="ALL_BENEFICIARIES">All family (Everyone)</option>
                                        <option value="SPECIFIC_BENEFICIARY" disabled={beneficiaries.length === 0}>
                                            Specific beneficiary
                                        </option>
                                    </select>
                                </div>
                                {recipientType === 'SPECIFIC_BENEFICIARY' && (
                                    <div>
                                        <label className="block font-medium mb-1">Choose recipient *</label>
                                        <select className={inputClass} value={recipientBeneficiaryId}
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
                                    <label className="block font-medium mb-1">Delivery trigger *</label>
                                    <select className={inputClass} value={deliveryTrigger}
                                        onChange={(e) => setDeliveryTrigger(e.target.value)}>
                                        <option value="ON_CLAIM">On claim (When vault is unlocked)</option>
                                        <option value="SPECIFIC_DATE">Specific date (Time-Capsule)</option>
                                    </select>
                                </div>
                                {deliveryTrigger === 'SPECIFIC_DATE' && (
                                    <div>
                                        <label className="block font-medium mb-1">Schedule date *</label>
                                        <input type="date" required className={inputClass} value={deliverDate}
                                            min={new Date(Date.now() + 86400000).toISOString().split('T')[0]}
                                            onChange={(e) => setDeliverDate(e.target.value)} />
                                    </div>
                                )}
                            </div>

                            <div>
                                <label className="block font-medium mb-1">Message content *</label>
                                <textarea required rows={5} className={textareaCls} value={content}
                                    placeholder="Write your private message or sentimental wishes. Encrypted before saving."
                                    onChange={(e) => setContent(e.target.value)} />
                            </div>

                            <div>
                                <label className="block font-medium mb-1">Attachment (Photo, Audio, Video, PDF - up to 25MB)</label>
                                {editing?.hasAttachment && (
                                    <div className="mb-2 flex items-center gap-2 text-xs text-on-surface-variant">
                                        <input type="checkbox" id="keepAtt" checked={keepAttachment}
                                            onChange={(e) => setKeepAttachment(e.target.checked)} />
                                        <label htmlFor="keepAtt">Keep existing file: {editing.attachmentName}</label>
                                    </div>
                                )}
                                <FilePicker file={file} onChange={setFile}
                                    accept="image/*,video/*,audio/*,application/pdf"
                                    maxSize={25 * 1024 * 1024} />
                            </div>

                            <div className="flex justify-end gap-2 pt-2">
                                <button type="button" onClick={() => setFormOpen(false)} className={btnOutline}>Cancel</button>
                                <button type="submit" disabled={submitting} className={btnPrimary}>
                                    {submitting ? 'Saving...' : 'Save message'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal xem chi tiết */}
            {viewing && (
                <div className="fixed inset-0 z-50 bg-scrim/40 flex items-center justify-center p-4">
                    <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-xl space-y-4">
                        <div className="flex justify-between items-center">
                            <h3 className="font-title-lg text-lg font-bold flex items-center gap-2">
                                <Icon name="mail" className="text-primary" /> {viewing.title}
                            </h3>
                            <button onClick={() => setViewing(null)} className="text-outline hover:text-on-surface">
                                <Icon name="close" />
                            </button>
                        </div>
                        <div className="flex flex-wrap gap-2 text-xs">
                            <span className="px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant font-medium">
                                Recipient: {viewing.recipientType === 'ALL_BENEFICIARIES' ? 'All family' : (viewing.recipientName || viewing.recipientEmail)}
                            </span>
                            <span className="px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant font-medium">
                                Trigger: {viewing.deliveryTrigger === 'ON_CLAIM' ? 'On claim' : `Scheduled for ${formatDate(viewing.deliverDate)}`}
                            </span>
                        </div>
                        <div className="p-4 rounded-xl bg-background border border-outline-variant/30 text-sm whitespace-pre-wrap font-body max-h-60 overflow-y-auto">
                            {viewing.content}
                        </div>
                        {viewing.hasAttachment && (
                            <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low border border-outline-variant/30 text-xs">
                                <div className="flex items-center gap-2 truncate">
                                    <Icon name="attachment" className="text-primary" />
                                    <span className="truncate font-medium">{viewing.attachmentName || 'Attachment'}</span>
                                    {viewing.attachmentSize && <span className="text-outline">({Math.round(viewing.attachmentSize / 1024)} KB)</span>}
                                </div>
                                <button onClick={() => ownerApi.downloadLegacyMessageAttachment(vaultId, viewing.id, viewing.attachmentName)}
                                    className="px-3 py-1.5 rounded-lg bg-primary text-on-primary font-semibold hover:bg-primary-container">
                                    Download
                                </button>
                            </div>
                        )}
                        <div className="flex justify-end pt-2">
                            <button onClick={() => setViewing(null)} className={btnOutline}>Close</button>
                        </div>
                    </div>
                </div>
            )}

            {/* Confirm xóa */}
            <ConfirmDialog
                open={deleting !== null}
                danger
                title="Delete legacy message?"
                message={`Are you sure you want to delete "${deleting?.title}"? This cannot be undone.`}
                confirmLabel="Delete"
                busy={submitting}
                onConfirm={handleDelete}
                onCancel={() => setDeleting(null)}
            />
        </Card>
    );
}
