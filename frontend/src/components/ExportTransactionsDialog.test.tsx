import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { screen, waitFor, fireEvent } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { ExportTransactionsDialog } from "./ExportTransactionsDialog"
import { apiClient } from "@/api/client"
import { toast } from "sonner"
import { renderWithProviders } from "@/test/test-utils"
import { makeAccount } from "@/test/fixtures"

// Partial-mock the API client: only getBlob is stubbed, ApiError stays real so
// the error-handler's instanceof checks keep working.
vi.mock("@/api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/api/client")>()
  return {
    ...actual,
    apiClient: { getBlob: vi.fn() },
  }
})

// The dialog surfaces failures via the shared error handler, which toasts.
vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}))

const assetAccount = makeAccount()
const expenseAccount = makeAccount({
  id: "account-2",
  attributes: { slug: "groceries", name: "Groceries", type: "EXPENSE", balance: 0 },
})
const accounts = [assetAccount, expenseAccount]

const getBlobMock = vi.mocked(apiClient.getBlob)

function mockBlobSuccess(filename: string | null = "personal-finance-transactions-all.csv") {
  const blob = new Blob(["csv-content"], { type: "text/csv" })
  getBlobMock.mockResolvedValueOnce({ blob, filename })
  return blob
}

describe("ExportTransactionsDialog", () => {
  const originalCreateObjectURL = URL.createObjectURL
  const originalRevokeObjectURL = URL.revokeObjectURL
  let clickSpy: ReturnType<typeof vi.spyOn>
  let onOpenChange: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.clearAllMocks()
    // Drop any once-queued getBlob results a failed test may have left behind.
    getBlobMock.mockReset()
    URL.createObjectURL = vi.fn(() => "blob:mock-url")
    URL.revokeObjectURL = vi.fn()
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
    onOpenChange = vi.fn()
  })

  afterEach(() => {
    URL.createObjectURL = originalCreateObjectURL
    URL.revokeObjectURL = originalRevokeObjectURL
    clickSpy.mockRestore()
  })

  const renderDialog = (props: Partial<Parameters<typeof ExportTransactionsDialog>[0]> = {}) =>
    renderWithProviders(
      <ExportTransactionsDialog
        ledgerSlug="personal-finance"
        accounts={accounts}
        open
        onOpenChange={onOpenChange}
        {...props}
      />
    )

  it("renders format, account, date range, and sub-accounts fields", () => {
    renderDialog()

    expect(screen.getByRole("heading", { name: "Export transactions" })).toBeInTheDocument()
    expect(screen.getByLabelText("Format")).toBeInTheDocument()
    expect(screen.getByText("Account")).toBeInTheDocument()
    // No account selected yet: the autocomplete shows the all-accounts option
    // (the combobox role takes no accessible name from content, so query by text)
    expect(screen.getByText("All accounts")).toBeInTheDocument()
    expect(screen.getByLabelText("From")).toBeInTheDocument()
    expect(screen.getByLabelText("To")).toBeInTheDocument()
    // Include sub-accounts only applies with an account filter
    expect(screen.getByRole("checkbox", { name: "Include sub-accounts" })).toBeDisabled()
    expect(screen.getByRole("button", { name: "Export" })).toBeEnabled()
  })

  it("exports with default options and downloads the returned file", async () => {
    const user = userEvent.setup()
    const blob = mockBlobSuccess("personal-finance-transactions-all.csv")

    renderDialog()

    await user.click(screen.getByRole("button", { name: "Export" }))

    await waitFor(() => {
      expect(getBlobMock).toHaveBeenCalledWith("/ledgers/personal-finance/transactions/export", {
        format: "csv",
      })
    })

    expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
    expect(clickSpy).toHaveBeenCalled()
    const anchor = clickSpy.mock.instances[0] as HTMLAnchorElement
    expect(anchor.download).toBe("personal-finance-transactions-all.csv")
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock-url")
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it("exports with the chosen account, date range, and sub-accounts flag", async () => {
    const user = userEvent.setup()
    mockBlobSuccess()

    renderDialog()

    // Pick an account in the autocomplete
    await user.click(screen.getByText("All accounts"))
    await user.click(screen.getByText("Checking Account"))

    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-01-01" } })
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-06-30" } })
    await user.click(screen.getByRole("checkbox", { name: "Include sub-accounts" }))

    await user.click(screen.getByRole("button", { name: "Export" }))

    await waitFor(() => {
      expect(getBlobMock).toHaveBeenCalledWith("/ledgers/personal-finance/transactions/export", {
        format: "csv",
        accountId: "account-1",
        includeChildren: true,
        from: "2026-01-01",
        to: "2026-06-30",
      })
    })
  })

  it("falls back to a client-built filename when the server sends none", async () => {
    const user = userEvent.setup()
    mockBlobSuccess(null)

    renderDialog()

    await user.click(screen.getByRole("button", { name: "Export" }))

    await waitFor(() => {
      expect(clickSpy).toHaveBeenCalled()
    })
    const anchor = clickSpy.mock.instances[0] as HTMLAnchorElement
    expect(anchor.download).toBe("personal-finance-transactions.csv")
  })

  it("shows the OFX hint and disables export for non asset/liability accounts", async () => {
    const user = userEvent.setup()

    renderDialog()

    // Switch format to OFX
    await user.click(screen.getByLabelText("Format"))
    await user.click(await screen.findByRole("option", { name: "OFX (bank statement)" }))

    // Pick an EXPENSE account
    await user.click(screen.getByText("All accounts"))
    await user.click(screen.getByText("Groceries"))

    expect(
      screen.getByText("OFX statements are generated for asset and liability accounts")
    ).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Export" })).toBeDisabled()
    expect(getBlobMock).not.toHaveBeenCalled()
  })

  it("shows a validation message and disables export for an inverted date range", () => {
    renderDialog()

    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-06-30" } })
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-01-01" } })

    expect(screen.getByText("The start date must not be after the end date")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Export" })).toBeDisabled()
  })

  it("locks the export to the fixed account when fixedAccountId is set", async () => {
    const user = userEvent.setup()
    mockBlobSuccess()

    renderDialog({ fixedAccountId: "account-1" })

    // The account is shown as plain text instead of the autocomplete
    expect(screen.getByText("Checking Account")).toBeInTheDocument()
    expect(screen.queryByText("All accounts")).not.toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Export" }))

    await waitFor(() => {
      expect(getBlobMock).toHaveBeenCalledWith("/ledgers/personal-finance/transactions/export", {
        format: "csv",
        accountId: "account-1",
      })
    })
  })

  it("toasts the error and keeps the dialog open when the export fails", async () => {
    const user = userEvent.setup()
    const { ApiError } = await vi.importActual<typeof import("@/api/client")>("@/api/client")
    getBlobMock.mockRejectedValueOnce(new ApiError(404, "Account not found"))

    renderDialog()

    await user.click(screen.getByRole("button", { name: "Export" }))

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalled()
    })

    // Dialog stays open and the button leaves its exporting state
    expect(onOpenChange).not.toHaveBeenCalledWith(false)
    expect(screen.getByRole("heading", { name: "Export transactions" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Export" })).toBeEnabled()
    expect(clickSpy).not.toHaveBeenCalled()
  })
})
