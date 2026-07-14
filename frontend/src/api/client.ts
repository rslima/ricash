const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "https://ricash.app/api/v1"

// Query-param bag accepted by every GET. Domain param types (PaginationParams,
// TransactionFilters, ...) must stay type aliases — aliases are assignable to
// this index signature without casts, interfaces are not.
export type QueryParams = Record<string, string | number | boolean | undefined>

interface RequestOptions extends RequestInit {
  params?: QueryParams
}

class ApiClient {
  private baseUrl: string
  private accessToken: string | null = null

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl
  }

  setAccessToken(token: string | null) {
    this.accessToken = token
  }

  private buildUrl(endpoint: string, params?: QueryParams): string {
    // A relative base (e.g. "/api/v1" via the dev proxy) needs an origin to
    // resolve against; for an absolute base the second arg is ignored.
    const url = new URL(`${this.baseUrl}${endpoint}`, window.location.origin)
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined) {
          url.searchParams.append(key, String(value))
        }
      })
    }
    return url.toString()
  }

  private async request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    const { params, ...fetchOptions } = options

    const headers: HeadersInit = {
      "Content-Type": "application/vnd.api+json",
      Accept: "application/vnd.api+json",
      ...(options.headers || {}),
    }

    if (this.accessToken) {
      (headers as Record<string, string>)["Authorization"] = `Bearer ${this.accessToken}`
    }

    const response = await fetch(this.buildUrl(endpoint, params), {
      ...fetchOptions,
      headers,
    })

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: "Request failed" }))
      throw new ApiError(response.status, error.message || "Request failed", error)
    }

    if (response.status === 204) {
      return {} as T
    }

    return response.json()
  }

  async get<T>(endpoint: string, params?: QueryParams): Promise<T> {
    return this.request<T>(endpoint, { method: "GET", params })
  }

  /**
   * GET a binary response (file download). Unlike request(), no JSON:API
   * headers are sent and the body is returned as a Blob together with the
   * filename from the Content-Disposition header, when present.
   */
  async getBlob(
    endpoint: string,
    params?: QueryParams
  ): Promise<{ blob: Blob; filename: string | null }> {
    const headers: Record<string, string> = { Accept: "*/*" }
    if (this.accessToken) {
      headers["Authorization"] = `Bearer ${this.accessToken}`
    }

    const response = await fetch(this.buildUrl(endpoint, params), { method: "GET", headers })

    if (!response.ok) {
      // Errors arrive as JSON:API error documents ({ errors: [{ detail }] }).
      const body = await response.json().catch(() => null)
      throw new ApiError(response.status, body?.errors?.[0]?.detail ?? body?.message ?? "Request failed", body)
    }

    const disposition = response.headers.get("content-disposition")
    const filename = disposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? null
    return { blob: await response.blob(), filename }
  }

  async post<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: "POST",
      body: data ? JSON.stringify(data) : undefined,
    })
  }

  async put<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: "PUT",
      body: data ? JSON.stringify(data) : undefined,
    })
  }

  async patch<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: "PATCH",
      body: data ? JSON.stringify(data) : undefined,
    })
  }

  async delete<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: "DELETE" })
  }
}

export class ApiError extends Error {
  status: number
  data?: unknown

  constructor(status: number, message: string, data?: unknown) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.data = data
  }
}

export const apiClient = new ApiClient(API_BASE_URL)
