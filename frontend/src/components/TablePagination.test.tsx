import { describe, it, expect, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { TablePagination } from "./TablePagination"

function renderPagination(overrides: Partial<Parameters<typeof TablePagination>[0]> = {}) {
  const props = {
    currentPage: 1,
    totalPages: 5,
    totalElements: 42,
    pageSize: 10,
    onPageChange: vi.fn(),
    onPageSizeChange: vi.fn(),
    ...overrides,
  }
  const view = render(<TablePagination {...props} />)
  return { props, view }
}

describe("TablePagination", () => {
  it("renders nothing when there is a single page", () => {
    const { view } = renderPagination({ totalPages: 1 })

    expect(view.container).toBeEmptyDOMElement()
  })

  it("shows the element count and page info", () => {
    renderPagination({ currentPage: 1, totalPages: 5, totalElements: 42 })

    expect(screen.getByText("42 transaction(s)")).toBeInTheDocument()
    expect(screen.getByText("Page 2 of 5")).toBeInTheDocument()
  })

  it("disables first and previous on the first page, keeping next and last enabled", () => {
    renderPagination({ currentPage: 0 })

    expect(screen.getByRole("button", { name: "First page" })).toBeDisabled()
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled()
    expect(screen.getByRole("button", { name: "Next page" })).toBeEnabled()
    expect(screen.getByRole("button", { name: "Last page" })).toBeEnabled()
  })

  it("disables next and last on the last page, keeping first and previous enabled", () => {
    renderPagination({ currentPage: 4, totalPages: 5 })

    expect(screen.getByRole("button", { name: "First page" })).toBeEnabled()
    expect(screen.getByRole("button", { name: "Previous page" })).toBeEnabled()
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled()
    expect(screen.getByRole("button", { name: "Last page" })).toBeDisabled()
  })

  it("navigates to the adjacent pages", async () => {
    const user = userEvent.setup()
    const { props } = renderPagination({ currentPage: 2, totalPages: 5 })

    await user.click(screen.getByRole("button", { name: "Next page" }))
    expect(props.onPageChange).toHaveBeenLastCalledWith(3)

    await user.click(screen.getByRole("button", { name: "Previous page" }))
    expect(props.onPageChange).toHaveBeenLastCalledWith(1)
  })

  it("jumps to the first and last pages", async () => {
    const user = userEvent.setup()
    const { props } = renderPagination({ currentPage: 2, totalPages: 5 })

    await user.click(screen.getByRole("button", { name: "First page" }))
    expect(props.onPageChange).toHaveBeenLastCalledWith(0)

    await user.click(screen.getByRole("button", { name: "Last page" }))
    expect(props.onPageChange).toHaveBeenLastCalledWith(4)
  })

  it("changes the page size through the select", async () => {
    const user = userEvent.setup()
    const { props } = renderPagination({ pageSize: 10 })

    await user.click(screen.getByRole("combobox"))
    await user.click(screen.getByRole("option", { name: "50" }))

    expect(props.onPageSizeChange).toHaveBeenCalledTimes(1)
    expect(props.onPageSizeChange).toHaveBeenCalledWith(50)
  })

  it("offers custom page size options", async () => {
    const user = userEvent.setup()
    renderPagination({ pageSize: 25, pageSizeOptions: [25, 100] })

    await user.click(screen.getByRole("combobox"))

    expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual(["25", "100"])
  })
})
