import { useTranslation } from "react-i18next"
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
import type { AccountResource, EnvelopeResource, InstrumentResource } from "@/api/types"
import type { useTransactionForm } from "@/hooks/use-transaction-form"
import { formatCurrency } from "@/lib/utils"
import { Plus, X } from "lucide-react"

interface TransactionEntryFormProps {
  form: ReturnType<typeof useTransactionForm>
  type: "DEBIT" | "CREDIT"
  accounts: AccountResource[]
  instruments: InstrumentResource[]
  envelopes: EnvelopeResource[]
}

/**
 * The bordered entry section of the transaction dialog for one entry type:
 * add button, per-entry account/amount/currency row with optional conversion,
 * instrument, and envelope rows, and the per-type total footer.
 */
export function TransactionEntryForm({
  form,
  type,
  accounts,
  instruments,
  envelopes,
}: TransactionEntryFormProps) {
  const { t } = useTranslation()
  const total = type === "DEBIT" ? form.debitTotal : form.creditTotal

  const getAccountCurrency = (accountId: string) => {
    const account = accounts.find(a => a.id === accountId)
    return account?.attributes.currency
  }

  return (
    <div className="border rounded-lg p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h4 className="font-medium">{type === "DEBIT" ? t("transactions.debitEntries") : t("transactions.creditEntries")}</h4>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => form.addEntry(type)}
        >
          <Plus className="h-4 w-4 mr-1" />
          {type === "DEBIT" ? t("transactions.addDebit") : t("transactions.addCredit")}
        </Button>
      </div>
      {form.entries
        .map((entry, index) => ({ entry, index }))
        .filter(({ entry }) => entry.type === type)
        .map(({ entry, index }) => {
          const accountCurrency = getAccountCurrency(entry.accountId)
          const needsConversion = accountCurrency && entry.currency !== accountCurrency

          return (
            <div key={index} className="space-y-2">
              <div className="flex items-center gap-2">
                <div className="flex-1">
                  <AccountAutocomplete
                    accounts={accounts}
                    value={entry.accountId}
                    onValueChange={(value) => form.updateEntry(index, "accountId", value)}
                    placeholder={t("transactions.selectAccount")}
                  />
                </div>
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  value={entry.amount}
                  onChange={(e) => form.updateEntry(index, "amount", e.target.value)}
                  onBlur={() => form.handleAmountBlur()}
                  placeholder="0.00"
                  className="w-28"
                />
                <Input
                  type="text"
                  value={entry.currency}
                  onChange={(e) => form.updateEntry(index, "currency", e.target.value.toUpperCase())}
                  placeholder="USD"
                  maxLength={3}
                  className="w-20 uppercase"
                />
                {form.entries.filter((e) => e.type === type).length > 1 && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    onClick={() => form.removeEntry(index)}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                )}
              </div>
              {needsConversion && (
                <div className="pl-4 space-y-2">
                  <div className="text-sm text-muted-foreground">
                    → {t("transactions.accountCurrency")}: {accountCurrency}
                  </div>
                  <div className="flex items-center gap-2">
                    <Label className="text-xs text-muted-foreground w-24">{t("transactions.convertedTo")}:</Label>
                    <Input
                      type="number"
                      step="0.01"
                      min="0"
                      value={entry.toAmount || ""}
                      onChange={(e) => form.updateEntry(index, "toAmount", e.target.value)}
                      placeholder={t("transactions.autoConversion")}
                      className="w-28 h-8 text-sm"
                    />
                    <Input
                      type="text"
                      value={entry.toCurrency || ""}
                      readOnly
                      placeholder={accountCurrency}
                      className="w-20 h-8 text-sm uppercase bg-muted"
                    />
                    <span className="text-xs text-muted-foreground">
                      ({t("transactions.manualConversion")})
                    </span>
                  </div>
                </div>
              )}
              {/* Instrument selection (optional) */}
              {instruments.length > 0 && (
                <div className="pl-4 space-y-2">
                  <div className="flex items-center gap-2">
                    <Label className="text-xs text-muted-foreground w-24">{t("transactions.instrument")}:</Label>
                    <Select
                      value={entry.instrumentId || "none"}
                      onValueChange={(value) => form.updateEntry(index, "instrumentId", value === "none" ? "" : value)}
                    >
                      <SelectTrigger className="w-40 h-8 text-sm">
                        <SelectValue placeholder={t("transactions.selectInstrument")} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="none">{t("common.none")}</SelectItem>
                        {instruments.map((instrument) => (
                          <SelectItem key={instrument.id} value={instrument.id}>
                            {instrument.attributes.symbol}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {entry.instrumentId && (
                      <Input
                        type="number"
                        step="0.00000001"
                        min="0"
                        value={entry.quantity || ""}
                        onChange={(e) => form.updateEntry(index, "quantity", e.target.value)}
                        placeholder={t("transactions.quantity")}
                        className="w-28 h-8 text-sm"
                      />
                    )}
                  </div>
                </div>
              )}
              {/* Envelope selection (optional) */}
              {envelopes.length > 0 && (
                <div className="pl-4 space-y-2">
                  <div className="flex items-center gap-2">
                    <Label className="text-xs text-muted-foreground w-24">{t("transactions.envelope")}:</Label>
                    <Select
                      value={entry.envelopeId || "none"}
                      onValueChange={(value) => form.updateEntry(index, "envelopeId", value === "none" ? "" : value)}
                    >
                      <SelectTrigger className="w-48 h-8 text-sm">
                        <SelectValue placeholder={t("transactions.selectEnvelope")} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="none">{t("common.none")}</SelectItem>
                        {envelopes.map((envelope) => (
                          <SelectItem key={envelope.id} value={envelope.id}>
                            {envelope.attributes.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      <div className="text-right text-sm font-medium">
        {t("transactions.total")}: {formatCurrency(total, form.ledgerCurrency)}
      </div>
    </div>
  )
}
