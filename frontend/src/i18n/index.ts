import i18n from "i18next"
import { initReactI18next } from "react-i18next"

import en from "./locales/en.json"
import ptBR from "./locales/pt-BR.json"

const resources = {
  en: { translation: en },
  "pt-BR": { translation: ptBR },
}

// Get saved language from localStorage or detect from browser
const savedLanguage = localStorage.getItem("language")
const browserLanguage = navigator.language
const defaultLanguage = savedLanguage || (browserLanguage.startsWith("pt") ? "pt-BR" : "en")

i18n.use(initReactI18next).init({
  resources,
  lng: defaultLanguage,
  fallbackLng: "en",
  interpolation: {
    escapeValue: false,
  },
})

export default i18n

export const languages = [
  { code: "en", name: "English" },
  { code: "pt-BR", name: "Português (Brasil)" },
]

export function changeLanguage(lang: string) {
  i18n.changeLanguage(lang)
  localStorage.setItem("language", lang)
}
