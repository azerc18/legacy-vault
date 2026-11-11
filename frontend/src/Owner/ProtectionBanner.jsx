// Cảnh báo khi Owner đã có vault nhưng kế hoạch bàn giao chưa đủ (thiếu Executor / chưa bật DMS / vault chưa có người thụ hưởng).
// Hiện ở đầu mọi trang Owner (gắn trong OwnerLayout). Tự tải lại khi ownerApi báo dữ liệu bảo vệ vừa thay đổi.
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router-dom';
import { ownerApi, PROTECTION_CHANGED } from '../api/ownerApi.js';

// Không import Icon từ ownerUi.jsx để tránh vòng import (ownerUi.jsx import file này)
const Icon = ({ name, className = '' }) => <span className={`material-symbols-outlined ${className}`}>{name}</span>;

async function loadState() {
    const [vaults, executors, dms] = await Promise.all([
        ownerApi.listVaults(),
        ownerApi.listExecutors().catch(() => []),
        // 404 = chưa cấu hình DMS (nếu BE đổi sang trả { configured:false } thì cũng xử lý bên dưới)
        ownerApi.getDms().catch((e) => { if (e.response?.status === 404) return null; throw e; }),
    ]);
    return {
        vaults,
        executors: executors ?? [],
        dms: dms && dms.configured !== false ? dms : null,
        // KYC: chỉ có khi DMS chưa cấu hình (BE trả identityVerified cùng configured=false)
        identityVerified: dms && dms.configured === false ? dms.identityVerified !== false : true,
    };
}

// Trả về danh sách bước còn thiếu, theo đúng thứ tự BE bắt buộc: Executor -> DMS. Rỗng = không cần banner.
// Bỏ phần ?query để so với pathname hiện tại
const pathOf = (to) => (to || '').split('?')[0];

export function computeIssues({ vaults, executors, dms, identityVerified = true }, t) {
    if (vaults.length === 0) return [];                  // chưa có gì để bảo vệ
    if (dms?.status === 'TRIGGERED') return [];          // bàn giao đã bắt đầu, banner không còn ý nghĩa

    const issues = [];
    const hasPrimary = executors.some((e) => e.executorType === 'PRIMARY' && e.status !== 'REVOKED');

    if (!hasPrimary) {
        issues.push({ key: 'executor', critical: true, text: t('banner.issues.noPrimary'), cta: t('banner.issues.noPrimaryCta'), to: '/owner/executors' });
    } else if (dms && dms.hasUsableExecutor === false) {
        issues.push({ key: 'executor', critical: true, text: t('banner.issues.unusableExecutor'), cta: t('banner.issues.unusableExecutorCta'), to: '/owner/executors' });
    }
    if (!dms && !identityVerified) {
        // KYC: bật DMS lần đầu cần xác minh danh tính; làm được ngay, không phụ thuộc Executor
        issues.push({ key: 'identity', critical: true, text: t('identity:banner.text'), cta: t('identity:banner.cta'), to: '/owner/identity?returnTo=/owner/dms' });
    }
    if (!dms) {
        issues.push({ key: 'dms', critical: true, text: t('banner.issues.noDms'), cta: t('banner.issues.noDmsCta'), to: '/owner/dms',
            needs: !hasPrimary ? 'executor' : (!identityVerified ? 'identity' : null) });
    } else if (dms.status === 'SUSPENDED') {
        issues.push({ key: 'dms', critical: true, text: t('banner.issues.dmsSuspended'), to: null });
    }
    const noBeneficiary = vaults.filter((v) => !v.beneficiaryEmail);
    if (noBeneficiary.length) {
        issues.push({
            key: 'beneficiary', critical: false,
            text: noBeneficiary.length === 1
                ? t('banner.issues.oneVaultNoBeneficiary', { name: noBeneficiary[0].name })
                : t('banner.issues.manyVaultsNoBeneficiary', { count: noBeneficiary.length }),
            cta: t('banner.issues.chooseBeneficiaryCta'), to: `/owner/vaults/${noBeneficiary[0].id}`,
        });
    }
    return issues;
}

export default function ProtectionBanner() {
    const { t } = useTranslation(['vault', 'identity']);
    const [state, setState] = useState(null);
    const { pathname } = useLocation();

    const refresh = useCallback(() => {
        loadState().then(setState).catch(() => setState(null)); // lỗi tải: im lặng, không chặn trang
    }, []);

    useEffect(() => {
        refresh();
        window.addEventListener(PROTECTION_CHANGED, refresh);
        return () => window.removeEventListener(PROTECTION_CHANGED, refresh);
    }, [refresh]);

    const issues = state ? computeIssues(state, t) : [];
    if (issues.length === 0) return null;

    const critical = issues.some((i) => i.critical);
    // Nút chính = bước đầu tiên làm được ngay (bỏ qua bước còn phụ thuộc bước khác)
    const next = issues.find((i) => i.to && !i.needs);

    return (
        <div role="alert"
             className={`mb-8 rounded-2xl border p-5 backdrop-blur-md shadow-lg ${critical
                 ? 'bg-error-container/65 border-error/40 text-on-error-container'
                 : 'bg-warning-container/70 border-warning/40 text-on-warning-container'}`}>
            <div className="flex flex-wrap items-start gap-4">
                <Icon name={critical ? 'gpp_bad' : 'gpp_maybe'} className="text-[28px] shrink-0" />
                <div className="flex-1 min-w-[220px]">
                    <p className="font-bold">
                        {critical ? t('banner.criticalTitle') : t('banner.warningTitle')}
                    </p>
                    <p className="mt-0.5 text-sm opacity-90">
                        {critical ? t('banner.criticalDesc') : t('banner.warningDesc')}
                    </p>
                    <ol className="mt-3 space-y-1.5 text-sm">
                        {issues.map((i, idx) => (
                            <li key={i.key} className="flex items-start gap-2">
                                <span className="mt-0.5 w-5 h-5 shrink-0 rounded-full bg-surface-container-lowest/80 text-xs font-bold flex items-center justify-center">
                                    {idx + 1}
                                </span>
                                <span className="flex flex-wrap items-baseline gap-x-2">
                                    <span className={i.needs ? 'opacity-60' : ''}>{i.text}{i.needs ? t('banner.afterStep1') : ''}</span>
                                    {i.to && !i.needs && i !== next && pathname !== pathOf(i.to) && (
                                        <Link to={i.to} className="font-semibold underline underline-offset-2 whitespace-nowrap">{i.cta}</Link>
                                    )}
                                </span>
                            </li>
                        ))}
                    </ol>
                </div>
                {next && pathname !== pathOf(next.to) && (
                    <Link to={next.to}
                          className="shrink-0 h-10 px-4 rounded-xl bg-primary text-on-primary text-sm font-bold inline-flex items-center gap-2 hover:opacity-90 lv-pill">
                        {next.cta} <Icon name="arrow_forward" className="text-[18px]" />
                    </Link>
                )}
            </div>
        </div>
    );
}
