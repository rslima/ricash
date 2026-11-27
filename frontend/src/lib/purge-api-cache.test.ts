import { afterEach, describe, expect, it, vi } from "vitest"
import { LEGACY_API_CACHE, purgeLegacyApiCache } from "./purge-api-cache"

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("purgeLegacyApiCache", () => {
  it("deletes the API response cache older service workers created", async () => {
    const del = vi.fn().mockResolvedValue(true)
    vi.stubGlobal("caches", { delete: del })

    await purgeLegacyApiCache()

    expect(LEGACY_API_CACHE).toBe("api-cache")
    expect(del).toHaveBeenCalledWith("api-cache")
  })

  it("does nothing where Cache Storage is unavailable", async () => {
    vi.stubGlobal("caches", undefined)

    await expect(purgeLegacyApiCache()).resolves.toBeUndefined()
  })

  it("swallows Cache Storage failures", async () => {
    vi.stubGlobal("caches", { delete: vi.fn().mockRejectedValue(new Error("SecurityError")) })

    await expect(purgeLegacyApiCache()).resolves.toBeUndefined()
  })
})
