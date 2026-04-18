<script setup lang="ts">
import type { TableColumn, TableRow } from '@nuxt/ui'
import { h, resolveComponent } from 'vue'

import type { Glyph } from '~/types/virtual-keyboard'
import { GlyphService } from '~/utils/glyph-service'
import { isPUA } from '~/utils/unicode'

const SEARCH_DEBOUNCE_MS = 300
const PAGE_SIZE = 100
const TABLE_ROW_ESTIMATE = 56
const TABLE_OVERSCAN = 12
const LOAD_MORE_THRESHOLD = 240

const props = defineProps<{ title?: string }>()
const emit = defineEmits<{ close: [Glyph | null] }>()
const UBadge = resolveComponent('UBadge')

type GlyphTableRow = {
  id: string
  character: string
  codepoint: string
  description: string
  type: 'Unicode' | 'MUFI'
  pua: 'Yes' | 'No'
  glyph: Glyph
}

const query = ref('')
const sources = reactive({ mufi: true, unicode: false })
const puaOnly = ref(false)
const results = ref<Glyph[]>([])
const loading = ref(false)
const page = ref(0)
const hasMore = ref(false)
const total = ref(0)
const tableRef = ref<{ $el?: HTMLElement | null } | null>(null)

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let searchRequestId = 0

const tableRows = computed<GlyphTableRow[]>(() =>
  results.value.map(glyph => ({
    id: `${glyph.codepoint}-${glyph.source}`,
    character: glyph.utf8,
    codepoint: glyph.codepoint,
    description: glyph.description,
    type: glyph.source === 'mufi' ? 'MUFI' : 'Unicode',
    pua: isPUA(glyph.codepoint) ? 'Yes' : 'No',
    glyph
  }))
)

const columns: TableColumn<GlyphTableRow>[] = [
  {
    accessorKey: 'character',
    header: 'Character',
    cell: ({ row }) => h('span', { class: 'font-junicode text-2xl leading-none' }, row.original.character),
    meta: {
      class: {
        td: 'font-junicode whitespace-nowrap'
      }
    }
  },
  {
    accessorKey: 'codepoint',
    header: 'Codepoint',
    cell: ({ row }) => h(UBadge, {
      color: 'neutral',
      variant: 'soft',
      class: 'font-mono text-xs'
    }, () => row.original.codepoint),
    meta: {
      class: {
        td: 'font-mono text-xs whitespace-nowrap'
      }
    }
  },
  {
    accessorKey: 'description',
    header: 'Description',
    cell: ({ row }) => h('span', {
      class: 'block truncate',
      title: row.original.description
    }, row.original.description),
    meta: {
      class: {
        td: 'max-w-0 w-full'
      }
    }
  },
  {
    accessorKey: 'type',
    header: 'Type',
    cell: ({ row }) => h(UBadge, {
      color: row.original.glyph.source === 'mufi' ? 'warning' : 'info',
      variant: 'soft'
    }, () => row.original.type)
  },
  {
    accessorKey: 'pua',
    header: 'PUA',
    cell: ({ row }) => h(UBadge, {
      color: row.original.pua === 'Yes' ? 'warning' : 'neutral',
      variant: row.original.pua === 'Yes' ? 'soft' : 'subtle'
    }, () => row.original.pua)
  }
]

function getTableScrollElement(): HTMLElement | null {
  const rootElement = tableRef.value?.$el
  return rootElement instanceof HTMLElement ? rootElement : null
}

function maybeLoadMore(scrollElement: HTMLElement | null = getTableScrollElement()) {
  if (!scrollElement || loading.value || !hasMore.value) return

  const remainingScroll = scrollElement.scrollHeight - scrollElement.scrollTop - scrollElement.clientHeight
  if (remainingScroll <= LOAD_MORE_THRESHOLD) {
    void performSearch(false)
  }
}

function handleTableScroll(event: Event) {
  const target = event.target
  maybeLoadMore(target instanceof HTMLElement ? target : null)
}

function handleRowSelect(_event: Event, row: TableRow<GlyphTableRow>) {
  emit('close', row.original.glyph)
}

const performSearch = async (reset = false) => {
  if (loading.value && !reset) return

  if (reset) {
    page.value = 0
    results.value = []
    total.value = 0
    hasMore.value = false
    getTableScrollElement()?.scrollTo({ top: 0 })
  }

  const requestId = ++searchRequestId
  const nextPage = reset ? 0 : page.value + 1
  let shouldCheckForMore = false
  loading.value = true

  try {
    const res = await GlyphService.search(query.value, sources, nextPage, PAGE_SIZE, {
      isPua: puaOnly.value || undefined
    })
    if (requestId !== searchRequestId) return

    page.value = nextPage
    results.value = reset ? res.data : [...results.value, ...res.data]
    total.value = res.total
    hasMore.value = res.hasMore
    shouldCheckForMore = true
  } finally {
    if (requestId === searchRequestId) {
      loading.value = false
      if (shouldCheckForMore) {
        await nextTick()
        maybeLoadMore()
      }
    }
  }
}

function scheduleResetSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    void performSearch(true)
  }, SEARCH_DEBOUNCE_MS)
}

watch(query, scheduleResetSearch)
watch([() => sources.mufi, () => sources.unicode], () => {
  void performSearch(true)
})
watch(puaOnly, () => {
  void performSearch(true)
})

onMounted(() => {
  void performSearch(true)
})

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})

const headerTitle = computed(() => props.title || `Glyph Picker${total.value ? ` (${total.value})` : ''}`)
</script>

<template>
  <USlideover
    :ui="{ content: 'max-w-7/8 md:max-w-6/8 xl:max-w-4/8' }"
    side="left"
    :title="headerTitle"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #body>
      <div class="flex h-full min-h-0 flex-col gap-4">
        <div class="space-y-3 px-2">
          <UInput v-model="query" placeholder="Search by name, hex, or char..." autofocus />
          <div class="flex gap-4 text-xs">
            <UCheckbox v-model="sources.mufi" label="MUFI" />
            <UCheckbox v-model="sources.unicode" label="Unicode" />
            <UCheckbox v-model="puaOnly" label="PUA" />
          </div>
        </div>

        <div class="flex min-h-0 flex-1 flex-col px-2 pb-2">
          <AppTable
            table-id="virtual-keyboard-glyph-picker"
            ref="tableRef"
            :data="tableRows"
            :columns="columns"
            :empty="loading && tableRows.length === 0 ? 'Loading...' : 'No glyphs found.'"
            :virtualize="{ estimateSize: TABLE_ROW_ESTIMATE, overscan: TABLE_OVERSCAN }"
            :on-select="handleRowSelect"
            class="min-h-0 flex-1"
            @scroll.passive="handleTableScroll"
          />
          <div v-if="loading && results.length > 0" class="py-3 text-center text-xs text-muted">
            Loading more...
          </div>
        </div>
      </div>
    </template>
  </USlideover>
</template>
