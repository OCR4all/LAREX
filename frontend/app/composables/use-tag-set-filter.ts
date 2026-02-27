import type { FlattenedTag } from '~/types/tag-set'

interface TagSetFilterOptions {
  workspaceId: string
  tagSetId: string | null
}

export function useTagSetFilter(options: TagSetFilterOptions) {
  const { workspaceId, tagSetId } = options

  const { data: flattenedTags, status, refresh } = useFetch<FlattenedTag[]>(
    () => tagSetId ? `/api/workspaces/${workspaceId}/tag-sets/${tagSetId}/flattened` : '',
    {
      key: `tag-set-filter-${workspaceId}-${tagSetId}`,
      immediate: !!tagSetId,
      default: () => []
    }
  )

  const selectedTagIds = ref<string[]>([])

  const filterMode = ref<'direct' | 'fuzzy'>('fuzzy')

  const expandedTagIds = computed(() => {
    if (!flattenedTags.value || selectedTagIds.value.length === 0) {
      return selectedTagIds.value
    }

    if (filterMode.value === 'direct') {
      return selectedTagIds.value
    }

    const expanded = new Set<string>(selectedTagIds.value)

    for (const tagId of selectedTagIds.value) {
      const tag = flattenedTags.value.find(t => t.id === tagId)
      if (tag?.descendantIds) {
        for (const descendantId of tag.descendantIds) {
          expanded.add(descendantId)
        }
      }
    }

    return Array.from(expanded)
  })

  const getTagById = (tagId: string): FlattenedTag | undefined => {
    return flattenedTags.value?.find(t => t.id === tagId)
  }

  const getTagTitle = (tagId: string): string => {
    return getTagById(tagId)?.title || tagId
  }

  const getTagPath = (tagId: string): string => {
    return getTagById(tagId)?.path || tagId
  }

  const getTagColor = (tagId: string): string => {
    return getTagById(tagId)?.color || '#6b7280'
  }

  const matchesFilter = (projectTagIds: string[]): boolean => {
    if (expandedTagIds.value.length === 0) return true
    return projectTagIds.some(tagId => expandedTagIds.value.includes(tagId))
  }

  const filterProjects = <T extends { tags?: string[] }>(projects: T[]): T[] => {
    if (expandedTagIds.value.length === 0) return projects
    return projects.filter(project => matchesFilter(project.tags || []))
  }

  const clearFilter = () => {
    selectedTagIds.value = []
  }

  const toggleTag = (tagId: string) => {
    const index = selectedTagIds.value.indexOf(tagId)
    if (index === -1) {
      selectedTagIds.value.push(tagId)
    } else {
      selectedTagIds.value.splice(index, 1)
    }
  }

  const isTagSelected = (tagId: string): boolean => {
    return selectedTagIds.value.includes(tagId)
  }

  const selectOptions = computed(() => {
    if (!flattenedTags.value) return []

    return flattenedTags.value.map(tag => ({
      label: tag.title,
      value: tag.id,
      path: tag.path,
      color: tag.color,
      descendantCount: tag.descendantIds.length
    }))
  })

  const hasActiveFilter = computed(() => selectedTagIds.value.length > 0)

  const isLoading = computed(() => status.value === 'pending')

  return {
    flattenedTags,
    selectedTagIds,
    filterMode,
    expandedTagIds,

    selectOptions,
    hasActiveFilter,
    isLoading,

    getTagById,
    getTagTitle,
    getTagPath,
    getTagColor,
    matchesFilter,
    filterProjects,
    clearFilter,
    toggleTag,
    isTagSelected,
    refresh
  }
}
