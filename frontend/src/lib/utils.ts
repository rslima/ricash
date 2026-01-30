import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"
import slugifyLib from "slugify"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function slugify(input: string): string {
  return slugifyLib(input, { lower: true, strict: true })
}

export function formatCurrency(amount: number, currency: string = "BRL"): string {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency,
  }).format(amount)
}

export function formatDate(date: string | Date): string {
  let dateObj: Date
  if (typeof date === "string") {
    // Parse date string as local time to avoid timezone issues
    // "2025-01-30" should be Jan 30 in local time, not UTC
    const [year, month, day] = date.split("T")[0].split("-").map(Number)
    dateObj = new Date(year, month - 1, day)
  } else {
    dateObj = date
  }
  return new Intl.DateTimeFormat("pt-BR", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(dateObj)
}
