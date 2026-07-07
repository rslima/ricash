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
import { DescriptionAutocomplete } from "@/components/DescriptionAutocomplete"
import { EntrySection } from "@/components/transactions/EntrySection"
import type { AccountResource, EnvelopeResource, InstrumentResource, TransactionResource } from "@/api/types"
import {
  calculateTotal,
  isBalanced,
  isTransactionFormValid,
  type TransactionEntryFormValue,
} from "@/hooks/useTransactionEntries"
import { formatCurrency } from "@/lib/utils"

interface TransactionDialogProps {
  mode: "create" | "edit"
  open: boolean
  onOpenChange: (open: boolean) => void
  date: string
  onDateChange: (date: string) => void
  description: string
  onDescriptionChange: (description: string) => void
  entries: TransactionEntryFormValue[]
  onAddEntry: (type: "DEBIT" | "CREDIT") => void
  onRemoveEntry: (index: number) => void
  onUpdateEntry: (index: number, field: keyof TransactionEntryFormValue, value: string) => void
  onAmountBlur: () => void
  onTemplateSelect: (template: TransactionResource) => void
  onSubmit: () => void
  isSubmitting: boolean
  accounts: AccountResource[]
  instruments: InstrumentResource[]
  envelopes: EnvelopeResource[]
  templates: TransactionResource[]
  currency: string
}

/** Create/edit transaction dialog: date, description and the double-entry form. */
export function TransactionDialog({
  mode,
  open,
  onOpenChange,
  date,
  onDateChange,
  description,
  onDescriptionChange,
  entries,
  onAddEntry,
  onRemoveEntry,
  onUpdateEntry,
  onAmountBlur,
  onTemplateSelect,
  onSubmit,
  isSubmitting,
  accounts,
  instruments,
  envelopes,
  templates,
  currency,
}: TransactionDialogProps) {
  const { t } = useTranslation()

  const debitTotal = calculateTotal("DEBIT", entries)
  const creditTotal = calculateTotal("CREDIT", entries)
  const idPrefix = mode === "edit" ? "edit-" : ""

  const sectionProps = {
    entries,
    currency,
    accounts,
    instruments,
    envelopes,
    onAdd: onAddEntry,
    onRemove: onRemoveEntry,
    onUpdate: onUpdateEntry,
    onAmountBlur,
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[700px] lg:max-w-[900px] flex flex-col md:max-h-[90vh]">
        <DialogHeader>
          <DialogTitle>
            {mode === "create" ? t("transactions.createTransaction") : t("transactions.editTransaction")}
          </DialogTitle>
          <DialogDescription>
            {mode === "create" ? t("transactions.createDescription") : t("transactions.editDescription")}
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-4 flex-1 min-h-0 overflow-y-auto">
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor={`${idPrefix}date`} className="text-right">{t("common.date")}</Label>
            <Input
              id={`${idPrefix}date`}
              type="date"
              value={date}
              onChange={(e) => onDateChange(e.target.value)}
              className="col-span-3"
            />
          </div>
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor={`${idPrefix}description`} className="text-right">{t("common.description")}</Label>
            <div className="col-span-3">
              <DescriptionAutocomplete
                templates={templates}
                value={description}
                onValueChange={onDescriptionChange}
                onTemplateSelect={onTemplateSelect}
                placeholder={t("common.description")}
              />
            </div>
          </div>
          <EntrySection {...sectionProps} type="CREDIT" total={creditTotal} />
          <EntrySection {...sectionProps} type="DEBIT" total={debitTotal} />
          {!isBalanced(entries) && debitTotal > 0 && creditTotal > 0 && (
            <p className="text-sm text-destructive text-center">
              {t("transactions.notBalanced", {
                debits: formatCurrency(debitTotal, currency),
                credits: formatCurrency(creditTotal, currency),
              })}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button
            type="submit"
            onClick={onSubmit}
            disabled={!isTransactionFormValid(entries, date, description) || isSubmitting}
          >
            {mode === "create"
              ? isSubmitting ? t("transactions.creating") : t("transactions.createTransaction")
              : isSubmitting ? t("transactions.saving") : t("common.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
