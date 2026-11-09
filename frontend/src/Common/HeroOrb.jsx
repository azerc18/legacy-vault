import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useReducedMotion } from 'motion/react';

/**
 * Quả cầu di sản ở hero landing.
 * Ảnh nền (hero-orb.webp) đã được tách sẵn: bỏ các vòng quỹ đạo và chiếc quai khoá khỏi ảnh gốc,
 * quai khoá nằm riêng trong hero-orb-shackle.webp. Mọi toạ độ bên dưới tính theo hệ 1672x941 của ảnh,
 * vẽ bằng một SVG phủ lên ảnh nên co giãn theo ảnh ở mọi kích thước màn hình.
 *
 *  - Các vòng quỹ đạo quay liên tục, nghiêng dần như đang xoay trong không gian.
 *  - Rê chuột lại gần (hoặc focus bằng bàn phím): mắt cú sáng lên, quai khoá bật mở.
 *  - Bấm: lóe sáng toả ra phủ màn hình rồi sang /login, trang đăng nhập hiện dần từ nền sáng.
 */
const W = 1672;
const H = 941;
const CX = 834;
const CY = 460;
const R = 153;                       // bán kính quả cầu trong ảnh
const SPHERE = 183;                  // nửa cạnh ảnh quả cầu đã tách (hero-orb-sphere.webp)
const RING = { cx: 843, cy: 445, s: 395 };   // tâm và nửa cạnh ảnh vòng xanh đã tách (hero-orb-ring.webp)
const NEAR = R * 1.9;                // khoảng cách coi là "lại gần"
const EYES = [[800, 441], [868, 441]];
const SHACKLE = { x: 784, y: 340, w: 104, h: 59, pivotX: 868, pivotY: 397 };
const GO_TO = '/login';

// Viền mờ dần theo đường cong mượt (smoothstep) ở cả 4 cạnh, để ảnh tan vào nền trang không để lại đường biên
const edgeFade = (dir, size) => {
    const stops = [];
    for (let i = 0; i <= 10; i++) {
        const k = i / 10;
        const a = k * k * (3 - 2 * k);
        stops.push(`rgba(0,0,0,${a.toFixed(3)}) ${(k * size).toFixed(1)}%`);
    }
    const rev = [...stops].reverse().map((_, i) => {
        const k = 1 - i / 10;
        const a = k * k * (3 - 2 * k);
        return `rgba(0,0,0,${a.toFixed(3)}) ${(100 - k * size).toFixed(1)}%`;
    });
    return `linear-gradient(${dir}, ${stops.join(', ')}, ${rev.join(', ')})`;
};
const MASK = `${edgeFade('to right', 36)}, ${edgeFade('to bottom', 22)}`;

const CSS = `
@keyframes lv-orb-spin { to { transform: rotate(360deg); } }
@keyframes lv-orb-breath { 0%,100% { opacity: .5; } 50% { opacity: .85; } }
@keyframes lv-orb-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-6px); } }
.lv-orb-spin { animation: lv-orb-spin linear infinite; }
.lv-orb-spin-rev { animation: lv-orb-spin linear infinite reverse; }
.lv-orb-halo { animation: lv-orb-breath 4.2s ease-in-out infinite; transition: transform .6s ease; transform-origin: ${CX}px ${CY}px; }
.lv-orb-rings { opacity: .8; transition: opacity .5s ease, transform .7s cubic-bezier(.2,.8,.2,1); transform-origin: ${CX}px ${CY}px; }
.lv-orb-blue { opacity: .75; transition: opacity .6s ease; }
[data-near="true"] .lv-orb-blue { opacity: 1; }
.lv-orb-eye { opacity: 0; transition: opacity .4s ease; }
.lv-orb-bloom { opacity: 0; transition: opacity .5s ease; }
.lv-orb-sphere { transform-origin: ${CX}px ${CY}px; transition: transform .9s cubic-bezier(.2,.8,.2,1); }
[data-near="true"] .lv-orb-sphere { transform: scale(1.14); }
.lv-orb-shackle { transition: transform .7s cubic-bezier(.3,1.5,.45,1); transform-origin: ${SHACKLE.pivotX}px ${SHACKLE.pivotY}px; }
[data-near="true"] .lv-orb-rings { opacity: 1; }
[data-near="true"] .lv-orb-eye { opacity: 1; }
[data-near="true"] .lv-orb-bloom { opacity: .35; }
[data-near="true"] .lv-orb-shackle { transform: translate(-6px, -15px) rotate(-30deg); }
@media (prefers-reduced-motion: reduce) {
  .lv-orb-spin, .lv-orb-spin-rev, .lv-orb-halo { animation: none; }
  .lv-orb-sphere, .lv-orb-shackle, .lv-orb-rings, .lv-orb-eye, .lv-orb-bloom { transition: none; }
}
`;

