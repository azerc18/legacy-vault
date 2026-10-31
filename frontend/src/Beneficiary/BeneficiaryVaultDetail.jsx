import React, { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { beneficiaryApi } from '../api/beneficiaryApi.js';
import { getErrorMessage } from '../api/http.js';
import {
    BeneficiaryLayout, Icon, StatusBadge, Alert, formatDate,
    ASSET_TYPE, DOCUMENT_TYPE, inputClass, btnPrimary, btnOutline,
} from './beneficiaryUi.jsx';

const OTP_RESEND_SECONDS = 60; // khớp BeneficiaryServiceImpl.OTP_RESEND_SECONDS

/* =====================================================================
   Trang chi tiết 1 vault: Xác thực (FR-16/17) -> Xem/Tải (FR-17/18) -> Đóng hồ sơ (FR-19)
   Route: /beneficiary/vaults/:vaultId  (khớp link trong email BE gửi)
   ===================================================================== */
export default function BeneficiaryVaultDetail() {
    const { vaultId } = useParams();
    const user = getUser();

    const [vault, setVault] = useState(null);       // BeneficiaryVaultSummaryResponse
    const [stage, setStage] = useState('loading');  // loading | verify | content | claimed | expired | locked | error
    const [message, setMessage] = useState('');
    const [assets, setAssets] = useState([]);
    const [documents, setDocuments] = useState([]);
    const [sessionExpiresAt, setSessionExpiresAt] = useState(null);

    // Map lỗi BE -> trạng thái màn hình
    const handleAccessError = useCallback((err, fallback = 'Something went wrong.') => {
        const status = err.response?.status;
        const msg = getErrorMessage(err, fallback);
        if (status === 403) { setStage('verify'); setMessage(''); }          // chưa xác thực / phiên 30 phút hết hạn
        else if (status === 423) { setStage('locked'); setMessage(msg); }    // sai quá 5 lần -> khoá 24h
        else if (status === 410) { setStage('expired'); setMessage(msg); }   // hết hạn nhận
        else { setMessage(msg); setStage((s) => (s === 'loading' ? 'error' : s)); }
    }, []);

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
        } catch (err) {
            handleAccessError(err);
        }
    }, [vaultId, handleAccessError]);

    useEffect(() => {
        (async () => {
            try {
                const list = await beneficiaryApi.listVaults();
                const v = list.find((x) => x.vaultId === vaultId);
                if (!v) { setStage('error'); setMessage('Vault not found or not assigned to you.'); return; }
                setVault(v);
                if (v.vaultStatus === 'CLAIMED' || v.claimStatus === 'CLAIMED') setStage('claimed');
                else if (v.vaultStatus === 'ARCHIVED_LOCKED' || v.claimStatus === 'EXPIRED') setStage('expired');
                else await loadContent();
            } catch (err) {
                setStage('error');
                setMessage(getErrorMessage(err, 'Could not load this vault.'));
            }
        })();
    }, [vaultId, loadContent]);

    if (!user) return <Navigate to="/login" replace />;

    return (
        <BeneficiaryLayout
            back="/beneficiary"
            title={vault?.vaultName || 'Vault'}
            subtitle={vault ? `From ${vault.ownerName || 'Unknown owner'} · claim before ${formatDate(vault.claimDeadlineAt)}` : ''}
        >
            {vault && <div className="mb-6"><StatusBadge vaultStatus={vault.vaultStatus} claimStatus={vault.claimStatus} /></div>}
            <Link to="/beneficiary/guide" className="inline-flex items-center gap-1 text-sm text-primary font-semibold mb-6">
                <Icon name="menu_book" className="text-[18px]" /> Not sure what to do next? Read the guide
            </Link>

            {stage === 'loading' && <p className="text-on-surface-variant">Loading...</p>}
            {stage === 'error' && <Alert>{message}</Alert>}
            {stage === 'locked' && <Alert type="warning">{message}</Alert>}
            {stage === 'expired' && <Alert type="warning">{message || 'The claim period for this vault has ended.'}</Alert>}
            {stage === 'claimed' && (
                <Alert type="success">
                    You have already received this vault{vault?.claimedAt ? ` on ${formatDate(vault.claimedAt, true)}` : ''}. Its content is no longer available.
                </Alert>
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
                        <Alert type="info">Verified. Your viewing session is valid until {formatDate(sessionExpiresAt, true)}.</Alert>
                    )}
                    {message && <Alert>{message}</Alert>}
                    <AssetList vaultId={vaultId} assets={assets} onError={(e) => handleAccessError(e)} />
                    <DocumentList vaultId={vaultId} documents={documents} onError={(e) => handleAccessError(e)} />
                    <CloseVault
                        vaultId={vaultId}
                        onClosed={(res) => {
                            setVault((v) => ({ ...v, vaultStatus: res.vaultStatus, claimStatus: res.claimStatus, claimedAt: res.claimedAt }));
                            setStage('claimed');
                        }}
                        onError={(e) => handleAccessError(e)}
                    />
                </>
            )}
        </BeneficiaryLayout>
    );
}

