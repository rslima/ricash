import type { TFunction } from "i18next"

/** English month names as used by the budget.months.* i18n keys (index 0 = January). */
export const MONTH_KEYS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
] as const

/** Today's date as the YYYY-MM-DD string date inputs and the API expect. */
export function todayISO(): string {
  return new Date().toISOString().slice(0, 10)
}

/** Localized "Month YYYY" label for a 1-based month. */
export function getMonthLabel(t: TFunction, year: number, month: number): string {
  return `${t(`budget.months.${MONTH_KEYS[month - 1]}`)} ${year}`
}
