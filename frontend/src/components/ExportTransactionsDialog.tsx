import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Download } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
import { downloadTransactionsExport, type ExportFormat } from "@/api/exports"
import { useErrorHandler } from "@/hooks/use-error-handler"
import type { AccountResource } from "@/api/types"

interface ExportTransactionsDialogProps {
  ledgerSlug: string
  accounts: AccountResource[]
  /** Locks the export to this account (per-account transactions page). */
  fixedAccountId?: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

const OFX_ACCOUNT_TYPES = ["ASSET", "LIABILITY"]

export function ExportTransactionsDialog({
  ledgerSlug,
  accounts,
  fixedAccountId,
  open,
  onOpenChange,
}: ExportTransactionsDialogProps) {
  const { t } = useTranslation()
  const handleError = useErrorHandler()

  const [format, setFormat] = useState<ExportFormat>("csv")
  const [accountId, setAccountId] = useState(fixedAccountId ?? "")
  const [includeChildren, setIncludeChildren] = useState(false)
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")
  const [isExporting, setIsExporting] = useState(false)

  // Re-sync the locked account when the dialog opens on another account.
  useEffect(() => {
    if (open && fixedAccountId) setAccountId(fixedAccountId)
  }, [open, fixedAccountId])

  const effectiveAccountId = fixedAccountId ?? accountId
  const selectedAccount = useMemo(
    () => accounts.find((account) => account.id === effectiveAccountId),
    [accounts, effectiveAccountId]
  )

  const invalidRange = !!from && !!to && from > to
  const ofxUnsupportedAccount =
    format === "ofx" && !!selectedAccount && !OFX_ACCOUNT_TYPES.includes(selectedAccount.attributes.type)
  const canExport = !isExporting && !invalidRange && !ofxUnsupportedAccount

  const resetForm = () => {
    setFormat("csv")
    setAccountId(fixedAccountId ?? "")
    setIncludeChildren(false)
    setFrom("")
    setTo("")
  }

  const handleExport = async () => {
    setIsExporting(true)
    try {
      await downloadTransactionsExport(ledgerSlug, {
        format,
        accountId: effectiveAccountId || undefined,
        includeChildren,
        from: from || undefined,
        to: to || undefined,
      })
      onOpenChange(false)
      resetForm()
    } catch (error) {
      handleError(error, "exportFailed")
    } finally {
      setIsExporting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(isOpen) => {
      onOpenChange(isOpen)
      if (!isOpen) resetForm()
    }}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>{t("transactions.export.title")}</DialogTitle>
          <DialogDescription>
            {t("transactions.export.description")}
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-4">
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="exportFormat" className="text-right">
              {t("transactions.export.format")}
            </Label>
            <div className="col-span-3">
              <Select value={format} onValueChange={(value) => setFormat(value as ExportFormat)}>
                <SelectTrigger id="exportFormat" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="csv">{t("transactions.export.formatCsv")}</SelectItem>
                  <SelectItem value="ofx">{t("transactions.export.formatOfx")}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="grid grid-cols-4 items-center gap-4">
            <Label className="text-right">
              {t("transactions.export.account")}
            </Label>
            <div className="col-span-3">
              {fixedAccountId ? (
                <span className="text-sm font-medium">
                  {selectedAccount?.attributes.name ?? fixedAccountId}
                </span>
              ) : (
                <AccountAutocomplete
                  accounts={accounts}
                  value={accountId}
                  onValueChange={setAccountId}
                  allowNone
                  noneLabel={t("transactions.export.allAccounts")}
                />
              )}
            </div>
          </div>
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="exportFrom" className="text-right">
              {t("transactions.export.fromDate")}
            </Label>
            <Input
              id="exportFrom"
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              className="col-span-3"
            />
          </div>
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="exportTo" className="text-right">
              {t("transactions.export.toDate")}
            </Label>
            <Input
              id="exportTo"
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              className="col-span-3"
            />
          </div>
          <div className="grid grid-cols-4 items-center gap-4">
            <div />
            <div className="col-span-3 flex items-center gap-2">
              <Checkbox
                id="exportIncludeChildren"
                checked={includeChildren}
                disabled={!effectiveAccountId}
                onCheckedChange={(checked) => setIncludeChildren(checked === true)}
              />
              <Label htmlFor="exportIncludeChildren" className="font-normal">
                {t("transactions.export.includeSubAccounts")}
              </Label>
            </div>
          </div>
          {invalidRange && (
            <p className="text-sm text-destructive text-center">
              {t("transactions.export.invalidRange")}
            </p>
          )}
          {ofxUnsupportedAccount && (
            <p className="text-sm text-muted-foreground text-center">
              {t("transactions.export.ofxAccountHint")}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button onClick={handleExport} disabled={!canExport}>
            <Download className="mr-2 h-4 w-4" />
            {isExporting ? t("transactions.export.exporting") : t("transactions.export.export")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
