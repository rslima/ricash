import { describe, it, expect, vi } from "vitest"
import { ApiError } from "@/api/client"
import { recoverFromUnauthorized } from "@/lib/session-recovery"
import { queryClient } from "./queryClient"

vi.mock("@/lib/session-recovery", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/session-recovery")>()),
  recoverFromUnauthorized: vi.fn().mockResolvedValue(undefined),
}))

describe("queryClient", () => {
  it("hands query 401s to session recovery", async () => {
    await queryClient
      .fetchQuery({ queryKey: ["q-401"], queryFn: () => Promise.reject(new ApiError(401, "Unauthorized")), retry: false })
      .catch(() => {})

    expect(recoverFromUnauthorized).toHaveBeenCalledWith(queryClient)
  })

  it("hands mutation 401s to session recovery", async () => {
    await queryClient
      .getMutationCache()
      .build(queryClient, { mutationFn: () => Promise.reject(new ApiError(401, "Unauthorized")) })
      .execute(undefined)
      .catch(() => {})

    expect(recoverFromUnauthorized).toHaveBeenCalledWith(queryClient)
  })

  it("leaves other failures alone", async () => {
    await queryClient
      .fetchQuery({ queryKey: ["q-500"], queryFn: () => Promise.reject(new ApiError(500, "Boom")), retry: false })
      .catch(() => {})

    expect(recoverFromUnauthorized).not.toHaveBeenCalled()
  })
})
