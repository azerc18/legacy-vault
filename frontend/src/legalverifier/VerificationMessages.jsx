import React, { useCallback, useEffect, useState } from 'react';
import { getMessages, sendMessage, getAttachmentBlob } from '../api/verifierApi.js';
import { getErrorMessage } from '../api/http.js';
import { formatDate } from './verifierUi.js';
import { showBlobInTab, getBlobErrorMessage } from './blobUtils.js';

export default function VerificationMessages({ requestId, status }) {
    const [messages, setMessages] = useState([]);
    const [text, setText] = useState('');
    const [file, setFile] = useState(null);
    const [sending, setSending] = useState(false);
    const [error, setError] = useState('');

    const canSend = status === 'PENDING' || status === 'APPROVED';

    const load = useCallback(async () => {
        try {
            setMessages((await getMessages(requestId)) ?? []);
        } catch (e) {
            setError(getErrorMessage(e, 'Could not load messages.'));
        }
    }, [requestId]);

    useEffect(() => { load(); }, [load]);

    const handleSend = async (e) => {
        e.preventDefault();
        if (!text.trim() && !file) return;
        setError('');
        setSending(true);
        try {
            await sendMessage(requestId, text.trim(), file);
            setText('');
            setFile(null);
            e.target.reset();
            await load();
        } catch (err) {
            setError(getErrorMessage(err, 'Could not send the message.'));
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
            else setError('Your browser blocked the pop-up. Please allow pop-ups for this site.');
        } catch (err) {
            if (tab) tab.close();
            setError(await getBlobErrorMessage(err, 'Could not open the attachment.'));
        }
    };

    return (
        <div className="mt-6 pt-6 border-t border-outline-variant/20">
            <h3 className="font-bold mb-3">Messages with the Executor</h3>

            {error && <p className="text-sm text-error mb-3">{error}</p>}

            <div className="space-y-3 mb-4 max-h-80 overflow-y-auto">
                {messages.length === 0 && (
                    <p className="text-sm text-on-surface-variant">No messages yet.</p>
                )}
                {messages.map((m) => (
                    <div
                        key={m.id}
                        className={`p-3 rounded-xl text-sm ${m.senderRole === 'VERIFIER'
                            ? 'bg-secondary-fixed text-on-secondary-fixed'
                            : 'border border-outline-variant/40'}`}
                    >
                        <p className="text-xs opacity-70 mb-1">
                            {m.senderRole === 'VERIFIER' ? 'Verifier' : 'Executor'} · {m.senderName} · {formatDate(m.createdAt, true)}
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
                        placeholder="Write a reply to the Executor..."
                        className="w-full p-3 rounded-xl border border-outline-variant/40 text-sm"
                    />
                    <input
                        type="file"
                        accept=".pdf,.png,.jpg,.jpeg"
                        onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                        className="text-sm"
                    />
                    <div>
                        <button
                            type="submit"
                            disabled={sending || (!text.trim() && !file)}
                            className="px-4 py-2 rounded-full bg-primary text-on-primary text-sm font-bold disabled:opacity-50"
                        >
                            {sending ? 'Sending...' : 'Send reply'}
                        </button>
                    </div>
                </form>
            ) : (
                <p className="text-xs text-on-surface-variant">
                    This request is closed, so you can only read the messages.
                </p>
            )}
        </div>
    );
}
