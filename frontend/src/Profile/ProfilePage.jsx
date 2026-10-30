// Trang hồ sơ cá nhân (/me) — dùng chung cho mọi role
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { userApi, toStoredUser } from '../api/userApi.js';
import UserAvatar from '../Common/UserAvatar.jsx';
import { getErrorMessage } from '../api/http.js';
import { updateStoredUser } from '../auth/auth.js';
import { Icon, Card, Alert, formatDate, inputClass, btnPrimary, btnOutline } from '../Owner/ownerUi.jsx';
import PasswordStrengthMeter from "../Common/PasswordStrengthMeter.jsx";
import {isPasswordValid} from "../Common/passwordPolicy.js";
import useNamedBeneficiary from '../Common/useNamedBeneficiary.js';
import AppHeader, { PAGE_WIDTH, PageTitle } from '../Common/AppHeader.jsx';

const ROLE_LABEL = {
    OWNER: 'Vault Owner',
    BENEFICIARY: 'Beneficiary',
    EXECUTOR: 'Executor',
    LEGAL_VERIFIER: 'Legal Verifier',
    ADMIN: 'Administrator',
};

const STATUS_STYLE = {
    ACTIVE: 'bg-success-container text-on-success-container',
    PENDING: 'bg-warning-container text-on-warning-container',
    LOCKED: 'bg-error-container text-on-error-container',
};

// Giống regex ở UpdateProfileRequest (BE)
const PHONE_RE = /^[0-9+()\-\s]{8,20}$/;

const MAX_AVATAR = 2 * 1024 * 1024; // khớp giới hạn 2MB ở BE

function Field({ label, hint, children }) {
    return (
        <label className="block">
            <span className="block text-sm font-semibold mb-1.5">{label}</span>
            {children}
            {hint && <span className="block text-xs text-on-surface-variant mt-1">{hint}</span>}
        </label>
    );
}

