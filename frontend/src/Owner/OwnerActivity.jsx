import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'motion/react';
import { ReducedMotionConfig, useMotionTokens } from '@animate-ui/react/utils/motion';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Icon, Alert, formatDate, humanize, inputClass, LoadingSkeleton } from './ownerUi.jsx';
import EmptyState from '../Common/EmptyState.jsx';

// Nhật ký cấp Owner (FR-05): sự kiện DMS và Executor — không gắn với vault nào nên không có trong nhật ký từng vault.
// F6: tab thứ hai "Ai đã xem di sản" (Executor / Beneficiary / Admin / Khẩn cấp xem hoặc tải), chữ ở namespace accessLog.
// Không gửi startDate thì BE trả toàn bộ lịch sử, không giới hạn độ dài khoảng ngày. value 0 = "All time".
const RANGES = [
    { value: 0, labelKey: 'range.all' },
    { value: 30, labelKey: 'range.days30' },
    { value: 90, labelKey: 'range.days90' },
    { value: 365, labelKey: 'range.months12' },
];

// Tên action: ưu tiên enums.json (audit.<ACTION>) nếu đã có, chưa có thì activity.json (action.<ACTION>), cuối cùng humanize
const actionLabel = (t, i18n, action) =>
    i18n.exists(`audit.${action}`, { ns: 'enums' })
        ? t(`audit.${action}`, { ns: 'enums' })
        : t(`action.${action}`, { defaultValue: humanize(action) });

// BE (AuditDescriptionBuilder.maskActorName) trả nhãn tiếng Anh cố định; nhãn lạ thì hiện nguyên văn
const ACTOR_KEYS = { System: 'actor.system', You: 'actor.you', 'System administrator': 'actor.admin', 'Another user': 'actor.otherUser' };
const EXECUTOR_PREFIX = 'Executor: ';
const actorLabel = (t, name) => {
    if (!name) return t('actor.system');
    if (ACTOR_KEYS[name]) return t(ACTOR_KEYS[name]);
    if (name.startsWith(EXECUTOR_PREFIX)) return t('actor.executor', { name: name.slice(EXECUTOR_PREFIX.length) });
    return name;
};

