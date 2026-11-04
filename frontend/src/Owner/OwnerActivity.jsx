import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ownerApi } from '../api/ownerApi.js';
import { getErrorMessage } from '../api/http.js';
import { OwnerLayout, Card, Alert, formatDate, humanize, inputClass } from './ownerUi.jsx';
import EmptyState from "../Common/EmptyState.jsx";

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
    const [tab, setTab] = useState('dms');
    const [days, setDays] = useState(0);
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        let cancelled = false; // F6 (m4): đổi tab / trang nhanh thì bỏ kết quả của request cũ
        setError('');
        setData(null);
        const range = {};
        if (days > 0) {
            const end = new Date();
            range.startDate = new Date(end.getTime() - days * 24 * 60 * 60 * 1000).toISOString();
            range.endDate = end.toISOString();
        }
        const fetcher = tab === 'access' ? ownerApi.getEstateAccessLogs : ownerApi.getOwnerActivityLogs;
        fetcher({ ...range, page, size: 20 })
            .then((res) => { if (!cancelled) setData(res); })
            .catch((e) => { if (!cancelled) setError(getErrorMessage(e, t('errors.load'))); });
        return () => { cancelled = true; };
    }, [tab, days, page]);

    const rows = data?.content ?? [];
    const totalPages = data?.totalPages ?? 0;
    const isAccess = tab === 'access';
    const tabs = [
        { id: 'dms', label: tAccess('tabDms') },
        { id: 'access', label: tAccess('tab') },
    ];

    return (
        <OwnerLayout
            title={t('title')}
            subtitle={isAccess ? tAccess('subtitle') : t('subtitle')}
            actions={
                <select value={days} onChange={(e) => { setDays(Number(e.target.value)); setPage(0); }} className={`${inputClass} w-48`}>
                    {RANGES.map((r) => <option key={r.value} value={r.value}>{t(r.labelKey)}</option>)}
                </select>
            }
        >
            <div className="flex gap-2 mb-4 border-b border-outline-variant/20 pb-2">
                {tabs.map((tabItem) => (
                    <button
                        key={tabItem.id}
                        type="button"
                        onClick={() => { setTab(tabItem.id); setPage(0); }}
                        className={`px-4 py-2 rounded-lg text-sm font-semibold transition-colors ${
                            tab === tabItem.id
                                ? 'bg-secondary text-on-secondary'
                                : 'text-on-surface-variant hover:bg-surface-container-high'
                        }`}
                    >
                        {tabItem.label}
                    </button>
                ))}
            </div>
            <Alert>{error}</Alert>
            <Card>
                {!data && !error && <p className="text-sm text-on-surface-variant">{t('loading')}</p>}
                {data && rows.length === 0 && (
                    <EmptyState compact icon={isAccess ? 'visibility' : 'history'}
                                title={days > 0 ? t('empty.periodTitle') : (isAccess ? tAccess('emptyTitle') : t('empty.noneTitle'))}
                                text={days > 0 ? t('empty.periodText') : (isAccess ? tAccess('emptyText') : t('empty.noneText'))} />
                )}
                {rows.length > 0 && (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                            <thead className="text-on-surface-variant">
                                <tr><th className="py-2 pr-4">{t('columns.time')}</th><th className="py-2 pr-4">{t('columns.action')}</th><th className="py-2 pr-4">{t('columns.by')}</th><th className="py-2">{t('columns.details')}</th></tr>
                            </thead>
                            <tbody>
                                {rows.map((r) => (
                                    <tr key={r.id} className="border-t border-outline-variant/20">
                                        <td className="py-2 pr-4 whitespace-nowrap">{formatDate(r.createdAt, true)}</td>
                                        <td className="py-2 pr-4 font-semibold">{actionLabel(t, i18n, r.action)}</td>
                                        <td className="py-2 pr-4">{actorLabel(t, r.actorName)}</td>
                                        <td className="py-2 text-on-surface-variant">{r.description || '—'}</td>
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
            </Card>
        </OwnerLayout>
    );
}
