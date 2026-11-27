import type { TFunction } from "i18next"

/** English month names as used by the budget.months.* i18n keys (index 0 = January). */
export const MONTH_KEYS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
] as const

/**
 * Today's date in the user's time zone, as the YYYY-MM-DD string date inputs
 * and the API expect. Not `toISOString()`: that is the UTC date, which is
 * already tomorrow in the evening west of Greenwich.
 */
export function todayISO(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, "0")
  const day = String(now.getDate()).padStart(2, "0")
  return `${now.getFullYear()}-${month}-${day}`
}

/** Localized "Month YYYY" label for a 1-based month. */
export function getMonthLabel(t: TFunction, year: number, month: number): string {
  return `${t(`budget.months.${MONTH_KEYS[month - 1]}`)} ${year}`
}
