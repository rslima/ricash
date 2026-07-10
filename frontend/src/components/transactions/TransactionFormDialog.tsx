import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { DescriptionAutocomplete } from "@/components/DescriptionAutocomplete"
import { TransactionEntryForm } from "@/components/transactions/TransactionEntryForm"
import type {
  AccountResource,
  EnvelopeResource,
  InstrumentResource,
  TransactionResource,
} from "@/api/types"
import type { useTransactionForm } from "@/hooks/use-transaction-form"
import { formatCurrency } from "@/lib/utils"

interface TransactionFormDialogProps {
  mode: "create" | "edit"
  open: boolean
  onOpenChange: (open: boolean) => void
  form: ReturnType<typeof useTransactionForm>
  accounts: AccountResource[]
  instruments: InstrumentResource[]
  envelopes: EnvelopeResource[]
  templates: TransactionResource[]
  onSubmit: () => void
  isPending: boolean
}

/**
 * The create/edit transaction dialog: date and description inputs (with
 * template autocomplete), the CREDIT and DEBIT entry sections, the
 * not-balanced warning, and the mode-keyed submit button. Form state lives in
 * the useTransactionForm instance the page passes in; submission stays in the
 * page's mutation handlers.
 */
export function TransactionFormDialog({
  mode,
  open,
  onOpenChange,
  form,
  accounts,
  instruments,
  envelopes,
  templates,
  onSubmit,
  isPending,
}: TransactionFormDialogProps) {
  const { t } = useTranslation()
  const isEdit = mode === "edit"

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[700px] lg:max-w-[900px] flex flex-col md:max-h-[90vh]">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? t("transactions.editTransaction") : t("transactions.createTransaction")}
          </DialogTitle>
          <DialogDescription>
            {isEdit ? t("transactions.editDescription") : t("transactions.createDescription")}
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-4 flex-1 min-h-0 overflow-y-auto">
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor={`${mode}-date`} className="text-right">{t("common.date")}</Label>
            <Input
              id={`${mode}-date`}
              type="date"
              value={form.date}
              onChange={(e) => form.setDate(e.target.value)}
              className="col-span-3"
            />
          </div>
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor={`${mode}-description`} className="text-right">{t("common.description")}</Label>
            <div className="col-span-3">
              <DescriptionAutocomplete
                templates={templates}
                value={form.description}
                onValueChange={form.setDescription}
                onTemplateSelect={form.loadFromTemplate}
                placeholder={t("common.description")}
              />
            </div>
          </div>
          <TransactionEntryForm
            form={form}
            type="CREDIT"
            accounts={accounts}
            instruments={instruments}
            envelopes={envelopes}
          />
          <TransactionEntryForm
            form={form}
            type="DEBIT"
            accounts={accounts}
            instruments={instruments}
            envelopes={envelopes}
          />
          {!form.balanced && form.debitTotal > 0 && form.creditTotal > 0 && (
            <p className="text-sm text-destructive text-center">
              {t("transactions.notBalanced", {
                debits: formatCurrency(form.debitTotal, form.ledgerCurrency),
                credits: formatCurrency(form.creditTotal, form.ledgerCurrency),
              })}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button
            type="submit"
            onClick={onSubmit}
            disabled={!form.isValid || isPending}
          >
            {isEdit
              ? (isPending ? t("transactions.saving") : t("common.save"))
              : (isPending ? t("transactions.creating") : t("transactions.createTransaction"))}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