function Orbit({ rx, ry, tilt, spin, reverse, squishDur, delay, width, animate }) {
    const squash = animate ? (
        <animate attributeName="ry" values={`${ry};${(ry * 0.28).toFixed(1)};${ry}`} dur={`${squishDur}s`}
                 begin={`${delay}s`} repeatCount="indefinite" calcMode="spline" keySplines=".45 0 .55 1;.45 0 .55 1" />
    ) : null;
    return (
        <g transform={`rotate(${tilt} ${CX} ${CY})`}>
            <g className={animate ? (reverse ? 'lv-orb-spin-rev' : 'lv-orb-spin') : undefined}
               style={{ transformOrigin: `${CX}px ${CY}px`, animationDuration: `${spin}s` }}>
                <ellipse cx={CX} cy={CY} rx={rx} ry={ry} fill="none" stroke="url(#lvorb-grad)" strokeWidth={width} filter="url(#lvorb-glow)">
                    {squash}
                </ellipse>
                {animate && (
                    <ellipse cx={CX} cy={CY} rx={rx} ry={ry} fill="none" stroke="#fff4d6" strokeWidth={width * 1.1}
                             strokeLinecap="round" pathLength="1000" strokeDasharray="90 910" filter="url(#lvorb-glow)">
                        {squash}
                        <animate attributeName="stroke-dashoffset" from="0" to="-1000" dur={`${squishDur * 0.7}s`} repeatCount="indefinite" />
                    </ellipse>
                )}
            </g>
        </g>
    );
}

