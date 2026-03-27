import { Toaster as Sonner } from "sonner"

function Toaster() {
  return (
    <Sonner
      position="bottom-right"
      toastOptions={{
        classNames: {
          error: "bg-destructive text-destructive-foreground",
        },
      }}
    />
  )
}

export { Toaster }
