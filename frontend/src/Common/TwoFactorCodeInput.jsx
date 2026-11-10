// Ô nhập mã 2FA cho các màn mà BE nhận cả mã app (6 số) lẫn mã dự phòng (MfaServiceImpl.verifyCode):
// mặc định là 6 ô số, có nút "Dùng mã dự phòng" để chuyển sang ô nhập thường (giống màn Login).
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import OtpInput from './OtpInput.jsx';

export default function TwoFactorCodeInput({ value, onChange, label, id, inputClass = '' }) {
    const { t } = useTranslation('profile');
    const [useBackup, setUseBackup] = useState(false);

    return (
        <div className="space-y-2">
            {useBackup ? (
                <input
                    id={id}
                    autoFocus
                    aria-label={label}
                    value={value}
                    onChange={(e) => onChange(e.target.value.toUpperCase())}
                    autoComplete="one-time-code"
                    maxLength={9}
                    placeholder={t('mfa.backupPlaceholder')}
                    className={`${inputClass} text-center tracking-[0.2em]`}
                />
            ) : (
                <OtpInput id={id} label={label} value={value} onChange={onChange} />
            )}
            <button
                type="button"
                onClick={() => { setUseBackup(!useBackup); onChange(''); }}
                className="text-sm font-semibold text-secondary hover:text-primary"
            >
                {useBackup ? t('mfa.useApp') : t('mfa.useBackup')}
            </button>
        </div>
    );
}
