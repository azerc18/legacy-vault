import React, { useEffect, useRef, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from './auth/auth.js';
import LanguageSwitch from './Common/LanguageSwitch.jsx';
import ThemeToggle from './Common/ThemeToggle.jsx';
import BrandMark from './Common/BrandMark.jsx';

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
    const [openFaq, setOpenFaq] = useState(0);
    const [mode, setMode] = useState('dark');
    const rootRef = useRef(null);

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

    // Đã đăng nhập -> vào thẳng trang chủ người dùng
    if (getUser()) return <Navigate to="/home" replace />;

    return (
        <div ref={rootRef} className="font-body-md text-on-surface antialiased min-h-screen relative">
            <div aria-hidden="true" className="lv-bg fixed inset-0 z-0" data-mode={mode} />
            {/* NAVBAR */}
            <header className="sticky top-0 z-20 bg-brand/95 backdrop-blur border-b border-white/10 text-white">
                <div className="max-w-6xl mx-auto h-16 px-5 flex items-center justify-between">
                    <Link to="/" className="flex items-center gap-2 font-bold tracking-[0.18em] text-sm">
                        <BrandMark className="h-[30px] w-[30px]" />LEGACYVAULT
                    </Link>
                    <nav className="hidden md:flex gap-8 text-sm font-semibold text-white/75">
                        {NAV.map((n) => (
                            <a key={n.href} href={n.href} className="hover:text-white">{t(`nav.${n.key}`)}</a>
                        ))}
                    </nav>
                    <div className="flex items-center gap-3">
                        <ThemeToggle variant="dark" />
                        <LanguageSwitch variant="dark" />
                        <Link to="/login" className="hidden sm:block text-sm font-semibold text-white/85 hover:text-white">{t('signIn')}</Link>
                        <Link to="/register" className="h-10 px-5 rounded-full bg-white text-brand text-sm font-bold flex items-center hover:bg-white/85">
                            {t('getStarted')}
                        </Link>
                    </div>
                </div>
            </header>

            {/* 1. HERO */}
            <section data-mode="dark" className="relative z-10 overflow-hidden text-white px-5 pt-24 pb-20 text-center"
                     style={{ backgroundImage: 'radial-gradient(900px 420px at 50% 0%, rgba(80,105,230,.35), transparent 70%)' }}>
                <img src="/key-photo.jpg" alt="" aria-hidden="true"
                     className="absolute left-1/2 -translate-x-1/2 bottom-0 h-[52%] min-h-[280px] w-auto max-w-none opacity-80 pointer-events-none select-none"
                     style={{ WebkitMaskImage: 'radial-gradient(ellipse 52% 56% at 50% 52%, #000 28%, rgba(0,0,0,.55) 55%, transparent 100%)', maskImage: 'radial-gradient(ellipse 52% 56% at 50% 52%, #000 28%, rgba(0,0,0,.55) 55%, transparent 100%)' }} />
                <div className="relative max-w-3xl mx-auto">
                    <h1 className="font-headline-lg text-[40px] md:text-[64px] leading-[1.08] font-bold tracking-tight pb-2">
                        {t('hero.title').split(' ').map((w, i) => (
                            <span key={i} className="lv-word lv-shine inline-block pb-[0.14em] mr-[0.25em]" style={{ animationDelay: `${0.12 * i}s, ${1.6 + 0.12 * i}s` }}>{w}</span>
                        ))}
                    </h1>
                    <p className="mt-5 text-body-lg text-white/80">{t('hero.text')}</p>
                    <div className="mt-8 flex flex-col sm:flex-row gap-3 justify-center">
                        <Link to="/register" className="h-12 px-7 rounded-full bg-white text-brand font-bold flex items-center justify-center gap-2 hover:bg-white/85">
                            <Icon name="arrow_forward" className="text-[18px]" /> {t('hero.cta')}
                        </Link>
                        <a href="#how" className="h-12 px-7 rounded-full border border-white/40 text-white font-bold flex items-center justify-center hover:bg-white/10">
                            {t('hero.how')}
                        </a>
                    </div>
                </div>
                {/* Trust badges */}
                <div className="relative mt-48 md:mt-64 flex flex-wrap justify-center gap-3">
                    {BADGES.map((b) => (
                        <span key={b.key} className="flex items-center gap-2 px-4 h-9 rounded-full bg-white/10 border border-white/15 text-sm text-white/85 backdrop-blur">
                            <Icon name={b.icon} className="text-[18px] text-[#b9c9ff]" /> {t(`badges.${b.key}`)}
                        </span>
                    ))}
                </div>
            </section>

            {/* PREVIEW — khung trình duyệt nghiêng, cuộn tới thì dựng thẳng */}
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
                    <div className="relative rounded-xl overflow-hidden">
                        <img src="/dash-preview.jpg" alt="" className="w-full block" />
                        <div aria-hidden="true" className="absolute inset-x-0 bottom-0 h-1/3" style={{ background: 'linear-gradient(180deg,transparent,#0b1020)' }} />
                    </div>
                </div>
            </section>

            {/* 2. PROBLEM */}
            <Section eyebrow={t('problem.eyebrow')} title={t('problem.title')}>
                <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
                    {PROBLEMS.map((p) => (
                        <div key={p.key} className="bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
                            <Icon name={p.icon} className="text-error text-[28px]" />
                            <p className="mt-4 font-label-sm text-error">{t(`problem.items.${p.key}.when`)}</p>
                            <p className="mt-1 text-on-surface-variant">{t(`problem.items.${p.key}.text`)}</p>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 3. HOW IT WORKS */}
            <Section id="how" eyebrow={t('how.eyebrow')} title={t('how.title')}>
                <div className="grid md:grid-cols-4 gap-6">
                    {STEPS.map((s, i) => (
                        <div key={s.key} className="relative">
                            <div className="w-12 h-12 rounded-2xl bg-primary-fixed text-primary flex items-center justify-center">
                                <Icon name={s.icon} />
                            </div>
                            <p className="mt-4 text-sm text-outline font-semibold">{t('how.step', { n: i + 1 })}</p>
                            <h3 className="font-title-lg text-title-lg mt-1">{t(`how.items.${s.key}.title`)}</h3>
                            <p className="mt-2 text-on-surface-variant">{t(`how.items.${s.key}.text`)}</p>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 4. SECURITY */}
            <Section id="security" mode="dark" eyebrow={t('security.eyebrow')} title={t('security.title')}>
                <div className="grid md:grid-cols-3 gap-5">
                    {SECURITY.map((s) => (
                        <div key={s.key} className="rounded-2xl p-7 border border-white/15 bg-white/5 backdrop-blur">
                            <Icon name={s.icon} className="text-[#b9c9ff] text-[32px]" />
                            <h3 className="font-title-lg text-title-lg mt-4 text-white">{t(`security.items.${s.key}.title`)}</h3>
                            <p className="mt-2 text-white/70">{t(`security.items.${s.key}.text`)}</p>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 5. WHAT YOU CAN STORE */}
            <Section eyebrow={t('assets.eyebrow')} title={t('assets.title')}>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4 max-w-3xl mx-auto">
                    {ASSETS.map((a) => (
                        <div key={a.key} className="flex items-center gap-3 p-4 rounded-xl border border-outline-variant/40">
                            <Icon name={a.icon} className="text-primary" />
                            <span className="font-semibold">{t(`assets.items.${a.key}`)}</span>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 6. ROLES — tên vai trò dùng chung common:roles */}
            <Section id="roles" eyebrow={t('rolesSection.eyebrow')} title={t('rolesSection.title')}>
                <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
                    {ROLES.map((r) => (
                        <div key={r.key} className="bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
                            <div className="w-11 h-11 rounded-full bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center">
                                <Icon name={r.icon} />
                            </div>
                            <h3 className="font-title-lg text-title-lg mt-4">{t(`common:roles.${r.key}`)}</h3>
                            <p className="mt-2 text-on-surface-variant">{t(`rolesSection.items.${r.key}`)}</p>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 7. FAQ */}
            <Section id="faq" eyebrow={t('faq.eyebrow')} title={t('faq.title')}>
                <div className="max-w-2xl mx-auto divide-y divide-outline-variant/40 border-y border-outline-variant/40">
                    {FAQS.map((k, i) => (
                        <div key={k}>
                            <button
                                type="button"
                                onClick={() => setOpenFaq(openFaq === i ? -1 : i)}
                                className="w-full py-5 flex justify-between items-center text-left font-semibold"
                            >
                                {t(`faq.items.${k}.q`)}
                                <Icon name={openFaq === i ? 'remove' : 'add'} className="text-outline" />
                            </button>
                            {openFaq === i && <p className="pb-5 text-on-surface-variant">{t(`faq.items.${k}.a`)}</p>}
                        </div>
                    ))}
                </div>
            </Section>

            {/* 8. CLOSING CTA */}
            <section data-mode="dark" className="relative z-10 px-5 py-24">
                <div className="lv-reveal max-w-6xl mx-auto rounded-3xl border border-white/15 text-white px-8 py-16 text-center"
                     style={{ backgroundImage: 'radial-gradient(700px 260px at 50% 0%, rgba(80,105,230,.35), transparent 70%)' }}>
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
                            <div className="text-[13px] font-semibold tracking-[0.08em] text-white/50">{t('footerNav.product')}</div>
                            <a href="#how" className="text-white/70 hover:text-white">{t('footerNav.features')}</a>
                            <a href="#how" className="text-white/70 hover:text-white">{t('footerNav.how')}</a>
                            <a href="#security" className="text-white/70 hover:text-white">{t('footerNav.security')}</a>
                            <Link to="/login" className="text-white/70 hover:text-white">{t('footerNav.signIn')}</Link>
                        </div>
                        <div className="flex flex-col gap-3.5 min-w-[150px]">
                            <div className="text-[13px] font-semibold tracking-[0.08em] text-white/50">{t('footerNav.forWho')}</div>
                            <Link to="/register" className="text-white/70 hover:text-white">{t('footerNav.owner')}</Link>
                            <Link to="/register" className="text-white/70 hover:text-white">{t('footerNav.beneficiary')}</Link>
                            <Link to="/register" className="text-white/70 hover:text-white">{t('footerNav.executor')}</Link>
                            <a href="#roles" className="text-white/70 hover:text-white">{t('footerNav.verifier')}</a>
                        </div>
                        <div className="flex flex-col gap-3.5 min-w-[150px]">
                            <div className="text-[13px] font-semibold tracking-[0.08em] text-white/50">{t('footerNav.account')}</div>
                            <Link to="/login" className="text-white/70 hover:text-white">{t('footerNav.signIn')}</Link>
                            <Link to="/register" className="text-white/70 hover:text-white">{t('footerNav.signUp')}</Link>
                            <Link to="/forgot-password" className="text-white/70 hover:text-white">{t('footerNav.forgot')}</Link>
                        </div>
                        <div className="flex flex-col gap-3.5 min-w-[150px]">
                            <div className="text-[13px] font-semibold tracking-[0.08em] text-white/50">{t('footerNav.project')}</div>
                            <span className="text-white/70">SWP391, FPT University</span>
                            <a href="https://github.com/azerc18/legacy-vault" target="_blank" rel="noreferrer" className="text-white/70 hover:text-white">GitHub</a>
                            <a href="#faq" className="text-white/70 hover:text-white">{t('footerNav.contact')}</a>
                        </div>
                        </div>
                    </div>
                    <div aria-hidden="true" className="mt-16 font-headline-lg font-bold select-none whitespace-nowrap overflow-hidden text-center"
                         style={{ fontSize: 'clamp(40px,10.4vw,160px)', lineHeight: 1.15, paddingBottom: '0.08em', letterSpacing: '0.02em', backgroundImage: 'linear-gradient(180deg,rgba(255,255,255,0.20),rgba(255,255,255,0))', WebkitBackgroundClip: 'text', backgroundClip: 'text', color: 'transparent' }}>
                        LEGACYVAULT
                    </div>
                    <div className="mt-6 pt-5 border-t border-white/10 flex flex-wrap justify-between gap-3 text-sm text-white/50">
                        <span>{t('footerNav.legal1')}</span>
                        <span>{t('footerNav.legal2')}</span>
                    </div>
                </div>
            </footer>
        </div>
    );
}