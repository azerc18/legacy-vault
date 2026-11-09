// Bản xem trước có thể bấm thử của giao diện Owner (trang chủ, phần "Preview").
// Dựng lại đúng bố cục thật: thanh bên tối (AppHeader), PageTitle, thẻ Điểm sẵn sàng (EstateReadinessCard),
// thẻ két (OwnerVaults), Dead Man's Switch, Người thi hành, Nhật ký — dùng chung chuỗi i18n và class với trang thật.
// Không gọi API: dữ liệu mẫu nằm trong landing.json (preview.demo), mọi thao tác chỉ đổi state cục bộ.
// Dựng ở bề rộng cố định rồi thu nhỏ theo khung để giữ đúng tỉ lệ như ảnh chụp màn hình.
import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CountingNumber } from '@animate-ui/react/primitives/texts/counting-number';
import { AutoHeight } from '@animate-ui/react/primitives/effects/auto-height';
import { useReducedMotion } from 'motion/react';
import BrandMark from './BrandMark.jsx';
import { PageTitle } from './AppHeader.jsx';
import { Card, Icon, formatDate, btnPrimary, btnOutline } from '../Owner/ownerUi.jsx';
import './appPreview.css';

const DESIGN_W = 1200;
const DESIGN_H = 760;
const DAY = 864e5;

const NAV = [
    { id: 'vaults', key: 'myVaults', icon: 'inventory_2' },
    { id: 'dms', key: 'dms', icon: 'favorite' },
    { id: 'executors', key: 'executors', icon: 'badge' },
    { id: 'activity', key: 'activity', icon: 'history' },
];

// Cùng điểm và ngưỡng hạng với OwnerReadinessServiceImpl (85 / 65 / 40)
const ITEMS = [
    { key: 'masterVaultAndAsset', points: 20, done: true },
    { key: 'assetBeneficiaryAllocated', points: 15, done: true },
    { key: 'legalWillDocument', points: 15, done: false, fix: 'self' },
    { key: 'executorsAssigned', points: 15, done: false, fix: 'executors' },
    { key: 'dmsActive', points: 15, done: true },
    { key: 'safetyNetConfigured', points: 10, done: true },
    { key: 'recentReview', points: 10, done: false, fix: 'dms' },
];
const gradeOf = (s) => (s >= 85 ? 'EXCELLENT' : s >= 65 ? 'GOOD' : s >= 40 ? 'FAIR' : 'AT_RISK');
const GRADE_META = {
    EXCELLENT: { badgeCls: 'bg-[#8ee9b0]/20 text-[#8ee9b0]', strokeCls: 'text-[#8ee9b0]' },
    GOOD: { badgeCls: 'bg-[#b9c9ff]/20 text-[#b9c9ff]', strokeCls: 'text-[#8fa8ff]' },
    FAIR: { badgeCls: 'bg-[#ffd27a]/20 text-[#ffd27a]', strokeCls: 'text-[#ffd27a]' },
    AT_RISK: { badgeCls: 'bg-[#ffb4ab]/20 text-[#ffb4ab]', strokeCls: 'text-[#ffb4ab]' },
};

