import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import InfoTip from '../Common/InfoTip.jsx';
import UpgradeDialog from '../Common/UpgradeDialog.jsx';
import { VipChip, VipLocked } from '../Common/VipLock.jsx';
import { useVipStatus } from '../Common/useVipStatus.js';
import { Icon, inputClass } from './ownerUi.jsx';

// N2-FE: phân bổ tài sản cho nhiều người thụ hưởng (khớp DigitalAssetRequest.AllocationRequest)
// K-of-N không còn là một kiểu phân chia: là ĐIỀU KIỆN tuỳ chọn của "Chia theo tỷ lệ" (consensus + threshold)
export const MODES = ['ALL', 'EXCLUSIVE', 'PROPORTIONAL'];
// Chế độ thuộc gói VIP (PlanFeature.ADVANCED_ALLOCATION ở BE), cùng với người dự phòng của EXCLUSIVE
const VIP_MODES = ['PROPORTIONAL'];


const textareaCls = 'w-full px-4 py-2 rounded-xl border border-outline-variant bg-background focus:outline-none focus:border-primary';
const SHARE_RE = /^\d{1,3}(\.\d{1,2})?$/;                    // tối đa 2 chữ số thập phân (cột decimal(5,2))
const cents = (s) => Math.round(parseFloat(s) * 100);         // so sánh bằng số nguyên để khỏi sai số số thực

export const emptyAllocation = () => ({ mode: 'ALL', selected: [], shares: {}, contingentId: '', consensus: false, threshold: '', note: '' });

// Dựng form từ tài sản trong danh sách (DigitalAssetResponse.allocations; rỗng = mặc định thuộc tất cả)
export function allocationFromAsset(asset) {
    const rows = asset?.allocations || [];
    if (rows.length === 0) return emptyAllocation();
    const first = rows[0];
    return {
        // Dữ liệu cũ JOINT_CONSENSUS đã được migration chuyển sang PROPORTIONAL; phòng khi còn sót thì vẫn hiểu là chia tỷ lệ
        mode: first.mode === 'JOINT_CONSENSUS' ? 'PROPORTIONAL' : first.mode,
        selected: rows.map((r) => r.beneficiaryId),
        shares: Object.fromEntries(rows.map((r) => [r.beneficiaryId, r.sharePercent != null ? String(r.sharePercent) : ''])),
        contingentId: first.contingentBeneficiaryId || '',
        consensus: first.consensusThreshold != null,
        threshold: first.consensusThreshold != null ? String(first.consensusThreshold) : '',
        note: first.note || '',
    };
}

const hasConsensus = (a) => a.mode === 'PROPORTIONAL' && !!a.consensus;

// Chỉ giữ phần có nghĩa với mode đang chọn, để so sánh "có đổi không"
const norm = (a) => {
    const ids = [...a.selected].sort();
    return {
        mode: a.mode,
        selected: ids,
        shares: a.mode === 'PROPORTIONAL' ? Object.fromEntries(ids.map((id) => [id, Number(a.shares[id])])) : null,
        contingentId: a.mode === 'EXCLUSIVE' ? a.contingentId : '',
        threshold: hasConsensus(a) ? String(a.threshold) : '',
        note: (a.note || '').trim(),
    };
};

// Trả về thông báo lỗi đầu tiên hoặc ''
export function validateAllocation(a, allowProportional, t) {
    const err = (key) => t(`detail.assets.allocation.errors.${key}`);
    const n = a.selected.length;
    switch (a.mode) {
        case 'EXCLUSIVE':
            if (n !== 1) return err('pickOne');
            if (a.contingentId && a.contingentId === a.selected[0]) return err('backupSame');
            return '';
        case 'PROPORTIONAL': {
            // Tài sản không pháp lý: chỉ chia tỷ lệ khi có điều kiện K-of-N
            if (!allowProportional && !a.consensus) return err('proportionalNotAllowed');
            if (n < 2) return err('pickTwo');
            let total = 0;
            for (const id of a.selected) {
                const s = (a.shares[id] || '').trim();
                if (!SHARE_RE.test(s) || cents(s) <= 0 || cents(s) > 10000) return err('shareInvalid');
                total += cents(s);
            }
            if (total !== 10000) return err('shareTotal');
            if (hasConsensus(a)) {
                const th = Number(a.threshold);
                if (!(Number.isInteger(th) && th >= 2 && th <= n)) return err('thresholdInvalid');
            }
            return '';
        }
        default:
            return '';
    }
}

