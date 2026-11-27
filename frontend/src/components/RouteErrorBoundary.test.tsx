import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import { RouteErrorBoundary } from "./RouteErrorBoundary"

function Boom(): never {
  throw new Error("render failed")
}

beforeEach(() => {
  // React and the boundary both log the caught error; keep test output clean.
  vi.spyOn(console, "error").mockImplementation(() => {})
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe("RouteErrorBoundary", () => {
  it("renders its children when nothing fails", () => {
    render(
      <RouteErrorBoundary resetKey="/a">
        <p>page</p>
      </RouteErrorBoundary>
    )

    expect(screen.getByText("page")).toBeInTheDocument()
  })

  it("replaces a failing page with a reload prompt", () => {
    render(
      <RouteErrorBoundary resetKey="/a">
        <Boom />
      </RouteErrorBoundary>
    )

    expect(screen.getByRole("alert")).toHaveTextContent("Something went wrong")
    expect(screen.getByRole("button", { name: "Reload" })).toBeInTheDocument()
  })

  it("recovers when the user navigates to another route", () => {
    const { rerender } = render(
      <RouteErrorBoundary resetKey="/a">
        <Boom />
      </RouteErrorBoundary>
    )

    rerender(
      <RouteErrorBoundary resetKey="/b">
        <p>other page</p>
      </RouteErrorBoundary>
    )

    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
    expect(screen.getByText("other page")).toBeInTheDocument()
  })
})
