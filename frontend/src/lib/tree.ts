/**
 * Generic parent/child tree helpers shared by the Accounts and Envelopes
 * pages (both render flat resource lists as expandable trees keyed by a
 * parent-id attribute).
 */
export interface TreeNode<T> {
  item: T
  children: TreeNode<T>[]
}

/** Links a flat list into root nodes; unknown/missing parents become roots. */
export function buildTree<T>(
  items: readonly T[],
  getId: (item: T) => string,
  getParentId: (item: T) => string | null | undefined
): TreeNode<T>[] {
  const nodeById = new Map<string, TreeNode<T>>()
  const roots: TreeNode<T>[] = []

  items.forEach((item) => {
    nodeById.set(getId(item), { item, children: [] })
  })

  items.forEach((item) => {
    const node = nodeById.get(getId(item))!
    const parentId = getParentId(item)
    if (parentId && nodeById.has(parentId)) {
      nodeById.get(parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })

  return roots
}

export function countTreeNodes<T>(nodes: readonly TreeNode<T>[]): number {
  let count = 0
  for (const node of nodes) {
    count += 1 + countTreeNodes(node.children)
  }
  return count
}

/**
 * The given id plus every descendant id, resolved from the flat list.
 * Used to exclude an item's subtree from parent pickers and to count
 * children in delete confirmations.
 */
export function collectDescendantIds<T>(
  items: readonly T[],
  id: string,
  getId: (item: T) => string,
  getParentId: (item: T) => string | null | undefined
): string[] {
  const ids: string[] = [id]
  const walk = (parentId: string) => {
    items
      .filter((item) => getParentId(item) === parentId)
      .forEach((child) => {
        ids.push(getId(child))
        walk(getId(child))
      })
  }
  walk(id)
  return ids
}
