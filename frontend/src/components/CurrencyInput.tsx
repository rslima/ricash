import type { ComponentProps } from "react"
import { Input } from "@/components/ui/input"
import { cn } from "@/lib/utils"

type CurrencyInputProps = Omit<ComponentProps<typeof Input>, "value" | "onChange" | "type" | "maxLength"> & {
  value: string
  /** Receives the code already uppercased and without whitespace. */
  onValueChange: (currency: string) => void
}

/**
 * Text field for an ISO 4217 currency code. The API accepts only three
 * uppercase letters, so every form edits codes through this one component
 * instead of normalizing (or forgetting to) on its own.
 */
export function CurrencyInput({ value, onValueChange, className, ...props }: CurrencyInputProps) {
  return (
    <Input
      type="text"
      value={value}
      onChange={(e) => onValueChange(e.target.value.replace(/\s/g, "").toUpperCase())}
      maxLength={3}
      autoCapitalize="characters"
      autoComplete="off"
      autoCorrect="off"
      spellCheck={false}
      className={cn("uppercase", className)}
      {...props}
    />
  )
}