/* ---------- Bước 1: xác thực danh tính (FR-16 + FR-17) ---------- */
function VerifyIdentity({ vaultId, onVerified, onLocked }) {
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
        const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
        return () => clearTimeout(t);
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
                setInfo('A 6-digit code has been sent to your email. It expires in 5 minutes.');
                setCooldown(OTP_RESEND_SECONDS);
            }
            setStarted(true);
        } catch (err) {
            fail(err, 'Could not start verification.');
        } finally {
            setLoading(false);
        }
    };

    const resend = async () => {
        setError(''); setInfo('');
        try {
            await beneficiaryApi.sendOtp(vaultId);
            setInfo('A new code has been sent.');
            setCooldown(OTP_RESEND_SECONDS);
        } catch (err) {
            // Phiên 15 phút sắp hết -> phải khởi tạo lại
            if (err.response?.status === 400) setStarted(false);
            fail(err, 'Could not resend code.');
        }
    };

    const submit = async (e) => {
        e.preventDefault();
        setError('');
        if (method === 'OTP' && !/^\d{6}$/.test(otp)) return setError('The code must be exactly 6 digits.');
        if (method === 'EKYC_MOCK' && !kycIdNumber.trim()) return setError('Please enter your ID number.');
        setLoading(true);
        try {
            const res = await beneficiaryApi.verify(vaultId, method,
                method === 'OTP' ? { otp } : { kycIdNumber: kycIdNumber.trim() });
            onVerified(res);
        } catch (err) {
            fail(err, 'Verification failed.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className="bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 max-w-lg">
            <div className="flex items-center gap-3">
                <Icon name="verified_user" className="text-secondary text-[28px]" />
                <h2 className="font-title-lg text-title-lg">Verify your identity</h2>
            </div>
            <p className="mt-2 text-sm text-on-surface-variant">
                For security, you need to verify your identity before viewing the assets. Verification lasts 30 minutes.
            </p>

            <div className="mt-5">
                <Alert>{error}</Alert>
                <Alert type="info">{info}</Alert>
            </div>

            {!started ? (
                <>
                    <div className="grid grid-cols-2 gap-3">
                        {[
                            { value: 'OTP', label: 'Email code', icon: 'mail' },
                            { value: 'EKYC_MOCK', label: 'ID number (eKYC)', icon: 'badge' },
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
                        {loading ? 'Starting...' : 'Start verification'}
                    </button>
                </>
            ) : (
                <form onSubmit={submit} className="flex flex-col gap-4">
                    {method === 'OTP' ? (
                        <>
                            <input
                                inputMode="numeric" maxLength={6} placeholder="6-digit code" value={otp}
                                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                                className={`${inputClass} tracking-[0.5em] text-center`}
                            />
                            <button type="button" onClick={resend} disabled={cooldown > 0} className="text-sm font-semibold text-secondary disabled:text-outline self-start">
                                {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
                            </button>
                        </>
                    ) : (
                        <input
                            placeholder="ID card number" value={kycIdNumber}
                            onChange={(e) => setKycIdNumber(e.target.value)}
                            className={inputClass}
                        />
                    )}
                    <div className="flex gap-3">
                        <button type="button" onClick={() => { setStarted(false); setError(''); setInfo(''); }} className={btnOutline}>
                            Change method
                        </button>
                        <button type="submit" disabled={loading} className={`${btnPrimary} flex-1`}>
                            {loading ? 'Verifying...' : 'Verify'}
                        </button>
                    </div>
                </form>
            )}
        </section>
    );
}

/* ---------- Bước 2a: tài sản (FR-17 xem, FR-18 tải) ---------- */
function AssetList({ vaultId, assets, onError }) {
    const [detail, setDetail] = useState(null);
    const [busyId, setBusyId] = useState(null);

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
            <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">Assets ({assets.length})</h2>
            {assets.length === 0 ? (
                <p className="text-sm text-on-surface-variant">This vault has no assets.</p>
            ) : (
                <ul className="divide-y divide-outline-variant/30 bg-surface-container-lowest rounded-2xl border border-outline-variant/30">
                    {assets.map((a) => {
                        const t = ASSET_TYPE[a.assetType] || ASSET_TYPE.OTHER;
                        return (
                            <li key={a.id} className="flex items-center gap-4 p-4">
                                <div className="w-10 h-10 rounded-xl bg-primary-fixed text-primary flex items-center justify-center shrink-0">
                                    <Icon name={t.icon} />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p className="font-semibold truncate">{a.assetName}</p>
                                    <p className="text-xs text-outline">{t.label}</p>
                                </div>
                                <button onClick={() => view(a)} disabled={busyId === a.id} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50">
                                    View
                                </button>
                                <button onClick={() => download(a)} disabled={busyId === a.id} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-1">
                                    <Icon name="download" className="text-[18px]" />
                                </button>
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
    const [reveal, setReveal] = useState(false);
    const [copied, setCopied] = useState(false);
    const t = ASSET_TYPE[detail.assetType] || ASSET_TYPE.OTHER;

    const copy = async () => {
        await navigator.clipboard.writeText(detail.secret || '');
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
    };

    return (
        <div className="fixed inset-0 z-30 bg-black/40 flex items-center justify-center px-4" onClick={onClose}>
            <div className="bg-surface-container-lowest rounded-2xl p-6 w-full max-w-md" onClick={(e) => e.stopPropagation()}>
                <div className="flex justify-between items-start">
                    <div>
                        <p className="text-xs text-outline">{t.label}</p>
                        <h3 className="font-title-lg text-title-lg">{detail.assetName}</h3>
                    </div>
                    <button onClick={onClose}><Icon name="close" className="text-outline" /></button>
                </div>

                <p className="mt-5 font-label-sm text-on-surface-variant">Secret</p>
                <div className="mt-1 flex items-center gap-2 p-3 rounded-xl bg-surface-container-low font-mono text-sm break-all">
                    <span className="flex-1">{reveal ? detail.secret : '••••••••••••'}</span>
                    <button onClick={() => setReveal(!reveal)}><Icon name={reveal ? 'visibility_off' : 'visibility'} className="text-[20px] text-outline" /></button>
                    <button onClick={copy}><Icon name={copied ? 'check' : 'content_copy'} className="text-[20px] text-outline" /></button>
                </div>

                {detail.notes && (
                    <>
                        <p className="mt-4 font-label-sm text-on-surface-variant">Notes</p>
                        <p className="mt-1 text-sm whitespace-pre-wrap">{detail.notes}</p>
                    </>
                )}
                <p className="mt-5 text-xs text-outline">This content is decrypted only for this session and is not stored in your browser.</p>
            </div>
        </div>
    );
}

/* ---------- Bước 2b: tài liệu pháp lý (FR-18 phần 2) ---------- */
function DocumentList({ vaultId, documents, onError }) {
    const [busyId, setBusyId] = useState(null);

    const download = async (doc) => {
        setBusyId(doc.id);
        try { await beneficiaryApi.downloadDocument(vaultId, doc.id, doc.fileName); }
        catch (err) { onError(err); }
        finally { setBusyId(null); }
    };

    return (
        <section className="mb-10">
            <h2 className="font-label-lg text-label-lg text-on-surface-variant mb-4">Legal documents ({documents.length})</h2>
            {documents.length === 0 ? (
                <p className="text-sm text-on-surface-variant">No legal documents in this vault.</p>
            ) : (
                <ul className="divide-y divide-outline-variant/30 bg-surface-container-lowest rounded-2xl border border-outline-variant/30">
                    {documents.map((d) => (
                        <li key={d.id} className="flex items-center gap-4 p-4">
                            <Icon name="description" className="text-secondary" />
                            <div className="flex-1 min-w-0">
                                <p className="font-semibold truncate">{d.fileName}</p>
                                <p className="text-xs text-outline">{DOCUMENT_TYPE[d.documentType] || d.documentType} · {formatDate(d.uploadedAt)}</p>
                            </div>
                            <button onClick={() => download(d)} disabled={busyId === d.id} className="h-9 px-3 rounded-lg text-sm font-semibold text-primary hover:bg-surface-container-low disabled:opacity-50 flex items-center gap-1">
                                <Icon name="download" className="text-[18px]" /> Download
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

/* ---------- Bước 3: đóng hồ sơ (FR-19) ---------- */
function CloseVault({ vaultId, onClosed, onError }) {
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
            setError(getErrorMessage(err, 'Could not close this vault.'));
        } finally {
            setLoading(false);
        }
    };

    const noneViewed = preview && preview.totalAssets > 0 && preview.viewedCount === 0;
    const needAck = preview && preview.unviewedCount > 0;

    return (
        <section className="bg-surface-container-low rounded-2xl p-6">
            <h2 className="font-title-lg text-title-lg">Finish and close</h2>
            <p className="mt-1 text-sm text-on-surface-variant">
                Confirm you have received everything. After closing, you can no longer view or download this vault.
            </p>
            {!preview ? (
                <button onClick={openPreview} disabled={loading} className={`${btnOutline} mt-5`}>
                    {loading ? 'Checking...' : 'Review before closing'}
                </button>
            ) : (
                <div className="mt-5">
                    <Alert>{error}</Alert>
                    <p className="text-sm">
                        Viewed <b>{preview.viewedCount}</b> of <b>{preview.totalAssets}</b> assets.
                    </p>
                    {needAck && (
                        <ul className="mt-3 text-sm list-disc pl-5 text-on-surface-variant">
                            {preview.unviewedAssets.map((a) => <li key={a.id}>{a.assetName} (not viewed)</li>)}
                        </ul>
                    )}
                    {noneViewed ? (
                        <Alert type="warning">You need to view or download at least one asset before closing.</Alert>
                    ) : (
                        <>
                            {needAck && (
                                <label className="mt-4 flex items-start gap-2 text-sm">
                                    <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} className="mt-1" />
                                    I understand that I will lose access to the assets I have not viewed.
                                </label>
                            )}
                            <div className="mt-5 flex gap-3">
                                <button onClick={() => setPreview(null)} className={btnOutline}>Cancel</button>
                                <button onClick={confirm} disabled={loading || (needAck && !ack)} className={btnPrimary}>
                                    {loading ? 'Closing...' : 'Confirm and close'}
                                </button>
                            </div>
                        </>
                    )}
                </div>
            )}
        </section>
    );
}
