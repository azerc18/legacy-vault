import React, { useEffect, useRef, useState } from 'react';
import { beneficiaryApi } from '../api/beneficiaryApi.js';
import { getErrorMessage } from '../api/http.js';
import { useTranslation } from 'react-i18next';
import { Icon, Alert, formatDate, btnPrimary, btnOutline } from './beneficiaryUi.jsx';

/* ---------- F4b: Nhận và đọc lời nhắn di nguyện & Time-Capsule ---------- */
export default function BeneficiaryLegacyMessages({ vaultId }) {
    const { t } = useTranslation('beneficiary');
    const tRef = useRef(t);          // effect đọc t qua ref để đổi ngôn ngữ không kích hoạt tải lại
    tRef.current = t;
    const [messages, setMessages] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [viewing, setViewing] = useState(null);
    const [busyId, setBusyId] = useState(null);

    useEffect(() => {
        beneficiaryApi.listLegacyMessages(vaultId)
            .then((list) => setMessages(Array.isArray(list) ? list : []))
            .catch((err) => setError(getErrorMessage(err, tRef.current('legacyMessages.loadError'))))
            .finally(() => setLoading(false));
    }, [vaultId]);

    const openDetail = async (msg) => {
        if (!msg.unlocked) return;
        setBusyId(msg.id);
        setError('');
        try {
            const detail = await beneficiaryApi.getLegacyMessage(vaultId, msg.id);
            setViewing(detail);
        } catch (err) {
            setError(getErrorMessage(err, t('legacyMessages.openError')));
        } finally {
            setBusyId(null);
        }
    };

    const downloadAttachment = async (msg) => {
        setBusyId(msg.id);
        setError('');
        try {
            await beneficiaryApi.downloadLegacyMessageAttachment(vaultId, msg.id, msg.attachmentName);
        } catch (err) {
            setError(getErrorMessage(err, t('legacyMessages.downloadError')));
        } finally {
            setBusyId(null);
        }
    };

    if (loading) {
        return null;
    }

    if (messages.length === 0 && !error) {
        return null;
    }

    return (
        <section className="my-8 bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
            <div className="flex items-center gap-3 mb-2">
                <Icon name="mark_email_unread" className="text-secondary text-[26px]" />
                <h2 className="font-title-lg text-title-lg">{t('legacyMessages.title', { count: messages.length })}</h2>
            </div>
            <p className="text-sm text-on-surface-variant mb-4">
                {t('legacyMessages.intro')}
            </p>
            <Alert>{error}</Alert>

            <ul className="divide-y divide-outline-variant/30">
                {messages.map((m) => (
                    <li key={m.id} className="py-4 flex flex-wrap items-center justify-between gap-4">
                        <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2 flex-wrap">
                                <span className="font-semibold text-on-surface truncate">{m.title}</span>
                                {m.recipientType === 'ALL_BENEFICIARIES' ? (
                                    <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-primary-fixed text-primary inline-flex items-center gap-1">
                                        <Icon name="groups" className="text-[14px]" /> {t('legacyMessages.family')}
                                    </span>
                                ) : (
                                    <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-secondary-fixed text-on-secondary-fixed inline-flex items-center gap-1">
                                        <Icon name="person" className="text-[14px]" /> {t('legacyMessages.personal')}
                                    </span>
                                )}
                                {m.unlocked ? (
                                    <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-success-container text-on-success-container inline-flex items-center gap-1">
                                        <Icon name="lock_open" className="text-[14px]" /> {t('legacyMessages.unlocked')}
                                    </span>
                                ) : (
                                    <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-warning-container text-on-warning-container inline-flex items-center gap-1">
                                        <Icon name="schedule" className="text-[14px]" /> {t('legacyMessages.opensOn', { date: formatDate(m.deliverDate) })}
                                    </span>
                                )}
                                {m.hasAttachment && m.unlocked && (
                                    <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-surface-container-low text-primary inline-flex items-center gap-1">
                                        <Icon name="attachment" className="text-[14px]" /> {m.attachmentName || t('legacyMessages.attachment')}
                                    </span>
                                )}
                            </div>
                            {m.unlocked ? (
                                <p className="text-sm text-on-surface-variant line-clamp-2 mt-1">
                                    {m.content}
                                </p>
                            ) : (
                                <p className="text-xs text-outline mt-1">
                                    {t('legacyMessages.sealed', { date: formatDate(m.deliverDate) })}
                                </p>
                            )}
                        </div>

                        {m.unlocked && (
                            <div className="flex items-center gap-2 shrink-0">
                                <button
                                    type="button"
                                    onClick={() => openDetail(m)}
                                    disabled={busyId === m.id}
                                    className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50"
                                >
                                    {t('legacyMessages.read')}
                                </button>
                                {m.hasAttachment && (
                                    <button
                                        type="button"
                                        onClick={() => downloadAttachment(m)}
                                        disabled={busyId === m.id}
                                        className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-1"
                                        title={t('legacyMessages.downloadAttachment')}
                                    >
                                        <Icon name="download" className="text-[18px]" />
                                    </button>
                                )}
                            </div>
                        )}
                    </li>
                ))}
            </ul>

            {viewing && (
                <div className="fixed inset-0 z-40 bg-black/40 flex items-center justify-center p-4" onClick={() => setViewing(null)}>
                    <div
                        className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-xl space-y-4 max-h-[90vh] overflow-y-auto"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="flex justify-between items-start gap-3">
                            <div>
                                <p className="text-xs text-outline">
                                    {viewing.recipientType === 'ALL_BENEFICIARIES' ? t('legacyMessages.viewer.family') : t('legacyMessages.viewer.personal')}
                                    {viewing.deliverDate ? ` · ${t('legacyMessages.viewer.scheduled', { date: formatDate(viewing.deliverDate) })}` : ''}
                                </p>
                                <h3 className="font-title-lg text-title-lg mt-0.5">{viewing.title}</h3>
                            </div>
                            <button type="button" onClick={() => setViewing(null)} className="text-outline hover:text-on-surface">
                                <Icon name="close" />
                            </button>
                        </div>

                        <div className="p-4 rounded-xl bg-surface-container-low text-sm whitespace-pre-wrap max-h-72 overflow-y-auto">
                            {viewing.content}
                        </div>

                        {viewing.hasAttachment && (
                            <div className="flex items-center justify-between gap-3 p-3 rounded-xl border border-outline-variant/30 text-sm">
                                <div className="flex items-center gap-2 min-w-0">
                                    <Icon name="attachment" className="text-primary shrink-0" />
                                    <span className="truncate font-medium">{viewing.attachmentName || t('legacyMessages.attachment')}</span>
                                    {viewing.attachmentSize != null && (
                                        <span className="text-xs text-outline shrink-0">
                                            ({Math.max(1, Math.round(viewing.attachmentSize / 1024))} KB)
                                        </span>
                                    )}
                                </div>
                                <button
                                    type="button"
                                    onClick={() => downloadAttachment(viewing)}
                                    disabled={busyId === viewing.id}
                                    className={btnPrimary}
                                >
                                    <Icon name="download" className="text-[18px]" /> {t('legacyMessages.download')}
                                </button>
                            </div>
                        )}

                        <div className="flex justify-end pt-2">
                            <button type="button" onClick={() => setViewing(null)} className={btnOutline}>
                                {t('legacyMessages.close')}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </section>
    );
}
