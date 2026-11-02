import React from 'react';
import {Link, Navigate} from 'react-router-dom';
import {getUser} from './auth/auth.js';
import AppHeader, {PAGE_WIDTH} from './Common/AppHeader.jsx';
import useNamedBeneficiary from './Common/useNamedBeneficiary.js';

// Mỗi chức năng: role nào thấy, đi tới đâu. to = null => chưa làm, hiện "Coming soon".
// Làm xong trang nào thì chỉ cần điền đường dẫn vào "to".
const FEATURES = [
    // Vault Owner
    {
        role: 'OWNER',
        icon: 'inventory_2',
        title: 'My vaults',
        text: 'Create vaults, add documents, choose beneficiaries.',
        to: '/owner/vaults'
    },
    {
        role: 'OWNER',
        icon: 'favorite',
        title: "Dead Man's Switch",
        text: 'Set your check-in schedule and check in.',
        to: '/owner/dms'
    },
    {
        role: 'OWNER',
        icon: 'badge',
        title: 'Executors',
        text: 'Assign your primary and backup executor.',
        to: '/owner/executors'
    },
    {
        role: 'OWNER',
        icon: 'history',
        title: 'Activity log',
        text: "Dead Man's Switch and executor events.",
        to: '/owner/activity'
    },
    // Beneficiary
    {
        role: 'BENEFICIARY',
        icon: 'redeem',
        title: 'Inherited vaults',
        text: 'Verify your identity and receive assets.',
        to: '/beneficiary'
    },
    // Executor
    {
        role: 'EXECUTOR',
        icon: 'badge',
        title: 'Executor duties',
        text: 'See vaults where you are the executor.',
        to: '/executor/home'
    },        // Legal Verifier
    {
        role: 'LEGAL_VERIFIER',
        icon: 'gavel',
        title: 'Verification requests',
        text: 'Review and sign pending requests.',
        to: '/verifier'
    },
    {
        role: 'LEGAL_VERIFIER',
        icon: 'manage_search',
        title: 'Verification history',
        text: 'Requests you have already handled.',
        to: '/verifier/history'
    },
    // System Admin
    {
        role: 'ADMIN',
        icon: 'group',
        title: 'Users',
        text: 'Lock accounts, grant Legal Verifier access, hold DMS.',
        to: '/admin/users'
    },
    {
        role: 'ADMIN',
        icon: 'inventory_2',
        title: 'All vaults',
        text: 'Browse every vault and its status.',
        to: '/admin/vaults'
    },
];

const ROLE_LABEL = {
    OWNER: 'Vault Owner',
    BENEFICIARY: 'Beneficiary',
    EXECUTOR: 'Executor',
    LEGAL_VERIFIER: 'Legal Verifier',
    ADMIN: 'Administrator',
};

function Icon({name, className = ''}) {
    return <span className={`material-symbols-outlined ${className}`}>{name}</span>;
}

function FeatureCard({f}) {
    const body = (
        <>
            <div className="flex items-start justify-between">
                <div className="w-11 h-11 rounded-xl bg-primary-fixed text-primary flex items-center justify-center">
                    <Icon name={f.icon}/>
                </div>
                {!f.to && (
                    <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-surface-container text-outline">
                        Coming soon
                    </span>
                )}
            </div>
            <h3 className="font-title-lg text-title-lg mt-4">{f.title}</h3>
            <p className="mt-1 text-sm text-on-surface-variant">{f.text}</p>
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
    const user = getUser();

    const allRoles = user?.roles?.length ? user.roles : [user?.role].filter(Boolean);
    const namedBeneficiary = useNamedBeneficiary(allRoles.includes('BENEFICIARY'));
    // Ẩn nhãn + nhóm Beneficiary khi chưa được ai chỉ định (role vẫn còn trong token)
    const roles = allRoles.filter((r) => r !== 'BENEFICIARY' || namedBeneficiary);
    const groups = roles
        .map((role) => ({role, items: FEATURES.filter((f) => f.role === role)}))
        .filter((g) => g.items.length > 0);

    if (!user) return <Navigate to="/login" replace/>;

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            {/* Top bar */}
            <AppHeader/>

            <main className={`${PAGE_WIDTH} py-10`}>
                {/* Lời chào */}
                <div className="mb-10">
                    <h1 className="font-headline-md text-headline-md">Welcome back, {user.name}</h1>
                    <div className="mt-3 flex flex-wrap gap-2">
                        {roles.map((r) => (
                            <span key={r}
                                  className="text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                                {ROLE_LABEL[r] || r}
                            </span>
                        ))}
                    </div>
                </div>

                {/* Chức năng theo từng role */}
                {groups.map((g) => (
                    <section key={g.role} className="mb-10">
                        <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{ROLE_LABEL[g.role] || g.role}</h2>
                        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
                            {g.items.map((f) => <FeatureCard key={f.title} f={f}/>)}
                        </div>
                    </section>
                ))}

                {allRoles.includes('BENEFICIARY') && namedBeneficiary === false && (
                    <p className="mb-10 text-sm text-on-surface-variant flex items-center gap-2">
                        <Icon name="redeem" className="text-[18px] text-outline"/>
                        You'll see inherited vaults here if someone names you as a beneficiary.
                    </p>
                )}

                {groups.length === 0 && (
                    <p className="text-on-surface-variant">No features are available for your account yet.</p>
                )}
            </main>
        </div>
    );
}
