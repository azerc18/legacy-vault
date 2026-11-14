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

// Giữ con cú nằm trọn trong màn hình. Chỉ đo con cú, không đo khung chat, nên mở/đóng chat cú đứng yên.
function clampPos(pos, el) {
    if (!el) return pos;
    const { width, height } = el.getBoundingClientRect();
    if (!width && !height) return pos;   // đang bị ẩn (có hộp thoại mở): chưa đo được, giữ nguyên
    const maxRight = Math.max(0, window.innerWidth - width);
    const maxBottom = Math.max(0, window.innerHeight - height);
    return {
        right: Math.round(Math.min(Math.max(pos.right, 0), maxRight)),
        bottom: Math.round(Math.min(Math.max(pos.bottom, 0), maxBottom)),
    };
}

const PANEL_GAP = 8;      // khe giữa con cú và khung chat
const SCREEN_MARGIN = 8;  // khung chat luôn cách mép màn hình ít nhất chừng này
const MIN_PANEL_H = 220;  // thấp hơn mức này thì thử phía khác trước khi chịu thu nhỏ

const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), Math.max(lo, hi));

// Chọn chỗ đặt khung chat quanh con cú: ưu tiên phía trên, rồi dưới, rồi bên trái/phải
// (bên nào rộng hơn). Không phía nào đủ chỗ thì lấy phía rộng nhất và cho khung chat thấp lại.
// `keep` = giữ nguyên phía cũ (đang kéo widget bằng thanh tiêu đề, đổi phía giữa chừng sẽ giật).
function placePanel(owl, panel, vw, vh, keep) {
    const M = SCREEN_MARGIN;
    const G = PANEL_GAP;
    const space = {
        top: owl.top - G - M,
        bottom: vh - owl.bottom - G - M,
        left: owl.left - G - M,
        right: vw - owl.right - G - M,
    };
    const fitsV = (side) => space[side] >= panel.height && panel.width <= vw - 2 * M;
    const fitsH = (side) => space[side] >= panel.width && panel.height <= vh - 2 * M;

    let side = keep;
    if (!side) {
        const horiz = space.left >= space.right ? ['left', 'right'] : ['right', 'left'];
        side = ['top', 'bottom'].find(fitsV) || horiz.find(fitsH);
        if (!side) {
            // Không phía nào đủ: lấy phía trên/dưới rộng hơn nếu còn chứa được khung thấp tối thiểu,
            // không thì bên trái/phải rộng hơn (màn hình ngang thấp)
            const v = space.top >= space.bottom ? 'top' : 'bottom';
            side = space[v] >= Math.min(panel.height, MIN_PANEL_H) || space[horiz[0]] < panel.width ? v : horiz[0];
        }
    }

    const vertical = side === 'top' || side === 'bottom';
    const maxHeight = vertical ? Math.max(space[side], 0) : vh - 2 * M;
    const h = Math.min(panel.height, maxHeight);
    const ownerOnRight = owl.left + owl.width / 2 > vw / 2;
    const ownerOnBottom = owl.top + owl.height / 2 > vh / 2;

    let left;
    let top;
    if (vertical) {
        // Canh mép khung chat theo mép con cú ở phía gần mép màn hình
        left = ownerOnRight ? owl.right - panel.width : owl.left;
        top = side === 'top' ? owl.top - G - h : owl.bottom + G;
    } else {
        left = side === 'left' ? owl.left - G - panel.width : owl.right + G;
        top = ownerOnBottom ? owl.bottom - h : owl.top;
    }
    return {
        side,
        left: Math.round(clamp(left, M, vw - panel.width - M)),
        top: Math.round(clamp(top, M, vh - h - M)),
        maxHeight: h < panel.height ? Math.floor(h) : null,
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
    const boxRef = useRef(null);      // khối con cú (không gồm khung chat)
    const panelRef = useRef(null);
    const panelSide = useRef(null);   // phía khung chat đang nằm, giữ nguyên trong lúc kéo
    const drag = useRef(null);        // thông tin lượt kéo đang diễn ra
    const justDragged = useRef(false); // chặn cú click ngay sau khi thả chuột
    const [pos, setPos] = useState(readPos);     // vị trí user chọn (đã lưu)
    const [view, setView] = useState(pos);       // vị trí đang hiện, đã kẹp vào màn hình
    const [dragging, setDragging] = useState(false);
    const [panelBox, setPanelBox] = useState(null);   // {side,left,top,maxHeight} của khung chat
    const [bubbleSide, setBubbleSide] = useState({ below: false, alignLeft: false });
    const [viewport, setViewport] = useState(() => ({ w: window.innerWidth, h: window.innerHeight }));

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

    // Đổi cỡ cửa sổ / xoay điện thoại: kéo con cú về lại trong màn hình (chỉ vị trí đang hiện,
    // vị trí user đã chọn vẫn giữ để nới cửa sổ ra là cú về đúng chỗ cũ).
    // Mở/đóng khung chat hay hiện câu thoại KHÔNG đụng tới vị trí con cú.
    useLayoutEffect(() => {
        if (hidden || drag.current?.moved) return;   // đang kéo thì moveDrag tự kẹp
        const c = clampPos(pos, boxRef.current);
        setView((v) => (c.right === v.right && c.bottom === v.bottom ? v : c));
    }, [hidden, pos, viewport]);

    useEffect(() => {
        if (hidden) return;
        const onResize = () => setViewport({ w: window.innerWidth, h: window.innerHeight });
        window.addEventListener('resize', onResize);
        // Con cú đổi cỡ (bản 2D tạm -> bản 3D, hết bị ẩn sau hộp thoại): đo lại cho chắc
        const ro = typeof ResizeObserver !== 'undefined' && boxRef.current ? new ResizeObserver(onResize) : null;
        ro?.observe(boxRef.current);
        return () => { window.removeEventListener('resize', onResize); ro?.disconnect(); };
    }, [hidden]);

    // Đặt khung chat dính vào con cú ở phía còn trống (trên/dưới/trái/phải), kẹp trong màn hình.
    // Chạy trước khi vẽ nên không bị chớp ở vị trí sai.
    useLayoutEffect(() => {
        if (hidden || !open) { panelSide.current = null; return; }
        const owlEl = boxRef.current;
        const wrap = panelRef.current;
        const inner = wrap?.firstElementChild;
        if (!owlEl || !inner) return;
        inner.style.maxHeight = '';   // đo cỡ tự nhiên, không tính lần thu nhỏ trước
        const panel = { width: inner.offsetWidth, height: inner.offsetHeight };
        const keep = dragging ? panelSide.current : null;
        const next = placePanel(owlEl.getBoundingClientRect(), panel, window.innerWidth, window.innerHeight, keep);
        if (next.maxHeight != null) inner.style.maxHeight = `${next.maxHeight}px`;
        panelSide.current = next.side;
        setPanelBox((b) => (b && b.side === next.side && b.left === next.left && b.top === next.top
            && b.maxHeight === next.maxHeight ? b : next));
    }, [hidden, open, view, viewport, dragging]);

    // Câu thoại: hiện phía trên con cú, sát mép trên thì xuống dưới; canh theo phía gần mép màn hình
    useLayoutEffect(() => {
        if (hidden || !bubble || !boxRef.current) return;
        const r = boxRef.current.getBoundingClientRect();
        const next = { below: r.top < 110, alignLeft: r.left + r.width / 2 < window.innerWidth / 2 };
        setBubbleSide((b) => (b.below === next.below && b.alignLeft === next.alignLeft ? b : next));
    }, [hidden, bubble, view, viewport]);

    const toggle = () => {
        if (justDragged.current) { justDragged.current = false; return; }
        clearTimeout(bubbleTimer.current);
        bubbleRef.current = null;
        setBubble(null);
        setOpen((o) => !o);
        play('jump', 500);
    };

    if (hidden) return null;

    const panelStyle = panelBox
        ? { left: panelBox.left, top: panelBox.top }
        : { left: 0, top: 0, visibility: 'hidden' };   // lần vẽ đầu chỉ để đo, chưa cho thấy

    return (
        <>
            {open && (
                <div ref={panelRef} style={panelStyle} data-side={panelBox?.side}
                     className={`lv-assistant lv-assistant-panel fixed z-50${dragging ? ' lv-assistant--dragging' : ''}`}>
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

            <div ref={boxRef} style={{ right: view.right, bottom: view.bottom }}
                 className={`lv-assistant fixed z-50 flex pointer-events-none${dragging ? ' lv-assistant--dragging' : ''}`}>
                {bubble && !open && (
                    // Đặt tuyệt đối theo con cú nên câu thoại không làm cú xê dịch
                    <button type="button" onClick={toggle}
                            className={`chibi-bubble lv-glass-thick pointer-events-auto absolute w-[max-content] max-w-[220px] px-3.5 py-2.5 rounded-2xl
                                       text-sm text-on-surface text-left ${bubbleSide.below ? 'top-full mt-2' : 'bottom-full mb-2'}
                                       ${bubbleSide.alignLeft ? 'left-3' : 'right-3'}`}>
                        {bubble}
                    </button>
                )}

                <button type="button" onClick={toggle} aria-label={t(open ? 'assistant.close' : 'assistant.open')}
                        aria-expanded={open}
                        title={t('assistant.launcherHint')} {...dragHandlers}
                        className={`lv-assistant-launcher lv-assistant-handle pointer-events-auto focus:outline-none focus-visible:ring-2 focus-visible:ring-primary`}>
                    <Suspense fallback={<ChibiAvatar action={thinking ? 'think' : action} />}>
                        <Owl3D action={thinking ? 'think' : action} costume={shownCostume} />
                    </Suspense>
                </button>
            </div>
        </>
    );
}
