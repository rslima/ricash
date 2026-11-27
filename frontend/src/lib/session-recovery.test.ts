import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { QueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { apiClient, ApiError } from "@/api/client"
import { userManager } from "@/lib/oidc"
import {
  isUnauthorized,
  recoverFromUnauthorized,
  resetSessionRecoveryForTests,
  RENEW_LOOP_WINDOW_MS,
} from "./session-recovery"

vi.mock("@/lib/oidc", () => ({
  userManager: { getUser: vi.fn(), signinSilent: vi.fn(), removeUser: vi.fn() },
}))
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }))

const signedInUser = { access_token: "old-token" }
const renewedUser = { access_token: "new-token" }

describe("recoverFromUnauthorized", () => {
  let queryClient: QueryClient

  beforeEach(() => {
    resetSessionRecoveryForTests()
    queryClient = new QueryClient()
    vi.spyOn(queryClient, "invalidateQueries").mockResolvedValue()
    vi.spyOn(queryClient, "clear")
    vi.spyOn(apiClient, "setAccessToken")
    vi.mocked(userManager.getUser).mockResolvedValue(signedInUser as never)
    vi.mocked(userManager.removeUser).mockResolvedValue()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it("renews silently and refetches with the new token", async () => {
    vi.mocked(userManager.signinSilent).mockResolvedValue(renewedUser as never)

    await recoverFromUnauthorized(queryClient)

    expect(apiClient.setAccessToken).toHaveBeenCalledWith("new-token")
    // The token must be in place before the refetches go out.
    expect(vi.mocked(apiClient.setAccessToken).mock.invocationCallOrder[0])
      .toBeLessThan(vi.mocked(queryClient.invalidateQueries).mock.invocationCallOrder[0]!)
    expect(userManager.removeUser).not.toHaveBeenCalled()
    expect(toast.error).not.toHaveBeenCalled()
  })

  it("shares one renew between concurrent 401s", async () => {
    vi.mocked(userManager.signinSilent).mockResolvedValue(renewedUser as never)

    await Promise.all([
      recoverFromUnauthorized(queryClient),
      recoverFromUnauthorized(queryClient),
      recoverFromUnauthorized(queryClient),
    ])

    expect(userManager.signinSilent).toHaveBeenCalledTimes(1)
  })

  it("signs out locally with one toast when the renew fails", async () => {
    vi.mocked(userManager.signinSilent).mockRejectedValue(new Error("login_required"))
    vi.spyOn(console, "warn").mockImplementation(() => {})

    await recoverFromUnauthorized(queryClient)

    expect(userManager.removeUser).toHaveBeenCalledTimes(1)
    expect(apiClient.setAccessToken).toHaveBeenCalledWith(null)
    expect(queryClient.clear).toHaveBeenCalled()
    expect(toast.error).toHaveBeenCalledTimes(1)
    expect(toast.error).toHaveBeenCalledWith(
      "Your session has expired. Please sign in again.", { id: "session-expired" })
  })

  it("signs out instead of renewing again when a 401 follows a fresh renew", async () => {
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.mocked(userManager.signinSilent).mockResolvedValue(renewedUser as never)

    await recoverFromUnauthorized(queryClient)
    vi.setSystemTime(Date.now() + RENEW_LOOP_WINDOW_MS - 1)
    await recoverFromUnauthorized(queryClient)

    expect(userManager.signinSilent).toHaveBeenCalledTimes(1)
    expect(userManager.removeUser).toHaveBeenCalledTimes(1)
  })

  it("renews again once the loop window has passed", async () => {
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.mocked(userManager.signinSilent).mockResolvedValue(renewedUser as never)

    await recoverFromUnauthorized(queryClient)
    vi.setSystemTime(Date.now() + RENEW_LOOP_WINDOW_MS)
    await recoverFromUnauthorized(queryClient)

    expect(userManager.signinSilent).toHaveBeenCalledTimes(2)
    expect(userManager.removeUser).not.toHaveBeenCalled()
  })

  it("does nothing for a visitor who never signed in", async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(null)

    await recoverFromUnauthorized(queryClient)

    expect(userManager.signinSilent).not.toHaveBeenCalled()
    expect(userManager.removeUser).not.toHaveBeenCalled()
    expect(toast.error).not.toHaveBeenCalled()
  })
})

describe("isUnauthorized", () => {
  it("matches only API 401s", () => {
    expect(isUnauthorized(new ApiError(401, "Unauthorized"))).toBe(true)
    expect(isUnauthorized(new ApiError(403, "Forbidden"))).toBe(false)
    expect(isUnauthorized(new Error("401"))).toBe(false)
  })
})
