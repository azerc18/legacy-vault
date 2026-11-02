import { useTranslation } from 'react-i18next';
import { LANGS } from '../i18n/index.js';

export default function LanguageSwitch({ className = '' }) {
    const { t, i18n } = useTranslation();
    const current = i18n.resolvedLanguage;
    return (
        <div role="group" aria-label={t('language.label')}
             className={`h-10 flex items-center rounded-xl border border-outline-variant overflow-hidden text-sm font-semibold ${className}`}>
            {LANGS.map((lng) => (
                <button key={lng} type="button" onClick={() => i18n.changeLanguage(lng)}
                        aria-pressed={current === lng}
                        className={`h-full px-2.5 ${current === lng
                            ? 'bg-primary text-on-primary'
                            : 'text-on-surface-variant hover:bg-surface-container-low'}`}>
                    {t(`language.${lng}`)}
                </button>
            ))}
        </div>
    );
}