export default function OwnerActivity() {
    const { t, i18n } = useTranslation('activity');
    const { t: tAccess } = useTranslation('accessLog');
    const { spring } = useMotionTokens();
    const [tab, setTab] = useState('all');   // all = gộp cả hai nhật ký thành một dòng thời gian
    const [days, setDays] = useState(0);
    const [page, setPage] = useState(0);
    // all: { merged: [...], truncated } gộp ở máy khách; dms / access: { rows, totalPages } phân trang từ máy chủ như trước
    const [data, setData] = useState(null);
    const [error, setError] = useState('');
    const PAGE_SIZE = 20;
    const FETCH_SIZE = 100;                      // tab "Tất cả": mỗi nguồn lấy tối đa 100 dòng gần nhất rồi gộp, chia trang ở máy khách
    const serverPage = tab === 'all' ? 0 : page; // tab "Tất cả" đổi trang không gọi lại API

    useEffect(() => {
        let cancelled = false; // F6 (m4): đổi bộ lọc nhanh thì bỏ kết quả của request cũ
        setError('');
        setData(null);
        const range = {};
        if (days > 0) {
            const end = new Date();
            range.startDate = new Date(end.getTime() - days * 24 * 60 * 60 * 1000).toISOString();
            range.endDate = end.toISOString();
        }
        const tag = (res, kind) => (res?.content ?? []).map((r) => ({ ...r, kind }));
        let request;
        if (tab === 'all') {
            const args = { ...range, page: 0, size: FETCH_SIZE };
            request = Promise.all([ownerApi.getOwnerActivityLogs(args), ownerApi.getEstateAccessLogs(args)])
                .then(([dms, access]) => ({
                    merged: [...tag(dms, 'dms'), ...tag(access, 'access')]
                        .sort((x, y) => new Date(y.createdAt) - new Date(x.createdAt)),
                    // Một nguồn còn dòng cũ hơn 100 dòng đã lấy: báo để Owner mở tab riêng xem tiếp
                    truncated: (dms?.totalElements ?? 0) > FETCH_SIZE || (access?.totalElements ?? 0) > FETCH_SIZE,
                }));
        } else {
            const fetcher = tab === 'access' ? ownerApi.getEstateAccessLogs : ownerApi.getOwnerActivityLogs;
            request = fetcher({ ...range, page: serverPage, size: PAGE_SIZE })
                .then((res) => ({ rows: tag(res, tab), totalPages: res?.totalPages ?? 0 }));
        }
        request
            .then((result) => { if (!cancelled) setData(result); })
            .catch((e) => { if (!cancelled) setError(getErrorMessage(e, t('errors.load'))); });
        return () => { cancelled = true; };
    }, [tab, days, serverPage, t]);

    const merged = data?.merged;
    const rows = merged ? merged.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE) : (data?.rows ?? []);
    const totalPages = merged ? Math.ceil(merged.length / PAGE_SIZE) : (data?.totalPages ?? 0);
    const isEmpty = !!data && (merged ? merged.length === 0 : rows.length === 0);
    const isAccess = tab === 'access';
    const tabs = [
        { id: 'all', icon: 'list_alt', label: t('filterAll', { defaultValue: 'Tất cả' }) },
        { id: 'dms', icon: 'history', label: tAccess('tabDms') },
        { id: 'access', icon: 'visibility', label: tAccess('tab') },
    ];

    return (
        <OwnerLayout
            title={t('title')}
            subtitle={t('subtitle')}
            actions={
                <div data-tour="activity-filter">
                    <select value={days} onChange={(e) => { setDays(Number(e.target.value)); setPage(0); }} className={`${inputClass} w-48`}>
                    {RANGES.map((r) => <option key={r.value} value={r.value}>{t(r.labelKey)}</option>)}
                </select>
                </div>
            }
        >
            <ReducedMotionConfig>
                <div data-tour="activity-header" className="lv-segmented-track mb-6 flex flex-wrap gap-1.5 p-1.5 rounded-2xl w-fit">
                    {tabs.map((tabItem) => {
                        const active = tab === tabItem.id;
                        return (
                            <button
                                key={tabItem.id}
                                type="button"
                                onClick={() => { setTab(tabItem.id); setPage(0); }}
                                className={`relative px-4 py-2 rounded-xl text-sm font-semibold transition-colors ${
                                    active ? 'text-on-surface font-bold' : 'text-on-surface-variant hover:text-on-surface'
                                }`}
                            >
                                {active && (
                                    <motion.span
                                        layoutId="owner-activity-tab-pill"
                                        transition={spring.selection}
                                        className="lv-glass-tinted absolute inset-0 rounded-xl"
                                    />
                                )}
                                <span className="relative z-10 inline-flex items-center gap-1.5">
                                    <Icon name={tabItem.icon} className="text-[18px]" />
                                    {tabItem.label}
                                </span>
                            </button>
                        );
                    })}
                </div>
            </ReducedMotionConfig>
            <Alert>{error}</Alert>
            <div data-tour="activity-list">
            <Card className="lv-glass-thick">
                {!data && !error && <LoadingSkeleton label={t('loading')}/>}
                {isEmpty && (
                    <EmptyState compact icon={isAccess ? 'visibility' : 'history'}
                                title={days > 0 ? t('empty.periodTitle') : (isAccess ? tAccess('emptyTitle') : t('empty.noneTitle'))}
                                text={days > 0 ? t('empty.periodText') : (isAccess ? tAccess('emptyText') : t('empty.noneText'))} />
                )}
                {rows.length > 0 && (
                    <div className="overflow-x-auto -mx-6 -my-2">
                        <table className="w-full text-sm text-left">
                            <thead className="text-xs uppercase tracking-wider text-on-surface-variant">
                                <tr><th className="py-3 px-6">{t('columns.time')}</th><th className="py-3 px-4">{t('columns.action')}</th><th className="py-3 px-4">{t('columns.by')}</th><th className="py-3 px-6">{t('columns.details')}</th></tr>
                            </thead>
                            <tbody>
                                {rows.map((r) => (
                                    <tr key={`${r.kind}-${r.id}`} className="border-t border-outline-variant/25">
                                        <td className="py-3 px-6 whitespace-nowrap tabular-nums">{formatDate(r.createdAt, true)}</td>
                                        <td className="py-3 px-4 font-semibold">{actionLabel(t, i18n, r.action)}</td>
                                        <td className="py-3 px-4">{actorLabel(t, r.actorName)}</td>
                                        <td className="py-3 px-6 text-on-surface-variant">{r.description || '—'}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
                {totalPages > 1 && (
                    <div className="flex justify-between items-center mt-4 text-sm">
                        <button onClick={() => setPage((p) => p - 1)} disabled={page === 0} className="font-semibold text-secondary disabled:text-outline">{t('prev')}</button>
                        <span className="text-on-surface-variant">{t('page', { page: page + 1, total: totalPages })}</span>
                        <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= totalPages} className="font-semibold text-secondary disabled:text-outline">{t('next')}</button>
                    </div>
                )}
                {data?.truncated && (
                    <p className="mt-3 text-xs text-on-surface-variant">{t('allTruncated', { count: FETCH_SIZE })}</p>
                )}
            </Card>
            </div>
        </OwnerLayout>
    );
}
