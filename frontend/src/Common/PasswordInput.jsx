// Ô mật khẩu có nút hiện/ẩn, dùng chung cho Login, Register, ResetPassword và Profile
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';

export default function PasswordInput({ id, name, value, onChange, autoComplete, placeholder, className = '' }) {
    const { t } = useTranslation('common');
    const [show, setShow] = useState(false);
    return (
        <div className="relative">
            <input
                id={id}
                name={name}
                type={show ? 'text' : 'password'}
                value={value}
                onChange={(e) => onChange(e.target.value)}
                autoComplete={autoComplete}
                placeholder={placeholder}
                className={`${className} pr-11`}
            />
            <button
                type="button"
                onClick={() => setShow((s) => !s)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-primary"
                aria-label={show ? t('password.hide') : t('password.show')}
                aria-pressed={show}
            >
                <span className="material-symbols-outlined text-[20px]">{show ? 'visibility_off' : 'visibility'}</span>
            </button>
        </div>
    );
}
