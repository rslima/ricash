import { describe, it, expect } from "vitest"
import { cn, formatCurrency, formatDate, slugify } from "./utils"

describe("cn", () => {
  it("merges class names", () => {
    expect(cn("foo", "bar")).toBe("foo bar")
  })

  it("handles conditional classes", () => {
    expect(cn("foo", false && "bar", "baz")).toBe("foo baz")
  })

  it("merges tailwind classes correctly", () => {
    expect(cn("px-2", "px-4")).toBe("px-4")
  })

  it("handles arrays", () => {
    expect(cn(["foo", "bar"])).toBe("foo bar")
  })

  it("handles undefined and null", () => {
    expect(cn("foo", undefined, null, "bar")).toBe("foo bar")
  })
})

describe("formatCurrency", () => {
  it("formats BRL currency with Brazilian format", () => {
    expect(formatCurrency(1234.56, "BRL")).toMatch(/R\$\s*1\.234,56/)
  })

  it("formats USD currency with Brazilian format", () => {
    expect(formatCurrency(1234.56, "USD")).toMatch(/US\$\s*1\.234,56/)
  })

  it("defaults to BRL", () => {
    expect(formatCurrency(100)).toMatch(/R\$\s*100,00/)
  })

  it("handles zero", () => {
    expect(formatCurrency(0, "BRL")).toMatch(/R\$\s*0,00/)
  })

  it("handles negative numbers", () => {
    expect(formatCurrency(-50.25, "BRL")).toMatch(/-R\$\s*50,25/)
  })

  it("handles large numbers", () => {
    expect(formatCurrency(1000000, "BRL")).toMatch(/R\$\s*1\.000\.000,00/)
  })
})

describe("formatDate", () => {
  it("formats date string with Brazilian format", () => {
    // Use ISO format with time to avoid timezone issues
    const result = formatDate("2024-01-15T12:00:00Z")
    expect(result).toContain("2024")
    expect(result).toContain("jan")
    expect(result).toContain("15")
  })

  it("formats Date object with Brazilian format", () => {
    // Month is 0-indexed, so 0 = January
    const result = formatDate(new Date(2024, 0, 15, 12, 0, 0))
    expect(result).toBe("15 de jan. de 2024")
  })

  it("formats ISO date string with Brazilian format", () => {
    const result = formatDate("2024-06-30T12:00:00Z")
    expect(result).toContain("2024")
    expect(result).toContain("jun")
  })
})

describe("slugify", () => {
  it("converts text to lowercase", () => {
    expect(slugify("Hello World")).toBe("hello-world")
  })

  it("replaces spaces with dashes", () => {
    expect(slugify("personal finance")).toBe("personal-finance")
  })

  it("removes special characters", () => {
    expect(slugify("Hello! World?")).toBe("hello-world")
  })

  it("handles accented characters", () => {
    expect(slugify("Café Résumé")).toBe("cafe-resume")
  })

  it("handles multiple spaces", () => {
    expect(slugify("hello   world")).toBe("hello-world")
  })

  it("trims leading and trailing spaces", () => {
    expect(slugify("  hello world  ")).toBe("hello-world")
  })

  it("handles numbers", () => {
    expect(slugify("Project 2024")).toBe("project-2024")
  })
})
