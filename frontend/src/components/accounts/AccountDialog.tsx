import type { FormEvent } from "react"
import { useTranslation } from "react-i18next"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { AccountAutocomplete } from "@/components/AccountAutocomplete"
import type { AccountResource, EnvelopeResource } from "@/api/types"

export type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE"

export const ACCOUNT_TYPES: AccountType[] = ["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"]

export interface AccountFormValues {
  name: string
  description: string
  currency: string
  type: AccountType
  parentAccountId: string
  envelopeId: string
}

interface AccountDialogProps {
  mode: "create" | "edit"
  open: boolean
  onOpenChange: (open: boolean) => void
  value: AccountFormValues
  onChange: (value: AccountFormValues) => void
  onSubmit: (e: FormEvent) => void
  isSubmitting: boolean
  /** Full account list, used to inherit type/currency from a chosen parent. */
  accounts: AccountResource[]
  /** Selectable parent accounts (excludes self and descendants in edit mode). */
  parentOptions: AccountResource[]
  envelopes: EnvelopeResource[]
}

/** Create/edit account dialog: same form, mode picks labels and behavior. */
export function AccountDialog({
  mode,
  open,
  onOpenChange,
  value,
  onChange,
  onSubmit,
  isSubmitting,
  accounts,
  parentOptions,
  envelopes,
}: AccountDialogProps) {
  const { t } = useTranslation()
  const idPrefix = mode === "edit" ? "edit-" : ""

  const handleParentChange = (parentAccountId: string) => {
    if (parentAccountId) {
      const parentAccount = accounts.find((a) => a.id === parentAccountId)
      if (parentAccount) {
        onChange({
          ...value,
          parentAccountId,
          type: parentAccount.attributes.type as AccountType,
          currency: parentAccount.attributes.currency,
        })
      }
    } else {
      onChange({ ...value, parentAccountId: "" })
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {mode === "create" ? t("accounts.createAccount") : t("accounts.editAccount")}
          </DialogTitle>
          <DialogDescription>
            {mode === "create" ? t("accounts.createDescription") : t("accounts.editDescription")}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}name`}>{t("common.name")}</Label>
              <Input
                id={`${idPrefix}name`}
                value={value.name}
                onChange={(e) => onChange({ ...value, name: e.target.value })}
                placeholder="Checking Account"
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}type`}>{t("common.type")}</Label>
              <Select
                value={value.type}
                onValueChange={(type: AccountType) => onChange({ ...value, type })}
                disabled={!!value.parentAccountId}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("common.type")} />
                </SelectTrigger>
                <SelectContent>
                  {ACCOUNT_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>{t(`accounts.types.${type}`)}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}parentAccount`}>
                {t("accounts.parentAccount")} ({t("common.optional")})
              </Label>
              <AccountAutocomplete
                accounts={parentOptions}
                value={value.parentAccountId}
                onValueChange={handleParentChange}
                placeholder={t("accounts.parentAccount")}
                allowNone
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}currency`}>{t("common.currency")}</Label>
              <Input
                id={`${idPrefix}currency`}
                value={value.currency}
                onChange={(e) => onChange({ ...value, currency: e.target.value })}
                placeholder="USD"
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}description`}>
                {t("common.description")} ({t("common.optional")})
              </Label>
              <Input
                id={`${idPrefix}description`}
                value={value.description}
                onChange={(e) => onChange({ ...value, description: e.target.value })}
                placeholder="Main checking account for daily expenses"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}envelope`}>
                {t("transactions.envelope")} ({t("common.optional")})
              </Label>
              <Select
                value={value.envelopeId || "none"}
                onValueChange={(envelopeId) =>
                  onChange({ ...value, envelopeId: envelopeId === "none" ? "" : envelopeId })
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("transactions.selectEnvelope")} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">{t("common.none")}</SelectItem>
                  {envelopes.map((envelope) => (
                    <SelectItem key={envelope.id} value={envelope.id}>
                      {envelope.attributes.name} ({t(`envelopes.types.${envelope.attributes.type}`)})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {mode === "create"
                ? isSubmitting ? t("accounts.creating") : t("accounts.createAccount")
                : isSubmitting ? t("accounts.saving") : t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
