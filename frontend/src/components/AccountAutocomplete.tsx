import * as React from "react"
import { useTranslation } from "react-i18next"
import { Check, ChevronsUpDown, ChevronRight } from "lucide-react"

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
import type { AccountResource } from "@/api/types"

interface AccountAutocompleteProps {
  accounts: AccountResource[]
  value: string
  onValueChange: (value: string) => void
  placeholder?: string
  allowNone?: boolean
  noneLabel?: string
}

const ACCOUNT_TYPE_ORDER: Record<string, number> = {
  ASSET: 0,
  LIABILITY: 1,
  EQUITY: 2,
  INCOME: 3,
  EXPENSE: 4,
}

// Build a map of account ID to its breadcrumb path
function buildAccountBreadcrumbs(accounts: AccountResource[]): Map<string, string[]> {
  const breadcrumbs = new Map<string, string[]>()
  const accountMap = new Map<string, AccountResource>()

  // Build account map for quick lookup
  accounts.forEach((account) => {
    accountMap.set(account.id, account)
  })

  // Build breadcrumb for each account
  function getBreadcrumb(accountId: string): string[] {
    if (breadcrumbs.has(accountId)) {
      return breadcrumbs.get(accountId)!
    }

    const account = accountMap.get(accountId)
    if (!account) return []

    const parentId = account.attributes.parentAccountId
    if (parentId && accountMap.has(parentId)) {
      const parentBreadcrumb = getBreadcrumb(parentId)
      const breadcrumb = [...parentBreadcrumb, account.attributes.name]
      breadcrumbs.set(accountId, breadcrumb)
      return breadcrumb
    }

    const breadcrumb = [account.attributes.name]
    breadcrumbs.set(accountId, breadcrumb)
    return breadcrumb
  }

  accounts.forEach((account) => {
    getBreadcrumb(account.id)
  })

  return breadcrumbs
}

function AccountBreadcrumb({ parts }: { parts: string[] }) {
  return (
    <span className="flex items-center gap-1 text-sm">
      {parts.map((part, index) => (
        <React.Fragment key={index}>
          {index > 0 && <ChevronRight className="h-3 w-3 text-muted-foreground" />}
          <span className={index === parts.length - 1 ? "font-medium" : "text-muted-foreground"}>
            {part}
          </span>
        </React.Fragment>
      ))}
    </span>
  )
}

export function AccountAutocomplete({
  accounts,
  value,
  onValueChange,
  placeholder,
  allowNone = false,
  noneLabel,
}: AccountAutocompleteProps) {
  const { t } = useTranslation()
  const [open, setOpen] = React.useState(false)

  const breadcrumbs = React.useMemo(() => buildAccountBreadcrumbs(accounts), [accounts])

  const selectedAccount = accounts.find((account) => account.id === value)
  const selectedBreadcrumb = value ? breadcrumbs.get(value) : null
  const effectiveNoneLabel = noneLabel || t("common.none")

  // Sort accounts by type first, then by breadcrumb path within each type
  const sortedAccounts = React.useMemo(() => {
    return [...accounts].sort((a, b) => {
      // First sort by account type
      const typeOrderA = ACCOUNT_TYPE_ORDER[a.attributes.type] ?? 99
      const typeOrderB = ACCOUNT_TYPE_ORDER[b.attributes.type] ?? 99
      if (typeOrderA !== typeOrderB) {
        return typeOrderA - typeOrderB
      }
      // Then sort by breadcrumb path within the same type
      const pathA = breadcrumbs.get(a.id)?.join(" > ") || ""
      const pathB = breadcrumbs.get(b.id)?.join(" > ") || ""
      return pathA.localeCompare(pathB)
    })
  }, [accounts, breadcrumbs])

  const effectivePlaceholder = placeholder || t("transactions.selectAccount")

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between font-normal"
        >
          {selectedAccount && selectedBreadcrumb ? (
            <AccountBreadcrumb parts={selectedBreadcrumb} />
          ) : allowNone && !value ? (
            <span>{effectiveNoneLabel}</span>
          ) : (
            <span className="text-muted-foreground">{effectivePlaceholder}</span>
          )}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[400px] p-0" align="start">
        <Command>
          <CommandInput placeholder={t("transactions.searchAccounts")} />
          <CommandList>
            <CommandEmpty>{t("common.noResults")}</CommandEmpty>
            <CommandGroup>
              {allowNone && (
                <CommandItem
                  value={effectiveNoneLabel}
                  onSelect={() => {
                    onValueChange("")
                    setOpen(false)
                  }}
                >
                  <Check
                    className={cn(
                      "mr-2 h-4 w-4",
                      !value ? "opacity-100" : "opacity-0"
                    )}
                  />
                  <span>{effectiveNoneLabel}</span>
                </CommandItem>
              )}
              {sortedAccounts.map((account) => {
                const accountBreadcrumb = breadcrumbs.get(account.id) || [account.attributes.name]
                return (
                  <CommandItem
                    key={account.id}
                    value={accountBreadcrumb.join(" > ")}
                    onSelect={() => {
                      onValueChange(account.id)
                      setOpen(false)
                    }}
                  >
                    <Check
                      className={cn(
                        "mr-2 h-4 w-4",
                        value === account.id ? "opacity-100" : "opacity-0"
                      )}
                    />
                    <AccountBreadcrumb parts={accountBreadcrumb} />
                  </CommandItem>
                )
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
