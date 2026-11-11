// Việc 5: báo cáo doanh thu cho Admin. Một biểu đồ cột (đã thu theo ngày/tháng), 4 thẻ số liệu so với kỳ trước,
// xếp hạng theo gói và theo cổng. Không thêm thư viện biểu đồ: SVG tự vẽ, có tooltip khi rê/focus và bảng thay thế.
import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminApi } from '../api/adminApi.js';
import { getErrorMessage } from '../api/http.js';
import { Alert } from '../Owner/ownerUi.jsx';
import { AdminLayout, Dossier, StatCard, formatVnd } from './adminUi.jsx';

const RANGES = ['30d', '12m'];
const CHART_H = 220;
const PAD = { top: 12, right: 8, bottom: 26, left: 52 };

const RANGE_KEY = 'lv_admin_revenue_range';
function savedRange() {
    try { return RANGES.includes(localStorage.getItem(RANGE_KEY)) ? localStorage.getItem(RANGE_KEY) : '30d'; } catch { return '30d'; }
}

// 0 -> 0, 1.234.000 -> "1,2 Tr" (vi) / "1.2M" (en): nhãn trục gọn, số đầy đủ nằm trong tooltip
function compact(n, lang) {
    return new Intl.NumberFormat(lang === 'en' ? 'en' : 'vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(n);
}

// Mốc trục y "tròn": 1, 2, 2.5, 5 x 10^k sao cho 4 vạch phủ hết giá trị lớn nhất
function niceTicks(max) {
    if (max <= 0) return [0];
    const raw = max / 4;
    const pow = 10 ** Math.floor(Math.log10(raw));
    const step = [1, 2, 2.5, 5, 10].map((m) => m * pow).find((s) => s >= raw);
    const ticks = [];
    for (let v = 0; v <= max + step * 0.001; v += step) ticks.push(v);
    if (ticks[ticks.length - 1] < max) ticks.push(ticks[ticks.length - 1] + step);
    return ticks;
}

const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

function useWidth(ref) {
    const [w, setW] = useState(0);
    useLayoutEffect(() => {
        if (!ref.current) return undefined;
        const ro = new ResizeObserver(([e]) => setW(Math.floor(e.contentRect.width)));
        ro.observe(ref.current);
        return () => ro.disconnect();
    }, [ref]);
    return w;
}

// Cột bo 4px ở đầu, đáy vuông nằm trên trục
function barPath(x, y, w, h) {
    const r = Math.min(4, w / 2, h);
    return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;
}

function ColumnChart({ buckets, range, labelOf, longLabelOf }) {
    const { t, i18n } = useTranslation('admin');
    const box = useRef(null);
    const width = useWidth(box);
    const [hover, setHover] = useState(null);
    const max = Math.max(0, ...buckets.map((b) => b.collectedVnd));
    const ticks = niceTicks(max);
    const top = ticks[ticks.length - 1] || 1;
    const plotW = Math.max(0, width - PAD.left - PAD.right);
    const plotH = CHART_H - PAD.top - PAD.bottom;
    const slot = buckets.length ? plotW / buckets.length : 0;
    const barW = Math.max(2, Math.min(24, slot - 4));
    const y = (v) => PAD.top + plotH - (v / top) * plotH;
    // 30 ngày: nhãn mỗi 5 ngày (đủ chỗ ở màn hẹp); 12 tháng: mỗi tháng, hoặc cách 1 khi quá hẹp
    const every = range === '30d' ? (slot < 14 ? 7 : 5) : (slot < 34 ? 2 : 1);

    const h = hover == null ? null : buckets[hover];
    const tipLeft = hover == null ? 0 : Math.min(Math.max(PAD.left + slot * (hover + 0.5), 90), Math.max(90, width - 90));

    return (
        <div ref={box} className="relative" onMouseLeave={() => setHover(null)}>
            {max === 0 && <p className="text-sm text-on-surface-variant py-16 text-center">{t('revenue.chart.empty')}</p>}
            {max > 0 && width > 0 && (
                <svg width={width} height={CHART_H} role="group" aria-label={t('revenue.chart.aria')} className="block overflow-visible">
                    {ticks.map((v) => (
                        <g key={v}>
                            <line x1={PAD.left} x2={width - PAD.right} y1={y(v)} y2={y(v)}
                                  stroke="rgb(var(--c-outline-variant))" strokeWidth={v === 0 ? 1 : 0.5}
                                  strokeDasharray={v === 0 ? undefined : '2 3'} />
                            <text x={PAD.left - 8} y={y(v)} dy="0.32em" textAnchor="end"
                                  className="lv-mono" fontSize="11" fill="rgb(var(--c-outline))">{compact(v, i18n.language)}</text>
                        </g>
                    ))}
                    {buckets.map((b, i) => {
                        const x = PAD.left + slot * i + (slot - barW) / 2;
                        const bh = Math.max(b.collectedVnd > 0 ? 2 : 0, (b.collectedVnd / top) * plotH);
                        const active = hover === i;
                        return (
                            <g key={b.start}>
                                {bh > 0 && (
                                    <path d={barPath(x, PAD.top + plotH - bh, barW, bh)}
                                          fill="rgb(var(--c-secondary))" fillOpacity={hover == null || active ? 0.9 : 0.45} />
                                )}
                                {i % every === 0 && (
                                    <text x={PAD.left + slot * (i + 0.5)} y={CHART_H - 8} textAnchor="middle"
                                          className="lv-mono" fontSize="11" fill="rgb(var(--c-outline))">{labelOf(b.start)}</text>
                                )}
                                {/* Vùng bắt chuột/focus phủ cả khe, rộng hơn cột */}
                                <rect x={PAD.left + slot * i} y={PAD.top} width={slot} height={plotH} fill="transparent"
                                      tabIndex={0} className="outline-none focus-visible:stroke-secondary"
                                      aria-label={`${longLabelOf(b.start)}: ${formatVnd(b.collectedVnd)}, ${t('revenue.chart.orders', { count: b.paidCount })}`}
                                      onMouseEnter={() => setHover(i)} onFocus={() => setHover(i)} onBlur={() => setHover(null)} />
                            </g>
                        );
                    })}
                </svg>
            )}
            {h && max > 0 && (
                <div aria-hidden="true" className="pointer-events-none z-10 rounded-xl border border-outline-variant shadow-lg px-3 py-2 text-xs whitespace-nowrap"
                     // Không dùng bg-surface-container-lowest: materials.css biến lớp đó thành tấm kính position: relative
                     style={{ position: 'absolute', left: tipLeft, top: Math.max(0, y(h.collectedVnd) - 92), transform: 'translateX(-50%)', width: 'max-content', textAlign: 'left', background: 'rgb(var(--c-surface-container-lowest))' }}>
                    <p className="font-semibold text-on-surface">{longLabelOf(h.start)}</p>
                    <p className="font-semibold tabular-nums text-on-surface">{formatVnd(h.collectedVnd)}</p>
                    <p className="text-on-surface-variant">{t('revenue.chart.orders', { count: h.paidCount })}</p>
                    {h.refundedVnd > 0 && <p className="text-on-surface-variant">{t('revenue.chart.refunded', { amount: formatVnd(h.refundedVnd) })}</p>}
                </div>
            )}
        </div>
    );
}

function BucketTable({ buckets, longLabelOf }) {
    const { t } = useTranslation('admin');
    return (
        <div className="overflow-x-auto max-h-[320px]">
            <table className="lv-ledger">
                <thead>
                <tr>
                    <th>{t('revenue.table.period')}</th>
                    <th className="text-right">{t('revenue.table.collected')}</th>
                    <th className="text-right">{t('revenue.table.orders')}</th>
                    <th className="text-right">{t('revenue.table.refunded')}</th>
                </tr>
                </thead>
                <tbody>
                {[...buckets].reverse().map((b) => (
                    <tr key={b.start}>
                        <td>{longLabelOf(b.start)}</td>
                        <td className="lv-num text-right">{formatVnd(b.collectedVnd)}</td>
                        <td className="lv-num text-right">{b.paidCount}</td>
                        <td className="lv-num text-right">{b.refundedVnd ? formatVnd(b.refundedVnd) : '—'}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}

// Xếp hạng ngang: một màu, dài nhất ở trên, số và tỉ trọng ở bên phải
function Ranked({ slices, labelOf, empty }) {
    const { t } = useTranslation('admin');
    const total = slices.reduce((s, x) => s + x.collectedVnd, 0);
    const max = Math.max(0, ...slices.map((s) => s.collectedVnd));
    if (!slices.length || total === 0) return <p className="text-sm text-on-surface-variant py-6 text-center">{empty}</p>;
    return (
        <ul className="space-y-3.5">
            {slices.map((s) => (
                <li key={s.key}>
                    <div className="flex items-baseline justify-between gap-3 text-sm">
                        <span className="font-semibold truncate">{labelOf(s)}</span>
                        <span className="lv-num whitespace-nowrap">
                            {formatVnd(s.collectedVnd)}
                            <span className="text-outline text-xs ml-2">{Math.round((s.collectedVnd / total) * 100)}%</span>
                        </span>
                    </div>
                    <div className="mt-1.5 h-2 rounded-full bg-surface-container-high overflow-hidden">
                        <div className="h-full rounded-full" style={{ width: `${(s.collectedVnd / max) * 100}%`, background: 'rgb(var(--c-secondary))' }} />
                    </div>
                    <p className="mt-1 text-[11px] text-outline">{t('revenue.chart.orders', { count: s.paidCount })}</p>
                </li>
            ))}
        </ul>
    );
}

// Phần trăm thay đổi so với kỳ trước; null khi kỳ trước bằng 0 (không chia được)
function delta(now, before) {
    if (!before) return null;
    return Math.round(((now - before) / before) * 1000) / 10;
}

export default function AdminRevenue() {
    const { t, i18n } = useTranslation('admin');
    const [range, setRange] = useState(savedRange);
    const [view, setView] = useState('chart');
    const [data, setData] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        let alive = true;
        adminApi.revenue(range)
            .then((d) => { if (alive) setData(d); })
            .catch((e) => { if (alive) setError(getErrorMessage(e, t('revenue.loadError'))); });
        return () => { alive = false; };
    }, [range, t]);

    const locale = i18n.language === 'en' ? 'en-GB' : 'vi-VN';
    const fmt = useMemo(() => {
        const day = new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit' });
        const dayLong = new Intl.DateTimeFormat(locale, { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' });
        const mon = new Intl.DateTimeFormat(locale, { month: 'short' });
        const monLong = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' });
        const d = (iso) => new Date(`${iso}T00:00:00`);
        return {
            short: (iso) => (data?.range === '12m' ? mon.format(d(iso)) : day.format(d(iso))),
            long: (iso) => cap(data?.range === '12m' ? monLong.format(d(iso)) : dayLong.format(d(iso))),
        };
    }, [locale, data?.range]);

    const pick = (r) => {
        setError('');
        setRange(r);
        try { localStorage.setItem(RANGE_KEY, r); } catch { /* chế độ riêng tư: bỏ qua */ }
    };

    const cur = data?.current;
    const prev = data?.previous;
    const netDelta = cur && prev ? delta(cur.netVnd, prev.netVnd) : null;
    const prevLabel = t(`revenue.vsPrevious.${range}`);
    const providerLabel = (s) => t(`revenue.providers.${s.key}`, { defaultValue: s.key });

    const toggle = (
        <div className="lv-segmented-track flex gap-1 p-1 rounded-2xl w-fit" role="group" aria-label={t('revenue.rangeLabel')}>
            {RANGES.map((r) => (
                <button key={r} type="button" aria-pressed={range === r} onClick={() => pick(r)}
                        className={`px-3.5 py-1.5 rounded-xl text-sm font-semibold transition-colors ${range === r ? 'lv-glass-tinted text-on-surface font-bold' : 'text-on-surface-variant hover:text-on-surface'}`}>
                    {t(`revenue.ranges.${r}`)}
                </button>
            ))}
        </div>
    );

    return (
        <AdminLayout title={t('revenue.title')} subtitle={t('revenue.subtitle')} actions={toggle}>
            <Alert>{error}</Alert>
            {!data && !error && <p className="text-on-surface-variant">{t('loading')}</p>}
            {data && (
                <>
                    <div className="grid gap-4 sm:grid-cols-2 2xl:grid-cols-4 mb-6">
                        <StatCard icon="account_balance_wallet" tone="ok" label={t('revenue.kpi.net')} value={formatVnd(cur.netVnd)}
                                  foot={netDelta == null
                                      ? t('revenue.kpi.noPrevious', { period: prevLabel })
                                      : t(netDelta >= 0 ? 'revenue.kpi.up' : 'revenue.kpi.down', { value: Math.abs(netDelta).toLocaleString(locale), period: prevLabel })} />
                        <StatCard icon="payments" label={t('revenue.kpi.collected')} value={formatVnd(cur.collectedVnd)}
                                  foot={t('revenue.kpi.refundedFoot', { amount: formatVnd(cur.refundedVnd), count: cur.refundCount })} />
                        <StatCard icon="shopping_bag" label={t('revenue.kpi.orders')} value={cur.paidCount}
                                  foot={t('revenue.kpi.ordersFoot', { avg: formatVnd(cur.averageOrderVnd), discount: formatVnd(cur.discountVnd) })} />
                        <StatCard icon="conversion_path" label={t('revenue.kpi.conversion')}
                                  value={cur.conversionPercent == null ? '—' : `${cur.conversionPercent.toLocaleString(locale)}%`}
                                  foot={t('revenue.kpi.conversionFoot', { count: cur.checkoutCount })} />
                    </div>

                    <Dossier className="mb-6" title={t(`revenue.chart.title.${data.range}`)} tab={t(`revenue.ranges.${data.range}`)}
                             right={(
                                 <button type="button" onClick={() => setView(view === 'chart' ? 'table' : 'chart')}
                                         className="text-xs font-semibold text-secondary hover:text-primary">
                                     {view === 'chart' ? t('revenue.showTable') : t('revenue.showChart')}
                                 </button>
                             )}>
                        {view === 'chart'
                            ? <ColumnChart buckets={data.buckets} range={data.range} labelOf={fmt.short} longLabelOf={fmt.long} />
                            : <BucketTable buckets={data.buckets} longLabelOf={fmt.long} />}
                        <p className="mt-3 text-[11px] text-outline">{t('revenue.chart.note')}</p>
                    </Dossier>

                    <div className="grid gap-6 lg:grid-cols-2">
                        <Dossier title={t('revenue.byPlan')}>
                            <Ranked slices={data.byPlan} labelOf={(s) => s.label || s.key} empty={t('revenue.chart.empty')} />
                        </Dossier>
                        <Dossier title={t('revenue.byProvider')}>
                            <Ranked slices={data.byProvider} labelOf={providerLabel} empty={t('revenue.chart.empty')} />
                        </Dossier>
                    </div>
                </>
            )}
        </AdminLayout>
    );
}
