import { describe, it, expect } from "vitest"
import { act, renderHook } from "@testing-library/react"
import { usePagination } from "./use-pagination"

describe("usePagination", () => {
  it("starts on the first page with the default page size", () => {
    const { result } = renderHook(() => usePagination())

    expect(result.current.currentPage).toBe(0)
    expect(result.current.pageSize).toBe(20)
  })

  it("honors a custom initial page size", () => {
    const { result } = renderHook(() => usePagination(50))

    expect(result.current.pageSize).toBe(50)
    expect(result.current.currentPage).toBe(0)
  })

  it("changes the current page", () => {
    const { result } = renderHook(() => usePagination())

    act(() => result.current.setCurrentPage(3))

    expect(result.current.currentPage).toBe(3)
    expect(result.current.pageSize).toBe(20)
  })

  it("restarts from the first page when the page size changes", () => {
    const { result } = renderHook(() => usePagination())

    act(() => result.current.setCurrentPage(4))
    act(() => result.current.setPageSize(50))

    expect(result.current.pageSize).toBe(50)
    expect(result.current.currentPage).toBe(0)
  })

  it("resetPage returns to the first page without touching the page size", () => {
    const { result } = renderHook(() => usePagination(10))

    act(() => result.current.setCurrentPage(7))
    act(() => result.current.resetPage())

    expect(result.current.currentPage).toBe(0)
    expect(result.current.pageSize).toBe(10)
  })

  it("keeps callback identities stable across rerenders", () => {
    const { result, rerender } = renderHook(() => usePagination())
    const { setPageSize, resetPage } = result.current

    act(() => result.current.setCurrentPage(2))
    rerender()

    expect(result.current.setPageSize).toBe(setPageSize)
    expect(result.current.resetPage).toBe(resetPage)
  })
})
