import { describe, it, expect } from "vitest"
import { buildTree, countTreeNodes, collectDescendantIds } from "./tree"

interface Item {
  id: string
  parentId: string | null
}

const getId = (item: Item) => item.id
const getParentId = (item: Item) => item.parentId

const item = (id: string, parentId: string | null = null): Item => ({ id, parentId })

describe("buildTree", () => {
  it("returns an empty forest for no items", () => {
    expect(buildTree<Item>([], getId, getParentId)).toEqual([])
  })

  it("links children under their parents", () => {
    const items = [item("a"), item("a1", "a"), item("a2", "a"), item("b")]
    const roots = buildTree(items, getId, getParentId)

    expect(roots.map((n) => n.item.id)).toEqual(["a", "b"])
    expect(roots[0].children.map((n) => n.item.id)).toEqual(["a1", "a2"])
    expect(roots[1].children).toEqual([])
  })

  it("nests grandchildren recursively", () => {
    const roots = buildTree([item("a"), item("a1", "a"), item("a1x", "a1")], getId, getParentId)
    expect(roots[0].children[0].children[0].item.id).toBe("a1x")
  })

  it("treats items with unknown parents as roots", () => {
    const roots = buildTree([item("orphan", "missing")], getId, getParentId)
    expect(roots.map((n) => n.item.id)).toEqual(["orphan"])
  })
})

describe("countTreeNodes", () => {
  it("counts every node in the forest", () => {
    const roots = buildTree(
      [item("a"), item("a1", "a"), item("a1x", "a1"), item("b")],
      getId,
      getParentId
    )
    expect(countTreeNodes(roots)).toBe(4)
    expect(countTreeNodes([])).toBe(0)
  })
})

describe("collectDescendantIds", () => {
  const items = [item("a"), item("a1", "a"), item("a2", "a"), item("a1x", "a1"), item("b")]

  it("returns the id plus all descendant ids", () => {
    expect(collectDescendantIds(items, "a", getId, getParentId)).toEqual(["a", "a1", "a1x", "a2"])
  })

  it("returns just the id for a leaf", () => {
    expect(collectDescendantIds(items, "b", getId, getParentId)).toEqual(["b"])
  })
})
