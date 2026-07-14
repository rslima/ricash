import { describe, it, expect, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MonthYearPicker } from "./MonthYearPicker"

function renderPicker(overrides: Partial<Parameters<typeof MonthYearPicker>[0]> = {}) {
  const props = {
    month: 3,
    year: 2024,
    onMonthChange: vi.fn(),
    onYearChange: vi.fn(),
    ...overrides,
  }
  render(<MonthYearPicker {...props} />)
  return props
}

// The month select renders first, the year select second.
const monthTrigger = () => screen.getAllByRole("combobox")[0]
const yearTrigger = () => screen.getAllByRole("combobox")[1]

describe("MonthYearPicker", () => {
  it("displays the selected month and year", () => {
    renderPicker({ month: 3, year: 2024 })

    expect(monthTrigger()).toHaveTextContent("March")
    expect(yearTrigger()).toHaveTextContent("2024")
  })

  it("calls onMonthChange with the 1-based month when a month is picked", async () => {
    const user = userEvent.setup()
    const { onMonthChange, onYearChange } = renderPicker()

    await user.click(monthTrigger())
    await user.click(screen.getByRole("option", { name: "July" }))

    expect(onMonthChange).toHaveBeenCalledTimes(1)
    expect(onMonthChange).toHaveBeenCalledWith(7)
    expect(onYearChange).not.toHaveBeenCalled()
  })

  it("calls onYearChange with the picked year", async () => {
    const user = userEvent.setup()
    const currentYear = new Date().getFullYear()
    const { onYearChange, onMonthChange } = renderPicker({ year: currentYear })

    await user.click(yearTrigger())
    await user.click(screen.getByRole("option", { name: String(currentYear - 1) }))

    expect(onYearChange).toHaveBeenCalledTimes(1)
    expect(onYearChange).toHaveBeenCalledWith(currentYear - 1)
    expect(onMonthChange).not.toHaveBeenCalled()
  })

  it("offers five years back through next year", async () => {
    const user = userEvent.setup()
    const currentYear = new Date().getFullYear()
    renderPicker({ year: currentYear })

    await user.click(yearTrigger())

    const options = screen.getAllByRole("option").map((o) => o.textContent)
    const expected = []
    for (let y = currentYear - 5; y <= currentYear + 1; y++) expected.push(String(y))
    expect(options).toEqual(expected)
  })

  it("always includes an out-of-range selected year in the options", async () => {
    const user = userEvent.setup()
    renderPicker({ year: 2015 })

    expect(yearTrigger()).toHaveTextContent("2015")

    await user.click(yearTrigger())

    expect(screen.getByRole("option", { name: "2015" })).toBeInTheDocument()
  })
})
