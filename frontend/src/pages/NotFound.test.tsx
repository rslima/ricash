import { describe, expect, it } from "vitest"
import { render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router"
import { NotFound } from "./NotFound"

describe("NotFound", () => {
  it("catches unknown addresses and links back to the dashboard", () => {
    render(
      <MemoryRouter initialEntries={["/no-such-page"]}>
        <Routes>
          <Route path="/" element={<p>dashboard</p>} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText("Page not found")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Go to the dashboard" })).toHaveAttribute("href", "/")
  })
})
