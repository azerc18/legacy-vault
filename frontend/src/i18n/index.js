import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

// './locales/vi/owner.json' -> resources.vi.owner
const files = import.meta.glob('./locales/*/*.json', { eager: true, import: 'default' });
const resources = {};
for (const [path, data] of Object.entries(files)) {
    const [, lng, ns] = path.match(/\.\/locales\/(\w+)\/(\w+)\.json$/);
    (resources[lng] ??= {})[ns] = data;
}

export const LANGS = ['en', 'vi'];
export const LANG_KEY = 'lv_lang';

i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        resources,
        supportedLngs: LANGS,
        fallbackLng: 'en',
        load: 'languageOnly',            // vi-VN -> vi
        ns: Object.keys(resources.en),
        defaultNS: 'common',
        interpolation: { escapeValue: false }, // React đã tự escape
        detection: {
            order: ['localStorage', 'navigator'],
            lookupLocalStorage: LANG_KEY,
            caches: ['localStorage'],
        },
    });

const syncHtmlLang = (lng) => { document.documentElement.lang = lng; };
syncHtmlLang(i18n.resolvedLanguage);
i18n.on('languageChanged', syncHtmlLang);

export default i18n;