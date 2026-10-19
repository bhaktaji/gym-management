import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'
import hi from './hi.json'

export const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'hi', label: 'हिन्दी' },
] as const

export type LanguageCode = (typeof LANGUAGES)[number]['code']

const STORAGE_KEY = 'gymkhata.lang'

function storedLanguage(): LanguageCode {
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw === 'hi' ? 'hi' : 'en'
}

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    hi: { translation: hi },
  },
  lng: storedLanguage(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

export function setLanguage(code: LanguageCode) {
  localStorage.setItem(STORAGE_KEY, code)
  void i18n.changeLanguage(code)
  document.documentElement.lang = code
}

export function currentLanguage(): LanguageCode {
  return (i18n.language === 'hi' ? 'hi' : 'en') as LanguageCode
}

export default i18n