// Ô mật khẩu có nút hiện/ẩn
function PasswordInput({ value, onChange, autoComplete }) {
    const [show, setShow] = useState(false);
    return (
        <div className="relative">
            <input
                type={show ? 'text' : 'password'}
                value={value}
                onChange={(e) => onChange(e.target.value)}
                autoComplete={autoComplete}
                className={`${inputClass} pr-11`}
            />
            <button
                type="button"
                onClick={() => setShow(!show)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-primary"
                aria-label={show ? 'Hide password' : 'Show password'}
            >
                <Icon name={show ? 'visibility_off' : 'visibility'} className="text-[20px]" />
            </button>
        </div>
    );
}

// ---------- Cột trái: thẻ tóm tắt ----------
// Ảnh đại diện + nút đổi / xoá ảnh
function AvatarEditor({ profile, onChanged }) {
    const inputRef = useRef(null);
    const [preview, setPreview] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const pick = async (e) => {
        const file = e.target.files?.[0];
        e.target.value = ''; // cho phép chọn lại cùng 1 file
        if (!file) return;
        setError('');
        if (!['image/png', 'image/jpeg'].includes(file.type)) return setError('Only PNG or JPG images.');
        if (file.size > MAX_AVATAR) return setError('Image must be 2MB or smaller.');

        const localUrl = URL.createObjectURL(file);
        setPreview(localUrl); // hiện ngay ảnh vừa chọn trong lúc upload
        setBusy(true);
        try {
            onChanged(await userApi.uploadAvatar(file));
        } catch (err) {
            setError(getErrorMessage(err, 'Could not upload image.'));
        } finally {
            setBusy(false);
            setPreview(null);
            URL.revokeObjectURL(localUrl);
        }
    };

    const remove = async () => {
        setError('');
        setBusy(true);
        try {
            onChanged(await userApi.deleteAvatar());
        } catch (err) {
            setError(getErrorMessage(err, 'Could not remove image.'));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="flex flex-col items-center">
            <div className="relative">
                <UserAvatar
                    size="lg"
                    name={profile.fullName}
                    hasAvatar={profile.hasAvatar}
                    version={profile.avatarUpdatedAt}
                    previewUrl={preview}
                />
                <button
                    type="button"
                    onClick={() => inputRef.current?.click()}
                    disabled={busy}
                    title="Change photo"
                    className="absolute bottom-0 right-0 w-9 h-9 rounded-full bg-primary text-on-primary border-4 border-surface-container-lowest flex items-center justify-center hover:bg-primary-container disabled:opacity-60"
                >
                    <Icon name={busy ? 'progress_activity' : 'photo_camera'} className={`text-[18px] ${busy ? 'animate-spin' : ''}`} />
                </button>
                <input ref={inputRef} type="file" accept="image/png,image/jpeg" onChange={pick} className="hidden" />
            </div>
            {profile.hasAvatar && !busy && (
                <button type="button" onClick={remove} className="mt-2 text-xs font-semibold text-error hover:underline">
                    Remove photo
                </button>
            )}
            {error && <p className="mt-2 text-xs text-error text-center">{error}</p>}
        </div>
    );
}

function SummaryCard({ profile, onChanged }) {
    const namedBeneficiary = useNamedBeneficiary(profile.roles.includes('BENEFICIARY'));
    const shownRoles = profile.roles.filter((r) => r !== 'BENEFICIARY' || namedBeneficiary);
    return (
        <Card>
            <div className="flex flex-col items-center text-center">
                <AvatarEditor profile={profile} onChanged={onChanged} />
                <h2 className="mt-4 font-title-lg text-title-lg break-all">{profile.fullName}</h2>
                <p className="text-sm text-on-surface-variant break-all">{profile.email}</p>
                <span className={`mt-3 text-xs font-semibold px-3 py-1 rounded-full ${STATUS_STYLE[profile.status] || 'bg-surface-container'}`}>
                    {profile.status}
                </span>
            </div>

            <div className="mt-6 pt-5 border-t border-outline-variant/30">
                <p className="text-xs font-semibold text-on-surface-variant mb-2">ROLES</p>
                <div className="flex flex-wrap gap-2">
                    {shownRoles.map((r) => (
                        <span key={r} className="text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                            {ROLE_LABEL[r] || r}
                        </span>
                    ))}
                </div>
            </div>

            <dl className="mt-5 pt-5 border-t border-outline-variant/30 space-y-3 text-sm">
                <div className="flex justify-between gap-3">
                    <dt className="text-on-surface-variant">Member since</dt>
                    <dd className="font-semibold">{formatDate(profile.createdAt)}</dd>
                </div>
                <div className="flex justify-between gap-3">
                    <dt className="text-on-surface-variant">Last sign-in</dt>
                    <dd className="font-semibold text-right">{formatDate(profile.lastLoginAt, true)}</dd>
                </div>
            </dl>
        </Card>
    );
}

// ---------- Form thông tin cá nhân ----------
function ProfileForm({ profile, onSaved }) {
    const [fullName, setFullName] = useState(profile.fullName || '');
    const [phone, setPhone] = useState(profile.phoneNumber || '');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const dirty = fullName.trim() !== (profile.fullName || '') || phone.trim() !== (profile.phoneNumber || '');

    const reset = () => {
        setFullName(profile.fullName || '');
        setPhone(profile.phoneNumber || '');
        setError('');
    };

    const submit = async (e) => {
        e.preventDefault();
        setError('');
        setSuccess('');
        const name = fullName.trim();
        if (name.length < 2 || name.length > 100) return setError('Full name must be 2–100 characters.');
        if (phone.trim() && !PHONE_RE.test(phone.trim())) return setError('Phone number is not valid.');

        setBusy(true);
        try {
            const updated = await userApi.updateMe(name, phone.trim());
            onSaved(updated);
            setSuccess('Profile updated.');
        } catch (err) {
            setError(getErrorMessage(err, 'Could not update profile.'));
        } finally {
            setBusy(false);
        }
    };

    return (
        <Card title="Personal information" icon="person">
            <Alert>{error}</Alert>
            <Alert type="success">{success}</Alert>
            <form onSubmit={submit} className="space-y-4">
                <div className="grid sm:grid-cols-2 gap-4">
                    <Field label="Full name">
                        <input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={100} className={inputClass} />
                    </Field>
                    <Field label="Phone number" hint="Optional">
                        <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="0901 234 567" className={inputClass} />
                    </Field>
                </div>
                <Field label="Email" hint="Email is your sign-in ID and cannot be changed.">
                    <input value={profile.email} disabled className={`${inputClass} opacity-60 cursor-not-allowed`} />
                </Field>
                <div className="flex justify-end gap-3 pt-2">
                    <button type="button" onClick={reset} disabled={!dirty || busy} className={btnOutline}>Cancel</button>
                    <button type="submit" disabled={!dirty || busy} className={btnPrimary}>
                        {busy ? 'Saving...' : 'Save changes'}
                    </button>
                </div>
            </form>
        </Card>
    );
}

// ---------- Form đổi mật khẩu ----------
function ChangePasswordForm() {
    const navigate = useNavigate();
    const [current, setCurrent] = useState('');
    const [next, setNext] = useState('');
    const [confirm, setConfirm] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const submit = async (e) => {
        e.preventDefault();
        setError('');
        if (!current || !next || !confirm) return setError('Please fill in all password fields.');
        if (!isPasswordValid(next)) return setError('New password does not meet the requirements.');
        if (next !== confirm) return setError('Password confirmation does not match.');
        if (next === current) return setError('New password must be different from the current one.');

        setBusy(true);
        try {
            await userApi.changePassword(current, next, confirm);
            // BE đã thu hồi mọi refresh token -> đăng xuất và đăng nhập lại bằng mật khẩu mới
            logout();
            navigate('/login', { replace: true, state: { message: 'Password changed. Please sign in with your new password.' } });
        } catch (err) {
            setError(getErrorMessage(err, 'Could not change password.'));
            setBusy(false);
        }
    };

    return (
        <Card title="Change password" icon="lock_reset">
            <Alert>{error}</Alert>
            <Alert type="info">After changing your password you will be signed out on every device.</Alert>
            <form onSubmit={submit} className="space-y-4">
                <Field label="Current password">
                    <PasswordInput value={current} onChange={setCurrent} autoComplete="current-password" />
                </Field>
                <div className="grid sm:grid-cols-2 gap-4">
                    <Field label="New password" hint="8–72 characters">
                        <PasswordInput value={next} onChange={setNext} autoComplete="new-password" />
                        <PasswordStrengthMeter password={next} />
                    </Field>
                    <Field label="Confirm new password">
                        <PasswordInput value={confirm} onChange={setConfirm} autoComplete="new-password" />
                    </Field>
                </div>
                <div className="flex justify-end pt-2">
                    <button type="submit" disabled={busy} className={btnPrimary}>
                        {busy ? 'Updating...' : 'Update password'}
                    </button>
                </div>
            </form>
        </Card>
    );
}

// ---------- Trang ----------
export default function ProfilePage() {
    const [profile, setProfile] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        userApi.getMe()
            .then((p) => {
                setProfile(p);
                updateStoredUser(toStoredUser(p));
            })
            .catch((e) => setError(getErrorMessage(e, 'Could not load your profile.')));
    }, []);

    const handleSaved = (p) => {
        setProfile(p);
        updateStoredUser(toStoredUser(p));
    };

    return (
        <div className="bg-background font-body-md text-on-surface antialiased min-h-screen">
            <AppHeader/>

            <main className={`${PAGE_WIDTH} py-10`}>
                <PageTitle back={{ to: '/home', label: 'Back to home' }} title="My profile" />

                <Alert>{error}</Alert>
                {!profile && !error && <p className="text-on-surface-variant">Loading...</p>}

                {profile && (
                    <div className="grid lg:grid-cols-3 gap-6 items-start">
                        <SummaryCard profile={profile} onChanged={handleSaved} />
                        <div className="lg:col-span-2 space-y-6">
                            <ProfileForm profile={profile} onSaved={handleSaved} />
                            <ChangePasswordForm />
                        </div>
                    </div>
                )}
            </main>
        </div>
    );
}
