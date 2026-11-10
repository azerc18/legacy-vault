import React, { Suspense, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';
import { useVipStatus } from '../Common/useVipStatus.js';
import { isVipCostume } from './costumes.js';
import ChatPanel, { WELCOME } from './ChatPanel.jsx';
import { pickGreeting, pickTip } from './assistantLines.js';
import ChibiAvatar from './ChibiAvatar.jsx';
import './assistant.css';

// three (~590 KB) chỉ tải khi widget thật sự hiện; trong lúc chờ dùng bản SVG 2D
const Owl3D = React.lazy(() => import('./ChibiAvatar3D.jsx'));

const PUBLIC_PATHS = ['/', '/login', '/register', '/verify-email', '/forgot-password', '/reset-password'];
const TIP_INTERVAL_MS = 45_000;
const GREETED_KEY = 'lv_greeted';
const COSTUME_KEY = (id) => `lv_owl_costume_${id}`;   // trang phục lưu theo từng user
const POS_KEY = 'lv_owl_pos';          // vị trí widget sau khi user kéo đi (khoảng cách tới mép phải/dưới)
const EDGE = 16;                       // khoảng cách mặc định tới mép màn hình (bottom-4 right-4)
const DRAG_THRESHOLD = 5;              // kéo quá 5px mới tính là kéo, ít hơn vẫn là bấm

// sessionStorage có thể bị chặn (chế độ riêng tư) -> bọc try/catch
const readGreeted = () => { try { return sessionStorage.getItem(GREETED_KEY); } catch { return null; } };
const writeGreeted = (v) => { try { sessionStorage.setItem(GREETED_KEY, v); } catch { /* bỏ qua */ } };
const readCostume = (id) => { try { return localStorage.getItem(COSTUME_KEY(id)) || 'none'; } catch { return 'none'; } };
const writeCostume = (id, v) => { try { localStorage.setItem(COSTUME_KEY(id), v); } catch { /* bỏ qua */ } };
const readPos = () => {
    try {
        const p = JSON.parse(localStorage.getItem(POS_KEY));
        if (Number.isFinite(p?.right) && Number.isFinite(p?.bottom)) return p;
    } catch { /* bỏ qua */ }
    return { right: EDGE, bottom: EDGE };
};
const writePos = (p) => { try { localStorage.setItem(POS_KEY, JSON.stringify(p)); } catch { /* bỏ qua */ } };

// Giữ cả khối widget (cú + khung chat) nằm trọn trong màn hình
function clampPos(pos, el) {
    if (!el) return pos;
    const { width, height } = el.getBoundingClientRect();
    const maxRight = Math.max(0, window.innerWidth - width);
    const maxBottom = Math.max(0, window.innerHeight - height);
    return {
        right: Math.round(Math.min(Math.max(pos.right, 0), maxRight)),
        bottom: Math.round(Math.min(Math.max(pos.bottom, 0), maxBottom)),
    };
}

export default function AssistantWidget() {
    const { t } = useTranslation();
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
    const [thinking, setThinking] = useState(false);   // đang chờ Vaulty trả lời
    const [costume, setCostume] = useState('none');
    const vip = useVipStatus();
    const navigate = useNavigate();
    // Hết VIP thì tạm mặc đồ gốc nhưng vẫn giữ lựa chọn đã lưu, nâng cấp lại là có ngay
    const shownCostume = vip === false && isVipCostume(costume) ? 'none' : costume;

    const bubbleTimer = useRef(null);
    const actionTimer = useRef(null);
    const bubbleRef = useRef(null);
    const boxRef = useRef(null);
    const drag = useRef(null);        // thông tin lượt kéo đang diễn ra
    const justDragged = useRef(false); // chặn cú click ngay sau khi thả chuột
    const [pos, setPos] = useState(readPos);     // vị trí user chọn (đã lưu)
    const [view, setView] = useState(pos);       // vị trí đang hiện, đã kẹp vào màn hình
    const [dragging, setDragging] = useState(false);

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
        const timer = setTimeout(() => say(pickGreeting(userName), 'wave', 6000), 800);
        return () => clearTimeout(timer);
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

    // Nạp trang phục đã lưu của user
    useEffect(() => {
        if (userId) setCostume(readCostume(userId));
    }, [userId]);

    const changeCostume = (id) => {
        if (vip === false && isVipCostume(id)) {
            navigate('/pricing');
            return;
        }
        if (id === costume) return;
        setCostume(id);
        writeCostume(userId, id);
        play('jump', 600);
    };

    // Kéo widget: giữ chuột/ngón tay trên con cú hoặc thanh tiêu đề khung chat rồi kéo
    const startDrag = (e) => {
        if (e.button !== undefined && e.button !== 0) return;
        if (e.target.closest?.('[data-no-drag]')) return;   // nút trong thanh tiêu đề vẫn bấm bình thường
        drag.current = { x: e.clientX, y: e.clientY, right: view.right, bottom: view.bottom, moved: false, id: e.pointerId };
        e.currentTarget.setPointerCapture?.(e.pointerId);
    };

    const moveDrag = (e) => {
        const d = drag.current;
        if (!d || d.id !== e.pointerId) return;
        const dx = e.clientX - d.x;
        const dy = e.clientY - d.y;
        if (!d.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
        if (!d.moved) { d.moved = true; setDragging(true); }
        const next = clampPos({ right: d.right - dx, bottom: d.bottom - dy }, boxRef.current);
        d.last = next;
        setView(next);
    };

    const endDrag = (e) => {
        const d = drag.current;
        if (!d || d.id !== e.pointerId) return;
        drag.current = null;
        if (d.moved) {
            // Chặn đúng cú click đi liền sau lượt kéo. Kéo bằng thanh tiêu đề hoặc cảm ứng
            // thì không có click nào tới con cú, nên phải tự xoá cờ ở task kế tiếp.
            justDragged.current = true;
            setTimeout(() => { justDragged.current = false; });
            setDragging(false);
            if (d.last) { setPos(d.last); writePos(d.last); }
        }
    };

    const dragHandlers = { onPointerDown: startDrag, onPointerMove: moveDrag, onPointerUp: endDrag, onPointerCancel: endDrag };

    // Mở/đóng khung chat, hiện câu thoại hay đổi cỡ cửa sổ thì kéo widget về lại trong màn hình
    // Chỉ đổi vị trí đang hiện; đóng khung chat thì cú về lại đúng chỗ user đã chọn
    useLayoutEffect(() => {
        if (hidden || drag.current?.moved) return;   // đang kéo thì moveDrag tự kẹp, đừng kéo widget về chỗ cũ
        const c = clampPos(pos, boxRef.current);
        setView((v) => (c.right === v.right && c.bottom === v.bottom ? v : c));
    }, [hidden, open, bubble, pos]);

    useEffect(() => {
        if (hidden) return;
        const onResize = () => { if (!drag.current?.moved) setView(clampPos(pos, boxRef.current)); };
        window.addEventListener('resize', onResize);
        return () => window.removeEventListener('resize', onResize);
    }, [hidden, pos]);

    const toggle = () => {
        if (justDragged.current) { justDragged.current = false; return; }
        clearTimeout(bubbleTimer.current);
        bubbleRef.current = null;
        setBubble(null);
        setOpen((o) => !o);
        play('jump', 500);
    };

    if (hidden) return null;

    return (
        <div ref={boxRef} style={{ right: view.right, bottom: view.bottom }}
             className={`lv-assistant fixed z-50 flex flex-col items-end gap-2 pointer-events-none${dragging ? ' lv-assistant--dragging' : ''}`}>
            {open && (
                <div className="pointer-events-auto">
                    <ChatPanel
                        messages={messages}
                        setMessages={setMessages}
                        onClose={() => setOpen(false)}
                        onReply={(ms) => play('talk', ms || 1500)}
                        onThinking={setThinking}
                        costume={shownCostume}
                        vip={vip}
                        onCostumeChange={changeCostume}
                        dragHandlers={dragHandlers}
                    />
                </div>
            )}

            {bubble && !open && (
                <button type="button" onClick={toggle}
                        className="chibi-bubble lv-glass-thick pointer-events-auto relative max-w-[220px] mr-3 px-3.5 py-2.5 rounded-2xl
                                   text-sm text-on-surface text-left">
                    {bubble}
                </button>
            )}

            <button type="button" onClick={toggle} aria-label={t(open ? 'assistant.close' : 'assistant.open')}
                    title={t('assistant.launcherHint')} {...dragHandlers}
                    className={`lv-assistant-launcher lv-assistant-handle pointer-events-auto focus:outline-none focus-visible:ring-2 focus-visible:ring-primary${vip ? ' lv-assistant-launcher--vip' : ''}`}>
                <Suspense fallback={<ChibiAvatar action={thinking ? 'think' : action} />}>
                    <Owl3D action={thinking ? 'think' : action} costume={shownCostume} />
                </Suspense>
            </button>
        </div>
    );
}