// ---------- Thẻ Điểm sẵn sàng (giống EstateReadinessCard) ----------
function Readiness({ done, onFix, play }) {
    const { t } = useTranslation('readiness');
    const [expanded, setExpanded] = useState(false);
    const score = ITEMS.reduce((s, it) => s + (done[it.key] ? it.points : 0), 0);
    const grade = gradeOf(score);
    const meta = GRADE_META[grade];
    const r = 36;
    const c = 2 * Math.PI * r;
    const shown = play ? score : 0;
    const completed = ITEMS.filter((it) => done[it.key]).length;
    const pending = ITEMS.filter((it) => !done[it.key]);
    const visible = expanded ? ITEMS : pending.slice(0, 3);

    return (
        <div className="mb-6 rounded-3xl p-7 text-white lv-page-hero">
            <div className="flex items-center justify-between gap-6">
                <div className="flex items-center gap-5">
                    <div className="relative w-24 h-24 shrink-0 flex items-center justify-center">
                        <svg className="w-24 h-24 -rotate-90" viewBox="0 0 88 88">
                            <circle cx="44" cy="44" r={r} fill="none" stroke="currentColor" strokeWidth="8" className="text-white/15" />
                            <circle cx="44" cy="44" r={r} fill="none" stroke="currentColor" strokeWidth="8" strokeLinecap="round"
                                    strokeDasharray={c} strokeDashoffset={c - (shown / 100) * c}
                                    className={`${meta.strokeCls} transition-[stroke-dashoffset,color] duration-700`} />
                        </svg>
                        <div className="absolute inset-0 flex flex-col items-center justify-center">
                            <CountingNumber number={shown} className="font-headline-sm text-headline-sm leading-none tabular-nums" />
                            <span className="text-[11px] text-white/60">{t('outOf', { max: 100 })}</span>
                        </div>
                    </div>
                    <div>
                        <div className="flex flex-wrap items-center gap-2.5">
                            <h2 className="font-title-lg text-title-lg">{t('title')}</h2>
                            <span key={grade} className={`lv-ap-pop text-xs font-semibold px-2.5 py-0.5 rounded-full ${meta.badgeCls}`}>{t(`grade.${grade}`)}</span>
                        </div>
                        <p className="mt-1 text-sm text-white/70">
                            {t('progress', { completed, count: ITEMS.length })} {pending.length > 0 ? t('pendingHint') : t('doneHint')}
                        </p>
                    </div>
                </div>
                <button type="button" onClick={() => setExpanded((v) => !v)}
                        className="shrink-0 inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-white/30 text-xs font-label-lg text-white hover:bg-white/10 transition-colors">
                    <Icon name={expanded ? 'expand_less' : 'checklist'} className="text-[16px]" />
                    {expanded ? t('showFewer') : t('viewAll', { count: ITEMS.length })}
                </button>
            </div>
            <AutoHeight>
                {visible.length > 0 && (
                    <div className="pt-5">
                        <div className="pt-4 border-t border-white/15 grid grid-cols-3 gap-3">
                            {visible.map((it) => {
                                const ok = !!done[it.key];
                                return (
                                    <div key={it.key} className={`p-3.5 rounded-xl border flex flex-col justify-between gap-2 transition-colors ${ok ? 'bg-white/5 border-white/10' : 'bg-white/10 border-white/20'}`}>
                                        <div className="flex items-start gap-2.5">
                                            <Icon name={ok ? 'check_circle' : 'radio_button_unchecked'} className={`text-[18px] mt-0.5 ${ok ? 'text-[#8ee9b0]' : 'text-white/60'}`} />
                                            <div className="min-w-0 flex-1">
                                                <p className={`text-xs font-medium leading-snug ${ok ? 'text-white/50 line-through' : 'text-white'}`}>{t(`items.${it.key}`)}</p>
                                                <span className="inline-block mt-1 text-[11px] font-semibold text-[#b9c9ff]">{t('points', { count: it.points })}</span>
                                            </div>
                                        </div>
                                        {!ok && it.fix && (
                                            <div className="flex justify-end">
                                                <button type="button" onClick={() => onFix(it)}
                                                        className="lv-ap-fix inline-flex items-center gap-1 text-xs font-semibold text-[#b9c9ff] hover:underline">
                                                    {t('fixNow')} <Icon name="arrow_forward" className="text-[14px]" />
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}
            </AutoHeight>
        </div>
    );
}

// ---------- Các màn ----------
function VaultsView({ demo, done, onFix, play }) {
    const { t } = useTranslation('vault');
    return (
        <>
            <PageTitle title={t('list.title')} subtitle={t('list.subtitle')} className="mb-6"
                       right={<span className={btnPrimary}><Icon name="add" className="text-[18px]" /> {t('list.newVault')}</span>} />
            <Readiness done={done} onFix={onFix} play={play} />
            <div className="grid grid-cols-2 gap-5">
                {demo.vaults.map((v, i) => (
                    <div key={v.name} className="lv-ap-card bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
                        <div className="flex justify-between items-start gap-3">
                            <h3 className="font-title-lg text-title-lg">{v.name}</h3>
                            <span className="shrink-0 text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">{t('status.ACTIVE')}</span>
                        </div>
                        <p className="mt-2 text-sm text-on-surface-variant line-clamp-2">{v.desc}</p>
                        <div className="mt-4 flex flex-wrap gap-4 text-xs text-on-surface-variant">
                            <span>{t('list.beneficiary')} <strong className="text-on-surface">{v.beneficiary}</strong></span>
                            <span>·</span><span>{t('list.assetsCount', { count: v.assets })}</span>
                            <span>·</span><span>{t('list.docsCount', { count: v.docs + (i === 0 && done.legalWillDocument ? 1 : 0) })}</span>
                        </div>
                    </div>
                ))}
            </div>
        </>
    );
}

// Một dòng thông tin (giống Info trong DeadMansSwitch); vừa check-in thì giá trị nháy nhẹ
function Info({ label, value, highlight, flash }) {
    return (
        <div>
            <dt className="text-outline">{label}</dt>
            <dd className={`font-semibold ${highlight ? 'text-primary' : ''} ${flash ? 'lv-ap-pop' : ''}`}>{value}</dd>
        </div>
    );
}

function DmsView({ lastCheckin, onCheckIn, flash }) {
    const { t } = useTranslation('dms');
    const [checking, setChecking] = useState(false);
    const click = () => {
        if (checking) return;
        setChecking(true);
        setTimeout(() => { setChecking(false); onCheckIn(); }, 700);
    };
    const due = lastCheckin + 30 * DAY;
    return (
        <>
            <PageTitle title={t('dms.title')} subtitle={t('dms.subtitle')} className="mb-6" />
            {flash && <div className="lv-ap-pop mb-4 p-3 rounded-lg text-sm bg-success-container text-on-success-container">{t('dms.checkedIn')}</div>}
            <Card>
                <div className="flex items-center justify-between gap-3">
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-semibold bg-success-container text-on-success-container">
                        <Icon name="favorite" className="text-[18px] lv-ap-beat" /> {t('dms.status.ACTIVE')}
                    </span>
                    <button type="button" onClick={click} disabled={checking} className={`${btnPrimary} lv-ap-cta`}>
                        <Icon name="how_to_reg" className="text-[18px]" /> {checking ? t('dms.checkingIn') : t('dms.checkIn')}
                    </button>
                </div>
                <dl className="mt-6 grid grid-cols-2 gap-4 text-sm">
                    <Info flash={flash} label={t('dms.info.lastCheckin')} value={formatDate(new Date(lastCheckin).toISOString(), true)} />
                    <Info flash={flash} label={t('dms.info.nextDue')} value={formatDate(new Date(due).toISOString(), true)} highlight />
                    <Info flash={flash} label={t('dms.info.triggerAt')} value={formatDate(new Date(due + 7 * DAY).toISOString(), true)} />
                    <Info flash={flash} label={t('dms.info.remindersSent')} value="0 / 3" />
                    <Info flash={flash} label={t('dms.info.notifiedBy')} value={t('dms.channel.EMAIL')} />
                </dl>
            </Card>
        </>
    );
}

function ExecutorsView({ demo, backup, onAssign, since }) {
    const { t } = useTranslation('dms');
    const slot = (type, person, isNew) => (
        <Card>
            <div className="flex items-start justify-between gap-3">
                <div>
                    <h2 className="font-title-lg text-title-lg">{t(`executors.positions.${type}.title`)}</h2>
                    <p className="text-sm text-on-surface-variant mt-1">{t(`executors.positions.${type}.text`)}</p>
                </div>
                {person && <span className="shrink-0 text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">{t('executors.status.INACTIVE')}</span>}
            </div>
            {person ? (
                <div className={`mt-5 flex items-center gap-3 p-4 rounded-xl bg-surface-container-low ${isNew ? 'lv-ap-pop' : ''}`}>
                    <div className="w-10 h-10 rounded-full bg-primary-fixed text-primary flex items-center justify-center"><Icon name="person" /></div>
                    <div className="min-w-0">
                        <p className="font-semibold truncate">{person.name}</p>
                        <p className="text-sm text-on-surface-variant truncate">{person.email}</p>
                        <p className="text-xs text-outline">{t('executors.since', { date: formatDate(new Date(person.since).toISOString()) })}</p>
                    </div>
                </div>
            ) : (
                <p className="mt-5 text-sm text-on-surface-variant">{t('executors.noneAssigned')}</p>
            )}
            <div className="mt-4 flex gap-3">
                {person ? (
                    <>
                        <span className={btnPrimary}><Icon name="swap_horiz" className="text-[18px]" /> {t('executors.replace')}</span>
                        <span className={btnOutline}>{t('executors.revoke')}</span>
                    </>
                ) : (
                    <button type="button" onClick={onAssign} className={`${btnPrimary} lv-ap-cta`}>
                        <Icon name="person_add" className="text-[18px]" /> {t('executors.assign')}
                    </button>
                )}
            </div>
        </Card>
    );
    return (
        <>
            <PageTitle title={t('executors.title')} subtitle={t('executors.subtitle')} className="mb-6" />
            <div className="grid grid-cols-2 gap-5 items-start">
                {slot('PRIMARY', { ...demo.primary, since })}
                {slot('BACKUP', backup, true)}
            </div>
        </>
    );
}

function ActivityView({ log }) {
    const { t } = useTranslation('activity');
    return (
        <>
            <PageTitle title={t('title')} subtitle={t('subtitle')} className="mb-6" />
            <Card>
                <table className="w-full text-sm">
                    <thead>
                        <tr className="text-left text-outline">
                            <th className="pb-3 font-semibold">{t('columns.time')}</th>
                            <th className="pb-3 font-semibold">{t('columns.action')}</th>
                            <th className="pb-3 font-semibold">{t('columns.by')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {log.map((row) => (
                            <tr key={row.id} className={`border-t border-outline-variant/30 ${row.fresh ? 'lv-ap-pop' : ''}`}>
                                <td className="py-3 text-on-surface-variant">{formatDate(new Date(row.at).toISOString(), true)}</td>
                                <td className="py-3 font-semibold">{t(`action.${row.action}`)}</td>
                                <td className="py-3 text-on-surface-variant">{t(`actor.${row.by}`)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </Card>
        </>
    );
}

// ---------- Khung chính ----------
export default function AppPreview() {
    const { t } = useTranslation('landing');
    const { t: tc } = useTranslation('common');
    const reduce = useReducedMotion();
    const demo = t('preview.demo', { returnObjects: true });
    const boxRef = useRef(null);
    const [scale, setScale] = useState(0.8);
    const [play, setPlay] = useState(false);
    const [tab, setTab] = useState('vaults');
    const [start] = useState(() => Date.now());   // mốc thời gian cố định cho dữ liệu mẫu
    const [done, setDone] = useState(() => Object.fromEntries(ITEMS.map((it) => [it.key, it.done])));
    const [lastCheckin, setLastCheckin] = useState(() => start - 3 * DAY);
    const [flash, setFlash] = useState(false);
    const [backup, setBackup] = useState(null);
    const [log, setLog] = useState(() => [
        { id: 3, at: start - 3 * DAY, action: 'DMS_CHECKIN', by: 'you' },
        { id: 2, at: start - 20 * DAY, action: 'EXECUTOR_ASSIGNED', by: 'you' },
        { id: 1, at: start - 21 * DAY, action: 'DMS_CONFIGURED', by: 'you' },
    ]);

    // Thu nhỏ bản dựng 1200px cho vừa khung
    useEffect(() => {
        const el = boxRef.current;
        if (!el) return undefined;
        const ro = new ResizeObserver(([e]) => setScale(e.contentRect.width / DESIGN_W));
        ro.observe(el);
        return () => ro.disconnect();
    }, []);

    // Cuộn tới mới chạy vòng điểm (đếm từ 0 lên)
    useEffect(() => {
        const el = boxRef.current;
        if (!el || !('IntersectionObserver' in window)) { setPlay(true); return undefined; }
        const io = new IntersectionObserver(([e]) => { if (e.isIntersecting) { setPlay(true); io.disconnect(); } }, { threshold: 0.35 });
        io.observe(el);
        return () => io.disconnect();
    }, []);

    if (!demo || typeof demo !== 'object') return null;

    const addLog = (action) => setLog((l) => [{ id: Date.now(), at: Date.now(), action, by: 'you', fresh: true }, ...l.map((r) => ({ ...r, fresh: false }))]);
    const complete = (key) => setDone((d) => ({ ...d, [key]: true }));
    const onFix = (it) => (it.fix === 'self' ? complete(it.key) : setTab(it.fix));
    const onCheckIn = () => {
        setLastCheckin(Date.now()); complete('recentReview'); addLog('DMS_CHECKIN');
        setFlash(true); setTimeout(() => setFlash(false), 2600);
    };
    const onAssign = () => {
        setBackup({ ...demo.backup, since: Date.now() }); complete('executorsAssigned'); addLog('EXECUTOR_ASSIGNED');
    };

    return (
        <div ref={boxRef} className="lv-ap-box relative w-full overflow-hidden text-left" style={{ height: DESIGN_H * scale }}>
            <div className="lv-ap-stage absolute top-0 left-0 flex bg-background text-on-surface font-body-md"
                 style={{ width: DESIGN_W, height: DESIGN_H, transform: `scale(${scale})` }}>
                {/* Thanh bên (giống AppHeader) */}
                <aside className="bg-brand text-white w-[232px] shrink-0 flex flex-col gap-2 py-5 px-4">
                    <div className="lv-side-top flex items-center gap-2 text-white">
                        <BrandMark className="h-[30px] w-[30px] shrink-0" />
                        <span className="font-bold text-sm tracking-[0.18em] uppercase">LegacyVault</span>
                    </div>
                    <nav className="lv-side-nav" aria-label={t('preview.eyebrow')}>
                        {NAV.map((n) => (
                            <button key={n.id} type="button" onClick={() => setTab(n.id)} aria-current={tab === n.id ? 'page' : undefined}
                                    className={`flex items-center gap-3 px-4 py-3 rounded-xl text-[15px] font-semibold whitespace-nowrap transition-colors text-left ${tab === n.id ? 'bg-brand-container text-white' : 'text-white/70 hover:text-white hover:bg-white/5'}`}>
                                <Icon name={n.icon} className="text-[20px]" /> {tc(`nav.${n.key}`)}
                            </button>
                        ))}
                    </nav>
                    <div className="lv-side-bottom">
                        <div className="lv-side-user flex items-center gap-3 pt-3 border-t border-white/10">
                            <span className="w-8 h-8 rounded-full bg-primary-fixed text-primary text-sm font-bold flex items-center justify-center">{demo.name.charAt(0)}</span>
                            <span className="text-sm font-semibold text-white truncate">{demo.name}</span>
                        </div>
                    </div>
                </aside>

                {/* Nội dung */}
                <main className="flex-1 min-w-0 overflow-y-auto overscroll-contain px-8 py-8">
                    <div key={tab} className={reduce ? '' : 'lv-ap-enter'}>
                        {tab === 'vaults' && <VaultsView demo={demo} done={done} onFix={onFix} play={play} />}
                        {tab === 'dms' && <DmsView lastCheckin={lastCheckin} onCheckIn={onCheckIn} flash={flash} />}
                        {tab === 'executors' && <ExecutorsView demo={demo} backup={backup} onAssign={onAssign} since={start - 20 * DAY} />}
                        {tab === 'activity' && <ActivityView log={log} />}
                    </div>
                </main>
            </div>
        </div>
    );
}
