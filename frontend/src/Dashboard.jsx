import React, { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from './auth/auth.js';

// ---------- Nội dung (sửa text ở đây, không cần đụng JSX) ----------
const NAV = [
    { label: 'How it works', href: '#how' },
    { label: 'Security', href: '#security' },
    { label: 'Roles', href: '#roles' },
    { label: 'FAQ', href: '#faq' },
];

const BADGES = [
    { icon: 'lock', text: 'AES-256 encryption' },
    { icon: 'key', text: 'JWT authentication' },
    { icon: 'gavel', text: 'Legal verification' },
    { icon: 'favorite', text: "Dead Man's Switch" },
];

const PROBLEMS = [
    { when: 'Day 1', icon: 'help', text: 'Family cannot find your accounts or passwords.' },
    { when: 'Month 1', icon: 'block', text: 'Banks and platforms lock access without proof.' },
    { when: 'Month 6', icon: 'hourglass_bottom', text: 'Subscriptions keep charging, data starts expiring.' },
    { when: 'Forever', icon: 'cloud_off', text: 'Photos, documents and memories are lost.' },
];

const STEPS = [
    { icon: 'inventory_2', title: 'Create your vault', text: 'Store assets and legal documents, encrypted before they are saved.' },
    { icon: 'group_add', title: 'Name your people', text: 'Choose an Executor and the Beneficiaries who will receive each asset.' },
    { icon: 'favorite', title: 'Check in regularly', text: "The Dead Man's Switch only starts counting when you stop checking in." },
    { icon: 'verified', title: 'Verified hand-over', text: 'A Legal Verifier reviews the case before the vault is ever unlocked.' },
];

const SECURITY = [
    { icon: 'enhanced_encryption', title: 'Encrypted at rest', text: 'Every asset and document is encrypted with a master key the database never sees in plain text.' },
    { icon: 'notifications_active', title: 'No false alarms', text: 'Reminders are sent before the switch triggers, so a missed check-in never unlocks anything.' },
    { icon: 'policy', title: 'Human verification', text: 'Unlocking requires a Legal Verifier to approve the request and sign with a one-time code.' },
];

const ASSETS = [
    { icon: 'password', label: 'Passwords' },
    { icon: 'account_balance', label: 'Bank accounts' },
    { icon: 'currency_bitcoin', label: 'Crypto wallets' },
    { icon: 'description', label: 'Legal documents' },
    { icon: 'sticky_note_2', label: 'Private notes' },
    { icon: 'photo_library', label: 'Files & photos' },
];

const ROLES = [
    { icon: 'person', title: 'Vault Owner', text: 'Builds the vault, sets the switch and decides who receives what.' },
    { icon: 'badge', title: 'Executor', text: 'Is notified when the switch triggers and starts the hand-over.' },
    { icon: 'family_restroom', title: 'Beneficiary', text: 'Verifies identity and receives only the assets assigned to them.' },
    { icon: 'gavel', title: 'Legal Verifier', text: 'Reviews documents and signs off before anything is released.' },
];

const FAQS = [
    { q: 'Can LegacyVault staff read my assets?', a: 'No. Assets are encrypted before storage and are only decrypted for the assigned beneficiary after verification.' },
    { q: 'What if I forget to check in?', a: 'You receive reminders before the deadline. The switch only triggers after the full grace period has passed.' },
    { q: 'Who decides when the vault is unlocked?', a: 'A Legal Verifier must approve the request and sign it. The switch alone cannot release your assets.' },
    { q: 'Can I change my beneficiaries later?', a: 'Yes. As long as the vault is active, you can update people and assets at any time.' },
];

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
                            <a key={n.href} href={n.href} className="hover:text-primary">{n.label}</a>
                        ))}
                    </nav>
                    <div className="flex items-center gap-3">
                        <Link to="/login" className="hidden sm:block text-sm font-semibold text-primary">Sign in</Link>
                        <Link to="/register" className="h-10 px-4 rounded-xl bg-primary text-on-primary text-sm font-bold flex items-center hover:bg-primary-container">
                            Get started
                        </Link>
                    </div>
                </div>
            </header>

            {/* 1. HERO */}
            <section className="px-5 pt-20 pb-16 text-center">
                <div className="max-w-3xl mx-auto">
                    <h1 className="font-headline-lg text-[40px] md:text-[52px] leading-tight text-primary font-bold">
                        Your digital life deserves a plan, too.
                    </h1>
                    <p className="mt-5 text-body-lg text-on-surface-variant">
                        Keep passwords, accounts and documents in one encrypted vault, and make sure they reach
                        the right people when it matters.
                    </p>
                    <div className="mt-8 flex flex-col sm:flex-row gap-3 justify-center">
                        <Link to="/register" className="h-12 px-6 rounded-xl bg-primary text-on-primary font-bold flex items-center justify-center gap-2 hover:bg-primary-container">
                            <Icon name="arrow_forward" className="text-[18px]" /> Create your vault
                        </Link>
                        <a href="#how" className="h-12 px-6 rounded-xl border border-outline-variant text-primary font-bold flex items-center justify-center hover:bg-surface-container-low">
                            How it works
                        </a>
                    </div>
                </div>
                {/* Trust badges */}
                <div className="mt-12 flex flex-wrap justify-center gap-3">
                    {BADGES.map((b) => (
                        <span key={b.text} className="flex items-center gap-2 px-4 h-9 rounded-full bg-surface-container text-sm text-on-surface-variant">
                            <Icon name={b.icon} className="text-[18px] text-secondary" /> {b.text}
                        </span>
                    ))}
                </div>
            </section>

            {/* 2. PROBLEM */}
            <Section alt eyebrow="The problem" title="What happens to your digital assets when you are gone?">
                <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
                    {PROBLEMS.map((p) => (
                        <div key={p.when} className="bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
                            <Icon name={p.icon} className="text-error text-[28px]" />
                            <p className="mt-4 font-label-sm text-error">{p.when}</p>
                            <p className="mt-1 text-on-surface-variant">{p.text}</p>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 3. HOW IT WORKS */}
            <Section id="how" eyebrow="How it works" title="Four steps. Then it runs quietly in the background.">
                <div className="grid md:grid-cols-4 gap-6">
                    {STEPS.map((s, i) => (
                        <div key={s.title} className="relative">
                            <div className="w-12 h-12 rounded-2xl bg-primary-fixed text-primary flex items-center justify-center">
                                <Icon name={s.icon} />
                            </div>
                            <p className="mt-4 text-sm text-outline font-semibold">Step {i + 1}</p>
                            <h3 className="font-title-lg text-title-lg mt-1">{s.title}</h3>
                            <p className="mt-2 text-on-surface-variant">{s.text}</p>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 4. SECURITY */}
            <Section id="security" alt eyebrow="Security" title="Built so that nobody can open it early.">
                <div className="grid md:grid-cols-3 gap-5">
                    {SECURITY.map((s) => (
                        <div key={s.title} className="bg-surface-container-lowest rounded-2xl p-7 border border-outline-variant/30">
                            <Icon name={s.icon} className="text-secondary text-[32px]" />
                            <h3 className="font-title-lg text-title-lg mt-4">{s.title}</h3>
                            <p className="mt-2 text-on-surface-variant">{s.text}</p>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 5. WHAT YOU CAN STORE */}
            <Section eyebrow="Your vault" title="What you can keep inside">
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4 max-w-3xl mx-auto">
                    {ASSETS.map((a) => (
                        <div key={a.label} className="flex items-center gap-3 p-4 rounded-xl border border-outline-variant/40">
                            <Icon name={a.icon} className="text-primary" />
                            <span className="font-semibold">{a.label}</span>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 6. ROLES */}
            <Section id="roles" alt eyebrow="Who is involved" title="Everyone has one clear job">
                <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
                    {ROLES.map((r) => (
                        <div key={r.title} className="bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
                            <div className="w-11 h-11 rounded-full bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center">
                                <Icon name={r.icon} />
                            </div>
                            <h3 className="font-title-lg text-title-lg mt-4">{r.title}</h3>
                            <p className="mt-2 text-on-surface-variant">{r.text}</p>
                        </div>
                    ))}
                </div>
            </Section>

            {/* 7. FAQ */}
            <Section id="faq" eyebrow="FAQ" title="Common questions">
                <div className="max-w-2xl mx-auto divide-y divide-outline-variant/40 border-y border-outline-variant/40">
                    {FAQS.map((f, i) => (
                        <div key={f.q}>
                            <button
                                onClick={() => setOpenFaq(openFaq === i ? -1 : i)}
                                className="w-full py-5 flex justify-between items-center text-left font-semibold"
                            >
                                {f.q}
                                <Icon name={openFaq === i ? 'remove' : 'add'} className="text-outline" />
                            </button>
                            {openFaq === i && <p className="pb-5 text-on-surface-variant">{f.a}</p>}
                        </div>
                    ))}
                </div>
            </Section>

            {/* 8. CLOSING CTA */}
            <section className="px-5 pb-20">
                <div className="max-w-6xl mx-auto rounded-3xl bg-primary text-on-primary px-8 py-14 text-center">
                    <h2 className="font-headline-md text-headline-md">Start your plan today. It takes ten minutes.</h2>
                    <p className="mt-3 opacity-80">Peace of mind for you, a clear path for the people you love.</p>
                    <Link to="/register" className="inline-flex mt-8 h-12 px-6 rounded-xl bg-on-primary text-primary font-bold items-center gap-2">
                        Create your vault <Icon name="arrow_forward" className="text-[18px]" />
                    </Link>
                </div>
            </section>

            {/* FOOTER */}
            <footer className="border-t border-outline-variant/30 px-5 py-8">
                <div className="max-w-6xl mx-auto flex flex-col md:flex-row gap-4 justify-between items-center text-sm text-outline">
                    <span>© 2026 LegacyVault. All rights reserved.</span>
                    <div className="flex gap-6">
                        <a href="#" className="hover:text-primary">Privacy Policy</a>
                        <a href="#" className="hover:text-primary">Terms of Service</a>
                        <a href="#faq" className="hover:text-primary">Support</a>
                    </div>
                </div>
            </footer>
        </div>
    );
}
