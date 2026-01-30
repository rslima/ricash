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
import type { TransactionResource } from "@/api/types"

interface DescriptionAutocompleteProps {
  templates: TransactionResource[]
  value: string
  onValueChange: (value: string) => void
  onTemplateSelect?: (template: TransactionResource) => void
  placeholder?: string
}

export function DescriptionAutocomplete({
  templates,
  value,
  onValueChange,
  onTemplateSelect,
  placeholder,
}: DescriptionAutocompleteProps) {
  const { t } = useTranslation()
  const [open, setOpen] = React.useState(false)
  const [inputValue, setInputValue] = React.useState(value)

  // Update input value when external value changes
  React.useEffect(() => {
    setInputValue(value)
  }, [value])

  // Build a map of descriptions to templates for quick lookup
  const templateMap = React.useMemo(() => {
    const map = new Map<string, TransactionResource>()
    templates.forEach((template) => {
      map.set(template.attributes.description, template)
    })
    return map
  }, [templates])

  // Get unique descriptions sorted alphabetically
  const uniqueDescriptions = React.useMemo(() => {
    return [...new Set(templates.map((t) => t.attributes.description))].sort((a, b) =>
      a.localeCompare(b)
    )
  }, [templates])

  // Filter suggestions based on input
  const filteredDescriptions = React.useMemo(() => {
    if (!inputValue) return uniqueDescriptions.slice(0, 10)
    const lower = inputValue.toLowerCase()
    return uniqueDescriptions
      .filter((s) => s.toLowerCase().includes(lower))
      .slice(0, 10)
  }, [uniqueDescriptions, inputValue])

  const effectivePlaceholder = placeholder || t("common.description")

  const handleSelect = (description: string) => {
    onValueChange(description)
    setInputValue(description)
    setOpen(false)

    // If there's a template for this description, call the callback
    const template = templateMap.get(description)
    if (template && onTemplateSelect) {
      onTemplateSelect(template)
    }
  }

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
              {filteredDescriptions.map((description) => (
                <CommandItem
                  key={description}
                  value={description}
                  onSelect={() => handleSelect(description)}
                >
                  <Check
                    className={cn(
                      "mr-2 h-4 w-4",
                      value === description ? "opacity-100" : "opacity-0"
                    )}
                  />
                  <span className="truncate">{description}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
