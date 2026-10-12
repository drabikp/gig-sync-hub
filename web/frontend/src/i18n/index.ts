import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { en } from './en';
import { sk } from './sk';

export const LANGUAGES = [
  { code: 'en', name: 'English' },
  { code: 'sk', name: 'Slovenčina' },
] as const;

function initialLanguage(): string {
  try {
    const saved = localStorage.getItem('lang');
    if (saved === 'en' || saved === 'sk') return saved;
  } catch {
    // no storage (private mode): the browser's language decides
  }
  const browser = (navigator.language || 'en').toLowerCase();
  return browser.startsWith('sk') || browser.startsWith('cs') ? 'sk' : 'en';
}

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, sk: { translation: sk } },
  lng: initialLanguage(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  returnNull: false,
});

document.documentElement.lang = i18n.language;

/** Switches the language and remembers it on this device. */
export function setLanguage(code: string) {
  i18n.changeLanguage(code);
  document.documentElement.lang = code;
  try {
    localStorage.setItem('lang', code);
  } catch {
    // not remembered; fine
  }
}

export default i18n;
