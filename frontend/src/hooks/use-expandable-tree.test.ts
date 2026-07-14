import { describe, it, expect } from "vitest"
import { act, renderHook } from "@testing-library/react"
import { useExpandableTree } from "./use-expandable-tree"

// The hook requires a stable array (its default-expand effect keys on the
// array identity), so every list below is a module-level constant.
const items = [{ id: "a" }, { id: "b" }, { id: "c" }]
const noItems: Array<{ id: string }> = []
const reloadedItems = [{ id: "a" }, { id: "d" }]

describe("useExpandableTree", () => {
  it("expands every item by default", () => {
    const { result } = renderHook(() => useExpandableTree(items))

    expect(result.current.expandedIds).toEqual(new Set(["a", "b", "c"]))
  })

  it("starts empty for an empty item list", () => {
    const { result } = renderHook(() => useExpandableTree(noItems))

    expect(result.current.expandedIds.size).toBe(0)
  })

  it("toggle collapses an expanded item", () => {
    const { result } = renderHook(() => useExpandableTree(items))

    act(() => result.current.toggle("b"))

    expect(result.current.expandedIds).toEqual(new Set(["a", "c"]))
  })

  it("toggle re-expands a collapsed item", () => {
    const { result } = renderHook(() => useExpandableTree(items))

    act(() => result.current.toggle("b"))
    act(() => result.current.toggle("b"))

    expect(result.current.expandedIds).toEqual(new Set(["a", "b", "c"]))
  })

  it("collapseAll clears the expanded set", () => {
    const { result } = renderHook(() => useExpandableTree(items))

    act(() => result.current.collapseAll())

    expect(result.current.expandedIds.size).toBe(0)
  })

  it("expandAll expands every item again after collapsing", () => {
    const { result } = renderHook(() => useExpandableTree(items))

    act(() => result.current.collapseAll())
    act(() => result.current.expandAll())

    expect(result.current.expandedIds).toEqual(new Set(["a", "b", "c"]))
  })

  it("re-expands everything when a new item list arrives", () => {
    const { result, rerender } = renderHook(({ list }) => useExpandableTree(list), {
      initialProps: { list: items },
    })

    act(() => result.current.collapseAll())
    expect(result.current.expandedIds.size).toBe(0)

    rerender({ list: reloadedItems })

    expect(result.current.expandedIds).toEqual(new Set(["a", "d"]))
  })
})