export default function HeroOrb() {
    const { t } = useTranslation('landing');
    const navigate = useNavigate();
    const reduce = useReducedMotion();
    const stageRef = useRef(null);
    const busy = useRef(false);
    const [hover, setHover] = useState(false);
    const [focus, setFocus] = useState(false);
    const [going, setGoing] = useState(false);
    const near = hover || focus || going;

    // Chuột: tính khoảng cách tới tâm quả cầu theo toạ độ ảnh. Cảm ứng (không có hover): tự "mở" khi cuộn tới giữa màn hình.
    useEffect(() => {
        const el = stageRef.current;
        if (!el) return undefined;
        if (window.matchMedia('(hover: none)').matches) {
            const io = new IntersectionObserver(([e]) => setHover(e.isIntersecting && e.intersectionRatio > 0.6), { threshold: [0, 0.6, 1] });
            io.observe(el);
            return () => io.disconnect();
        }
        let raf = 0;
        let last = false;
        const onMove = (ev) => {
            cancelAnimationFrame(raf);
            raf = requestAnimationFrame(() => {
                const r = el.getBoundingClientRect();
                if (!r.width) return;
                const x = ((ev.clientX - r.left) / r.width) * W;
                const y = ((ev.clientY - r.top) / r.height) * H;
                const n = Math.hypot(x - CX, y - CY) < NEAR;
                if (n !== last) { last = n; setHover(n); }
            });
        };
        window.addEventListener('pointermove', onMove, { passive: true });
        return () => { cancelAnimationFrame(raf); window.removeEventListener('pointermove', onMove); };
    }, []);

    const go = useCallback(() => {
        if (busy.current) return;
        busy.current = true;
        setGoing(true);
        if (reduce) { navigate(GO_TO); return; }

        const r = stageRef.current.getBoundingClientRect();
        const cx = r.left + (CX / W) * r.width;
        const cy = r.top + (CY / H) * r.height;
        const reach = Math.hypot(Math.max(cx, window.innerWidth - cx), Math.max(cy, window.innerHeight - cy));

        // Lớp lóe sáng gắn thẳng vào <body> để không mất khi React đổi trang
        const flash = document.createElement('div');
        Object.assign(flash.style, {
            position: 'fixed', left: `${cx}px`, top: `${cy}px`, width: '40px', height: '40px',
            marginLeft: '-20px', marginTop: '-20px', borderRadius: '50%', zIndex: '2147483000',
            background: 'radial-gradient(circle, #ffffff 0%, #fff7e0 40%, rgba(255,222,150,.9) 68%, rgba(255,222,150,0) 100%)',
            willChange: 'transform, opacity', pointerEvents: 'auto', opacity: '0',
        });
        document.body.appendChild(flash);
        const scale = (reach / 14) * 1.15;
        const grow = flash.animate(
            [
                { transform: 'scale(.6)', opacity: 0 },
                { transform: 'scale(2)', opacity: 1, offset: 0.2 },
                { transform: `scale(${scale})`, opacity: 1 },
            ],
            { duration: 760, easing: 'cubic-bezier(.55,0,.85,.35)', fill: 'forwards' },
        );
        grow.finished.catch(() => {}).then(() => {
            flash.style.background = '#fff7e0';
            navigate(GO_TO);
            // chờ trang đăng nhập dựng xong rồi mới tan dần
            requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(() => {
                flash.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 950, easing: 'ease-out', fill: 'forwards' })
                    .finished.catch(() => {}).then(() => flash.remove());
            }, 140)));
        });
    }, [navigate, reduce]);

    const animate = !reduce;
    const hitStyle = {
        left: `${((CX - R) / W) * 100}%`, top: `${((CY - R) / H) * 100}%`,
        width: `${((2 * R) / W) * 100}%`, height: `${((2 * R) / H) * 100}%`,
    };

    return (
        <div
            className="relative mx-auto w-full max-w-[1040px] aspect-[4/3] sm:aspect-video overflow-hidden"
            style={{
                // Mờ dần cả 4 cạnh để ảnh tan vào nền trang
                WebkitMaskImage: MASK,
                maskImage: MASK,
                WebkitMaskComposite: 'source-in',
                maskComposite: 'intersect',
            }}
        >
            <style>{CSS}</style>
            <div
                ref={stageRef}
                data-near={near ? 'true' : 'false'}
                className="absolute left-1/2 top-1/2 w-[190%] sm:w-full -translate-x-1/2 -translate-y-1/2"
                style={{ aspectRatio: `${W} / ${H}` }}
            >
                <img src="/hero-orb-bg.webp" alt={t('orb.alt')} width={W} height={H} fetchPriority="high" decoding="async"
                     className="absolute inset-0 h-full w-full select-none" draggable="false"
                     style={{ filter: 'brightness(1.22) contrast(0.78) saturate(1.05)' }} />

                <svg viewBox={`0 0 ${W} ${H}`} className="absolute inset-0 h-full w-full pointer-events-none" aria-hidden="true">
                    <defs>
                        <linearGradient id="lvorb-grad" x1="0" y1="0" x2="1" y2="0">
                            <stop offset="0" stopColor="#ffd68c" stopOpacity=".95" />
                            <stop offset=".5" stopColor="#ffeab8" stopOpacity=".18" />
                            <stop offset="1" stopColor="#ffd68c" stopOpacity=".95" />
                        </linearGradient>
                        <radialGradient id="lvorb-halo">
                            <stop offset=".55" stopColor="#ffc86b" stopOpacity=".0" />
                            <stop offset=".82" stopColor="#ffc86b" stopOpacity=".38" />
                            <stop offset="1" stopColor="#ffc86b" stopOpacity="0" />
                        </radialGradient>
                        <radialGradient id="lvorb-eye">
                            <stop offset="0" stopColor="#ffffff" stopOpacity="1" />
                            <stop offset=".35" stopColor="#ffe9b0" stopOpacity=".85" />
                            <stop offset="1" stopColor="#ffc86b" stopOpacity="0" />
                        </radialGradient>
                        <radialGradient id="lvorb-bloom">
                            <stop offset="0" stopColor="#fff2cc" stopOpacity=".55" />
                            <stop offset=".6" stopColor="#ffd37a" stopOpacity=".22" />
                            <stop offset="1" stopColor="#ffd37a" stopOpacity="0" />
                        </radialGradient>
                        <filter id="lvorb-glow" x="-20%" y="-60%" width="140%" height="220%">
                            <feGaussianBlur stdDeviation="3.2" result="b" />
                            <feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
                        </filter>
                        {/* Trong lòng quả cầu (thuỷ tinh) vòng quỹ đạo mờ đi một nửa */}
                        <mask id="lvorb-glass" maskUnits="userSpaceOnUse" x="0" y="0" width={W} height={H}>
                            <rect width={W} height={H} fill="#fff" />
                            <circle cx={CX} cy={CY} r={R - 4} fill="#7a7a7a" />
                        </mask>
                    </defs>

                    {/* Vòng xanh lớn có sẵn trong ảnh: được tách riêng ra rồi cho xoay quanh quả cầu */}
                    <g className="lv-orb-blue" style={{ mixBlendMode: 'screen' }}>
                        <image href="/hero-orb-ring.webp" x={RING.cx - RING.s} y={RING.cy - RING.s} width={RING.s * 2} height={RING.s * 2}
                               className={animate ? 'lv-orb-spin' : undefined}
                               style={{ transformOrigin: `${RING.cx}px ${RING.cy}px`, animationDuration: '48s', filter: 'brightness(2.1) saturate(1.3)' }} />
                    </g>

                    {/* Quả cầu là một lớp riêng: khi lại gần chỉ nó (cùng vòng, quầng, mắt, khoá) phóng to, ảnh nền đứng yên */}
                    <g className="lv-orb-sphere">
                    <image href="/hero-orb-sphere.webp" x={CX - SPHERE} y={CY - SPHERE} width={SPHERE * 2} height={SPHERE * 2} />
                    <circle className="lv-orb-halo" cx={CX} cy={CY} r={R * 1.55} fill="url(#lvorb-halo)" style={{ mixBlendMode: 'screen' }} />

                    <g className="lv-orb-rings" mask="url(#lvorb-glass)" style={{ mixBlendMode: 'screen' }}>
                        <Orbit rx={248} ry={78} tilt={-20} spin={46} squishDur={9} delay={0} width={3.4} animate={animate} />
                        <Orbit rx={226} ry={62} tilt={24} spin={58} reverse squishDur={12} delay={-4} width={3} animate={animate} />
                        <Orbit rx={200} ry={40} tilt={66} spin={72} squishDur={15} delay={-7} width={2} animate={animate} />
                    </g>

                    {/* Quai khoá: bật mở khi lại gần */}
                    <image className="lv-orb-shackle" href="/hero-orb-shackle.webp"
                           x={SHACKLE.x} y={SHACKLE.y} width={SHACKLE.w} height={SHACKLE.h} />

                    <circle className="lv-orb-bloom" cx={CX} cy={CY} r={R * 0.95} fill="url(#lvorb-bloom)" style={{ mixBlendMode: 'screen' }} />

                    {/* Mắt cú sáng lên */}
                    {EYES.map(([ex, ey]) => (
                        <g key={ex} className="lv-orb-eye" style={{ mixBlendMode: 'screen' }}>
                            <circle cx={ex} cy={ey} r="30" fill="url(#lvorb-eye)" />
                            <circle cx={ex} cy={ey} r="7" fill="#fff" filter="url(#lvorb-glow)" />
                        </g>
                    ))}
                    </g>
                </svg>

                {/* Vùng bấm: tròn, đúng khít quả cầu; bàn phím vào được bằng Tab */}
                <button
                    type="button"
                    onClick={go}
                    onFocus={() => setFocus(true)}
                    onBlur={() => setFocus(false)}
                    aria-label={t('orb.aria')}
                    style={hitStyle}
                    className="absolute rounded-full cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-[#ffe2a3] focus-visible:ring-offset-2 focus-visible:ring-offset-transparent"
                />
            </div>
        </div>
    );
}
