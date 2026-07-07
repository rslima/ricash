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
import type { EnvelopeResource, EnvelopeStatus, EnvelopeType } from "@/api/types"

export interface EnvelopeFormValues {
  name: string
  description: string
  currency: string
  type: EnvelopeType
  parentEnvelopeId: string
  /** Only editable in edit mode. */
  status?: EnvelopeStatus
}

interface EnvelopeDialogProps {
  mode: "create" | "edit"
  open: boolean
  onOpenChange: (open: boolean) => void
  value: EnvelopeFormValues
  onChange: (value: EnvelopeFormValues) => void
  onSubmit: (e: FormEvent) => void
  isSubmitting: boolean
  /** Full envelope list, used to inherit type/currency from a chosen parent. */
  envelopes: EnvelopeResource[]
  /** Selectable parent envelopes (excludes self and descendants in edit mode). */
  parentOptions: EnvelopeResource[]
}

/** Create/edit envelope dialog: same form, mode picks labels and the status field. */
export function EnvelopeDialog({
  mode,
  open,
  onOpenChange,
  value,
  onChange,
  onSubmit,
  isSubmitting,
  envelopes,
  parentOptions,
}: EnvelopeDialogProps) {
  const { t } = useTranslation()
  const idPrefix = mode === "edit" ? "edit-" : ""

  const handleParentChange = (parentEnvelopeId: string) => {
    if (parentEnvelopeId === "none") {
      onChange({ ...value, parentEnvelopeId: "" })
    } else {
      const parentEnvelope = envelopes.find((e) => e.id === parentEnvelopeId)
      if (parentEnvelope) {
        onChange({
          ...value,
          parentEnvelopeId,
          type: parentEnvelope.attributes.type,
          currency: parentEnvelope.attributes.currency,
        })
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {mode === "create" ? t("envelopes.createEnvelope") : t("envelopes.editEnvelope")}
          </DialogTitle>
          <DialogDescription>
            {mode === "create" ? t("envelopes.createDescription") : t("envelopes.editDescription")}
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
                placeholder={mode === "create" ? t("envelopes.namePlaceholder") : undefined}
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}type`}>{t("common.type")}</Label>
              <Select
                value={value.type}
                onValueChange={(type: EnvelopeType) => onChange({ ...value, type })}
                disabled={!!value.parentEnvelopeId}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("common.type")} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="EXPENSE">{t("envelopes.types.EXPENSE")}</SelectItem>
                  <SelectItem value="INCOME">{t("envelopes.types.INCOME")}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {mode === "edit" && (
              <div className="grid gap-2">
                <Label htmlFor="edit-status">{t("common.status")}</Label>
                <Select
                  value={value.status}
                  onValueChange={(status: EnvelopeStatus) => onChange({ ...value, status })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("common.status")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ACTIVE">{t("envelopes.statuses.ACTIVE")}</SelectItem>
                    <SelectItem value="ARCHIVED">{t("envelopes.statuses.ARCHIVED")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            )}
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}parentEnvelope`}>
                {t("envelopes.parentEnvelope")} ({t("common.optional")})
              </Label>
              <Select value={value.parentEnvelopeId || "none"} onValueChange={handleParentChange}>
                <SelectTrigger>
                  <SelectValue placeholder={t("envelopes.parentEnvelope")} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">{t("common.none")}</SelectItem>
                  {parentOptions.map((envelope) => (
                    <SelectItem key={envelope.id} value={envelope.id}>
                      {envelope.attributes.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor={`${idPrefix}currency`}>{t("common.currency")}</Label>
              <Input
                id={`${idPrefix}currency`}
                value={value.currency}
                onChange={(e) => onChange({ ...value, currency: e.target.value })}
                placeholder={mode === "create" ? "BRL" : undefined}
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
                placeholder={mode === "create" ? t("envelopes.descriptionPlaceholder") : undefined}
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {mode === "create"
                ? isSubmitting ? t("envelopes.creating") : t("envelopes.createEnvelope")
                : isSubmitting ? t("envelopes.saving") : t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
