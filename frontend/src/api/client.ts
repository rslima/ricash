const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "https://ricash.app/api/v1"

interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | undefined>
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

  private buildUrl(endpoint: string, params?: Record<string, string | number | undefined>): string {
    const url = new URL(`${this.baseUrl}${endpoint}`)
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
      // The API speaks JSON:API: errors arrive as { errors: [{ status, title, detail }] }
      const body: unknown = await response.json().catch(() => null)
      const errors = extractJsonApiErrors(body)
      const first = errors?.[0]
      const message = first?.detail || first?.title || `Request failed (${response.status})`
      throw new ApiError(response.status, message, body, errors)
    }

    if (response.status === 204) {
      return {} as T
    }

    return response.json() as Promise<T>
  }

  async get<T>(endpoint: string, params?: Record<string, string | number | undefined>): Promise<T> {
    return this.request<T>(endpoint, { method: "GET", params })
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

export interface JsonApiErrorObject {
  status?: string
  title?: string
  detail?: string
}

function extractJsonApiErrors(body: unknown): JsonApiErrorObject[] | undefined {
  if (body && typeof body === "object" && Array.isArray((body as { errors?: unknown }).errors)) {
    return (body as { errors: JsonApiErrorObject[] }).errors
  }
  return undefined
}

export class ApiError extends Error {
  status: number
  data?: unknown
  errors?: JsonApiErrorObject[]

  constructor(status: number, message: string, data?: unknown, errors?: JsonApiErrorObject[]) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.data = data
    this.errors = errors
  }
}

export const apiClient = new ApiClient(API_BASE_URL)
