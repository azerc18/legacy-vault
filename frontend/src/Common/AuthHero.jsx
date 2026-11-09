// Cột trái của các trang đăng nhập / đăng ký / quên mật khẩu / OTP.
// 0–2s intro (chạy 1 lần mỗi phiên): ảnh rõ dần, tiêu đề hiện từng từ, dòng phụ.
// Sau đó: ảnh "thở", trôi nhẹ theo chuột, hạt sáng bay lên từ chiếc chìa khoá,
// vệt sáng thỉnh thoảng quét qua tiêu đề, và một dòng chữ lớn hiện / biến mất từng ký tự (kiểu web thời trang).
// Giảm chuyển động (prefers-reduced-motion): chỉ còn ảnh và chữ tĩnh.
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import BrandMark from './BrandMark.jsx';
import './authHero.css';

// Intro chỉ chạy lần đầu; đổi qua lại login / register không chạy lại
let introPlayed = false;

const EASE = [0.22, 1, 0.36, 1];
// Vị trí chiếc chìa khoá trong ảnh (tỉ lệ theo khung cột trái), nơi hạt sáng bay lên
const KEY_X = 0.44;
const KEY_Y = 0.5;

/* ---------------- Hạt sáng (canvas, nhẹ, dừng khi tab ẩn) ---------------- */
function Embers({ active }) {
    const ref = useRef(null);
    useEffect(() => {
        if (!active) return undefined;
        const canvas = ref.current;
        const ctx = canvas.getContext('2d');
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        let w = 0;
        let h = 0;
        let raf = 0;
        let last = performance.now();
        let spawn = 0;
        const parts = [];

        const resize = () => {
            const r = canvas.getBoundingClientRect();
            w = r.width; h = r.height;
            canvas.width = w * dpr; canvas.height = h * dpr;
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        };
        resize();
        const ro = new ResizeObserver(resize);
        ro.observe(canvas);

        const add = () => parts.push({
            x: w * KEY_X + (Math.random() - 0.5) * w * 0.12,
            y: h * KEY_Y + (Math.random() - 0.5) * h * 0.06,
            vx: (Math.random() - 0.5) * 8,
            vy: -(14 + Math.random() * 22),
            r: 1.2 + Math.random() * 2.2,
            life: 0,
            max: 4 + Math.random() * 4,
            sway: Math.random() * Math.PI * 2,
        });

        const tick = (now) => {
            const dt = Math.min(0.05, (now - last) / 1000);
            last = now;
            spawn += dt;
            while (spawn > 0.3) { spawn -= 0.3; if (parts.length < 45) add(); }
            ctx.clearRect(0, 0, w, h);
            for (let i = parts.length - 1; i >= 0; i--) {
                const p = parts[i];
                p.life += dt;
                if (p.life > p.max) { parts.splice(i, 1); continue; }
                p.sway += dt * 1.3;
                p.x += (p.vx + Math.sin(p.sway) * 6) * dt;
                p.y += p.vy * dt;
                const t = p.life / p.max;
                const alpha = Math.sin(Math.PI * t) * 0.9;   // hiện dần rồi tan
                const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r * 4);
                g.addColorStop(0, `rgba(255, 226, 160, ${alpha})`);
                g.addColorStop(1, 'rgba(255, 190, 90, 0)');
                ctx.fillStyle = g;
                ctx.beginPath();
                ctx.arc(p.x, p.y, p.r * 4, 0, Math.PI * 2);
                ctx.fill();
            }
            raf = requestAnimationFrame(tick);
        };

        const start = () => { last = performance.now(); raf = requestAnimationFrame(tick); };
        const stop = () => cancelAnimationFrame(raf);
        const onVis = () => (document.hidden ? stop() : start());
        document.addEventListener('visibilitychange', onVis);
        start();
        return () => { stop(); ro.disconnect(); document.removeEventListener('visibilitychange', onVis); };
    }, [active]);
    return <canvas ref={ref} aria-hidden="true" className="absolute inset-0 w-full h-full pointer-events-none" />;
}

/* ---------------- Dòng chữ hiện / biến mất từng ký tự ---------------- */
function Rotator({ words, start, reduce }) {
    const [i, setI] = useState(0);
    useEffect(() => {
        if (!start || reduce || words.length < 2) return undefined;
        const id = setInterval(() => setI((n) => (n + 1) % words.length), 3200);
        return () => clearInterval(id);
    }, [start, reduce, words.length]);

    if (!words.length) return null;
    if (reduce) return <span className="lv-hero-rotator">{words[0]}</span>;

    const word = words[i];
    return (
        <span className="lv-hero-rotator" aria-live="off">
            <AnimatePresence mode="wait">
                {start && (
                    <motion.span key={word} className="inline-block" initial="hidden" animate="show" exit="leave" aria-label={word}>
                        {[...word].map((ch, k) => (
                            <motion.span
                                key={k}
                                aria-hidden="true"
                                className="lv-hero-char"
                                variants={{
                                    hidden: { y: '110%', opacity: 0 },
                                    show: { y: '0%', opacity: 1, transition: { duration: 0.55, ease: EASE, delay: k * 0.035 } },
                                    leave: { y: '-110%', opacity: 0, transition: { duration: 0.4, ease: [0.55, 0, 1, 0.45], delay: k * 0.02 } },
                                }}
                            >
                                {ch}
                            </motion.span>
                        ))}
                    </motion.span>
                )}
            </AnimatePresence>
        </span>
    );
}

