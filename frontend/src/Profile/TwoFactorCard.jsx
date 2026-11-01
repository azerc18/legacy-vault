import React, { useState } from 'react';
import { userApi } from '../api/userApi.js';
import { getErrorMessage } from '../api/http.js';
import { Card, Alert, Icon, inputClass, btnPrimary, btnOutline, btnDanger } from '../Owner/ownerUi.jsx';

// H4: bật/tắt xác thực 2 lớp (TOTP) + mã dự phòng
export default function TwoFactorCard({ profile, onChanged }) {
    const [setup, setSetup] = useState(null);          // { secret, qrDataUri } khi đang thiết lập
    const [backupCodes, setBackupCodes] = useState(null);
    const [disabling, setDisabling] = useState(false);
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const run = async (fn) => {
        setError(''); setBusy(true);
        try { await fn(); } catch (e) { setError(getErrorMessage(e, 'Something went wrong.')); }
        finally { setBusy(false); }
    };

    const startSetup = () => run(async () => { setSetup(await userApi.setup2fa()); setCode(''); });

    const confirmEnable = (e) => {
        e.preventDefault();
        run(async () => {
            const res = await userApi.enable2fa(code.trim());
            setBackupCodes(res.backupCodes);
            setSetup(null); setCode('');
            onChanged({ ...profile, mfaEnabled: true });
        });
    };

    const confirmDisable = (e) => {
        e.preventDefault();
        run(async () => {
            await userApi.disable2fa(code.trim());
            setDisabling(false); setCode('');
            onChanged({ ...profile, mfaEnabled: false });
        });
    };

    const downloadCodes = () => {
        const text = `LegacyVault backup codes (${profile.email})\nEach code can be used once.\n\n${backupCodes.join('\n')}\n`;
        const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
        const a = Object.assign(document.createElement('a'), { href: url, download: 'legacyvault-backup-codes.txt' });
        a.click();
        URL.revokeObjectURL(url);
    };

    const codeInput = (placeholder) => (
        <input value={code} onChange={(e) => setCode(e.target.value)} placeholder={placeholder}
               autoComplete="one-time-code" maxLength={9} className={`${inputClass} text-center tracking-[0.2em]`} />
    );

    // --- Vừa bật: hiện mã dự phòng đúng 1 lần ---
    if (backupCodes) {
        return (
            <Card title="Save your backup codes" icon="key">
                <Alert type="warning">
                    Store these codes somewhere safe. Each code works once if you lose your phone. They will not be shown again.
                </Alert>
                <div className="grid grid-cols-2 gap-2 font-mono text-center my-4">
                    {backupCodes.map((c) => (
                        <span key={c} className="py-2 rounded-lg bg-surface-container-low">{c}</span>
                    ))}
                </div>
                <div className="flex flex-wrap justify-end gap-3">
                    <button type="button" onClick={downloadCodes} className={btnOutline}>
                        <Icon name="download" /> Download
                    </button>
                    <button type="button" onClick={() => setBackupCodes(null)} className={btnPrimary}>
                        I have saved them
                    </button>
                </div>
            </Card>
        );
    }

    return (
        <Card title="Two-factor authentication" icon="phonelink_lock">
            <Alert>{error}</Alert>

            <div className="flex items-center justify-between gap-3 mb-4">
                <p className="text-sm text-on-surface-variant">
                    Require a code from an authenticator app (Google Authenticator, Microsoft Authenticator…) when you sign in.
                </p>
                <span className={`shrink-0 text-xs font-semibold px-3 py-1 rounded-full ${profile.mfaEnabled
                    ? 'bg-success-container text-on-success-container'
                    : 'bg-surface-container text-on-surface-variant'}`}>
                    {profile.mfaEnabled ? 'On' : 'Off'}
                </span>
            </div>

            {/* --- Đang tắt, chưa thiết lập --- */}
            {!profile.mfaEnabled && !setup && (
                <div className="flex justify-end">
                    <button type="button" onClick={startSetup} disabled={busy} className={btnPrimary}>
                        {busy ? 'Please wait...' : 'Turn on'}
                    </button>
                </div>
            )}

            {/* --- Đang thiết lập: quét QR + nhập mã --- */}
            {!profile.mfaEnabled && setup && (
                <form onSubmit={confirmEnable} className="space-y-4">
                    <ol className="text-sm list-decimal pl-5 space-y-1">
                        <li>Scan this QR code with your authenticator app.</li>
                        <li>Enter the 6-digit code the app shows.</li>
                    </ol>
                    <div className="flex flex-col sm:flex-row items-center gap-4">
                        <img src={setup.qrDataUri} alt="2FA QR code" className="w-44 h-44 rounded-xl border border-outline-variant bg-white p-2" />
                        <div className="text-sm min-w-0">
                            <p className="text-on-surface-variant">Can't scan? Enter this key manually:</p>
                            <code className="block mt-1 p-2 rounded-lg bg-surface-container-low break-all select-all">{setup.secret}</code>
                        </div>
                    </div>
                    {codeInput('123456')}
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={() => { setSetup(null); setCode(''); setError(''); }} className={btnOutline}>Cancel</button>
                        <button type="submit" disabled={busy || code.trim().length !== 6} className={btnPrimary}>
                            {busy ? 'Verifying...' : 'Verify and turn on'}
                        </button>
                    </div>
                </form>
            )}

            {/* --- Đang bật: nút tắt (cần nhập mã) --- */}
            {profile.mfaEnabled && !disabling && (
                <div className="flex justify-end">
                    <button type="button" onClick={() => { setDisabling(true); setCode(''); setError(''); }} className={btnDanger}>
                        Turn off
                    </button>
                </div>
            )}
            {profile.mfaEnabled && disabling && (
                <form onSubmit={confirmDisable} className="space-y-4">
                    <p className="text-sm">Enter a code from your authenticator app or one of your backup codes to turn off 2FA.</p>
                    {codeInput('123456 or XXXX-XXXX')}
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={() => setDisabling(false)} className={btnOutline}>Cancel</button>
                        <button type="submit" disabled={busy || !code.trim()} className={btnDanger}>
                            {busy ? 'Please wait...' : 'Turn off 2FA'}
                        </button>
                    </div>
                </form>
            )}
        </Card>
    );
}