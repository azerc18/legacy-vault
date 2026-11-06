import React from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from './auth/auth.js';
import AppHeader, { PAGE_WIDTH } from './Common/AppHeader.jsx';
import useNamedBeneficiary from './Common/useNamedBeneficiary.js';

// Mỗi chức năng: role nào thấy, đi tới đâu. to = null => chưa làm, hiện "Coming soon".
// Chữ nằm trong locales/{en,vi}/home.json theo key.
const FEATURES = [
    { key: 'ownerVaults', role: 'OWNER', icon: 'inventory_2', to: '/owner/vaults' },
    { key: 'ownerDms', role: 'OWNER', icon: 'favorite', to: '/owner/dms' },
    { key: 'ownerExecutors', role: 'OWNER', icon: 'badge', to: '/owner/executors' },
    { key: 'ownerActivity', role: 'OWNER', icon: 'history', to: '/owner/activity' },
    { key: 'beneficiaryVaults', role: 'BENEFICIARY', icon: 'redeem', to: '/beneficiary' },
    { key: 'executorHome', role: 'EXECUTOR', icon: 'badge', to: '/executor/home' },
    { key: 'verifierRequests', role: 'LEGAL_VERIFIER', icon: 'gavel', to: '/verifier' },
    { key: 'verifierHistory', role: 'LEGAL_VERIFIER', icon: 'manage_search', to: '/verifier/history' },
    { key: 'adminUsers', role: 'ADMIN', icon: 'group', to: '/admin/users' },
    { key: 'adminVaults', role: 'ADMIN', icon: 'inventory_2', to: '/admin/vaults' },
];

function Icon({ name, className = '' }) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

function FeatureCard({ f }) {
    const { t } = useTranslation('home');
    const body = (
        <>
            <div className="flex items-start justify-between">
                <div className="w-11 h-11 rounded-xl bg-primary-fixed text-primary flex items-center justify-center">
                    <Icon name={f.icon} />
                </div>
                {!f.to && (
                    <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-surface-container text-outline">
                        {t('comingSoon')}
                    </span>
                )}
            </div>
            <h3 className="font-title-lg text-title-lg mt-4">{t(`features.${f.key}.title`)}</h3>
            <p className="mt-1 text-sm text-on-surface-variant">{t(`features.${f.key}.text`)}</p>
        </>
    );

    const base = 'block bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30';
    return f.to ? (
        <Link to={f.to} className={`${base} hover:border-primary hover:shadow-sm transition-all`}>{body}</Link>
    ) : (
        <div className={`${base} opacity-60 cursor-not-allowed`}>{body}</div>
    );
}

export default function UserHome() {
    const { t } = useTranslation('home');
    const user = getUser();

    const allRoles = user?.roles?.length ? user.roles : [user?.role].filter(Boolean);
    const namedBeneficiary = useNamedBeneficiary(allRoles.includes('BENEFICIARY'));
    // Ẩn nhãn + nhóm Beneficiary khi chưa được ai chỉ định (role vẫn còn trong token)
    const roles = allRoles.filter((r) => r !== 'BENEFICIARY' || namedBeneficiary);
    const groups = roles
        .map((role) => ({ role, items: FEATURES.filter((f) => f.role === role) }))
        .filter((g) => g.items.length > 0);

    if (!user) return <Navigate to="/login" replace />;

    const roleLabel = (r) => t(`common:roles.${r}`, { defaultValue: r });

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader />

            <main className={`${PAGE_WIDTH} py-10`}>
                {/* Lời chào */}
                <div className="mb-10 rounded-3xl bg-brand text-white px-8 py-9 lv-rise"
                     style={{ backgroundImage: 'radial-gradient(700px 260px at 85% 0%, rgba(80,105,230,.45), transparent 70%)' }}>
                    <h1 className="font-headline-md text-headline-md">{t('welcome', { name: user.name })}</h1>
                    <div className="mt-3 flex flex-wrap gap-2">
                        {roles.map((r) => (
                            <span key={r} className="text-xs font-semibold px-3 py-1 rounded-full bg-white/15 text-white border border-white/20">
                                {roleLabel(r)}
                            </span>
                        ))}
                    </div>
                </div>

                {/* Chức năng theo từng role */}
                {groups.map((g) => (
                    <section key={g.role} className="mb-10">
                        <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{roleLabel(g.role)}</h2>
                        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
                            {g.items.map((f) => <FeatureCard key={f.key} f={f} />)}
                        </div>
                    </section>
                ))}

                {allRoles.includes('BENEFICIARY') && namedBeneficiary === false && (
                    <p className="mb-10 text-sm text-on-surface-variant flex items-center gap-2">
                        <Icon name="redeem" className="text-[18px] text-outline" />
                        {t('notNamedYet')}
                    </p>
                )}

                {groups.length === 0 && (
                    <p className="text-on-surface-variant">{t('noFeatures')}</p>
                )}
            </main>
        </div>
    );
}