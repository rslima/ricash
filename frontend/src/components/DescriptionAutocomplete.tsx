import * as React from "react"
import { useTranslation } from "react-i18next"
import { Check, ChevronsUpDown } from "lucide-react"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"

interface DescriptionAutocompleteProps {
  suggestions: string[]
  value: string
  onValueChange: (value: string) => void
  placeholder?: string
}

export function DescriptionAutocomplete({
  suggestions,
  value,
  onValueChange,
  placeholder,
}: DescriptionAutocompleteProps) {
  const { t } = useTranslation()
  const [open, setOpen] = React.useState(false)
  const [inputValue, setInputValue] = React.useState(value)

  // Update input value when external value changes
  React.useEffect(() => {
    setInputValue(value)
  }, [value])

  // Get unique suggestions, sorted by frequency (most common first)
  const uniqueSuggestions = React.useMemo(() => {
    const counts = new Map<string, number>()
    suggestions.forEach((s) => {
      counts.set(s, (counts.get(s) || 0) + 1)
    })
    return [...new Set(suggestions)].sort((a, b) => {
      const countDiff = (counts.get(b) || 0) - (counts.get(a) || 0)
      if (countDiff !== 0) return countDiff
      return a.localeCompare(b)
    })
  }, [suggestions])

  // Filter suggestions based on input
  const filteredSuggestions = React.useMemo(() => {
    if (!inputValue) return uniqueSuggestions.slice(0, 10)
    const lower = inputValue.toLowerCase()
    return uniqueSuggestions
      .filter((s) => s.toLowerCase().includes(lower))
      .slice(0, 10)
  }, [uniqueSuggestions, inputValue])

  const effectivePlaceholder = placeholder || t("common.description")

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between font-normal"
        >
          {value ? (
            <span className="truncate">{value}</span>
          ) : (
            <span className="text-muted-foreground">{effectivePlaceholder}</span>
          )}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[400px] p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput
            placeholder={effectivePlaceholder}
            value={inputValue}
            onValueChange={(newValue) => {
              setInputValue(newValue)
              onValueChange(newValue)
            }}
          />
          <CommandList>
            <CommandEmpty>{t("common.noResults")}</CommandEmpty>
            <CommandGroup>
              {filteredSuggestions.map((suggestion) => (
                <CommandItem
                  key={suggestion}
                  value={suggestion}
                  onSelect={() => {
                    onValueChange(suggestion)
                    setInputValue(suggestion)
                    setOpen(false)
                  }}
                >
                  <Check
                    className={cn(
                      "mr-2 h-4 w-4",
                      value === suggestion ? "opacity-100" : "opacity-0"
                    )}
                  />
                  <span className="truncate">{suggestion}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