// Body gửi BE; undefined = không gửi (tạo mới mặc định "tất cả", hoặc sửa mà không đổi phân bổ -> BE giữ nguyên)
export function allocationBody(a, initial, isEdit) {
    if (!isEdit && a.mode === 'ALL') return undefined;
    if (isEdit && JSON.stringify(norm(a)) === JSON.stringify(norm(initial))) return undefined;
    if (a.mode === 'ALL') return { recipients: [] };           // recipients rỗng = xoá phân bổ riêng
    return {
        mode: a.mode,
        recipients: a.selected.map((id) => ({
            beneficiaryId: id,
            ...(a.mode === 'PROPORTIONAL' ? { sharePercent: cents(a.shares[id]) / 100 } : {}),
            ...(a.mode === 'EXCLUSIVE' && a.contingentId ? { contingentBeneficiaryId: a.contingentId } : {}),
        })),
        note: a.note.trim() || null,
        ...(hasConsensus(a) ? { threshold: Number(a.threshold) } : {}),
    };
}

// Một dòng mô tả phân bổ trong danh sách tài sản
export function allocationSummary(asset, beneficiaries, t) {
    const s = (key, opts) => t(`detail.assets.allocation.summary.${key}`, opts);
    const rows = asset?.allocations || [];
    if (rows.length === 0) return s('ALL');
    const nameOf = (id) => {
        const b = beneficiaries.find((x) => x.id === id);
        return b ? (b.fullName || b.email) : '?';
    };
    const first = rows[0];
    switch (first.mode) {
        case 'EXCLUSIVE':
            return first.contingentBeneficiaryId
                ? s('EXCLUSIVE_BACKUP', { name: nameOf(first.beneficiaryId), backup: nameOf(first.contingentBeneficiaryId) })
                : s('EXCLUSIVE', { name: nameOf(first.beneficiaryId) });
        case 'PROPORTIONAL': {
            const list = rows.map((r) => `${nameOf(r.beneficiaryId)} ${r.sharePercent}%`).join(', ');
            return first.consensusThreshold != null
                ? s('PROPORTIONAL_CONSENSUS', { list, k: first.consensusThreshold, n: rows.length })
                : s('PROPORTIONAL', { list });
        }
        default:
            return '';
    }
}

