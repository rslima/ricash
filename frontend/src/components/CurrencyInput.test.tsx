import { useState } from "react"
import { describe, expect, it, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { CurrencyInput } from "./CurrencyInput"

function Harness({ onValueChange }: { onValueChange?: (v: string) => void }) {
  const [value, setValue] = useState("")
  return (
    <CurrencyInput
      aria-label="Currency"
      value={value}
      onValueChange={(v) => {
        setValue(v)
        onValueChange?.(v)
      }}
    />
  )
}

describe("CurrencyInput", () => {
  it("uppercases what the user types", async () => {
    const onValueChange = vi.fn()
    render(<Harness onValueChange={onValueChange} />)

    await userEvent.type(screen.getByLabelText("Currency"), "usd")

    expect(screen.getByLabelText("Currency")).toHaveValue("USD")
    expect(onValueChange).toHaveBeenLastCalledWith("USD")
  })

  it("drops whitespace and stops at three characters", async () => {
    render(<Harness />)

    await userEvent.type(screen.getByLabelText("Currency"), " b rlx")

    expect(screen.getByLabelText("Currency")).toHaveValue("BRL")
  })

  it("turns off mobile autocorrect and capitalizes on soft keyboards", () => {
    render(<CurrencyInput aria-label="Currency" value="" onValueChange={() => {}} />)
    const input = screen.getByLabelText("Currency")

    expect(input).toHaveAttribute("maxlength", "3")
    expect(input).toHaveAttribute("autocapitalize", "characters")
    expect(input).toHaveAttribute("spellcheck", "false")
  })
})
