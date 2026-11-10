import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { createPortal } from 'react-dom';
import { Trans, useTranslation } from 'react-i18next';
import { getUser } from '../auth/auth.js';
import { beneficiaryApi } from '../api/beneficiaryApi.js';
import { getErrorMessage } from '../api/http.js';
import ClaimProgress from '../Common/ClaimProgress.jsx';
import VerificationBadge from '../Common/VerificationBadge.jsx';
import { AutoHeight } from '@animate-ui/react/primitives/effects/auto-height';
import {
    BeneficiaryLayout, Icon, StatusBadge, Alert, formatDate,
    ASSET_ICON, useEnumLabels, inputClass, btnPrimary, btnOutline,
} from './beneficiaryUi.jsx';
import BeneficiaryLegacyMessages from './BeneficiaryLegacyMessages.jsx';

const OTP_RESEND_SECONDS = 60; // khớp BeneficiaryServiceImpl.OTP_RESEND_SECONDS

/* =====================================================================
   Trang chi tiết 1 vault: Xác thực (FR-16/17) -> Xem/Tải (FR-17/18) -> Đóng hồ sơ (FR-19)
   Route: /beneficiary/vaults/:vaultId  (khớp link trong email BE gửi)
   ===================================================================== */
export default function BeneficiaryVaultDetail() {
    const { vaultId } = useParams();
    const { t } = useTranslation('beneficiary');
    const tRef = useRef(t);          // callback/effect đọc t qua ref để đổi ngôn ngữ không kích hoạt tải lại
    useEffect(() => { tRef.current = t; }, [t]);
    const user = getUser();

    const [vault, setVault] = useState(null);       // BeneficiaryVaultSummaryResponse
    const [stage, setStage] = useState('loading');  // loading | verify | content | claimed | declined | expired | locked | error
    const [message, setMessage] = useState('');
    const [assets, setAssets] = useState([]);
    const [documents, setDocuments] = useState([]);
    const [sessionExpiresAt, setSessionExpiresAt] = useState(null);
    const [progress, setProgress] = useState(null);   // ClaimProgressResponse (N2-notify)
    const [declineResult, setDeclineResult] = useState(null);   // DeclineClaimResponse (X1: stalledAssets)

    // Map lỗi BE -> trạng thái màn hình
    const handleAccessError = useCallback((err, fallback) => {
        const status = err.response?.status;
        const msg = getErrorMessage(err, fallback ?? tRef.current('detail.genericError'));
        if (status === 403) { setStage('verify'); setMessage(''); }          // chưa xác thực / phiên 30 phút hết hạn
        else if (status === 423) { setStage('locked'); setMessage(msg); }    // sai quá 5 lần -> khoá 24h
        else if (status === 410) { setStage('expired'); setMessage(msg); }   // hết hạn nhận
        else { setMessage(msg); setStage((s) => (s === 'loading' ? 'error' : s)); }
    }, []);

    // Tiến độ không bao giờ được chặn trang: lỗi (kể cả 409 sau khi đã đóng hồ sơ) thì chỉ ẩn khối này
    const loadProgress = useCallback(async () => {
        try { setProgress(await beneficiaryApi.getProgress(vaultId)); }
        catch { setProgress(null); }
    }, [vaultId]);

    // Thử tải nội dung. BE trả 403 khi chưa có phiên xem hợp lệ -> chuyển sang bước xác thực
    const loadContent = useCallback(async () => {
        try {
            const [a, d] = await Promise.all([
                beneficiaryApi.listAssets(vaultId),
                beneficiaryApi.listDocuments(vaultId),
            ]);
            setAssets(a);
            setDocuments(d);
            setStage('content');
            loadProgress();
        } catch (err) {
            handleAccessError(err);
        }
    }, [vaultId, handleAccessError, loadProgress]);

    useEffect(() => {
        (async () => {
            try {
                const list = await beneficiaryApi.listVaults();
                const v = list.find((x) => x.vaultId === vaultId);
                if (!v) { setStage('error'); setMessage(tRef.current('detail.notFound')); return; }
                setVault(v);
                if (v.myStatus === 'DECLINED' || v.myStatus === 'WAIVED') setStage('declined');
                else if (v.vaultStatus === 'CLAIMED' || v.claimStatus === 'CLAIMED') setStage('claimed');
                else if (v.vaultStatus === 'ARCHIVED_LOCKED' || v.claimStatus === 'EXPIRED') setStage('expired');
                else await loadContent();
            } catch (err) {
                setStage('error');
                setMessage(getErrorMessage(err, tRef.current('detail.loadError')));
            }
        })();
    }, [vaultId, loadContent]);

    // #22: ở bước 'claimed' (vừa đóng hồ sơ trên trang này, hoặc mở lại trang đã đóng) tải danh sách ở dạng chỉ xem
    useEffect(() => {
        if (stage !== 'claimed') return;
        beneficiaryApi.listAssets(vaultId).then(setAssets).catch(() => setAssets([]));
    }, [stage, vaultId]);

    if (!user) return <Navigate to="/login" replace />;

    return (
        <BeneficiaryLayout
            back="/beneficiary"
            title={vault?.vaultName || t('detail.vaultFallback')}
            subtitle={vault ? t('detail.subtitle', { owner: vault.ownerName || t('unknownOwner'), date: formatDate(vault.claimDeadlineAt) }) : ''}
        >
            {vault && <div className="mb-6"><StatusBadge vaultStatus={vault.vaultStatus} claimStatus={vault.claimStatus} myStatus={vault.myStatus} /></div>}
            <Link to="/beneficiary/guide" className="inline-flex items-center gap-1 text-sm text-primary font-semibold mb-6">
                <Icon name="menu_book" className="text-[18px]" /> {t('detail.guideLink')}
            </Link>

            {stage === 'loading' && <p className="text-on-surface-variant">{t('detail.loading')}</p>}
            {stage === 'error' && <Alert>{message}</Alert>}
            {stage === 'locked' && <Alert type="warning">{message}</Alert>}
            {stage === 'expired' && <Alert type="warning">{message || t('detail.expired')}</Alert>}
            {stage === 'claimed' && (
                <Alert type="success">
                    {vault?.claimedAt
                        ? t('detail.claimedAt', { date: formatDate(vault.claimedAt, true) })
                        : t('detail.claimed')}
                </Alert>
            )}
            {stage === 'claimed' && assets.length > 0 && (
                <AssetList vaultId={vaultId} assets={assets} readOnly onError={() => {}} />
            )}
            {stage === 'declined' && (
                <>
                    <Alert type="info">
                        {vault?.myStatus === 'WAIVED' ? t('detail.waived') : t('detail.declined')}
                    </Alert>
                    {declineResult?.stalledAssets > 0 && (
                        <Alert type="warning">{t('detail.declinedStalled', { n: declineResult.stalledAssets })}</Alert>
                    )}
                </>
            )}

            {stage === 'verify' && (
                <VerifyIdentity
                    vaultId={vaultId}
                    onVerified={(res) => { setSessionExpiresAt(res.viewSessionExpiresAt); loadContent(); }}
                    onLocked={(msg) => { setStage('locked'); setMessage(msg); }}
                />
            )}

            {stage === 'content' && (
                <>
                    {sessionExpiresAt && (
                        <Alert type="info">{t('detail.verified', { date: formatDate(sessionExpiresAt, true) })}</Alert>
                    )}
                    {message && <Alert>{message}</Alert>}
                    <RespondClaim
                        vaultId={vaultId}
                        myStatus={progress?.myStatus ?? vault?.myStatus}
                        onAccepted={() => { setVault((v) => ({ ...v, myStatus: 'ACCEPTED' })); loadProgress(); }}
                        onDeclined={(res) => { setDeclineResult(res); setVault((v) => ({ ...v, myStatus: 'DECLINED' })); setProgress(null); setStage('declined'); }}
                        onError={(e) => handleAccessError(e)}
                    />
                    <ProgressPanel progress={progress} />
                    <AssetList vaultId={vaultId} assets={assets} onError={(e) => handleAccessError(e)} />
                    <DocumentList vaultId={vaultId} documents={documents} onError={(e) => handleAccessError(e)} />
                    <DossierCard vaultId={vaultId} onError={(e) => handleAccessError(e)} />
                    <CloseVault
                        vaultId={vaultId}
                        hasJoint={assets.some((a) => a.consensus)}
                        onClosed={(res) => {
                            setVault((v) => ({ ...v, vaultStatus: res.vaultStatus, claimStatus: res.claimStatus, claimedAt: res.claimedAt }));
                            setStage('claimed');
                        }}
                        onError={(e) => handleAccessError(e)}
                    />
                </>
            )}

            {(stage === 'content' || stage === 'claimed') && (
                <BeneficiaryLegacyMessages vaultId={vaultId} />
            )}
            {vault && !['loading', 'error'].includes(stage) && <ExecutorTasksCard vaultId={vaultId} />}
        </BeneficiaryLayout>
    );
}

