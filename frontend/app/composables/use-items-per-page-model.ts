import type { ComputedRef, Ref } from 'vue'

type ItemsPerPageOption = {
  value?: unknown
}

function normalizeItemsPerPage(value: number | string | ItemsPerPageOption): number | null {
  const rawValue = typeof value === 'object' && value !== null && 'value' in value
    ? value.value
    : value

  const normalizedValue = typeof rawValue === 'string' ? Number(rawValue) : rawValue

  if (typeof normalizedValue !== 'number' || !Number.isFinite(normalizedValue) || normalizedValue <= 0) {
    return null
  }

  return normalizedValue
}

export function useItemsPerPageModel(
  page: Ref<number>,
  itemsPerPage: Ref<number>,
  totalItems: Ref<number> | ComputedRef<number>
) {
  return computed({
    get: () => itemsPerPage.value,
    set: (value: number | string | ItemsPerPageOption) => {
      const nextItemsPerPage = normalizeItemsPerPage(value)
      if (!nextItemsPerPage || nextItemsPerPage === itemsPerPage.value) {
        return
      }

      const firstVisibleItemIndex = Math.max(0, (page.value - 1) * itemsPerPage.value)
      const maxPage = Math.max(1, Math.ceil(totalItems.value / nextItemsPerPage))

      page.value = Math.min(maxPage, Math.floor(firstVisibleItemIndex / nextItemsPerPage) + 1)
      itemsPerPage.value = nextItemsPerPage
    }
  })
}
