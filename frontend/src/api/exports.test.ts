import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"

describe("downloadTransactionsExport", () => {
  const originalFetch = global.fetch
  const originalCreateObjectURL = URL.createObjectURL
  const originalRevokeObjectURL = URL.revokeObjectURL
  let mockFetch: ReturnType<typeof vi.fn>
  let clickSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    mockFetch = vi.fn()
    global.fetch = mockFetch
    URL.createObjectURL = vi.fn(() => "blob:mock-url")
    URL.revokeObjectURL = vi.fn()
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
  })

  afterEach(() => {
    global.fetch = originalFetch
    URL.createObjectURL = originalCreateObjectURL
    URL.revokeObjectURL = originalRevokeObjectURL
    clickSpy.mockRestore()
    vi.resetModules()
  })

  function okResponse(disposition: string | null = null) {
    return {
      ok: true,
      headers: new Headers(disposition ? { "content-disposition": disposition } : {}),
      blob: () => Promise.resolve(new Blob(["csv-content"], { type: "text/csv" })),
    }
  }

  it("requests the export endpoint with all parameters", async () => {
    mockFetch.mockResolvedValueOnce(okResponse())

    const { downloadTransactionsExport } = await import("./exports")
    await downloadTransactionsExport("main", {
      format: "csv",
      accountId: "acc-1",
      includeChildren: true,
      from: "2026-01-01",
      to: "2026-06-30",
    })

    const calledUrl = mockFetch.mock.calls[0][0] as string
    expect(calledUrl).toContain("/ledgers/main/transactions/export")
    expect(calledUrl).toContain("format=csv")
    expect(calledUrl).toContain("accountId=acc-1")
    expect(calledUrl).toContain("includeChildren=true")
    expect(calledUrl).toContain("from=2026-01-01")
    expect(calledUrl).toContain("to=2026-06-30")
  })

  it("omits empty parameters and includeChildren without an account", async () => {
    mockFetch.mockResolvedValueOnce(okResponse())

    const { downloadTransactionsExport } = await import("./exports")
    await downloadTransactionsExport("main", {
      format: "ofx",
      accountId: "",
      includeChildren: true,
      from: "",
      to: "",
    })

    const calledUrl = mockFetch.mock.calls[0][0] as string
    expect(calledUrl).toContain("format=ofx")
    expect(calledUrl).not.toContain("accountId=")
    expect(calledUrl).not.toContain("includeChildren=")
    expect(calledUrl).not.toContain("from=")
    expect(calledUrl).not.toContain("to=")
  })

  it("uses the filename from Content-Disposition for the download", async () => {
    mockFetch.mockResolvedValueOnce(
      okResponse('attachment; filename="main-transactions-all.csv"')
    )

    const { downloadTransactionsExport } = await import("./exports")
    await downloadTransactionsExport("main", { format: "csv" })

    expect(clickSpy).toHaveBeenCalled()
    const anchor = clickSpy.mock.instances[0] as HTMLAnchorElement
    expect(anchor.download).toBe("main-transactions-all.csv")
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock-url")
  })

  it("falls back to a client-built filename without Content-Disposition", async () => {
    mockFetch.mockResolvedValueOnce(okResponse())

    const { downloadTransactionsExport } = await import("./exports")
    await downloadTransactionsExport("main", { format: "ofx" })

    const anchor = clickSpy.mock.instances[0] as HTMLAnchorElement
    expect(anchor.download).toBe("main-transactions.ofx")
  })

  it("throws ApiError with the JSON:API error detail on failure", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: () =>
        Promise.resolve({
          errors: [{ status: "404", title: "Not Found", detail: "Account not found: ghost" }],
        }),
    })

    const { downloadTransactionsExport } = await import("./exports")

    await expect(
      downloadTransactionsExport("main", { format: "csv", accountId: "ghost" })
    ).rejects.toMatchObject({ status: 404, message: "Account not found: ghost" })
  })

  it("throws a generic ApiError when the error body is not JSON", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 502,
      json: () => Promise.reject(new SyntaxError("not json")),
    })

    const { downloadTransactionsExport } = await import("./exports")

    await expect(
      downloadTransactionsExport("main", { format: "csv" })
    ).rejects.toMatchObject({ status: 502, message: "Request failed" })
  })

  it("does not send JSON:API headers on the blob request", async () => {
    mockFetch.mockResolvedValueOnce(okResponse())

    const { downloadTransactionsExport } = await import("./exports")
    await downloadTransactionsExport("main", { format: "csv" })

    const options = mockFetch.mock.calls[0][1] as RequestInit
    expect(options.headers).toMatchObject({ Accept: "*/*" })
    expect(options.headers).not.toHaveProperty("Content-Type")
  })
})
