import React, { useEffect, useRef, useState } from 'react';
import { assistantApi } from '../api/assistantApi.js';
import { getErrorMessage } from '../api/http.js';
import { Icon } from '../Owner/ownerUi.jsx';
import ChibiAvatar from './ChibiAvatar.jsx';

export const WELCOME = { role: 'assistant', content: "Hi! I'm Vaulty. Ask me how to use LegacyVault." };
const MAX_HISTORY = 10;   // khớp @Size(max = 10) ở ChatRequest
const MAX_LENGTH = 1000;  // khớp @Size(max = 1000) ở ChatMessage

// Bỏ câu chào mặc định, lấy 10 tin cuối và đảm bảo lượt đầu là của user (Gemini yêu cầu)
function toApiHistory(messages) {
    const history = messages.filter((m) => m !== WELCOME).slice(-MAX_HISTORY);
    while (history.length && history[0].role !== 'user') history.shift();
    return history.map(({ role, content }) => ({ role, content }));
}

// messages nằm ở AssistantWidget để đóng panel không mất lịch sử
// **đậm** -> <strong>
function renderInline(text) {
    return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
        part.length > 4 && part.startsWith('**') && part.endsWith('**')
            ? <strong key={i} className="font-semibold">{part.slice(2, -2)}</strong>
            : part);
}

// Markdown đơn giản: đoạn văn, danh sách số, gạch đầu dòng
function MessageContent({ text }) {
    const blocks = [];
    text.split('\n').forEach((raw) => {
        const line = raw.trim();
        const num = line.match(/^(\d+)[.)]\s+(.*)/);
        const bullet = line.match(/^[-*•]\s+(.*)/);
        const last = blocks[blocks.length - 1];
        if (num) {
            if (last?.type === 'ol') last.items.push(num[2]);
            else blocks.push({ type: 'ol', start: Number(num[1]), items: [num[2]] });
        } else if (bullet) {
            if (last?.type === 'ul') last.items.push(bullet[1]);
            else blocks.push({ type: 'ul', items: [bullet[1]] });
        } else if (line) {
            blocks.push({ type: 'p', text: line.replace(/^#+\s*/, '') });
        }
    });

    return (
        <div className="space-y-2">
            {blocks.map((b, i) => {
                if (b.type === 'p') return <p key={i}>{renderInline(b.text)}</p>;
                const items = b.items.map((it, j) => <li key={j} className="pl-1">{renderInline(it)}</li>);
                return b.type === 'ol'
                    ? <ol key={i} start={b.start} className="list-decimal pl-5 space-y-1">{items}</ol>
                    : <ul key={i} className="list-disc pl-5 space-y-1">{items}</ul>;
            })}
        </div>
    );
}

export default function ChatPanel({ messages, setMessages, onClose, onReply }) {
    const [input, setInput] = useState('');
    const [sending, setSending] = useState(false);
    const [error, setError] = useState('');
    const endRef = useRef(null);
    const inputRef = useRef(null);

    useEffect(() => { inputRef.current?.focus(); }, []);
    useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages, sending]);

    const send = async (e) => {
        e.preventDefault();
        const text = input.trim();
        if (!text || sending) return;

        const userMsg = { role: 'user', content: text };
        const next = [...messages, userMsg];
        setMessages(next);
        setInput('');
        setError('');
        setSending(true);
        try {
            const reply = await assistantApi.chat(toApiHistory(next));
            setMessages((prev) => [...prev, { role: 'assistant', content: reply }]);
            onReply?.();
        } catch (err) {
            // Gỡ tin lỗi ra khỏi lịch sử và trả lại ô nhập để user gửi lại
            setMessages((prev) => prev.filter((m) => m !== userMsg));
            setInput(text);
            setError(getErrorMessage(err, 'Vaulty is unavailable right now.'));
        } finally {
            setSending(false);
        }
    };


    return (
        <div className="w-[340px] max-w-[calc(100vw-2rem)] h-[460px] max-h-[calc(100vh-10rem)] flex flex-col
                        rounded-2xl border border-outline-variant bg-background shadow-2xl overflow-hidden chibi-bubble">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 bg-primary text-on-primary">
                <div>
                    <p className="font-semibold leading-tight">Vaulty</p>
                    <p className="text-xs opacity-80">LegacyVault guide</p>
                </div>
                <button type="button" onClick={onClose} aria-label="Close chat"
                        className="h-8 w-8 rounded-full flex items-center justify-center hover:bg-white/15">
                    <Icon name="close" />
                </button>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto px-3 py-3 space-y-3">
                {messages.map((m, i) => m.role === 'user' ? (
                    <div key={i} className="flex justify-end">
                        <div className="max-w-[80%] px-3 py-2 rounded-2xl rounded-br-sm bg-primary text-on-primary text-sm whitespace-pre-line break-words">
                            {m.content}
                        </div>
                    </div>
                ) : (
                    <div key={i} className="flex items-end gap-2">
                        <ChibiAvatar mini />
                        <div className="max-w-[80%] px-3 py-2 rounded-2xl rounded-bl-sm bg-surface-container-low text-on-surface text-sm leading-relaxed break-words">
                            <MessageContent text={m.content} />
                        </div>
                    </div>
                ))}
                {sending && (
                    <div className="flex items-end gap-2" aria-live="polite" aria-label="Vaulty is typing">
                        <ChibiAvatar mini />
                        <div className="px-3 py-3 rounded-2xl rounded-bl-sm bg-surface-container-low text-on-surface-variant flex gap-1">
                            <span className="typing-dot" />
                            <span className="typing-dot" />
                            <span className="typing-dot" />
                        </div>
                    </div>
                )}
                <div ref={endRef} />
            </div>

            {error && <p className="px-4 pb-1 text-xs text-error">{error}</p>}

            {/* Input */}
            <form onSubmit={send} className="flex items-center gap-2 p-3 border-t border-outline-variant">
                <input
                    ref={inputRef}
                    value={input}
                    onChange={(e) => setInput(e.target.value.slice(0, MAX_LENGTH))}
                    placeholder="Ask me anything about LegacyVault…"
                    className="flex-1 h-10 px-3 rounded-xl border border-outline-variant bg-background text-sm
                               focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <button type="submit" disabled={sending || !input.trim()} aria-label="Send"
                        className="h-10 w-10 rounded-xl bg-primary text-on-primary flex items-center justify-center disabled:opacity-50">
                    <Icon name="send" />
                </button>
            </form>
        </div>
    );
}
