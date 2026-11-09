import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from './auth/auth.js';
import LanguageSwitch from './Common/LanguageSwitch.jsx';
import BrandMark from './Common/BrandMark.jsx';
import InteractiveVaultLock from './Common/InteractiveVaultLock.jsx';
import { DecodeLoop } from './Common/DecodeText.jsx';
import CipherField from './Common/CipherField.jsx';
import ProblemTimeline from './Common/ProblemTimeline.jsx';
import CyberSteps from './Common/CyberSteps.jsx';
import './Common/cyber.css';
import { SecurityCard, VaultBox, RoleCard } from './Common/LandingCards.jsx';
import { ACCENT } from './Common/landingAccents.js';
import AppPreview from './Common/AppPreview.jsx';
import { useReducedMotion } from 'motion/react';
import { Magnetic } from '@animate-ui/react/primitives/effects/magnetic';
import { AutoHeight } from '@animate-ui/react/primitives/effects/auto-height';

// ---------- Cấu trúc (icon + khoá). Chữ nằm trong locales/{en,vi}/landing.json ----------
const NAV = [
    { key: 'how', href: '#how' },
    { key: 'security', href: '#security' },
    { key: 'roles', href: '#roles' },
    { key: 'faq', href: '#faq' },
];
const BADGES = [
    { key: 'encryption', icon: 'lock' },
    { key: 'twoStep', icon: 'verified_user' },
    { key: 'legal', icon: 'gavel' },
    { key: 'dms', icon: 'favorite' },
];
const PROBLEMS = [
    { key: 'day1', icon: 'help' },
    { key: 'month1', icon: 'block' },
    { key: 'month6', icon: 'hourglass_bottom' },
    { key: 'forever', icon: 'cloud_off' },
];
const STEPS = [
    { key: 'create', icon: 'inventory_2' },
    { key: 'people', icon: 'group_add' },
    { key: 'checkin', icon: 'favorite' },
    { key: 'handover', icon: 'verified' },
];
const SECURITY = [
    { key: 'encrypted', icon: 'enhanced_encryption' },
    { key: 'noFalseAlarm', icon: 'notifications_active' },
    { key: 'human', icon: 'policy' },
];
const ASSETS = [
    { key: 'passwords', icon: 'password' },
    { key: 'banks', icon: 'account_balance' },
    { key: 'crypto', icon: 'currency_bitcoin' },
    { key: 'legal', icon: 'description' },
    { key: 'notes', icon: 'sticky_note_2' },
    { key: 'files', icon: 'photo_library' },
];
const ROLES = [
    { key: 'OWNER', icon: 'person' },
    { key: 'EXECUTOR', icon: 'badge' },
    { key: 'BENEFICIARY', icon: 'family_restroom' },
    { key: 'LEGAL_VERIFIER', icon: 'gavel' },
];
const FAQS = ['staff', 'forget', 'unlock', 'change'];

// ---------- Khối dùng lại ----------
function Section({ id, eyebrow, title, subtitle, children, mode = 'light' }) {
    const dark = mode === 'dark';
    return (
        <section id={id} data-mode={mode} className="relative z-10 py-24 px-5 min-h-[70vh] flex items-center">
            <div className="max-w-6xl mx-auto w-full">
                <div className="lv-reveal text-center max-w-2xl mx-auto mb-12">
                    {eyebrow && <p className={`font-label-sm uppercase tracking-wider mb-2 ${dark ? 'text-[#b9c9ff]' : 'text-secondary'}`}>{eyebrow}</p>}
                    <h2 className={`font-headline-md text-headline-md ${dark ? 'text-white' : 'text-on-surface'}`}>{title}</h2>
                    {subtitle && <p className={`mt-3 ${dark ? 'text-white/70' : 'text-on-surface-variant'}`}>{subtitle}</p>}
                </div>
                <div className="lv-reveal">{children}</div>
            </div>
        </section>
    );
}

function Icon({ name, className = '' }) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