/* ---------- Bước 1: xác thực danh tính (FR-16 + FR-17) ---------- */
function VerifyIdentity({ vaultId, onVerified, onLocked }) {
    const { t } = useTranslation('beneficiary');
    const [method, setMethod] = useState('OTP');
    const [started, setStarted] = useState(false);   // đã gọi /claim (+ gửi OTP)
    const [otp, setOtp] = useState('');
    const [kycIdNumber, setKycIdNumber] = useState('');
    const [cooldown, setCooldown] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [info, setInfo] = useState('');

    useEffect(() => {
        if (cooldown <= 0) return;
        const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(timer);
    }, [cooldown]);

    const fail = (err, fallback) => {
        if (err.response?.status === 423) return onLocked(getErrorMessage(err));
        setError(getErrorMessage(err, fallback));
    };

    // FR-16: tạo claim + phiên xác thực PENDING; nếu OTP thì gửi luôn mã
    const start = async () => {
        setError(''); setInfo(''); setLoading(true);
        try {
            await beneficiaryApi.initClaim(vaultId, method);
            if (method === 'OTP') {
                await beneficiaryApi.sendOtp(vaultId);
                setInfo(t('verify.otpSent'));
                setCooldown(OTP_RESEND_SECONDS);
            }
            setStarted(true);
        } catch (err) {
            fail(err, t('verify.startError'));
        } finally {
            setLoading(false);
        }
    };

    const resend = async () => {
        setError(''); setInfo('');
        try {
            await beneficiaryApi.sendOtp(vaultId);
            setInfo(t('verify.otpResent'));
            setCooldown(OTP_RESEND_SECONDS);
        } catch (err) {
            // Phiên 15 phút sắp hết -> phải khởi tạo lại
            if (err.response?.status === 400) setStarted(false);
            fail(err, t('verify.resendError'));
        }
    };

    const submit = async (e) => {
        e.preventDefault();
        setError('');
        if (method === 'OTP' && !/^\d{6}$/.test(otp)) return setError(t('verify.otpInvalid'));
        if (method === 'EKYC_MOCK' && !kycIdNumber.trim()) return setError(t('verify.idRequired'));
        setLoading(true);
        try {
            const res = await beneficiaryApi.verify(vaultId, method,
                method === 'OTP' ? { otp } : { kycIdNumber: kycIdNumber.trim() });
            onVerified(res);
        } catch (err) {
            fail(err, t('verify.verifyError'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className="lv-surface-secure bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 max-w-lg">
            <div className="flex items-center gap-3">
                <Icon name="verified_user" className="text-secondary text-[28px]" />
                <h2 className="font-title-lg text-title-lg">{t('verify.title')}</h2>
            </div>
            <p className="mt-2 text-sm text-on-surface-variant">{t('verify.intro')}</p>

            <div className="mt-5">
                <Alert>{error}</Alert>
                <Alert type="info">{info}</Alert>
            </div>

            {!started ? (
                <>
                    <div className="grid grid-cols-2 gap-3">
                        {[
                            { value: 'OTP', label: t('verify.methodOtp'), icon: 'mail' },
                            { value: 'EKYC_MOCK', label: t('verify.methodKyc'), icon: 'badge' },
                        ].map((m) => (
                            <button
                                key={m.value}
                                type="button"
                                onClick={() => setMethod(m.value)}
                                className={`p-4 rounded-xl border text-left ${method === m.value ? 'border-primary bg-primary-fixed' : 'border-outline-variant'}`}
                            >
                                <Icon name={m.icon} className="text-primary" />
                                <p className="mt-1 font-semibold text-sm">{m.label}</p>
                            </button>
                        ))}
                    </div>
                    <button onClick={start} disabled={loading} className={`${btnPrimary} w-full mt-5`}>
                        {loading ? t('verify.starting') : t('verify.start')}
                    </button>
                </>
            ) : (
                <form onSubmit={submit} className="flex flex-col gap-4">
                    {method === 'OTP' ? (
                        <>
                            <input
                                inputMode="numeric" maxLength={6} placeholder={t('verify.otpPlaceholder')} value={otp}
                                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                                className={`${inputClass} tracking-[0.5em] text-center`}
                            />
                            <button type="button" onClick={resend} disabled={cooldown > 0} className="text-sm font-semibold text-secondary disabled:text-outline self-start">
                                {cooldown > 0 ? t('verify.resendIn', { seconds: cooldown }) : t('verify.resend')}
                            </button>
                        </>
                    ) : (
                        <input
                            placeholder={t('verify.idPlaceholder')} value={kycIdNumber}
                            onChange={(e) => setKycIdNumber(e.target.value)}
                            className={inputClass}
                        />
                    )}
                    <div className="flex gap-3">
                        <button type="button" onClick={() => { setStarted(false); setError(''); setInfo(''); }} className={btnOutline}>
                            {t('verify.changeMethod')}
                        </button>
                        <button type="submit" disabled={loading} className={`${btnPrimary} flex-1`}>
                            {loading ? t('verify.submitting') : t('verify.submit')}
                        </button>
                    </div>
                </form>
            )}
        </section>
    );
}

/* ---------- Bước 2a: tài sản (FR-17 xem, FR-18 tải) ---------- */
function AssetList({ vaultId, assets, onError, readOnly = false }) {
    const { t } = useTranslation('beneficiary');
    const label = useEnumLabels();
    const [detail, setDetail] = useState(null);
    const [busyId, setBusyId] = useState(null);
    // X1: trạng thái đồng thuận mới nhất theo assetId (ghi đè giá trị trong danh sách sau khi đồng ý / làm mới)
    const [consensusById, setConsensusById] = useState({});
    const consensusOf = (a) => consensusById[a.id] || a.consensus || null;

    const approve = async (asset) => {
        setBusyId(asset.id);
        try {
            const c = await beneficiaryApi.approveConsensus(vaultId, asset.id);
            setConsensusById((m) => ({ ...m, [asset.id]: c }));
        }
        catch (err) { onError(err); }
        finally { setBusyId(null); }
    };

    const refresh = async (asset) => {
        setBusyId(asset.id);
        try { const c = await beneficiaryApi.getConsensus(vaultId, asset.id); setConsensusById((m) => ({ ...m, [asset.id]: c })); }
        catch (err) { onError(err); }
        finally { setBusyId(null); }
    };

    const view = async (asset) => {
        setBusyId(asset.id);
        try { setDetail(await beneficiaryApi.getAsset(vaultId, asset.id)); }
        catch (err) { onError(err); }
        finally { setBusyId(null); }
    };

    const download = async (asset) => {
        setBusyId(asset.id);
        try { await beneficiaryApi.downloadAsset(vaultId, asset.id, asset.assetName); }
        catch (err) { onError(err); }
        finally { setBusyId(null); }
    };

    return (
        <section className="mb-10">
            <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{t('assets.title', { count: assets.length })}</h2>
            {readOnly && <p className="text-sm text-on-surface-variant mb-4">{t('assets.closedHint')}</p>}
            {assets.length === 0 ? (
                <p className="text-sm text-on-surface-variant">{t('assets.empty')}</p>
            ) : (
                <ul className="divide-y divide-outline-variant/30 bg-surface-container-lowest rounded-2xl border border-outline-variant/30">
                    {assets.map((a) => {
                        const icon = ASSET_ICON[a.assetType] || ASSET_ICON.OTHER;
                        const c = consensusOf(a);
                        const locked = !!c && !c.reached;   // X1: chưa đủ K người đồng ý thì chưa xem / tải được
                        return (
                            <li key={a.id} className="flex flex-wrap items-center gap-4 p-4">
                                <div className="w-10 h-10 rounded-xl bg-primary-fixed text-primary flex items-center justify-center shrink-0">
                                    <Icon name={icon} />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p className="font-semibold truncate">{a.assetName}</p>
                                    <p className="text-xs text-outline">{label.assetType(a.assetType)}</p>
                                    {a.verificationStatus && (
                                        <div className="mt-1">
                                            <VerificationBadge status={a.verificationStatus} method={a.verificationMethod} />
                                        </div>
                                    )}
                                    {c && (
                                        <p className="mt-1 text-xs text-on-surface-variant">
                                            {c.reached
                                                ? t('assets.consensusDone', { approved: c.approved, total: c.total })
                                                : t('assets.consensusNeeded', { threshold: c.threshold, total: c.total, approved: c.approved })}
                                        </p>
                                    )}
                                    {c && !c.reached && c.myApproved && (
                                        <p className="mt-1 text-xs text-outline">{t('assets.refreshHint')}</p>
                                    )}
                                </div>
                                {!readOnly && locked && (
                                    <>
                                        {c.myApproved ? (
                                            <span className="text-xs font-semibold text-primary">{t('assets.youApproved')}</span>
                                        ) : (
                                            <button onClick={() => approve(a)} disabled={busyId === a.id} className="h-9 px-3 rounded-lg text-sm font-semibold bg-primary text-on-primary disabled:opacity-50">
                                                {t('assets.approve')}
                                            </button>
                                        )}
                                        <button onClick={() => refresh(a)} disabled={busyId === a.id} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50">
                                            {t('assets.refresh')}
                                        </button>
                                    </>
                                )}
                                {!readOnly && (<>
                                <button onClick={() => view(a)} disabled={busyId === a.id || locked} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50">
                                    {t('assets.view')}
                                </button>
                                <button onClick={() => download(a)} disabled={busyId === a.id || locked} aria-label={t('assets.download')} title={t('assets.download')} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-1">
                                    <Icon name="download" className="text-[18px]" />
                                </button>
                                </>)}
                            </li>
                        );
                    })}
                </ul>
            )}
            {detail && <AssetDetailModal detail={detail} onClose={() => setDetail(null)} />}
        </section>
    );
}

function AssetDetailModal({ detail, onClose }) {
    const { t } = useTranslation('beneficiary');
    const label = useEnumLabels();
    const [reveal, setReveal] = useState(false);
    const [copied, setCopied] = useState(false);

    const copy = async () => {
        await navigator.clipboard.writeText(detail.secret || '');
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
    };

    return createPortal(
        <div className="lv-modal-backdrop fixed inset-0 z-50 bg-black/40 dark:bg-black/70 backdrop-blur-md flex items-center justify-center px-4" onClick={onClose}>
            <div className="lv-surface-secure bg-surface-container-lowest rounded-2xl p-6 w-full max-w-md" onClick={(e) => e.stopPropagation()}>
                <div className="flex justify-between items-start">
                    <div>
                        <p className="text-xs text-outline">{label.assetType(detail.assetType)}</p>
                        <h3 className="font-title-lg text-title-lg">{detail.assetName}</h3>
                    </div>
                    <button onClick={onClose} aria-label={t('assets.close')}><Icon name="close" className="text-outline" /></button>
                </div>

                <p className="mt-5 font-label-sm text-on-surface-variant">{t('assets.secret')}</p>
                <div className="mt-1 flex items-center gap-2 p-3 rounded-xl bg-surface-container-low font-mono text-sm break-all">
                    <span className="flex-1">{reveal ? detail.secret : '••••••••••••'}</span>
                    <button onClick={() => setReveal(!reveal)} aria-label={reveal ? t('assets.hide') : t('assets.reveal')}><Icon name={reveal ? 'visibility_off' : 'visibility'} className="text-[20px] text-outline" /></button>
                    <button onClick={copy} aria-label={t('assets.copy')}><Icon name={copied ? 'check' : 'content_copy'} className="text-[20px] text-outline" /></button>
                </div>

                {detail.notes && (
                    <>
                        <p className="mt-4 font-label-sm text-on-surface-variant">{t('assets.notes')}</p>
                        <p className="mt-1 text-sm whitespace-pre-wrap">{detail.notes}</p>
                    </>
                )}
                <p className="mt-5 text-xs text-outline">{t('assets.sessionNote')}</p>
            </div>
        </div>,
        document.body
    );
}

/* ---------- Bước 2b: tài liệu pháp lý (FR-18 phần 2) ---------- */
function DocumentList({ vaultId, documents, onError }) {
    const { t } = useTranslation('beneficiary');
    const label = useEnumLabels();
    const [busyId, setBusyId] = useState(null);

    const download = async (doc) => {
        setBusyId(doc.id);
        try { await beneficiaryApi.downloadDocument(vaultId, doc.id, doc.fileName); }
        catch (err) { onError(err); }
        finally { setBusyId(null); }
    };

    return (
        <section className="mb-10">
            <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{t('documents.title', { count: documents.length })}</h2>
            {documents.length === 0 ? (
                <p className="text-sm text-on-surface-variant">{t('documents.empty')}</p>
            ) : (
                <ul className="divide-y divide-outline-variant/30 bg-surface-container-lowest rounded-2xl border border-outline-variant/30">
                    {documents.map((d) => (
                        <li key={d.id} className="flex items-center gap-4 p-4">
                            <Icon name="description" className="text-secondary" />
                            <div className="flex-1 min-w-0">
                                <p className="font-semibold truncate">{d.fileName}</p>
                                <p className="text-xs text-outline">{label.documentType(d.documentType)} · {formatDate(d.uploadedAt)}</p>
                            </div>
                            <button onClick={() => download(d)} disabled={busyId === d.id} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-1">
                                <Icon name="download" className="text-[18px]" /> {t('documents.download')}
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

// F5: theo dõi Executor xử lý tài khoản số (MXH / email / ví điện tử) theo di nguyện, chỉ xem
const TASK_STATUS_CLS = {
    TODO: 'bg-surface-container text-on-surface-variant',
    DOING: 'bg-warning-container text-on-warning-container',
    DONE: 'bg-success-container text-on-success-container',
};

function ExecutorTasksCard({ vaultId }) {
    const { t } = useTranslation(['beneficiary', 'verification']);
    const [tasks, setTasks] = useState([]);
    const [disputing, setDisputing] = useState(null);   // task đang nhập lý do phản đối
    const [reason, setReason] = useState('');
    const [busyId, setBusyId] = useState('');
    const [error, setError] = useState('');

    // Không có task hoặc lỗi thì ẩn khối này, không chặn phần còn lại của trang
    useEffect(() => {
        beneficiaryApi.listExecutorTasks(vaultId).then(setTasks).catch(() => setTasks([]));
    }, [vaultId]);

    const replace = (u) => setTasks((prev) => prev.map((x) => (x.id === u.id ? u : x)));

    const confirm = async (task) => {
        setBusyId(task.id); setError('');
        try {
            replace(await beneficiaryApi.confirmExecutorTask(vaultId, task.id));
        } catch (e) {
            setError(getErrorMessage(e, t('verification:beneficiary.confirmError')));
        } finally {
            setBusyId('');
        }
    };

    const dispute = async () => {
        const text = reason.trim();
        if (text.length < 5 || text.length > 500) return setError(t('verification:beneficiary.reasonInvalid'));
        setBusyId(disputing.id); setError('');
        try {
            replace(await beneficiaryApi.disputeExecutorTask(vaultId, disputing.id, text));
            setDisputing(null); setReason('');
        } catch (e) {
            setError(getErrorMessage(e, t('verification:beneficiary.disputeError')));
        } finally {
            setBusyId('');
        }
    };

    if (tasks.length === 0) return null;
    const done = tasks.filter((x) => x.status === 'DONE').length;
    return (
        <section className="bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 mt-6">
            <h2 className="font-title-lg text-title-lg flex items-center gap-2">
                <Icon name="checklist" className="text-secondary" /> {t('executor.title')}
            </h2>
            <p className="mt-1 text-sm text-on-surface-variant">
                {t('executor.intro', { done, total: tasks.length })}
            </p>
            <div className="mt-3"><Alert>{error}</Alert></div>
            <ul className="mt-1 divide-y divide-outline-variant/20">
                {tasks.map((task) => {
                    // PENDING còn trong hạn phản đối thì mới xác nhận / phản đối được
                    const canRespond = task.status === 'DONE' && task.confirmationStatus === 'PENDING' && !task.autoConfirmed;
                    const confirmKey = task.autoConfirmed ? 'autoConfirmed' : task.confirmationStatus;
                    return (
                        <li key={task.id} className="py-3 flex flex-wrap items-start justify-between gap-3">
                            <div className="min-w-0">
                                <p className="font-semibold">{task.platform || t('executor.account')}{task.accountHandle && ` · ${task.accountHandle}`}</p>
                                <p className="text-sm text-on-surface-variant">{t(`executor.wish.${task.wish}`, { defaultValue: task.wish })}</p>
                                {task.note && <p className="text-sm text-on-surface-variant mt-1">{t('executor.note', { note: task.note })}</p>}
                                {task.evidenceRef && <p className="text-sm text-on-surface-variant mt-1">{t('verification:beneficiary.evidenceRef', { ref: task.evidenceRef })}</p>}
                                {task.hasEvidenceDocument && (
                                    <p className="text-sm text-on-surface-variant mt-1">
                                        {t('verification:beneficiary.evidenceFile', { name: task.evidenceFileName })}
                                        {task.evidenceSha256 && <span className="ml-2 text-xs font-mono text-outline">{t('verification:beneficiary.sha', { hash: task.evidenceSha256.slice(0, 16) })}</span>}
                                    </p>
                                )}
                                {confirmKey && (
                                    <p className="text-sm mt-2 text-on-surface-variant">
                                        {t(`verification:confirm.${confirmKey}`, { date: formatDate(task.confirmDeadlineAt), reason: task.disputeReason })}
                                    </p>
                                )}
                            </div>
                            <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full whitespace-nowrap ${TASK_STATUS_CLS[task.status] || ''}`}>
                                {t(`executor.taskStatus.${task.status}`, { defaultValue: task.status })}
                            </span>

                            {canRespond && disputing?.id !== task.id && (
                                <div className="w-full flex flex-wrap justify-end gap-2">
                                    <button type="button" disabled={busyId === task.id} onClick={() => { setDisputing(task); setReason(''); setError(''); }} className={btnOutline}>
                                        {t('verification:beneficiary.disputeBtn')}
                                    </button>
                                    <button type="button" disabled={busyId === task.id} onClick={() => confirm(task)} className={btnPrimary}>
                                        {t('verification:beneficiary.confirmBtn')}
                                    </button>
                                </div>
                            )}

                            <AutoHeight className="w-full">
                                {disputing?.id === task.id && (
                                    <div className="w-full pt-1">
                                        <label className="block">
                                            <span className="text-sm font-semibold text-on-surface-variant">{t('verification:beneficiary.reasonLabel')}</span>
                                            <textarea rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)}
                                                      placeholder={t('verification:beneficiary.reasonPlaceholder')} className={`${inputClass} mt-1 h-auto py-2`} />
                                        </label>
                                        <div className="flex justify-end gap-2 mt-2">
                                            <button type="button" disabled={busyId === task.id} onClick={() => { setDisputing(null); setError(''); }} className={btnOutline}>
                                                {t('verification:beneficiary.cancel')}
                                            </button>
                                            <button type="button" disabled={busyId === task.id} onClick={dispute} className={btnPrimary}>
                                                {t('verification:beneficiary.submitDispute')}
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </AutoHeight>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}

// X4: tải hồ sơ khai nhận di sản (PDF) — chỉ phần tài sản của mình, không chứa thông tin bí mật
function DossierCard({ vaultId, onError }) {
    const { t } = useTranslation('beneficiary');
    const [busy, setBusy] = useState(false);

    const download = async () => {
        setBusy(true);
        try { await beneficiaryApi.downloadDossier(vaultId); }
        catch (e) { onError(e); }
        finally { setBusy(false); }
    };

    return (
        <section className="bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 mt-6">
            <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0">
                    <h2 className="font-title-lg text-title-lg flex items-center gap-2">
                        <Icon name="picture_as_pdf" className="text-secondary" /> {t('dossier.title')}
                    </h2>
                    <p className="mt-1 text-sm text-on-surface-variant max-w-xl">{t('dossier.description')}</p>
                </div>
                <button type="button" onClick={download} disabled={busy} className={btnPrimary}>
                    <Icon name="download" className="text-[18px]" /> {busy ? t('dossier.preparing') : t('dossier.download')}
                </button>
            </div>
        </section>
    );
}

/* ---------- Bước 3: đóng hồ sơ (FR-19) ---------- */
function CloseVault({ vaultId, hasJoint, onClosed, onError }) {
    const { t } = useTranslation('beneficiary');
    const [preview, setPreview] = useState(null);
    const [ack, setAck] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const openPreview = async () => {
        setError(''); setAck(false); setLoading(true);
        try { setPreview(await beneficiaryApi.closePreview(vaultId)); }
        catch (err) { onError(err); }
        finally { setLoading(false); }
    };

    const confirm = async () => {
        setError(''); setLoading(true);
        try {
            onClosed(await beneficiaryApi.closeVault(vaultId, preview.unviewedCount > 0 ? ack : undefined));
        } catch (err) {
            if (err.response?.status === 403) return onError(err);
            setError(getErrorMessage(err, t('close.error')));
        } finally {
            setLoading(false);
        }
    };

    const noneViewed = preview && preview.totalAssets > 0 && preview.viewedCount === 0;
    const needAck = preview && preview.unviewedCount > 0;

    return (
        <section className="bg-surface-container-low rounded-2xl p-6">
            <h2 className="font-title-lg text-title-lg">{t('close.title')}</h2>
            <p className="mt-1 text-sm text-on-surface-variant">{t('close.intro')}</p>
            {!preview ? (
                <button onClick={openPreview} disabled={loading} className={`${btnOutline} mt-5`}>
                    {loading ? t('close.checking') : t('close.review')}
                </button>
            ) : (
                <div className="mt-5">
                    <Alert>{error}</Alert>
                    <p className="text-sm">
                        <Trans
                            i18nKey="close.viewedOf" ns="beneficiary"
                            values={{ viewed: preview.viewedCount, total: preview.totalAssets }}
                            components={{ b: <b /> }}
                        />
                    </p>
                    {hasJoint && <Alert type="info">{t('close.jointNote')}</Alert>}
                    {needAck && (
                        <ul className="mt-3 text-sm list-disc pl-5 text-on-surface-variant">
                            {preview.unviewedAssets.map((a) => <li key={a.id}>{t('close.notViewed', { name: a.assetName })}</li>)}
                        </ul>
                    )}
                    {noneViewed ? (
                        <Alert type="warning">{t('close.mustView')}</Alert>
                    ) : (
                        <>
                            {needAck && (
                                <label className="mt-4 flex items-start gap-2 text-sm">
                                    <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} className="mt-1" />
                                    {t('close.ack')}
                                </label>
                            )}
                            <div className="mt-5 flex gap-3">
                                <button onClick={() => setPreview(null)} className={btnOutline}>{t('close.cancel')}</button>
                                <button onClick={confirm} disabled={loading || (needAck && !ack)} className={btnPrimary}>
                                    {loading ? t('close.closing') : t('close.confirm')}
                                </button>
                            </div>
                        </>
                    )}
                </div>
            )}
        </section>
    );
}

/* ---------- N2b: phản hồi phần được chia (Accept / Decline) ---------- */
function RespondClaim({ vaultId, myStatus, onAccepted, onDeclined, onError }) {
    const { t } = useTranslation('beneficiary');
    const [confirming, setConfirming] = useState(false);
    const [reason, setReason] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    // Đã Accept thì không Decline được nữa (BE trả 409), nên chỉ hiện trạng thái
    if (myStatus === 'ACCEPTED') {
        return <Alert type="success">{t('respond.accepted')}</Alert>;
    }

    const run = async (action) => {
        setError(''); setLoading(true);
        try { await action(); }
        catch (err) {
            if (err.response?.status === 403) return onError(err);   // phiên xem hết hạn -> quay lại bước xác thực
            setError(getErrorMessage(err, t('respond.error')));
        } finally { setLoading(false); }
    };
    const accept = () => run(async () => { await beneficiaryApi.acceptClaim(vaultId); onAccepted(); });
    const decline = () => run(async () => { onDeclined(await beneficiaryApi.declineClaim(vaultId, reason.trim())); });

    return (
        <section className="mb-10 bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
            <h2 className="font-title-lg text-title-lg">{t('respond.title')}</h2>
            <p className="mt-1 text-sm text-on-surface-variant">{t('respond.intro')}</p>
            <div className="mt-4"><Alert>{error}</Alert></div>
            {!confirming ? (
                <div className="flex gap-3">
                    <button onClick={accept} disabled={loading} className={btnPrimary}>{loading ? t('respond.sending') : t('respond.accept')}</button>
                    <button onClick={() => setConfirming(true)} disabled={loading} className={btnOutline}>{t('respond.decline')}</button>
                </div>
            ) : (
                <div>
                    <label className="block text-sm font-semibold mb-1" htmlFor="decline-reason">{t('respond.reason')}</label>
                    <textarea
                        id="decline-reason" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)}
                        className="w-full min-h-[80px] px-4 py-3 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
                    />
                    <div className="mt-4 flex gap-3">
                        <button onClick={() => setConfirming(false)} disabled={loading} className={btnOutline}>{t('respond.cancel')}</button>
                        <button onClick={decline} disabled={loading} className={btnPrimary}>{loading ? t('respond.declining') : t('respond.confirmDecline')}</button>
                    </div>
                </div>
            )}
        </section>
    );
}

/* ---------- N2-notify: tiến độ phần của mình ---------- */
function ProgressPanel({ progress }) {
    const { t } = useTranslation('beneficiary');
    if (!progress) return null;
    return (
        <section className="mb-10 bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30">
            <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">{t('progress.title')}</h2>
            <ClaimProgress accepted={progress.acceptedBeneficiaries} required={progress.totalBeneficiaries} status={progress.vaultStatus} />
            <p className="mt-3 text-sm text-on-surface-variant">
                {t('progress.closedOf', { closed: progress.closedBeneficiaries, total: progress.totalBeneficiaries })}
                {progress.declinedBeneficiaries > 0 && ` · ${t('progress.declined', { count: progress.declinedBeneficiaries })}`}
            </p>
            <p className="mt-4 text-sm">
                <Trans
                    i18nKey="progress.viewedOf" ns="beneficiary"
                    values={{ viewed: progress.viewedAssets, total: progress.totalAssets }}
                    components={{ b: <b /> }}
                />
            </p>
            {progress.assets.length > 0 && (
                <ul className="mt-3 divide-y divide-outline-variant/30">
                    {progress.assets.map((a) => {
                        const icon = ASSET_ICON[a.assetType] || ASSET_ICON.OTHER;
                        return (
                            <li key={a.assetId} className="flex flex-wrap items-center gap-2 py-3">
                                <Icon name={icon} className="text-primary text-[20px]" />
                                <span className="font-semibold mr-2">{a.assetName}</span>
                                {a.allocationMode && (
                                    <span className="text-xs px-2 py-0.5 rounded-full bg-surface-container">
                                        {t(`progress.mode.${a.allocationMode}`, { defaultValue: a.allocationMode })}
                                        {a.allocationMode === 'PROPORTIONAL' && a.sharePercent != null && ` · ${a.sharePercent}%`}
                                    </span>
                                )}
                                {a.reassignedToMe && (
                                    <span className="text-xs px-2 py-0.5 rounded-full bg-warning-container text-on-warning-container">
                                        {t('progress.reassigned')}
                                    </span>
                                )}
                                <span className={`ml-auto text-xs font-semibold ${a.viewed ? 'text-primary' : 'text-outline'}`}>
                                    {a.viewed ? t('progress.viewed') : t('progress.notViewed')}
                                </span>
                            </li>
                        );
                    })}
                </ul>
            )}
        </section>
    );
}
