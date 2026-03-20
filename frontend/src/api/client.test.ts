import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { ApiError } from "./client"

// We need to test ApiError and mock fetch for API client tests
describe("ApiError", () => {
  it("creates error with status and message", () => {
    const error = new ApiError(404, "Not Found")

    expect(error.status).toBe(404)
    expect(error.message).toBe("Not Found")
    expect(error.name).toBe("ApiError")
  })

  it("creates error with data", () => {
    const data = { errors: [{ detail: "Resource not found" }] }
    const error = new ApiError(404, "Not Found", data)

    expect(error.status).toBe(404)
    expect(error.data).toEqual(data)
  })

  it("is instance of Error", () => {
    const error = new ApiError(500, "Internal Server Error")

    expect(error).toBeInstanceOf(Error)
    expect(error).toBeInstanceOf(ApiError)
  })
})

describe("ApiClient", () => {
  const originalFetch = global.fetch
  let mockFetch: ReturnType<typeof vi.fn>

  beforeEach(() => {
    mockFetch = vi.fn()
    global.fetch = mockFetch
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.resetModules()
  })

  it("builds URL with params", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ data: [] }),
    })

    // Import fresh instance
    const { apiClient } = await import("./client")
    await apiClient.get("/test", { page: 1, size: 10 })

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("page=1"),
      expect.any(Object)
    )
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("size=10"),
      expect.any(Object)
    )
  })

  it("skips undefined params", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ data: [] }),
    })

    const { apiClient } = await import("./client")
    await apiClient.get("/test", { page: 1, size: undefined })

    const calledUrl = mockFetch.mock.calls[0][0] as string
    expect(calledUrl).toContain("page=1")
    expect(calledUrl).not.toContain("size=")
  })

  it("sets authorization header when token is set", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ data: [] }),
    })

    const { apiClient } = await import("./client")
    apiClient.setAccessToken("test-token")
    await apiClient.get("/test")

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer test-token",
        }),
      })
    )
  })

  it("sets JSON:API content type headers", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ data: [] }),
    })

    const { apiClient } = await import("./client")
    await apiClient.get("/test")

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          "Content-Type": "application/vnd.api+json",
          Accept: "application/vnd.api+json",
        }),
      })
    )
  })

  it("throws ApiError on non-ok response", async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ message: "Not Found" }),
      })
      .mockResolvedValueOnce({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ message: "Not Found" }),
      })

    const { apiClient } = await import("./client")

    await expect(apiClient.get("/test")).rejects.toThrow("Not Found")
    await expect(apiClient.get("/test")).rejects.toMatchObject({
      status: 404,
    })
  })

  it("handles 204 No Content response", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
    })

    const { apiClient } = await import("./client")
    const result = await apiClient.delete("/test/1")

    expect(result).toEqual({})
  })

  it("makes POST request with body", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ data: { id: "1" } }),
    })

    const { apiClient } = await import("./client")
    await apiClient.post("/test", { name: "Test" })

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ name: "Test" }),
      })
    )
  })

  it("makes PATCH request with body", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ data: { id: "1" } }),
    })

    const { apiClient } = await import("./client")
    await apiClient.patch("/test/1", { name: "Updated" })

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ name: "Updated" }),
      })
    )
  })

  it("makes DELETE request", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
    })

    const { apiClient } = await import("./client")
    await apiClient.delete("/test/1")

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        method: "DELETE",
      })
    )
  })
})