// ---------- Trang ----------
export default function Dashboard() {
    const { t } = useTranslation('landing');
    const reduceMotion = useReducedMotion();
    // Tiêu đề hero chia dòng sẵn trong i18n (hero.titleLines); thiếu thì dùng cả câu
    const heroLines = useMemo(() => {
        const l = t('hero.titleLines', { returnObjects: true });
        return Array.isArray(l) && l.length ? l : [t('hero.title')];
    }, [t]);
    const [openFaq, setOpenFaq] = useState(0);
    const [mode, setMode] = useState('dark');
    const rootRef = useRef(null);
    const light = mode === 'light';

    // Nền đổi sáng/tối theo phần đang nằm giữa màn hình
    useEffect(() => {
        const els = rootRef.current ? rootRef.current.querySelectorAll('[data-mode]') : [];
        if (!('IntersectionObserver' in window) || els.length === 0) return undefined;
        const io = new IntersectionObserver((entries) => {
            entries.forEach((e) => { if (e.isIntersecting) setMode(e.target.dataset.mode); });
        }, { rootMargin: '-45% 0px -45% 0px' });
        els.forEach((el) => io.observe(el));
        return () => io.disconnect();
    }, []);

    // Phần tử hiện dần khi cuộn tới (mờ -> rõ, trượt nhẹ lên), so le từng thẻ trong một lưới
    useLayoutEffect(() => {
        const root = rootRef.current;
        if (!root || !('IntersectionObserver' in window)) return undefined;
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return undefined;
        const items = [];
        const add = (el, delay) => {
            if (!el || items.some((i) => i.el === el)) return;
            el.style.setProperty('--d', `${delay}s`);
            el.classList.add('lv-rv');
            items.push({ el, delay });
        };
        // Hero: hiện sau khi tiêu đề chạy chữ xong
        add(root.querySelector('.lv-hero-rv'), 0.8);
        add(root.querySelectorAll('.lv-hero-rv')[1], 0.95);
        root.querySelectorAll('.lv-hero-badges > *').forEach((el, i) => add(el, 1.1 + i * 0.08));
        // Các phần còn lại: tiêu đề, rồi từng thẻ so le
        root.querySelectorAll('.lv-reveal').forEach((el) => { if (!el.querySelector('.lv-stagger')) add(el, 0); }); // khối có lưới thì để từng thẻ tự hiện
        root.querySelectorAll('.lv-stagger').forEach((grid) => {
            Array.from(grid.children).forEach((el, i) => add(el, 0.1 + Math.min(i, 5) * 0.09));
        });
        const io = new IntersectionObserver((entries) => {
            entries.forEach((e) => {
                if (!e.isIntersecting) return;
                const el = e.target;
                io.unobserve(el);
                el.classList.add('lv-in');
                const d = parseFloat(el.style.getPropertyValue('--d')) || 0;
                // Xong thì gỡ lớp để không chặn hiệu ứng hover/transition riêng của phần tử
                setTimeout(() => { el.classList.remove('lv-rv', 'lv-in'); el.style.removeProperty('--d'); }, (d + 1) * 1000);
            });
        }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
        items.forEach(({ el }) => io.observe(el));
        return () => {
            io.disconnect();
            items.forEach(({ el }) => { el.classList.remove('lv-rv', 'lv-in'); el.style.removeProperty('--d'); });
        };
    }, []);

    // Tự khớp trang: vuốt qua ~30% phần kế bên rồi dừng thì tự trượt cho vừa khít phần đó
    useEffect(() => {
        const root = rootRef.current;
        if (!root || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return undefined;
        const THRESHOLD = 0.3;
        let timer = null;
        let lastY = window.scrollY;
        let dir = 1;
        let touching = false;
        let pressed = false;   // đang giữ chuột (kéo thanh cuộn): chưa được tự khớp, nếu không sẽ giành quyền với tay
        let snapping = false;
        let raf = 0;

        const snap = () => {
            if (touching || pressed) return;
            const vh = window.innerHeight;
            const head = root.querySelector('header');
            const off = head ? head.offsetHeight : 0;
            const y = window.scrollY + off; // mép trên vùng nhìn thấy (dưới navbar)
            const view = vh - off;
            const tops = Array.from(root.querySelectorAll('[data-mode]'))
                .filter((el) => el.tagName !== 'DIV')
                .map((el) => ({ top: el.getBoundingClientRect().top + window.scrollY, h: el.offsetHeight }));
            let target = null;
            if (dir > 0) {
                const next = tops.find((s) => s.top > y + 8);
                if (next && next.top - y < view * (1 - THRESHOLD)) target = next.top;
            } else {
                const cur = [...tops].reverse().find((s) => s.top < y - 8);
                // Phần cao hơn khung nhìn: để cuộn tự do, nếu kéo về đáy phần đó thì người dùng không cuộn lên nổi
                if (cur && cur.h <= view) {
                    const curBottom = cur.top + cur.h;
                    // phần trên đang hiện >=30% khung nhìn thì lùi về vừa khít phần đó
                    if (y - curBottom < 0 && curBottom - y >= view * THRESHOLD) target = Math.max(cur.top, curBottom - view);
                }
            }
            if (target === null) return;
            glide(Math.max(0, target - off));
        };

        // Tự trượt bằng rAF (êm hơn smooth mặc định); người dùng chạm/cuộn là dừng ngay
        const cancel = () => { if (raf) cancelAnimationFrame(raf); raf = 0; snapping = false; };
        const glide = (to) => {
            cancel();
            const from = window.scrollY;
            const dist = to - from;
            if (Math.abs(dist) < 2) return;
            const dur = Math.min(760, 380 + Math.abs(dist) * 0.35);
            const t0 = performance.now();
            snapping = true;
            const step = (now) => {
                const k = Math.min(1, (now - t0) / dur);
                const e = k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2; // easeInOutCubic: vào ra đều tay, không giật
                window.scrollTo(0, from + dist * e);
                if (k < 1) raf = requestAnimationFrame(step);
                else { raf = 0; snapping = false; lastY = window.scrollY; }
            };
            raf = requestAnimationFrame(step);
        };

        const onScroll = () => {
            const y = window.scrollY;
            if (snapping) { lastY = y; return; }
            if (Math.abs(y - lastY) > 1) dir = y > lastY ? 1 : -1;
            lastY = y;
            clearTimeout(timer);
            timer = setTimeout(snap, 130);
        };
        const down = () => { touching = true; cancel(); };
        const onMouseDown = () => { pressed = true; clearTimeout(timer); cancel(); };
        const onMouseUp = () => { if (!pressed) return; pressed = false; clearTimeout(timer); timer = setTimeout(snap, 100); };
        const up = () => { touching = false; clearTimeout(timer); timer = setTimeout(snap, 100); };

        const release = () => { pressed = false; cancel(); };
        window.addEventListener('wheel', release, { passive: true });
        window.addEventListener('keydown', release);
        window.addEventListener('mousedown', onMouseDown);
        window.addEventListener('mouseup', onMouseUp);
        window.addEventListener('pointerup', onMouseUp);
        window.addEventListener('pointercancel', onMouseUp);
        window.addEventListener('blur', onMouseUp);
        window.addEventListener('scroll', onScroll, { passive: true });
        window.addEventListener('touchstart', down, { passive: true });
        window.addEventListener('touchend', up, { passive: true });
        return () => {
            clearTimeout(timer);
            cancel();
            window.removeEventListener('wheel', release);
            window.removeEventListener('keydown', release);
            window.removeEventListener('mousedown', onMouseDown);
            window.removeEventListener('mouseup', onMouseUp);
            window.removeEventListener('pointerup', onMouseUp);
            window.removeEventListener('pointercancel', onMouseUp);
            window.removeEventListener('blur', onMouseUp);
            window.removeEventListener('scroll', onScroll);
            window.removeEventListener('touchstart', down);
            window.removeEventListener('touchend', up);
        };
    }, []);

    // Đã đăng nhập -> vào thẳng trang chủ người dùng
    if (getUser()) return <Navigate to="/home" replace />;

    return (
        <div ref={rootRef} className="lv-landing font-body-md text-white antialiased min-h-screen relative">
            <div aria-hidden="true" className="lv-bg fixed inset-0 z-0" data-mode={mode} />
            {/* Mưa ký tự nền chung cho cả trang (1 canvas), đậm/nhạt theo phần đang xem */}
            <CipherField mode={mode} />
            {/* NAVBAR */}
            <header className={`sticky top-0 z-20 backdrop-blur border-b transition-colors duration-500 ${light
                ? 'bg-white/80 border-black/10 text-brand'
                : 'bg-brand/95 border-white/10 text-white'}`}>
                <div className="max-w-6xl mx-auto h-16 px-5 flex items-center justify-between">
                    <Link to="/" className="flex items-center gap-2 font-bold tracking-[0.18em] text-sm">
                        <span className={`rounded-lg transition-colors duration-500 ${light ? 'bg-brand p-[3px]' : 'p-[3px]'}`}>
                            <BrandMark className="h-[30px] w-[30px]" />
                        </span>LEGACYVAULT
                    </Link>
                    <nav className={`hidden md:flex gap-8 text-sm font-semibold ${light ? 'text-brand/70' : 'text-white/75'}`}>
                        {NAV.map((n) => (
                            <a key={n.href} href={n.href} className={light ? 'hover:text-brand' : 'hover:text-white'}>{t(`nav.${n.key}`)}</a>
                        ))}
                    </nav>
                    <div className="flex items-center gap-3">
                        <LanguageSwitch variant={light ? 'light' : 'dark'} />
                        <Link to="/demo" className="h-9 px-3.5 rounded-full text-xs font-bold flex items-center gap-1.5 border border-amber-400/50 text-amber-300 hover:bg-amber-400/10 transition-colors">
                            <Icon name="rocket_launch" className="text-[15px]" />
                            <span>{t('demoNav', 'Dùng thử')}</span>
                        </Link>
                        <Link to="/login" className={`hidden sm:block text-sm font-semibold ${light ? 'text-brand/80 hover:text-brand' : 'text-white/85 hover:text-white'}`}>{t('signIn')}</Link>
                        <Link to="/register" className={`h-10 px-5 rounded-full text-sm font-bold flex items-center ${light ? 'bg-brand text-white hover:bg-brand-container' : 'bg-white text-brand hover:bg-white/85'}`}>
                            {t('getStarted')}
                        </Link>
                    </div>
                </div>
            </header>

            {/* 1. HERO */}
            <section data-mode="dark" className="relative z-10 overflow-hidden text-white px-5 pt-24 pb-20 text-center">
                <img src="/key-photo.jpg" alt="" aria-hidden="true"
                     className="absolute left-1/2 -translate-x-1/2 bottom-0 h-[52%] min-h-[280px] w-auto max-w-none opacity-80 pointer-events-none select-none"
                     style={{ WebkitMaskImage: 'radial-gradient(ellipse 52% 56% at 50% 52%, #000 28%, rgba(0,0,0,.55) 55%, transparent 100%)', maskImage: 'radial-gradient(ellipse 52% 56% at 50% 52%, #000 28%, rgba(0,0,0,.55) 55%, transparent 100%)' }} />
                <div className="relative max-w-3xl mx-auto">
                    {/* Tiêu đề "giải mã" lặp lại (dùng chung với trang đăng nhập) */}
                    <h1 className="font-headline-lg text-[40px] md:text-[64px] leading-[1.08] font-bold tracking-tight pb-2"
                        aria-label={t('hero.title')}>
                        <DecodeLoop lines={heroLines} reduce={reduceMotion} startDelay={0.2} noiseClassName="lv-decode-noise--blue" />
                    </h1>
                    <p className="lv-hero-rv mt-5 text-body-lg text-white/80">{t('hero.text')}</p>
                    <div className="lv-hero-rv mt-8 flex flex-col sm:flex-row gap-3 justify-center items-center">
                        <Magnetic strength={0.18} range={90}>
                            <Link to="/register" className="h-12 px-7 rounded-full bg-white text-brand font-bold flex items-center justify-center gap-2 hover:bg-white/85">
                                <Icon name="arrow_forward" className="text-[18px]" /> {t('hero.cta')}
                            </Link>
                        </Magnetic>
                        <Link to="/demo" className="h-12 px-6 rounded-full border border-amber-400/50 hover:border-amber-400 text-amber-300 hover:bg-amber-400/10 font-bold flex items-center justify-center gap-2 transition-all">
                            <Icon name="rocket_launch" className="text-[18px] text-amber-400" /> {t('hero.demoCta', 'Dùng thử nhanh')}
                        </Link>
                        <a href="#how" className="h-12 px-7 rounded-full border border-white/40 text-white font-bold flex items-center justify-center hover:bg-white/10">
                            {t('hero.how')}
                        </a>
                    </div>
                    <div className="mt-12 flex justify-center">
                        <InteractiveVaultLock variant="hero" to="/register" />
                    </div>
                </div>
                {/* Trust badges */}
                <div className="lv-hero-badges relative mt-20 md:mt-28 flex flex-wrap justify-center gap-3">
                    {BADGES.map((b) => (
                        <span key={b.key} className="flex items-center gap-2 px-4 h-9 rounded-full bg-white/10 border border-white/15 text-sm text-white/85 backdrop-blur">
                            <Icon name={b.icon} className="text-[18px] text-[#b9c9ff]" /> {t(`badges.${b.key}`)}
                        </span>
                    ))}
                </div>
            </section>

            {/* PREVIEW — khung trình duyệt nghiêng, cuộn tới thì dựng thẳng; bên trong là bản demo bấm thử được */}
            <section data-mode="dark" className="relative z-10 px-5 py-24 text-center text-white overflow-hidden">
                <div className="lv-reveal max-w-2xl mx-auto mb-12">
                    <p className="font-label-sm uppercase tracking-wider mb-2 text-[#b9c9ff]">{t('preview.eyebrow')}</p>
                    <h2 className="font-headline-md text-headline-md">{t('preview.title')}</h2>
                    <p className="mt-3 text-white/70">{t('preview.text')}</p>
                </div>
                <div className="lv-tilt max-w-5xl mx-auto rounded-2xl border border-white/15 bg-white/5 p-2 shadow-2xl" style={{ perspective: '1200px' }}>
                    <div className="flex items-center gap-1.5 px-3 py-2">
                        <span className="w-2.5 h-2.5 rounded-full bg-white/25" /><span className="w-2.5 h-2.5 rounded-full bg-white/25" /><span className="w-2.5 h-2.5 rounded-full bg-white/25" />
                    </div>
                    {/* Giao diện Owner thật, bấm thử được (dữ liệu mẫu, không gọi API) */}
                    <div className="relative rounded-xl overflow-hidden">
                        <AppPreview />
                    </div>
                </div>
                <p className="lv-ap-hint"><Icon name="touch_app" className="text-[18px] text-[#b9c9ff]" /> {t('preview.hint')}</p>
            </section>

            {/* 2. PROBLEM — dòng thời gian 3D, dữ liệu "hỏng" dần theo thời gian */}
            <Section mode="dark" eyebrow={t('problem.eyebrow')} title={t('problem.title')}>
                <ProblemTimeline items={PROBLEMS.map((p) => ({
                    key: p.key, icon: p.icon,
                    when: t(`problem.items.${p.key}.when`), text: t(`problem.items.${p.key}.text`),
                }))} />
            </Section>

            {/* 3. HOW IT WORKS — phong cách cyber: bus mạch + gói dữ liệu kích sáng từng bước */}
            <Section id="how" mode="dark" eyebrow={t('how.eyebrow')} title={t('how.title')}>
                <CyberSteps items={STEPS.map((s, i) => ({
                    key: s.key, icon: s.icon, stepLabel: t('how.step', { n: i + 1 }),
                    title: t(`how.items.${s.key}.title`), text: t(`how.items.${s.key}.text`),
                }))} />
            </Section>

            {/* 4. SECURITY — mỗi lớp bảo vệ một màu, rê chuột có tia quét */}
            <Section id="security" mode="dark" eyebrow={t('security.eyebrow')} title={t('security.title')}>
                <div className="lv-stagger grid md:grid-cols-3 gap-5">
                    {SECURITY.map((s) => (
                        <SecurityCard key={s.key} icon={s.icon} accent={ACCENT.security[s.key]}
                                      title={t(`security.items.${s.key}.title`)} text={t(`security.items.${s.key}.text`)} />
                    ))}
                </div>
            </Section>

            {/* 5. WHAT YOU CAN STORE — hộp ký gửi: rê chuột/chạm để mở cửa ô, xem mô tả bên trong */}
            <Section mode="dark" eyebrow={t('assets.eyebrow')} title={t('assets.title')}>
                <div className="lv-stagger grid sm:grid-cols-2 lg:grid-cols-3 gap-5 max-w-5xl mx-auto">
                    {ASSETS.map((a) => (
                        <VaultBox key={a.key} icon={a.icon} accent={ACCENT.assets[a.key]}
                                  label={t(`assets.items.${a.key}`)} desc={t(`assets.desc.${a.key}`)} />
                    ))}
                </div>
                <p className="lv-box-hint">{t('assets.hint')}</p>
            </Section>

            {/* 6. ROLES — thẻ định danh có ánh kim; tên vai trò dùng chung common:roles */}
            <Section id="roles" mode="dark" eyebrow={t('rolesSection.eyebrow')} title={t('rolesSection.title')}>
                <div className="lv-stagger grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
                    {ROLES.map((r) => (
                        <RoleCard key={r.key} icon={r.icon} accent={ACCENT.roles[r.key]}
                                  title={t(`common:roles.${r.key}`)} text={t(`rolesSection.items.${r.key}`)} />
                    ))}
                </div>
            </Section>

            {/* 7. FAQ */}
            <Section id="faq" mode="dark" eyebrow={t('faq.eyebrow')} title={t('faq.title')}>
                <div className="lv-stagger max-w-2xl mx-auto divide-y divide-white/10 border-y border-white/10">
                    {FAQS.map((k, i) => (
                        <div key={k}>
                            <button
                                type="button"
                                onClick={() => setOpenFaq(openFaq === i ? -1 : i)}
                                className="w-full py-5 flex justify-between items-center text-left font-semibold text-white hover:text-[#5ce1ff] transition-colors"
                            >
                                {t(`faq.items.${k}.q`)}
                                <Icon name={openFaq === i ? 'remove' : 'add'} className="text-[#5ce1ff]/80" />
                            </button>
                            <AutoHeight>
                                {openFaq === i && <p className="pb-5 text-white/70">{t(`faq.items.${k}.a`)}</p>}
                            </AutoHeight>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 8. CLOSING CTA */}
            <section data-mode="dark" className="relative z-10 px-5 py-24">
                <div className="lv-reveal max-w-6xl mx-auto rounded-3xl border border-white/15 bg-white/5 backdrop-blur-sm text-white px-8 py-16 text-center">
                    <h2 className="font-headline-md text-headline-md">{t('closing.title')}</h2>
                    <p className="mt-3 opacity-80">{t('closing.text')}</p>
                    <Link to="/register" className="inline-flex mt-8 h-12 px-7 rounded-full bg-white text-brand font-bold items-center gap-2">
                        {t('closing.cta')} <Icon name="arrow_forward" className="text-[18px]" />
                    </Link>
                </div>
            </section>

            {/* FOOTER */}
            <footer data-mode="dark" className="relative z-10 text-white px-5 pt-14 pb-8 overflow-hidden border-t border-white/10">
                <div className="max-w-6xl mx-auto">
                    <div className="flex flex-wrap gap-12 justify-between">
                        <div className="flex-[1_1_280px] max-w-[360px] flex flex-col gap-4">
                            <span className="flex items-center gap-2.5 font-bold tracking-[0.14em] text-lg"><BrandMark className="h-[34px] w-[34px]" />LEGACYVAULT</span>
                            <p className="text-white/70 leading-relaxed">{t('footerNav.tagline')}</p>
                        </div>
                        <div className="flex-[2_1_520px] flex flex-wrap gap-9">
                        <div className="flex flex-col gap-3.5 min-w-[150px]">
                            <div className="text-[13px] font-semibold tracking-[0.08em] text-white/75">{t('footerNav.product')}</div>
                            <a href="#how" className="text-white/70 hover:text-white">{t('footerNav.features')}</a>
                            <a href="#how" className="text-white/70 hover:text-white">{t('footerNav.how')}</a>
                            <a href="#security" className="text-white/70 hover:text-white">{t('footerNav.security')}</a>
                            <Link to="/login" className="text-white/70 hover:text-white">{t('footerNav.signIn')}</Link>
                        </div>
                        <div className="flex flex-col gap-3.5 min-w-[150px]">
                            <div className="text-[13px] font-semibold tracking-[0.08em] text-white/75">{t('footerNav.forWho')}</div>
                            <Link to="/register" className="text-white/70 hover:text-white">{t('footerNav.owner')}</Link>
                            <Link to="/register" className="text-white/70 hover:text-white">{t('footerNav.beneficiary')}</Link>
                            <Link to="/register" className="text-white/70 hover:text-white">{t('footerNav.executor')}</Link>
                            <a href="#roles" className="text-white/70 hover:text-white">{t('footerNav.verifier')}</a>
                        </div>
                        <div className="flex flex-col gap-3.5 min-w-[150px]">
                            <div className="text-[13px] font-semibold tracking-[0.08em] text-white/75">{t('footerNav.account')}</div>
                            <Link to="/login" className="text-white/70 hover:text-white">{t('footerNav.signIn')}</Link>
                            <Link to="/register" className="text-white/70 hover:text-white">{t('footerNav.signUp')}</Link>
                            <Link to="/forgot-password" className="text-white/70 hover:text-white">{t('footerNav.forgot')}</Link>
                        </div>
                        <div className="flex flex-col gap-3.5 min-w-[150px]">
                            <div className="text-[13px] font-semibold tracking-[0.08em] text-white/75">{t('footerNav.project')}</div>
                            <span className="text-white/70">SWP391, FPT University</span>
                            <a href="https://github.com/azerc18/legacy-vault" target="_blank" rel="noreferrer" className="text-white/70 hover:text-white">GitHub</a>
                            <a href="#faq" className="text-white/70 hover:text-white">{t('footerNav.contact')}</a>
                        </div>
                        </div>
                    </div>
                    <div aria-hidden="true" className="lv-wordmark mt-16 font-headline-lg font-bold select-none whitespace-nowrap overflow-hidden text-center"
                         style={{ fontSize: 'clamp(40px,10.4vw,160px)', lineHeight: 1.15, paddingBottom: '0.08em', letterSpacing: '0.02em' }}>
                        LEGACYVAULT
                    </div>
                    <div className="mt-6 pt-5 border-t border-white/10 flex flex-wrap justify-between gap-3 text-sm text-white/70">
                        <span>{t('footerNav.legal1')}</span>
                        <span>{t('footerNav.legal2')}</span>
                    </div>
                </div>
            </footer>
        </div>
    );
}