export function AllocationFields({ beneficiaries, value, onChange, allowProportional }) {
    const { t } = useTranslation(['vault']);
    const tt = (key, opts) => t(`detail.assets.allocation.${key}`, opts);
    const vip = useVipStatus();
    const freePlan = vip === false;          // chỉ khoá khi chắc chắn là FREE; chưa biết gói thì để mở
    const [upgradeOpen, setUpgradeOpen] = useState(false);
    const set = (patch) => onChange({ ...value, ...patch });
    const nameOf = (b) => b.fullName || b.email;
    const n = value.selected.length;

    const setMode = (mode) => set({
        mode,
        selected: mode === 'ALL' ? [] : mode === 'EXCLUSIVE' ? value.selected.slice(0, 1) : value.selected,
        contingentId: '',
        consensus: mode === 'PROPORTIONAL' && !allowProportional,
        threshold: '',
    });
    const toggle = (id) => {
        const selected = value.selected.includes(id) ? value.selected.filter((x) => x !== id) : [...value.selected, id];
        // threshold không được vượt số người nhận
        set({ selected, threshold: Number(value.threshold) > selected.length ? '' : value.threshold });
    };
    const total = value.selected.reduce((sum, id) => {
        const s = (value.shares[id] || '').trim();
        return sum + (SHARE_RE.test(s) ? cents(s) : 0);
    }, 0);
    // Ô tỷ lệ đỏ khi chính nó sai, hoặc khi tổng đã vượt 100% (để thấy cần giảm ô nào)
    const shareBad = (id) => {
        const sv = (value.shares[id] || '').trim();
        return (sv !== '' && (!SHARE_RE.test(sv) || cents(sv) <= 0 || cents(sv) > 10000)) || total > 10000;
    };
    return (
        <div role="group" aria-label={tt('title')} className="rounded-xl border border-outline-variant/40 bg-surface-container-low p-4 space-y-3">
            <div className="flex items-start gap-3">
                <span className="w-9 h-9 shrink-0 rounded-full border-2 border-primary text-primary flex items-center justify-center">
                    <Icon name="call_split" className="text-[18px]" />
                </span>
                <div>
                    <p className="font-semibold text-on-surface">{tt('title')}</p>
                    <p className="text-xs text-on-surface-variant">{tt('subtitle')}</p>
                </div>
            </div>
            <div role="radiogroup" aria-label={tt('title')} className="grid sm:grid-cols-2 gap-2">
                {MODES.map((m, i, modes) => {
                    const locked = freePlan && VIP_MODES.includes(m);
                    const active = value.mode === m;
                    const wide = modes.length % 2 === 1 && i === modes.length - 1;   // số ô lẻ: ô cuối chiếm cả hàng, không lệch, không xuống dòng
                    return (
                        <button key={m} type="button" role="radio" aria-checked={active}
                                onClick={() => (locked ? setUpgradeOpen(true) : setMode(m))}
                                className={`flex items-center gap-2 rounded-xl border px-3 py-2 text-left text-sm font-semibold transition-colors ${wide ? 'sm:col-span-2 ' : ''}${
                                    active ? 'border-primary bg-primary/10 text-on-surface' : 'border-outline-variant/50 text-on-surface hover:bg-surface-container'
                                } ${locked ? 'opacity-55' : ''}`}>
                            <Icon name={locked ? 'lock' : active ? 'radio_button_checked' : 'radio_button_unchecked'} className="text-[18px] shrink-0" />
                            <span className="flex-1">{tt(`mode.${m}`)}</span>
                            {VIP_MODES.includes(m) && <VipChip />}
                        </button>
                    );
                })}
            </div>
            <UpgradeDialog open={upgradeOpen} onClose={() => setUpgradeOpen(false)} message={t('vip.upgradeMessage', { ns: 'common' })} />
            <p className="text-xs text-on-surface-variant">{tt(`modeHint.${value.mode}`)}</p>
            {!allowProportional && value.mode === 'PROPORTIONAL' && <p className="text-xs text-outline">{tt('proportionalNeedsConsensus')}</p>}

            {value.mode === 'EXCLUSIVE' && (
                <>
                    <select
                        value={value.selected[0] || ''} className={inputClass}
                        onChange={(e) => set({
                            selected: e.target.value ? [e.target.value] : [],
                            contingentId: value.contingentId === e.target.value ? '' : value.contingentId,
                        })}
                    >
                        <option value="">{tt('recipientPlaceholder')}</option>
                        {beneficiaries.map((b) => <option key={b.id} value={b.id}>{nameOf(b)}</option>)}
                    </select>
                    {/* Gói FREE: khoá chọn người dự phòng. Tài sản cũ đã có người dự phòng (lúc còn VIP) thì vẫn mở ô để bỏ chọn,
                        chỉ còn "Không có" và người đang chọn; nếu không Owner FREE không lưu lại được tài sản đó. */}
                    <VipLocked locked={freePlan && !value.contingentId} className="space-y-1.5">
                        <label className="flex items-center gap-2 text-xs font-semibold">{tt('contingent')} <VipChip /></label>
                        <select value={value.contingentId} onChange={(e) => set({ contingentId: e.target.value })} className={inputClass}>
                            <option value="">{tt('contingentNone')}</option>
                            {beneficiaries
                                .filter((b) => b.id !== value.selected[0] && (!freePlan || b.id === value.contingentId))
                                .map((b) => (
                                    <option key={b.id} value={b.id}>{nameOf(b)}</option>
                                ))}
                        </select>
                    </VipLocked>
                </>
            )}

            {value.mode === 'PROPORTIONAL' && (
                <ul className="space-y-2">
                    {beneficiaries.map((b) => {
                        const on = value.selected.includes(b.id);
                        return (
                            <li key={b.id} className="flex items-center gap-3">
                                <label className="flex items-center gap-2 text-sm flex-1 min-w-0">
                                    <input type="checkbox" checked={on} onChange={() => toggle(b.id)} />
                                    <span className="truncate">{nameOf(b)}</span>
                                </label>
                                {value.mode === 'PROPORTIONAL' && on && (
                                    <input
                                        inputMode="decimal" value={value.shares[b.id] || ''} placeholder="%" style={{ width: '6rem' }}
                                        aria-label={tt('share')} aria-invalid={shareBad(b.id) || undefined}
                                        className={`${inputClass}${shareBad(b.id) ? ' lv-field-invalid' : ''}`}
                                        onChange={(e) => set({ shares: { ...value.shares, [b.id]: e.target.value } })}
                                    />
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}

            {value.mode === 'PROPORTIONAL' && n > 0 && (() => {
                if (total === 0) return <p className="text-xs text-on-surface-variant">{tt('totalHint')}</p>;
                const diff = total - 10000;
                const pct = (c) => String(+(Math.abs(c) / 100).toFixed(2));
                return (
                    <p role="status" className={`flex items-start gap-1.5 text-sm font-semibold ${diff === 0 ? 'text-success' : 'text-error'}`}>
                        <Icon name={diff === 0 ? 'check_circle' : 'error'} className="text-[18px] shrink-0" />
                        <span>{diff === 0 ? tt('totalOk') : tt(diff > 0 ? 'totalOver' : 'totalUnder', { total: pct(total), diff: pct(diff) })}</span>
                    </p>
                );
            })()}

            {/* Điều kiện mở tài sản (K-of-N): chỉ khi chia theo tỷ lệ và két có từ 2 người thụ hưởng trở lên */}
            {value.mode === 'PROPORTIONAL' && beneficiaries.length >= 2 && (
                <fieldset className="space-y-2 rounded-xl border border-outline-variant/40 p-3">
                    <legend className="px-1 text-xs font-semibold flex items-center gap-1">{tt('condition.title')} <InfoTip term="kOfN" /></legend>
                    <label className="flex items-center gap-2 text-sm">
                        <input type="radio" name="consensus" checked={!value.consensus} disabled={!allowProportional}
                               onChange={() => set({ consensus: false, threshold: '' })} />
                        {tt('condition.none')}
                    </label>
                    <label className="flex items-center gap-2 text-sm">
                        <input type="radio" name="consensus" checked={!!value.consensus} onChange={() => set({ consensus: true })} />
                        {tt('condition.kOfN')}
                    </label>
                    {value.consensus && (
                        <>
                            <select value={value.threshold} onChange={(e) => set({ threshold: e.target.value })} disabled={n < 2} className={inputClass}>
                                <option value="">{tt('thresholdPlaceholder')}</option>
                                {Array.from({ length: Math.max(0, n - 1) }, (_, i) => i + 2).map((k) => (
                                    <option key={k} value={k}>{tt('thresholdOption', { k, n })}</option>
                                ))}
                            </select>
                            <p className="text-xs text-on-surface-variant">{tt('condition.hint')}</p>
                        </>
                    )}
                </fieldset>
            )}

            {value.mode !== 'ALL' && (
                <textarea
                    rows={2} maxLength={1000} value={value.note} onChange={(e) => set({ note: e.target.value })}
                    placeholder={tt('notePlaceholder')} className={textareaCls}
                />
            )}
            {freePlan && <p className="text-xs text-outline">{tt('vipNote')}</p>}
        </div>
    );
}