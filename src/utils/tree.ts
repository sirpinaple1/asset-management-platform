/**
 * 扁平列表（parentId 关联）→ 树形结构。
 * 供分类/位置的 el-tree-select 使用（后端返回扁平列表，前端组树）。
 * 悬挂节点（parentId 指向不存在的记录）按根节点处理，避免整树丢失。
 */
export function buildTree<T extends { id: number; parentId?: number | null; name: string }>(
  list: T[],
): Array<T & { children?: T[] }> {
  type Node = T & { children?: Node[] }
  const nodes = new Map<number, Node>()
  list.forEach((item) => nodes.set(item.id, { ...item }))

  const roots: Node[] = []
  list.forEach((item) => {
    const node = nodes.get(item.id)!
    const parent = item.parentId != null ? nodes.get(item.parentId) : undefined
    if (parent) {
      ;(parent.children ??= []).push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}
