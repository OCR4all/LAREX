import type { TagNode, TagSetMeta, FlattenedTag } from '~/types/tag-set'

interface TagSetBuilderTag extends TagNode {
  expanded?: boolean
  errors?: string[]
}

const meta = reactive<TagSetMeta>({
  name: 'My Tag Set',
  description: '',
  tags: []
})

const tags = ref<TagSetBuilderTag[]>([])
const activeTag = ref<TagSetBuilderTag | null>(null)
const expandedIds = ref<Set<string>>(new Set())
const searchQuery = ref('')

function generateId(): string {
  return `tag-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function generateColor(): string {
  const colors = [
    '#ef4444', '#f97316', '#eab308', '#22c55e', '#14b8a6',
    '#3b82f6', '#6366f1', '#a855f7', '#ec4899', '#f43f5e',
    '#84cc16', '#06b6d4', '#8b5cf6', '#d946ef', '#fb923c'
  ]
  return colors[Math.floor(Math.random() * colors.length)] ?? '#3b82f6'
}

function collectAllTitles(tagList: TagSetBuilderTag[]): Set<string> {
  const titles = new Set<string>()

  const collect = (tags: TagSetBuilderTag[]): void => {
    for (const tag of tags) {
      titles.add(tag.title.trim().toLowerCase())
      if (tag.children) {
        collect(tag.children as TagSetBuilderTag[])
      }
    }
  }

  collect(tagList)
  return titles
}

function generateUniqueTitle(): string {
  const existingTitles = collectAllTitles(tags.value)
  let title = 'Untitled Tag'
  let counter = 1

  while (existingTitles.has(title.toLowerCase())) {
    counter++
    title = `Untitled Tag ${counter}`
  }

  return title
}

function createTag(parentId?: string): TagSetBuilderTag {
  const tag: TagSetBuilderTag = {
    id: generateId(),
    title: generateUniqueTitle(),
    description: '',
    color: generateColor(),
    children: [],
    expanded: true,
    errors: []
  }

  if (parentId) {
    const parent = findTagById(parentId, tags.value)
    if (parent) {
      if (!parent.children) {
        parent.children = []
      }
      parent.children = [...parent.children, tag]
      expandedIds.value.add(parentId)
    }
  } else {
    tags.value = [...tags.value, tag]
  }

  activeTag.value = tag
  return tag
}

function findTagById(id: string, tagList: TagSetBuilderTag[]): TagSetBuilderTag | null {
  for (const tag of tagList) {
    if (tag.id === id) return tag
    if (tag.children) {
      const found = findTagById(id, tag.children as TagSetBuilderTag[])
      if (found) return found
    }
  }
  return null
}

function findParentTag(childId: string, tagList: TagSetBuilderTag[], parent: TagSetBuilderTag | null = null): TagSetBuilderTag | null {
  for (const tag of tagList) {
    if (tag.id === childId) return parent
    if (tag.children) {
      const found = findParentTag(childId, tag.children as TagSetBuilderTag[], tag)
      if (found !== null) return found
    }
  }
  return null
}

function deleteTag(tagId: string): void {
  const rootIdx = tags.value.findIndex(t => t.id === tagId)
  if (rootIdx !== -1) {
    tags.value = tags.value.filter(t => t.id !== tagId)
    if (activeTag.value?.id === tagId) {
      activeTag.value = null
    }
    return
  }

  const parent = findParentTag(tagId, tags.value)
  if (parent && parent.children) {
    parent.children = parent.children.filter(t => t.id !== tagId)
  }

  if (activeTag.value?.id === tagId) {
    activeTag.value = null
  }
}

function duplicateTag(tagId: string): void {
  const original = findTagById(tagId, tags.value)
  if (!original) return

  const cloneTag = (tag: TagSetBuilderTag): TagSetBuilderTag => ({
    ...tag,
    id: generateId(),
    title: tag.title + ' (Copy)',
    children: tag.children?.map(c => cloneTag(c as TagSetBuilderTag))
  })

  const parent = findParentTag(tagId, tags.value)
  const duplicate = cloneTag(original)

  if (parent && parent.children) {
    const idx = parent.children.findIndex(t => t.id === tagId)
    const newChildren = [...parent.children]
    newChildren.splice(idx + 1, 0, duplicate as TagNode)
    parent.children = newChildren
  } else {
    const idx = tags.value.findIndex(t => t.id === tagId)
    const newTags = [...tags.value]
    newTags.splice(idx + 1, 0, duplicate)
    tags.value = newTags
  }

  activeTag.value = duplicate
}

function moveTag(tagId: string, direction: 'up' | 'down'): void {
  const parent = findParentTag(tagId, tags.value)
  const isRoot = !parent
  const list = isRoot ? tags.value : (parent?.children as TagSetBuilderTag[])
  if (!list) return

  const idx = list.findIndex(t => t.id === tagId)

  if (idx === -1) return
  if (direction === 'up' && idx === 0) return
  if (direction === 'down' && idx === list.length - 1) return

  const newIdx = direction === 'up' ? idx - 1 : idx + 1
  const newList = [...list]
  const [tag] = newList.splice(idx, 1)
  if (!tag) return
  newList.splice(newIdx, 0, tag)

  if (isRoot) {
    tags.value = newList
  } else if (parent) {
    parent.children = newList
  }
}

function selectTag(tagId: string): void {
  activeTag.value = findTagById(tagId, tags.value)
}

function toggleExpand(tagId: string): void {
  if (expandedIds.value.has(tagId)) {
    expandedIds.value.delete(tagId)
  } else {
    expandedIds.value.add(tagId)
  }
}

function expandAll(): void {
  const collectIds = (tagList: TagSetBuilderTag[]): void => {
    for (const tag of tagList) {
      if (tag.children && tag.children.length > 0) {
        expandedIds.value.add(tag.id)
        collectIds(tag.children as TagSetBuilderTag[])
      }
    }
  }
  collectIds(tags.value)
}

function collapseAll(): void {
  expandedIds.value.clear()
}

function optimizeColors(): void {
  const palette = [
    '#ef4444', '#f97316', '#eab308', '#22c55e', '#14b8a6',
    '#3b82f6', '#6366f1', '#a855f7', '#ec4899', '#f43f5e',
    '#84cc16', '#06b6d4', '#8b5cf6', '#d946ef', '#fb923c',
    '#0ea5e9', '#10b981', '#f59e0b', '#8b5cf6', '#6366f1'
  ]
  let colorIndex = 0

  const assignColors = (tagList: TagSetBuilderTag[]): void => {
    for (const tag of tagList) {
      tag.color = palette[colorIndex % palette.length] ?? '#3b82f6'
      colorIndex++
      if (tag.children) {
        assignColors(tag.children as TagSetBuilderTag[])
      }
    }
  }

  assignColors(tags.value)
}

function flattenTags(): FlattenedTag[] {
  const result: FlattenedTag[] = []

  const flatten = (tagList: TagSetBuilderTag[], path: string = '', ancestorIds: string[] = []): void => {
    for (const tag of tagList) {
      const currentPath = path ? `${path} > ${tag.title}` : tag.title
      const descendantIds = collectDescendantIds(tag)

      result.push({
        id: tag.id,
        title: tag.title,
        path: currentPath,
        color: tag.color,
        ancestorIds: [...ancestorIds],
        descendantIds
      })

      if (tag.children) {
        flatten(tag.children as TagSetBuilderTag[], currentPath, [...ancestorIds, tag.id])
      }
    }
  }

  flatten(tags.value)
  return result
}

function collectDescendantIds(tag: TagSetBuilderTag): string[] {
  const ids: string[] = []

  const collect = (children: TagNode[] | undefined): void => {
    if (!children) return
    for (const child of children) {
      ids.push(child.id)
      collect(child.children)
    }
  }

  collect(tag.children)
  return ids
}

function countTags(): number {
  let count = 0

  const countRecursive = (tagList: TagSetBuilderTag[]): void => {
    for (const tag of tagList) {
      count++
      if (tag.children) {
        countRecursive(tag.children as TagSetBuilderTag[])
      }
    }
  }

  countRecursive(tags.value)
  return count
}

const filteredTags = computed(() => {
  if (!searchQuery.value.trim()) return tags.value

  const query = searchQuery.value.toLowerCase()

  const filterRecursive = (tagList: TagSetBuilderTag[]): TagSetBuilderTag[] => {
    return tagList.filter((tag) => {
      const matchesTitle = tag.title.toLowerCase().includes(query)
      const matchesDescription = tag.description?.toLowerCase().includes(query)

      if (tag.children && tag.children.length > 0) {
        const filteredChildren = filterRecursive(tag.children as TagSetBuilderTag[])
        if (filteredChildren.length > 0) {
          return true
        }
      }

      return matchesTitle || matchesDescription
    })
  }

  return filterRecursive(tags.value)
})

const totalErrors = computed(() => {
  let count = 0

  const countErrors = (tagList: TagSetBuilderTag[]): void => {
    for (const tag of tagList) {
      if (tag.errors && tag.errors.length > 0) {
        count += tag.errors.length
      }
      if (tag.children) {
        countErrors(tag.children as TagSetBuilderTag[])
      }
    }
  }

  countErrors(tags.value)
  return count
})

function validateTags(): void {
  const seenTitles = new Set<string>()

  const validate = (tagList: TagSetBuilderTag[]): void => {
    for (const tag of tagList) {
      tag.errors = []

      if (!tag.title.trim()) {
        tag.errors.push('Title is required')
      } else {
        const normalizedTitle = tag.title.trim().toLowerCase()
        if (seenTitles.has(normalizedTitle)) {
          tag.errors.push('Tag title must be unique')
        }
        seenTitles.add(normalizedTitle)
      }

      if (tag.children) {
        validate(tag.children as TagSetBuilderTag[])
      }
    }
  }

  validate(tags.value)
}

type TagTitleSnapshot = {
  id: string
  title: string
  children?: TagTitleSnapshot[]
}

function extractTitles(t: TagSetBuilderTag): TagTitleSnapshot {
  return {
    id: t.id,
    title: t.title,
    children: t.children?.map(extractTitles)
  }
}

watch(() => JSON.stringify(tags.value.map(extractTitles)), () => validateTags())

function reset(): void {
  Object.assign(meta, {
    name: 'My Tag Set',
    description: '',
    tags: []
  })
  tags.value = []
  activeTag.value = null
  expandedIds.value.clear()
  searchQuery.value = ''
}

export function useTagSetBuilder() {
  return {
    meta,
    tags,
    activeTag,
    expandedIds,
    searchQuery,
    filteredTags,
    totalErrors,

    createTag,
    deleteTag,
    duplicateTag,
    moveTag,
    selectTag,
    toggleExpand,
    expandAll,
    collapseAll,
    optimizeColors,
    flattenTags,
    countTags,
    validateTags,
    reset,
    findTagById
  }
}
