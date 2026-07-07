import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react"
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

interface ConfirmOptions {
  title?: string
  description: string
  confirmLabel?: string
  cancelLabel?: string
  destructive?: boolean
}

type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>

const ConfirmContext = createContext<ConfirmFn | undefined>(undefined)

/**
 * Promise-based replacement for window.confirm() (which is unreliable inside
 * Capacitor WebViews): `const ok = await confirm({ description: ... })`.
 */
export function ConfirmDialogProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [options, setOptions] = useState<ConfirmOptions | null>(null)
  const resolverRef = useRef<((confirmed: boolean) => void) | null>(null)

  const confirm = useCallback<ConfirmFn>((opts) => {
    return new Promise<boolean>((resolve) => {
      resolverRef.current = resolve
      setOptions(opts)
    })
  }, [])

  const close = (confirmed: boolean) => {
    resolverRef.current?.(confirmed)
    resolverRef.current = null
    setOptions(null)
  }

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Dialog open={options !== null} onOpenChange={(open) => !open && close(false)}>
        {options && (
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{options.title ?? t("common.confirmTitle", { defaultValue: "Are you sure?" })}</DialogTitle>
              <DialogDescription>{options.description}</DialogDescription>
            </DialogHeader>
            <DialogFooter className="gap-2 sm:gap-0">
              <Button variant="outline" onClick={() => close(false)}>
                {options.cancelLabel ?? t("common.cancel", { defaultValue: "Cancel" })}
              </Button>
              <Button
                variant={options.destructive === false ? "default" : "destructive"}
                onClick={() => close(true)}
              >
                {options.confirmLabel ?? t("common.confirm", { defaultValue: "Confirm" })}
              </Button>
            </DialogFooter>
          </DialogContent>
        )}
      </Dialog>
    </ConfirmContext.Provider>
  )
}

export function useConfirm(): ConfirmFn {
  const confirm = useContext(ConfirmContext)
  if (confirm === undefined) {
    throw new Error("useConfirm must be used within a ConfirmDialogProvider")
  }
  return confirm
}
