import { Preferences } from "@capacitor/preferences"

/**
 * A Storage implementation backed by Capacitor Preferences.
 * Uses an in-memory cache for synchronous access (required by oidc-client-ts)
 * and persists asynchronously to Capacitor Preferences.
 */
export class NativeStorage implements Storage {
  private cache: Map<string, string> = new Map()
  private ready: Promise<void>

  constructor() {
    this.ready = this.loadAll()
  }

  private async loadAll(): Promise<void> {
    const { keys } = await Preferences.keys()
    for (const key of keys) {
      const { value } = await Preferences.get({ key })
      if (value !== null) {
        this.cache.set(key, value)
      }
    }
  }

  async waitForReady(): Promise<void> {
    await this.ready
  }

  get length(): number {
    return this.cache.size
  }

  key(index: number): string | null {
    const keys = Array.from(this.cache.keys())
    return keys[index] ?? null
  }

  getItem(key: string): string | null {
    return this.cache.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    this.cache.set(key, value)
    Preferences.set({ key, value })
  }

  removeItem(key: string): void {
    this.cache.delete(key)
    Preferences.remove({ key })
  }

  clear(): void {
    this.cache.clear()
    Preferences.clear()
  }
}
