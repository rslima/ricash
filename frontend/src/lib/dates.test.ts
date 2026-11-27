import { afterEach, describe, expect, it, vi } from "vitest"
import { todayISO } from "./dates"

afterEach(() => {
  vi.useRealTimers()
})

describe("todayISO", () => {
  it("runs under the suite's non-UTC zone", () => {
    // Guards the tests below: in UTC they would pass even with the old
    // toISOString() implementation.
    expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe("America/Sao_Paulo")
  })

  it("returns the local date late in the evening, not the UTC date", () => {
    // 22:30 on Jan 31 in São Paulo is already Feb 1 in UTC.
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.setSystemTime(new Date("2026-02-01T01:30:00Z"))

    expect(todayISO()).toBe("2026-01-31")
  })

  it("zero-pads month and day", () => {
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.setSystemTime(new Date("2026-03-05T12:00:00Z"))

    expect(todayISO()).toBe("2026-03-05")
  })
})
