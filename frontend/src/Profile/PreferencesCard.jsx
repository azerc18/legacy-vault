// Tuỳ chọn cá nhân trên trình duyệt này: giao diện, ngôn ngữ giao diện, tự đăng xuất khi rảnh
// (ý từ Bitwarden Preferences: theme + vault timeout). Không lưu ở BE nên không cần migration.
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, Icon, inputClass } from '../Owner/ownerUi.jsx';
import { LANGS } from '../i18n/index.js';
import { getThemePreference, setThemePreference, useTheme } from '../Common/theme.js';
import { getIdleMinutes, setIdleMinutes, IDLE_OPTIONS } from '../Common/idleTimeout.js';

function Segmented({ label, options, value, onChange }) {
    return (
        <div role="radiogroup" aria-label={label} className="grid grid-flow-col auto-cols-fr p-1 rounded-xl bg-surface-container-low gap-1">
            {options.map((o) => (
                <button key={o.value} type="button" role="radio" aria-checked={value === o.value}
                        onClick={() => onChange(o.value)}
                        className={`h-10 px-3 rounded-lg text-sm font-semibold inline-flex items-center justify-center gap-1.5 transition-colors ${value === o.value
                            ? 'bg-surface-container-lowest text-on-surface shadow-sm'
                            : 'text-on-surface-variant hover:text-on-surface'}`}>
                    {o.icon && <Icon name={o.icon} className="text-[18px]" />}
                    <span>{o.label}</span>
                </button>
            ))}
        </div>
    );
}

function Row({ title, hint, children }) {
    return (
        <div className="grid sm:grid-cols-[1fr_minmax(0,1.3fr)] gap-2 sm:gap-6 sm:items-center py-4 first:pt-0 last:pb-0">
            <div>
                <p className="text-sm font-semibold">{title}</p>
                {hint && <p className="text-xs text-on-surface-variant mt-0.5">{hint}</p>}
            </div>
            {children}
        </div>
    );
}

export default function PreferencesCard() {
    const { t, i18n } = useTranslation('profile');
    useTheme();                                       // vẽ lại khi đổi nền ở nút trên header / tab khác
    const [, rerender] = useState(0);
    const themePref = getThemePreference();
    const [idle, setIdle] = useState(getIdleMinutes);

    const changeTheme = (v) => { setThemePreference(v); rerender((n) => n + 1); };
    const changeIdle = (v) => { setIdleMinutes(v); setIdle(v); };

    return (
        <Card title={t('prefs.title')} icon="tune">
            <p className="-mt-2 mb-4 text-sm text-on-surface-variant">{t('prefs.description')}</p>
            <div className="divide-y divide-outline-variant/30">
                <Row title={t('prefs.theme')}>
                    <Segmented label={t('prefs.theme')} value={themePref} onChange={changeTheme} options={[
                        { value: 'light', label: t('prefs.themeLight'), icon: 'light_mode' },
                        { value: 'dark', label: t('prefs.themeDark'), icon: 'dark_mode' },
                        { value: 'system', label: t('prefs.themeSystem'), icon: 'contrast' },
                    ]} />
                </Row>
                <Row title={t('prefs.uiLanguage')} hint={t('prefs.uiLanguageHint')}>
                    <Segmented label={t('prefs.uiLanguage')} value={i18n.resolvedLanguage} onChange={(v) => i18n.changeLanguage(v)}
                               options={LANGS.map((l) => ({ value: l, label: t(l === 'vi' ? 'info.languageVi' : 'info.languageEn') }))} />
                </Row>
                <Row title={t('prefs.idle')} hint={t('prefs.idleHint')}>
                    <select value={idle} onChange={(e) => changeIdle(Number(e.target.value))} className={inputClass} aria-label={t('prefs.idle')}>
                        {IDLE_OPTIONS.map((m) => (
                            <option key={m} value={m}>{m === 0 ? t('prefs.idleOff') : t('prefs.idleMinutes', { count: m })}</option>
                        ))}
                    </select>
                </Row>
            </div>
        </Card>
    );
}
