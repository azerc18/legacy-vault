import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import ChibiAvatar from './ChibiAvatar.jsx';
import ChatPanel, { WELCOME } from './ChatPanel.jsx';
import { pickGreeting, pickTip } from './assistantLines.js';
import './assistant.css';

const PUBLIC_PATHS = ['/', '/login', '/register', '/verify-email', '/forgot-password', '/reset-password'];
const TIP_INTERVAL_MS = 45_000;
const GREETED_KEY = 'lv_greeted';

// sessionStorage có thể bị chặn (chế độ riêng tư) -> bọc try/catch
const readGreeted = () => { try { return sessionStorage.getItem(GREETED_KEY); } catch { return null; } };
const writeGreeted = (v) => { try { sessionStorage.setItem(GREETED_KEY, v); } catch { /* bỏ qua */ } };

export default function AssistantWidget() {
    const { pathname } = useLocation();
    const user = getUser();
    const hidden = !user || PUBLIC_PATHS.includes(pathname);
    const userId = user?.id;
    const userName = user?.name;
    const rolesKey = (user?.roles || []).join(',');

    const [open, setOpen] = useState(false);
    const [action, setAction] = useState('idle');
    const [bubble, setBubble] = useState(null);
    const [messages, setMessages] = useState([WELCOME]);

    const bubbleTimer = useRef(null);
    const actionTimer = useRef(null);
    const bubbleRef = useRef(null);

    // Chạy 1 animation rồi quay về idle
    const play = useCallback((name, ms) => {
        clearTimeout(actionTimer.current);
        setAction(name);
        actionTimer.current = setTimeout(() => setAction('idle'), ms);
    }, []);

    // Hiện khung thoại trong ms mili giây
    const say = useCallback((text, act = 'talk', ms = 5000) => {
        clearTimeout(bubbleTimer.current);
        bubbleRef.current = text;
        setBubble(text);
        play(act, Math.min(ms, 3000));
        bubbleTimer.current = setTimeout(() => {
            bubbleRef.current = null;
            setBubble(null);
        }, ms);
    }, [play]);

    useEffect(() => () => {
        clearTimeout(bubbleTimer.current);
        clearTimeout(actionTimer.current);
    }, []);

    // Chào 1 lần cho mỗi user trong tab này (đổi tài khoản thì chào lại)
    useEffect(() => {
        if (hidden || readGreeted() === userId) return;
        writeGreeted(userId);
        const t = setTimeout(() => say(pickGreeting(userName), 'wave', 6000), 800);
        return () => clearTimeout(t);
    }, [hidden, userId, userName, say]);

    // Thỉnh thoảng nói 1 mẹo, chỉ khi panel đóng và không có câu nào đang hiện
    useEffect(() => {
        if (hidden || open) return;
        const id = setInterval(() => {
            if (!bubbleRef.current && document.visibilityState === 'visible') {
                say(pickTip(rolesKey ? rolesKey.split(',') : []));
            }
        }, TIP_INTERVAL_MS);
        return () => clearInterval(id);
    }, [hidden, open, rolesKey, say]);

    // Đăng xuất / đổi user -> xoá lịch sử chat cũ
    useEffect(() => {
        setMessages([WELCOME]);
        setOpen(false);
    }, [userId]);

    const toggle = () => {
        clearTimeout(bubbleTimer.current);
        bubbleRef.current = null;
        setBubble(null);
        setOpen((o) => !o);
        play('jump', 500);
    };

    if (hidden) return null;

    return (
        <div className="fixed bottom-4 right-4 z-50 flex flex-col items-end gap-2 pointer-events-none">
            {open && (
                <div className="pointer-events-auto">
                    <ChatPanel
                        messages={messages}
                        setMessages={setMessages}
                        onClose={() => setOpen(false)}
                        onReply={() => play('talk', 1500)}
                    />
                </div>
            )}

            {bubble && !open && (
                <button type="button" onClick={toggle}
                        className="chibi-bubble pointer-events-auto relative max-w-[220px] mr-6 px-3 py-2 rounded-2xl
                                   bg-background border border-outline-variant shadow-lg text-sm text-on-surface text-left">
                    {bubble}
                    {/* đuôi khung thoại chỉ xuống nhân vật */}
                    <span className="absolute -bottom-1.5 right-8 h-3 w-3 rotate-45 bg-background
                                     border-r border-b border-outline-variant" />
                </button>
            )}

            <button type="button" onClick={toggle} aria-label={open ? 'Close assistant' : 'Open assistant'}
                    className="pointer-events-auto rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-primary">
                <ChibiAvatar action={action} />
            </button>
        </div>
    );
}
