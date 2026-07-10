import { describe, it, expect, afterEach } from "vitest"
import i18n from "@/i18n"
import { cn, formatCurrency, formatDate, slugify } from "./utils"

// Formatting follows the app language (test setup pins it to "en").
afterEach(async () => {
  await i18n.changeLanguage("en")
})

describe("cn", () => {
  it("merges class names", () => {
    expect(cn("foo", "bar")).toBe("foo bar")
  })

  it("handles conditional classes", () => {
    const condition = false
    expect(cn("foo", condition && "bar", "baz")).toBe("foo baz")
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
  it("formats with the pt-BR locale when the app language is pt-BR", async () => {
    await i18n.changeLanguage("pt-BR")
    expect(formatCurrency(1234.56, "BRL")).toMatch(/R\$\s*1\.234,56/)
    expect(formatCurrency(1234.56, "USD")).toMatch(/US\$\s*1\.234,56/)
    expect(formatCurrency(-50.25, "BRL")).toMatch(/-R\$\s*50,25/)
    expect(formatCurrency(1000000, "BRL")).toMatch(/R\$\s*1\.000\.000,00/)
  })

  it("formats with the en-US locale when the app language is en", () => {
    expect(formatCurrency(1234.56, "BRL")).toMatch(/R\$\s*1,234\.56/)
    expect(formatCurrency(1234.56, "USD")).toMatch(/\$\s*1,234\.56/)
  })

  it("defaults to BRL", () => {
    expect(formatCurrency(100)).toMatch(/R\$\s*100\.00/)
  })

  it("handles zero", () => {
    expect(formatCurrency(0, "BRL")).toMatch(/R\$\s*0\.00/)
  })
})

describe("formatDate", () => {
  it("formats with the pt-BR locale when the app language is pt-BR", async () => {
    await i18n.changeLanguage("pt-BR")
    // Month is 0-indexed, so 0 = January
    expect(formatDate(new Date(2024, 0, 15, 12, 0, 0))).toBe("15 de jan. de 2024")
  })

  it("formats with the en-US locale when the app language is en", () => {
    expect(formatDate(new Date(2024, 0, 15, 12, 0, 0))).toBe("Jan 15, 2024")
  })

  it("parses date-only strings as local time to avoid timezone shifts", () => {
    // "2024-06-30" must stay June 30 in local time, not shift via UTC
    const result = formatDate("2024-06-30T12:00:00Z")
    expect(result).toContain("2024")
    expect(result).toContain("Jun")
    expect(result).toContain("30")
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
