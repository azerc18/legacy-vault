import React from 'react';
import { Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';
import { BeneficiaryLayout, Icon } from './beneficiaryUi.jsx';

// X6b: trang tĩnh hướng dẫn cho người ở lại (không gọi API)
// Icon theo thứ tự bước; tiêu đề và nội dung nằm trong beneficiary.json (guide.steps / guide.tips)
const STEP_ICONS = ['mark_email_read', 'verified_user', 'how_to_reg', 'download', 'task_alt'];

export default function BeneficiaryGuide() {
    const { t } = useTranslation('beneficiary');
    const user = getUser();
    if (!user) return <Navigate to="/login" replace />;

    const steps = t('guide.steps', { returnObjects: true });
    const tips = t('guide.tips', { returnObjects: true });

    return (
        <BeneficiaryLayout
            back="/beneficiary"
            title={t('guide.title')}
            subtitle={t('guide.subtitle')}
        >
            <ol className="space-y-4">
                {(Array.isArray(steps) ? steps : []).map((s, i) => (
                    <li key={i} className="flex gap-4 bg-surface-container-lowest rounded-2xl p-5 border border-outline-variant/30">
                        <Icon name={STEP_ICONS[i] || 'info'} className="text-primary text-[28px]" />
                        <div>
                            <h3 className="font-title-lg text-title-lg">{i + 1}. {s.title}</h3>
                            <p className="text-sm text-on-surface-variant mt-1">{s.text}</p>
                        </div>
                    </li>
                ))}
            </ol>

            <h2 className="font-title-lg text-title-lg mt-8 mb-3">{t('guide.goodToKnow')}</h2>
            <ul className="list-disc pl-6 space-y-2 text-sm text-on-surface-variant">
                {(Array.isArray(tips) ? tips : []).map((tip, i) => <li key={i}>{tip}</li>)}
            </ul>
        </BeneficiaryLayout>
    );
}