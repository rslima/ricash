import { describe, it, expect, vi, beforeEach } from "vitest"
import { renderHook } from "@testing-library/react"
import { toast } from "sonner"
import { ApiError } from "@/api/client"
import { useErrorHandler } from "./use-error-handler"

vi.mock("sonner", () => ({ toast: { error: vi.fn() } }))

describe("useErrorHandler", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {})
  })

  function handle(error: unknown, operationKey?: string) {
    const { result } = renderHook(() => useErrorHandler())
    result.current(error, operationKey)
  }

  it("shows the server's validation detail under the localized title", () => {
    handle(new ApiError(400, "x", undefined, "Transaction is not balanced for currency USD"), "createFailed")

    expect(toast.error).toHaveBeenCalledWith("Failed to create. Please try again.",
      { description: "Transaction is not balanced for currency USD" })
  })

  it("shows the conflict detail", () => {
    handle(new ApiError(409, "x", undefined, "Account acct-1 has transactions"), "deleteFailed")

    expect(toast.error).toHaveBeenCalledWith("This operation conflicts with existing data.",
      { description: "Account acct-1 has transactions" })
  })

  it("falls back to the plain message when there is no detail", () => {
    handle(new ApiError(400, "Request failed"), "updateFailed")

    expect(toast.error).toHaveBeenCalledWith("Failed to save changes. Please try again.", undefined)
  })

  it("leaves 401s to session recovery", () => {
    handle(new ApiError(401, "Unauthorized"), "fetchFailed")

    expect(toast.error).not.toHaveBeenCalled()
  })

  it("keeps the generic server-error message for 5xx", () => {
    handle(new ApiError(503, "x", undefined, "internal detail"), "fetchFailed")

    expect(toast.error).toHaveBeenCalledWith("A server error occurred. Please try again later.")
  })
})
