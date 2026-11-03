import React, { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from './auth/auth.js';
import LanguageSwitch from './Common/LanguageSwitch.jsx';

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
function Section({ id, eyebrow, title, subtitle, children, alt }) {
    return (
        <section id={id} className={`py-20 px-5 ${alt ? 'bg-surface-container-low' : ''}`}>
            <div className="max-w-6xl mx-auto">
                <div className="text-center max-w-2xl mx-auto mb-12">
                    {eyebrow && <p className="font-label-sm text-secondary uppercase tracking-wider mb-2">{eyebrow}</p>}
                    <h2 className="font-headline-md text-headline-md text-on-surface">{title}</h2>
                    {subtitle && <p className="mt-3 text-on-surface-variant">{subtitle}</p>}
                </div>
                {children}
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

    // Đã đăng nhập -> vào thẳng trang chủ người dùng
    if (getUser()) return <Navigate to="/home" replace />;

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            {/* NAVBAR */}
            <header className="sticky top-0 z-20 bg-background/90 backdrop-blur border-b border-outline-variant/30">
                <div className="max-w-6xl mx-auto h-16 px-5 flex items-center justify-between">
                    <Link to="/" className="flex items-center gap-2">
                        <span className="w-9 h-9 rounded-xl bg-primary text-on-primary flex items-center justify-center">
                            <Icon name="shield_locked" className="text-[20px]" />
                        </span>
                        <span className="font-headline-sm text-primary font-bold text-xl">LegacyVault</span>
                    </Link>
                    <nav className="hidden md:flex gap-8 text-sm font-semibold text-on-surface-variant">
                        {NAV.map((n) => (
                            <a key={n.href} href={n.href} className="hover:text-primary">{t(`nav.${n.key}`)}</a>
                        ))}
                    </nav>
                    <div className="flex items-center gap-3">
                        <LanguageSwitch />
                        <Link to="/login" className="hidden sm:block text-sm font-semibold text-primary">{t('signIn')}</Link>
                        <Link to="/register" className="h-10 px-4 rounded-xl bg-primary text-on-primary text-sm font-bold flex items-center hover:bg-primary-container">
                            {t('getStarted')}
                        </Link>
                    </div>
                </div>
            </header>

            {/* 1. HERO */}
            <section className="px-5 pt-20 pb-16 text-center">
                <div className="max-w-3xl mx-auto">
                    <h1 className="font-headline-lg text-[40px] md:text-[52px] leading-tight text-primary font-bold">
                        {t('hero.title')}
                    </h1>
                    <p className="mt-5 text-body-lg text-on-surface-variant">{t('hero.text')}</p>
                    <div className="mt-8 flex flex-col sm:flex-row gap-3 justify-center">
                        <Link to="/register" className="h-12 px-6 rounded-xl bg-primary text-on-primary font-bold flex items-center justify-center gap-2 hover:bg-primary-container">
                            <Icon name="arrow_forward" className="text-[18px]" /> {t('hero.cta')}
                        </Link>
                        <a href="#how" className="h-12 px-6 rounded-xl border border-outline-variant text-primary font-bold flex items-center justify-center hover:bg-surface-container-low">
                            {t('hero.how')}
                        </a>
                    </div>
                </div>
                {/* Trust badges */}
                <div className="mt-12 flex flex-wrap justify-center gap-3">
                    {BADGES.map((b) => (
                        <span key={b.key} className="flex items-center gap-2 px-4 h-9 rounded-full bg-surface-container text-sm text-on-surface-variant">
                            <Icon name={b.icon} className="text-[18px] text-secondary" /> {t(`badges.${b.key}`)}
                        </span>
                    ))}
                </div>
            </section>

            {/* 2. PROBLEM */}
            <Section alt eyebrow={t('problem.eyebrow')} title={t('problem.title')}>
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
            <Section id="security" alt eyebrow={t('security.eyebrow')} title={t('security.title')}>
                <div className="grid md:grid-cols-3 gap-5">
                    {SECURITY.map((s) => (
                        <div key={s.key} className="bg-surface-container-lowest rounded-2xl p-7 border border-outline-variant/30">
                            <Icon name={s.icon} className="text-secondary text-[32px]" />
                            <h3 className="font-title-lg text-title-lg mt-4">{t(`security.items.${s.key}.title`)}</h3>
                            <p className="mt-2 text-on-surface-variant">{t(`security.items.${s.key}.text`)}</p>
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
            <Section id="roles" alt eyebrow={t('rolesSection.eyebrow')} title={t('rolesSection.title')}>
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
            <section className="px-5 pb-20">
                <div className="max-w-6xl mx-auto rounded-3xl bg-primary text-on-primary px-8 py-14 text-center">
                    <h2 className="font-headline-md text-headline-md">{t('closing.title')}</h2>
                    <p className="mt-3 opacity-80">{t('closing.text')}</p>
                    <Link to="/register" className="inline-flex mt-8 h-12 px-6 rounded-xl bg-on-primary text-primary font-bold items-center gap-2">
                        {t('closing.cta')} <Icon name="arrow_forward" className="text-[18px]" />
                    </Link>
                </div>
            </section>

            {/* FOOTER */}
            <footer className="border-t border-outline-variant/30 px-5 py-8">
                <div className="max-w-6xl mx-auto flex flex-col md:flex-row gap-4 justify-between items-center text-sm text-outline">
                    <span>{t('footer.rights')}</span>
                    <div className="flex gap-6">
                        <a href="#" className="hover:text-primary">{t('footer.privacy')}</a>
                        <a href="#" className="hover:text-primary">{t('footer.terms')}</a>
                        <a href="#faq" className="hover:text-primary">{t('footer.support')}</a>
                    </div>
                </div>
            </footer>
        </div>
    );
}