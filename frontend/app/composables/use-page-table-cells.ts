import type { Ref } from 'vue'
import type { TableSort } from '@/composables/use-table-filters'
import type { Page } from '@/types/project-page'
import UiColorTag from '@/components/ui/color-tag.vue'

const DEFAULT_TAG_COLOR = '#2563eb'

export function usePageTableCells(sort: Ref<TableSort>) {
  const UBadge = resolveComponent('UBadge')
  const UButton = resolveComponent('UButton')
  const UPopover = resolveComponent('UPopover')

  function toggleSort(column: string, initialDirection: TableSort['direction']) {
    if (sort.value.column === column) {
      sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
    } else {
      sort.value = { column, direction: initialDirection }
    }
  }

  function sortButton(column: string, initialDirection: TableSort['direction']) {
    const active = sort.value.column === column
    return h(UButton, {
      icon: active
        ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
        : 'i-lucide-arrow-up-down',
      size: 'xs',
      variant: 'ghost',
      color: active ? 'primary' : 'neutral',
      onClick: () => toggleSort(column, initialDirection)
    })
  }

  function sortableHeader(
    label: string,
    column: string,
    options: { initialDirection?: TableSort['direction'], align?: 'start' | 'center' } = {}
  ) {
    const alignClass = options.align === 'center' ? 'justify-center' : ''
    return () => h('div', { class: ['flex items-center gap-2', alignClass] }, [
      h('span', label),
      sortButton(column, options.initialDirection ?? 'asc')
    ])
  }

  function tagsCell(page: Pick<Page, 'tags' | 'resolvedTags'>) {
    const tags = page.tags
    if (!tags || tags.length === 0) return null

    const displayTags = page.resolvedTags && page.resolvedTags.length > 0
      ? page.resolvedTags.map(tag => ({ label: tag.label || tag.id, color: tag.color || DEFAULT_TAG_COLOR }))
      : tags.map(tagId => ({ label: tagId, color: DEFAULT_TAG_COLOR }))

    const renderTagBadge = (tag: { label: string, color: string }, index: number) => h(
      UiColorTag,
      { key: `${tag.label}-${index}`, color: tag.color, variant: 'subtle', size: 'sm' },
      () => tag.label
    )

    if (displayTags.length <= 2) {
      return h('div', { class: 'flex flex-wrap gap-1' }, displayTags.map(renderTagBadge))
    }

    return h('div', { class: 'flex flex-wrap gap-1' }, [
      ...displayTags.slice(0, 2).map(renderTagBadge),
      h(UPopover, { mode: 'hover' }, {
        default: () => h(UBadge, {
          'color': 'primary',
          'variant': 'subtle',
          'size': 'sm',
          'tabindex': 0,
          'class': 'cursor-help',
          'aria-label': `Show all ${displayTags.length} tags`
        }, () => `+${displayTags.length - 2}`),
        content: () => h('div', { class: 'p-2 flex flex-col gap-1' }, displayTags.map(renderTagBadge))
      })
    ])
  }

  return { sortButton, sortableHeader, tagsCell }
}
