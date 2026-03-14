import { h, type Component, type Ref } from 'vue'

type SortState = Ref<{ column: string, direction: 'asc' | 'desc' }>

type ButtonLike = Component

type SortHeaderOptions = {
  align?: 'start' | 'end'
}

type SimpleTagComponents = {
  UBadge: Component
  UButton: Component
  UPopover: Component
}

type DropdownComponents = {
  UButton: Component
  UDropdownMenu: Component
}

export function createSortableHeader(
  label: string,
  column: string,
  sort: SortState,
  UButton: ButtonLike,
  options?: SortHeaderOptions
) {
  return () => {
    const justifyClass = options?.align === 'end' ? 'justify-end' : ''
    return h('div', { class: `flex items-center gap-2 ${justifyClass}`.trim() }, [
      h('span', label),
      h(UButton, {
        icon: sort.value.column === column
          ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
          : 'i-lucide-arrow-up-down',
        size: 'xs',
        variant: 'ghost',
        color: sort.value.column === column ? 'primary' : 'neutral',
        onClick: () => {
          if (sort.value.column === column) {
            sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
          } else {
            sort.value = { column, direction: 'asc' }
          }
        }
      })
    ])
  }
}

export function renderTruncatedText(value: string | null | undefined, emptyState = '—') {
  if (!value) {
    return h('div', { class: 'text-neutral-400 dark:text-neutral-500 text-sm' }, emptyState)
  }

  return h('div', {
    class: 'text-neutral-700 dark:text-neutral-400 max-w-32 sm:max-w-48 lg:max-w-64 xl:max-w-80 truncate',
    title: value
  }, value)
}

export function renderSimpleTagCell(
  tags: string[] | undefined,
  components: SimpleTagComponents
) {
  if (!tags || tags.length === 0) return null

  if (tags.length <= 3) {
    return h('div', { class: 'flex flex-wrap gap-1' },
      tags.map(tag =>
        h(components.UBadge, {
          variant: 'subtle',
          color: 'primary',
          size: 'md',
          key: tag
        }, () => tag)
      )
    )
  }

  const visibleTags = tags.slice(0, 2)
  const hiddenTags = tags.slice(2)

  return h('div', { class: 'flex flex-wrap items-center gap-1' }, [
    ...visibleTags.map(tag =>
      h(components.UBadge, {
        variant: 'soft',
        color: 'neutral',
        size: 'sm',
        key: tag
      }, () => tag)
    ),
    h(components.UPopover, { mode: 'hover' }, {
      default: () => h(components.UButton, {
        variant: 'soft',
        color: 'primary',
        size: 'sm',
        class: 'h-[22px]'
      }, () => `+${hiddenTags.length}`),
      content: () => h('div', { class: 'p-2 flex flex-col gap-1' },
        hiddenTags.map(tag =>
          h(components.UBadge, {
            variant: 'soft',
            color: 'neutral',
            size: 'sm',
            key: tag
          }, () => tag)
        )
      )
    })
  ])
}

export function renderDropdownActionsCell(items: unknown[], components: DropdownComponents) {
  if (items.length === 0) return null

  return h(
    'div',
    { class: 'text-right' },
    h(
      components.UDropdownMenu,
      {
        content: { align: 'end' },
        items,
        'aria-label': 'Actions dropdown'
      },
      () => h(components.UButton, {
        icon: 'i-lucide-ellipsis-vertical',
        color: 'neutral',
        variant: 'ghost',
        class: 'ml-auto',
        'aria-label': 'Actions dropdown'
      })
    )
  )
}
