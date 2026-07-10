import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"
import slugifyLib from "slugify"
import i18n from "@/i18n"

// Maps the app language to an Intl locale for number/date formatting.
// Defaults to pt-BR (the app's original hardcoded locale) when i18n isn't
// initialized or reports an unknown language.
const INTL_LOCALES: Record<string, string> = {
  en: "en-US",
  "pt-BR": "pt-BR",
}

function currentLocale(): string {
  return INTL_LOCALES[i18n.language] ?? "pt-BR"
}

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function slugify(input: string): string {
  return slugifyLib(input, { lower: true, strict: true })
}

// Fallback currency when a ledger/account currency isn't available yet.
export const DEFAULT_CURRENCY = "BRL"

export function formatCurrency(amount: number, currency: string = DEFAULT_CURRENCY): string {
  return new Intl.NumberFormat(currentLocale(), {
    style: "currency",
    currency,
  }).format(amount)
}

export function formatDate(date: string | Date): string {
  let dateObj: Date
  if (typeof date === "string") {
    // Parse date string as local time to avoid timezone issues
    // "2025-01-30" should be Jan 30 in local time, not UTC
    const [datePart = date] = date.split("T")
    const [year = 0, month = 1, day = 1] = datePart.split("-").map(Number)
    dateObj = new Date(year, month - 1, day)
  } else {
    dateObj = date
  }
  return new Intl.DateTimeFormat(currentLocale(), {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(dateObj)
}
