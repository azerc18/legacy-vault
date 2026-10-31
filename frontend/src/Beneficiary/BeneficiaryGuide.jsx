import React from 'react';
import { Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { BeneficiaryLayout, Icon } from './beneficiaryUi.jsx';

// X6b: trang tĩnh hướng dẫn cho người ở lại (không gọi API)
const STEPS = [
    { icon: 'mark_email_read', title: 'Check your email and sign in',
        text: 'You receive an email when a vault is handed over to you. Sign in to LegacyVault with the same email address.' },
    { icon: 'verified_user', title: 'Verify your identity',
        text: 'Confirm it is you with a one-time code sent to your email. The code expires after a few minutes.' },
    { icon: 'how_to_reg', title: 'Accept the handover',
        text: 'When a vault has several beneficiaries, every person must accept before the assets can be opened.' },
    { icon: 'download', title: 'View and save the assets',
        text: 'Download the information you need and store it safely. Delete downloaded files once you have saved them.' },
    { icon: 'task_alt', title: 'Close the handover case',
        text: 'Confirm that you have received everything. Closing the case cannot be undone.' },
];

const TIPS = [
    'Act before the claim deadline shown on each vault. After it, the content is archived.',
    'Keep the death certificate and identity documents of the owner ready for legal procedures.',
    'For inheritance procedures, consider contacting a licensed lawyer or notary in your area.',
];

export default function BeneficiaryGuide() {
    const user = getUser();
    if (!user) return <Navigate to="/login" replace />;

    return (
        <BeneficiaryLayout
            back="/beneficiary"
            title="Guide for beneficiaries"
            subtitle="What happens after a vault is handed over to you."
        >
            <ol className="space-y-4">
                {STEPS.map((s, i) => (
                    <li key={s.title} className="flex gap-4 bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30">
                        <Icon name={s.icon} className="text-primary text-[28px]" />
                        <div>
                            <h3 className="font-title-lg text-title-lg">{i + 1}. {s.title}</h3>
                            <p className="text-sm text-on-surface-variant mt-1">{s.text}</p>
                        </div>
                    </li>
                ))}
            </ol>

            <h2 className="font-title-lg text-title-lg mt-8 mb-3">Good to know</h2>
            <ul className="list-disc pl-6 space-y-2 text-sm text-on-surface-variant">
                {TIPS.map((t) => <li key={t}>{t}</li>)}
            </ul>
        </BeneficiaryLayout>
    );
}