// Vùng nguy hiểm: tự xoá tài khoản (ý từ Bitwarden "Delete account").
// BE xoá mềm + ẩn danh; chặn khi còn két, còn là người thi hành / người thụ hưởng, hoặc là tài khoản nhân viên.
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { accountApi } from '../api/accountApi.js';
import { getErrorMessage } from '../api/http.js';
import { logout } from '../auth/auth.js';
import { Alert, Icon, inputClass, btnOutline, btnDanger } from '../Owner/ownerUi.jsx';
import SharedPasswordInput from '../Common/PasswordInput.jsx';

// Mỗi lý do chặn -> chỗ để xử lý (null = không có trang tự xử lý)
const BLOCKER_LINK = { VAULTS_OWNED: '/owner/vaults', EXECUTOR: '/executor/home', BENEFICIARY: '/beneficiary', STAFF_ROLE: null };

export default function DeleteAccountCard({ profile }) {
    const { t } = useTranslation('profile');
    const navigate = useNavigate();
    const [open, setOpen] = useState(false);
    const [check, setCheck] = useState(null);
    const [password, setPassword] = useState('');
    const [confirmEmail, setConfirmEmail] = useState('');
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const start = async () => {
        setOpen(true); setError(''); setCheck(null);
        try {
            setCheck(await accountApi.deletionCheck());
        } catch (e) {
            setError(getErrorMessage(e, t('deleteAccount.errors.check')));
        }
    };

    const cancel = () => {
        setOpen(false); setPassword(''); setConfirmEmail(''); setCode(''); setError('');
    };

    const emailOk = confirmEmail.trim().toLowerCase() === (profile.email || '').toLowerCase();
    const ready = check?.deletable && password && emailOk && (!check.mfaRequired || code.trim());

    const submit = async (e) => {
        e.preventDefault();
        if (!ready) return;
        setError(''); setBusy(true);
        try {
            await accountApi.deleteAccount({ password, confirmEmail: confirmEmail.trim(), code: code.trim() });
            logout();
            navigate('/login', { replace: true, state: { message: t('deleteAccount.done') } });
        } catch (err) {
            setError(getErrorMessage(err, t('deleteAccount.errors.failed')));
            setBusy(false);
        }
    };

    return (
        <section className="rounded-2xl p-6 border border-error/40 bg-surface-container-lowest">
            <h2 className="font-title-lg text-title-lg flex items-center gap-2 mb-2 text-error">
                <Icon name="person_remove" /> {t('deleteAccount.title')}
            </h2>
            <p className="text-sm text-on-surface-variant">{t('deleteAccount.description')}</p>

            {!open && (
                <div className="mt-4 flex justify-end">
                    <button type="button" onClick={start} className={`${btnOutline} !text-error !border-error/50`}>
                        {t('deleteAccount.start')}
                    </button>
                </div>
            )}

            {open && (
                <div className="mt-4">
                    <Alert>{error}</Alert>
                    {!check && !error && <p className="text-sm text-on-surface-variant">{t('loading')}</p>}

                    {check && !check.deletable && (
                        <div className="p-4 rounded-xl bg-warning-container text-on-warning-container text-sm">
                            <p className="font-semibold mb-2">{t('deleteAccount.blockedTitle')}</p>
                            <ul className="space-y-2">
                                {check.blockers.map((b) => (
                                    <li key={b} className="flex items-start gap-2">
                                        <Icon name="block" className="text-[18px] mt-0.5" />
                                        <span className="flex-1">
                                            {t(`deleteAccount.blockers.${b}`, { count: check.vaultCount, defaultValue: b })}
                                            {BLOCKER_LINK[b] && (
                                                <>{' '}<Link to={BLOCKER_LINK[b]} className="font-semibold underline">{t('deleteAccount.goFix')}</Link></>
                                            )}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}

                    {check?.deletable && (
                        <form onSubmit={submit} className="space-y-4">
                            <ul className="p-4 rounded-xl bg-error-container text-on-error-container text-sm space-y-1.5 list-disc pl-8">
                                <li>{t('deleteAccount.consequences.signIn')}</li>
                                <li>{t('deleteAccount.consequences.anonymize')}</li>
                                <li>{t('deleteAccount.consequences.keep')}</li>
                                <li>{t('deleteAccount.consequences.noRefund')}</li>
                            </ul>
                            <p className="text-xs text-on-surface-variant">{t('deleteAccount.exportFirst')}</p>

                            <label className="block">
                                <span className="block text-sm font-semibold mb-1.5">{t('deleteAccount.password')}</span>
                                <SharedPasswordInput className={inputClass} value={password} onChange={setPassword} autoComplete="current-password" />
                                <span className="block text-xs text-on-surface-variant mt-1">{t('deleteAccount.passwordHint')}</span>
                            </label>
                            {check.mfaRequired && (
                                <label className="block">
                                    <span className="block text-sm font-semibold mb-1.5">{t('deleteAccount.code')}</span>
                                    <input value={code} onChange={(e) => setCode(e.target.value)} className={inputClass}
                                           inputMode="text" autoComplete="one-time-code" placeholder="123456 / XXXX-XXXX" />
                                </label>
                            )}
                            <label className="block">
                                <span className="block text-sm font-semibold mb-1.5">
                                    {t('deleteAccount.typeEmail')} <span className="font-mono text-error break-all">{profile.email}</span>
                                </span>
                                <input value={confirmEmail} onChange={(e) => setConfirmEmail(e.target.value)} className={inputClass}
                                       autoComplete="off" spellCheck={false} />
                            </label>

                            <div className="flex justify-end gap-3 pt-1">
                                <button type="button" onClick={cancel} disabled={busy} className={btnOutline}>{t('common:actions.cancel')}</button>
                                <button type="submit" disabled={!ready || busy} className={btnDanger}>
                                    {busy ? t('deleteAccount.deleting') : t('deleteAccount.confirm')}
                                </button>
                            </div>
                        </form>
                    )}

                    {check && !check.deletable && (
                        <div className="mt-4 flex justify-end">
                            <button type="button" onClick={cancel} className={btnOutline}>{t('deleteAccount.close')}</button>
                        </div>
                    )}
                </div>
            )}
        </section>
    );
}
