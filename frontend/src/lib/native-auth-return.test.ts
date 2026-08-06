import { describe, it, expect, vi, afterEach } from "vitest"
import {
  consumeReturnTo,
  getPendingReturnTo,
  publishReturnTo,
  subscribeReturnTo,
} from "./native-auth-return"

// Module-level store, so it outlives a single test — reset between them.
afterEach(() => {
  consumeReturnTo()
})

describe("native-auth-return", () => {
  it("starts empty", () => {
    expect(getPendingReturnTo()).toBeNull()
  })

  it("holds a published path", () => {
    publishReturnTo("/budget")
    expect(getPendingReturnTo()).toBe("/budget")
  })

  it("clears on consume, so a path fires only once", () => {
    publishReturnTo("/budget")
    consumeReturnTo()
    expect(getPendingReturnTo()).toBeNull()
  })

  it("notifies subscribers on publish and on consume", () => {
    const notify = vi.fn()
    subscribeReturnTo(notify)

    publishReturnTo("/accounts")
    expect(notify).toHaveBeenCalledTimes(1)

    consumeReturnTo()
    expect(notify).toHaveBeenCalledTimes(2)
  })

  it("stops notifying after unsubscribe", () => {
    const notify = vi.fn()
    const unsubscribe = subscribeReturnTo(notify)
    unsubscribe()

    publishReturnTo("/accounts")
    expect(notify).not.toHaveBeenCalled()
  })

  it("keeps the latest path when published twice", () => {
    publishReturnTo("/first")
    publishReturnTo("/second")
    expect(getPendingReturnTo()).toBe("/second")
  })
})