export default function AuthHero() {
    const { t } = useTranslation('auth');
    const reduce = useReducedMotion();
    const [replay] = useState(() => introPlayed);    // đã chạy intro trong phiên này
    const [ready, setReady] = useState(replay);      // intro xong -> bật hiệu ứng liên tục
    const [shift, setShift] = useState({ x: 0, y: 0 });
    const asideRef = useRef(null);

    const words = useMemo(() => {
        const w = t('brand.rotating', { returnObjects: true });
        return Array.isArray(w) ? w : [];
    }, [t]);
    const titleWords = useMemo(() => t('brand.tagline').split(' '), [t]);

    useEffect(() => {
        introPlayed = true;
        if (replay) return undefined;
        const id = setTimeout(() => setReady(true), reduce ? 0 : 1700);
        return () => clearTimeout(id);
    }, [replay, reduce]);

    // Ảnh trôi nhẹ ngược hướng chuột (tối đa ~10px)
    const onMove = (e) => {
        if (reduce) return;
        const r = asideRef.current.getBoundingClientRect();
        setShift({
            x: ((e.clientX - r.left) / r.width - 0.5) * -20,
            y: ((e.clientY - r.top) / r.height - 0.5) * -14,
        });
    };

    const delay = (s) => (replay || reduce ? 0 : s);
    const appear = (d) => ({
        initial: replay || reduce ? false : { opacity: 0, y: 18, filter: 'blur(6px)' },
        animate: { opacity: 1, y: 0, filter: 'blur(0px)' },
        transition: { duration: 0.7, ease: EASE, delay: delay(d) },
    });

    return (
        <aside ref={asideRef} onMouseMove={onMove} onMouseLeave={() => setShift({ x: 0, y: 0 })}
               className="relative hidden lg:flex flex-col justify-between overflow-hidden bg-brand text-white p-12">
            <div className="absolute -inset-4 lv-hero-parallax" style={{ transform: `translate3d(${shift.x}px, ${shift.y}px, 0)` }}>
                <img src="/key-photo.jpg" alt="" aria-hidden="true"
                     className={`absolute inset-0 w-full h-full object-cover lv-hero-photo ${replay ? 'is-replay' : ''}`}
                     style={{ objectPosition: '60% 50%' }} />
            </div>
            <div aria-hidden="true" className="absolute inset-0"
                 style={{ background: 'linear-gradient(180deg,rgba(11,16,32,.72) 0%,rgba(11,16,32,.35) 38%,rgba(11,16,32,.86) 100%)' }} />
            <div aria-hidden="true" className="absolute inset-0"
                 style={{ background: 'linear-gradient(90deg,rgba(11,16,32,.7) 0%,rgba(11,16,32,0) 75%)' }} />
            {/* Hạt sáng nằm trên lớp phủ tối để thấy rõ, vẫn trôi theo chuột cùng ảnh */}
            <div className="absolute -inset-4 lv-hero-parallax pointer-events-none" style={{ transform: `translate3d(${shift.x}px, ${shift.y}px, 0)` }}>
                <Embers active={ready && !reduce} />
            </div>

            <motion.div {...appear(0.1)} className="relative">
                <Link to="/" className="flex items-center gap-2 text-white font-bold tracking-[0.18em] text-sm">
                    <BrandMark className="h-[30px] w-[30px]" />LEGACYVAULT
                </Link>
            </motion.div>

            <div className="relative max-w-[520px] flex flex-col gap-5">
                <h1 className="font-headline-lg text-[44px] leading-[1.1] font-semibold tracking-tight" aria-label={t('brand.tagline')}>
                    {titleWords.map((w, k) => (
                        <motion.span
                            key={`${w}-${k}`}
                            aria-hidden="true"
                            className={`inline-block mr-[0.25em] ${k >= titleWords.length - 2 ? 'lv-hero-sheen' : ''}`}
                            initial={replay || reduce ? false : { opacity: 0, y: '0.6em', filter: 'blur(8px)' }}
                            animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
                            transition={{ duration: 0.65, ease: EASE, delay: delay(0.3 + k * 0.07) }}
                        >
                            {w}
                        </motion.span>
                    ))}
                </h1>

                <motion.p {...appear(0.3 + titleWords.length * 0.07 + 0.15)}
                          className="font-headline-lg text-[52px] leading-none italic font-semibold tracking-tight text-[#f6e3b0] -mt-1">
                    <Rotator words={words} start={ready} reduce={reduce} />
                </motion.p>

                <motion.p {...appear(0.3 + titleWords.length * 0.07 + 0.3)} className="text-lg text-white/80">
                    {t('brand.footer')}
                </motion.p>
            </div>

            <motion.p {...appear(1.2)} className="relative text-sm text-white/60">SWP391, FPT University</motion.p>
        </aside>
    );
}
