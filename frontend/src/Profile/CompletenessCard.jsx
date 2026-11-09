// Mức độ hoàn thiện tài khoản (ý từ "Plan Score" của EstateOS và "Security checkup").
// Tính hoàn toàn ở FE từ hồ sơ đã tải; Owner thấy thêm điểm sẵn sàng thừa kế (BE /owner/readiness).
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CountingNumber } from '@animate-ui/react/primitives/texts/counting-number';
import { useMotionTokens } from '@animate-ui/react/utils/motion';
import { ownerApi } from '../api/ownerApi.js';
import { Card, Icon } from '../Owner/ownerUi.jsx';

// Mỗi mục: đã xong chưa + chỗ để sửa (id của thẻ trên trang)
const accountItems = (profile) => [
    { key: 'verified', done: profile.status === 'ACTIVE', target: null },
    { key: 'avatar', done: !!profile.hasAvatar, target: 'profile-summary' },
    { key: 'phone', done: !!profile.phoneNumber, target: 'profile-info' },
    { key: 'mfa', done: !!profile.mfaEnabled, target: 'profile-2fa' },
];

function Ring({ value }) {
    const { spring } = useMotionTokens();
    const r = 30;
    const c = 2 * Math.PI * r;
    const tone = value >= 80 ? 'text-success' : value >= 50 ? 'text-secondary' : 'text-error';
    return (
        <div className="relative w-20 h-20 shrink-0">
            <svg className="w-20 h-20 -rotate-90" viewBox="0 0 72 72" aria-hidden="true">
                <circle cx="36" cy="36" r={r} fill="none" stroke="currentColor" strokeWidth="7" className="text-outline-variant/40" />
                <circle cx="36" cy="36" r={r} fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round"
                        strokeDasharray={c} strokeDashoffset={c - (value / 100) * c}
                        className={`${tone} transition-all duration-500`} />
            </svg>
            <span className="absolute inset-0 flex items-center justify-center font-title-lg text-title-lg tabular-nums">
                <CountingNumber number={value} transition={spring.number} />%
            </span>
        </div>
    );
}

// Cuộn tới thẻ, nháy viền, rồi làm luôn việc đó (mở chọn ảnh / focus ô số điện thoại / bắt đầu bật 2FA)
// để bấm "Do it" luôn thấy phản ứng, kể cả khi thẻ đã nằm sẵn trong màn hình.
const ACTIONS = {
    'profile-summary': () => document.querySelector('#profile-summary input[type="file"]')?.click(),
    'profile-info': () => document.getElementById('profile-phone')?.focus({ preventScroll: true }),
    'profile-2fa': () => document.getElementById('profile-2fa-start')?.click(),
};
// Lưu ý: với mục 2FA, "Do it" bấm luôn nút bật tức là gọi API tạo secret ngay (chấp nhận được vì người dùng đã chủ động bấm).
const goTo = (id) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('ring-2', 'ring-secondary', 'rounded-2xl');
    setTimeout(() => el.classList.remove('ring-2', 'ring-secondary', 'rounded-2xl'), 1600);
    ACTIONS[id]?.();
};

export default function CompletenessCard({ profile }) {
    const { t } = useTranslation('profile');
    const isOwner = profile.roles?.includes('OWNER');
    const [readiness, setReadiness] = useState(null);

    useEffect(() => {
        if (!isOwner) return;
        ownerApi.getReadiness().then(setReadiness).catch(() => {});
    }, [isOwner]);

    const items = accountItems(profile);
    const doneCount = items.filter((i) => i.done).length;
    const percent = Math.round((doneCount / items.length) * 100);

    return (
        <Card title={t('completeness.title')} icon="verified_user">
            <div className="flex items-center gap-4">
                <Ring value={percent} />
                <p className="text-sm text-on-surface-variant">
                    {percent === 100 ? t('completeness.allDone') : t('completeness.hint', { done: doneCount, total: items.length })}
                </p>
            </div>

            <ul className="mt-5 space-y-2">
                {items.map((i) => (
                    <li key={i.key} className="flex items-center gap-2.5 text-sm">
                        <Icon name={i.done ? 'check_circle' : 'radio_button_unchecked'}
                              className={`text-[18px] ${i.done ? 'text-success' : 'text-on-surface-variant'}`} />
                        <span className={`flex-1 ${i.done ? 'text-on-surface-variant' : 'font-semibold'}`}>
                            {t(`completeness.items.${i.key}`)}
                        </span>
                        {!i.done && i.target && (
                            <button type="button" onClick={() => goTo(i.target)}
                                    className="text-xs font-semibold text-secondary hover:underline">
                                {t('completeness.fix')}
                            </button>
                        )}
                    </li>
                ))}
            </ul>

            {isOwner && readiness && (
                <Link to="/owner/vaults"
                      className="mt-5 pt-4 border-t border-outline-variant/30 flex items-center gap-3 group">
                    <span className="w-10 h-10 rounded-xl bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center">
                        <Icon name="shield_with_heart" className="text-[20px]" />
                    </span>
                    <span className="flex-1 min-w-0">
                        <span className="block text-xs text-on-surface-variant">{t('completeness.estateScore')}</span>
                        <span className="block font-semibold">
                            {readiness.score ?? 0}/{readiness.maxScore || 100}
                            <span className="ml-2 text-xs font-normal text-on-surface-variant">
                                {t(`readiness:grade.${readiness.grade}`, { defaultValue: '' })}
                            </span>
                        </span>
                    </span>
                    <Icon name="arrow_forward" className="text-[18px] text-on-surface-variant group-hover:text-on-surface" />
                </Link>
            )}
        </Card>
    );
}
