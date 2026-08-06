import { describe, it, expect, afterEach } from "vitest"
import { captureReturnTo, resolveReturnTo } from "./auth-return"

const origin = window.location.origin

// Each test drives the address bar via pushState; reset so tests stay isolated.
afterEach(() => {
  window.history.pushState({}, "", "/")
})

describe("captureReturnTo", () => {
  it("captures the current path", () => {
    window.history.pushState({}, "", "/transactions")
    expect(captureReturnTo()).toBe("/transactions")
  })

  it("keeps query string and hash", () => {
    window.history.pushState({}, "", "/ledgers/home/accounts?page=2#row-7")
    expect(captureReturnTo()).toBe("/ledgers/home/accounts?page=2#row-7")
  })
})

describe("resolveReturnTo", () => {
  it("returns the stored path", () => {
    expect(resolveReturnTo({ returnTo: "/budget" })).toBe("/budget")
  })

  it("preserves query string and hash", () => {
    expect(resolveReturnTo({ returnTo: "/reports?year=2026#totals" })).toBe("/reports?year=2026#totals")
  })

  it("round-trips a captured location", () => {
    window.history.pushState({}, "", "/portfolio?sort=value")
    expect(resolveReturnTo({ returnTo: captureReturnTo() })).toBe("/portfolio?sort=value")
  })

  it.each([
    ["undefined state", undefined],
    ["null state", null],
    ["state without returnTo", {}],
    ["non-string returnTo", { returnTo: 42 }],
    ["empty returnTo", { returnTo: "" }],
  ])("falls back to home for %s", (_label, state) => {
    expect(resolveReturnTo(state)).toBe("/")
  })

  it("falls back to home for the callback route itself, avoiding a login loop", () => {
    expect(resolveReturnTo({ returnTo: "/callback" })).toBe("/")
    expect(resolveReturnTo({ returnTo: "/callback?code=abc" })).toBe("/")
  })

  it.each([
    ["an absolute off-origin URL", "https://evil.example/steal"],
    ["a protocol-relative path", "//evil.example/steal"],
    ["a backslash-smuggled host", "/\\evil.example/steal"],
    ["a relative path", "transactions"],
  ])("falls back to home for %s", (_label, returnTo) => {
    expect(resolveReturnTo({ returnTo })).toBe("/")
  })

  it("allows a same-origin absolute path", () => {
    expect(resolveReturnTo({ returnTo: "/accounts" })).toBe("/accounts")
    expect(new URL("/accounts", origin).origin).toBe(origin)
  })
})
