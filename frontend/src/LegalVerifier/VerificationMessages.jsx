import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getMessages, sendMessage, getAttachmentBlob } from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import { formatDate } from './verifierUi.js';
import { showBlobInTab, getBlobErrorMessage } from './blobUtils.js';
import FilePicker from '../Common/FilePicker.jsx';
import EmptyState from "../Common/EmptyState.jsx";

const MAX_FILE_BYTES = 10 * 1024 * 1024; // khớp giới hạn 10MB của BE
const POLL_MS = 30000;                   // hồ sơ còn mở: tự tải tin mới mỗi 30 giây

export default function VerificationMessages({ requestId, status }) {
    const { t } = useTranslation('verifier');
    const [messages, setMessages] = useState([]);
    const [text, setText] = useState('');
    const [file, setFile] = useState(null);
    const [sending, setSending] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState('');

    const canSend = status === 'PENDING' || status === 'APPROVED';

    const load = useCallback(async () => {
        try {
            setMessages((await getMessages(requestId)) ?? []);
        } catch (e) {
            setError(getErrorMessage(e, t('messages.loadError')));
        }
    }, [requestId]); // eslint-disable-line react-hooks/exhaustive-deps -- t chỉ dùng cho thông báo lỗi

    useEffect(() => { load(); }, [load]);

    // Tự làm mới khi hồ sơ còn mở và tab đang hiển thị
    useEffect(() => {
        if (!canSend) return;
        const timer = setInterval(() => {
            if (document.visibilityState === 'visible') load();
        }, POLL_MS);
        return () => clearInterval(timer);
    }, [canSend, load]);

    const handleRefresh = async () => {
        setRefreshing(true);
        setError('');
        await load();
        setRefreshing(false);
    };

    const handleFileChange = (picked) => {
        if (picked && picked.size > MAX_FILE_BYTES) {
            setError(t('messages.tooBig'));
            setFile(null);
            return;
        }
        setError('');
        setFile(picked);
    };

    const handleSend = async (e) => {
        e.preventDefault();
        if (!text.trim() && !file) return;
        setError('');
        setSending(true);
        try {
            await sendMessage(requestId, text.trim(), file);
            setText('');
            setFile(null);
            await load();
        } catch (err) {
            setError(getErrorMessage(err, t('messages.sendError')));
        } finally {
            setSending(false);
        }
    };

    const openAttachment = async (messageId) => {
        setError('');
        const tab = window.open('', '_blank');
        try {
            const res = await getAttachmentBlob(requestId, messageId);
            if (tab) showBlobInTab(tab, res.data);
            else setError(t('messages.popupBlocked'));
        } catch (err) {
            if (tab) tab.close();
            setError(await getBlobErrorMessage(err, t('messages.openError')));
        }
    };

    return (
        <div>
            <div className="flex items-center justify-between gap-3 mb-6">
                <h2 className="font-title-lg text-on-surface font-bold flex items-center gap-3">
                    <span className="material-symbols-outlined text-secondary">forum</span>
                    {t('messages.title')}
                </h2>
                <button
                    type="button"
                    onClick={handleRefresh}
                    disabled={refreshing}
                    className="flex items-center gap-1 text-sm font-semibold text-secondary hover:text-primary disabled:text-outline"
                >
                    <span className="material-symbols-outlined text-[18px]">refresh</span>
                    {refreshing ? t('messages.refreshing') : t('messages.refresh')}
                </button>
            </div>

            {error && <p role="alert" className="text-sm text-error mb-3">{error}</p>}

            <div className="space-y-3 mb-4 max-h-96 overflow-y-auto">
                {messages.length === 0 && (<EmptyState compact icon="forum" title={t('messages.emptyTitle')}
                                                       text={canSend ? t('messages.emptyCanSend') : t('messages.emptyClosed')} />)}
                {messages.map((m) => (
                    <div
                        key={m.id}
                        className={`p-3 rounded-xl text-sm ${m.senderRole === 'VERIFIER'
                            ? 'bg-secondary-fixed text-on-secondary-fixed'
                            : 'border border-outline-variant/40'}`}
                    >
                        <p className="text-xs opacity-70 mb-1">
                            {m.senderRole === 'VERIFIER' ? t('messages.verifier') : t('messages.executor')} · {m.senderName} · {formatDate(m.createdAt, true)}
                        </p>
                        {m.content && <p className="whitespace-pre-wrap">{m.content}</p>}
                        {m.attachmentName && (
                            <button
                                type="button"
                                onClick={() => openAttachment(m.id)}
                                className="underline mt-1"
                            >
                                📎 {m.attachmentName}
                            </button>
                        )}
                    </div>
                ))}
            </div>

            {canSend ? (
                <form onSubmit={handleSend} className="space-y-2">
                    <textarea
                        value={text}
                        onChange={(e) => setText(e.target.value)}
                        maxLength={2000}
                        rows={3}
                        placeholder={t('messages.placeholder')}
                        aria-label={t('messages.replyLabel')}
                        className="w-full p-3 rounded-xl border border-outline-variant/40 text-sm"
                    />
                    <div className="text-xs text-outline text-right">{text.length}/2000</div>
                    <FilePicker file={file} onChange={handleFileChange} accept=".pdf,.png,.jpg,.jpeg"
                                label={t('messages.attach')} hint={t('messages.attachHint')} disabled={sending} />
                    <div>
                        <button
                            type="submit"
                            disabled={sending || (!text.trim() && !file)}
                            className="px-4 py-2 rounded-full bg-primary text-on-primary text-sm font-bold disabled:opacity-50"
                        >
                            {sending ? t('messages.sending') : t('messages.send')}
                        </button>
                    </div>
                </form>
            ) : (
                <p className="text-xs text-on-surface-variant">
                    {t('messages.closed')}
                </p>
            )}
        </div>
    );
}
