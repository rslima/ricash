import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { MONTH_KEYS } from "@/lib/dates"

interface MonthYearPickerProps {
  /** 1-based month. */
  month: number
  year: number
  onMonthChange: (month: number) => void
  onYearChange: (year: number) => void
}

/** Month + year Select pair for report/breakdown period selection. */
export function MonthYearPicker({ month, year, onMonthChange, onYearChange }: MonthYearPickerProps) {
  const { t } = useTranslation()

  // Five years back through next year, always including the selected year.
  const yearOptions = useMemo(() => {
    const current = new Date().getFullYear()
    const years = new Set<number>()
    for (let y = current - 5; y <= current + 1; y++) years.add(y)
    years.add(year)
    return [...years].sort((a, b) => a - b)
  }, [year])

  return (
    <div className="flex items-center gap-2">
      <Select value={month.toString()} onValueChange={(v) => onMonthChange(parseInt(v))}>
        <SelectTrigger className="h-9 w-36">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {MONTH_KEYS.map((m, index) => (
            <SelectItem key={m} value={(index + 1).toString()}>
              {t(`budget.months.${m}`)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select value={year.toString()} onValueChange={(v) => onYearChange(parseInt(v))}>
        <SelectTrigger className="h-9 w-24">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {yearOptions.map((y) => (
            <SelectItem key={y} value={y.toString()}>
              {y}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
