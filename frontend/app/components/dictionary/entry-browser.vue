<script setup lang="ts">
import { useVirtualizer, type VirtualItem } from '@tanstack/vue-virtual'
import type { DictionaryEntry, DictionaryEntryPageResponse } from '@/types/dictionary'

type EditableDictionaryEntry = {
  form: string
  sourceEntryKey: string
  metadata: Record<string, unknown> | null
}

const props = withDefaults(defineProps<{
  workspaceId: string
  dictionaryId?: string | null
  editable?: boolean
  heightClass?: string
}>(), {
  dictionaryId: null,
  editable: false,
  heightClass: 'h-[32rem]'
})

const emit = defineEmits<{
  changed: []
  stats: [payload: { totalEntries: number }]
}>()

const PAGE_SIZE = 100
const OVERSCAN = 8
const LOAD_AHEAD_THRESHOLD = 20
const ROW_HEIGHT = 88

const toast = useToast()
const dictionaryKey = computed(() => props.dictionaryId ? wsKey(props.workspaceId, 'dictionaries', props.dictionaryId) : '')
const dictionariesListKey = computed(() => wsKey(props.workspaceId, 'dictionaries', 'list'))

const searchInput = ref('')
const debouncedSearch = ref('')
const entries = ref<DictionaryEntry[]>([])
const totalEntries = ref(0)
const totalPages = ref(0)
const currentPage = ref(0)
const isLoading = ref(false)
const isLoadingMore = ref(false)
const hasLoaded = ref(false)
const isAddingEntry = ref(false)
const savingEntryIds = ref<Set<string>>(new Set())
const deletingEntryIds = ref<Set<string>>(new Set())

const pendingEntry = reactive<EditableDictionaryEntry>({
  form: '',
  sourceEntryKey: '',
  metadata: null
})

const editingEntryId = ref<string | null>(null)
const editingEntry = reactive<EditableDictionaryEntry>({
  form: '',
  sourceEntryKey: '',
  metadata: null
})

const scrollerRef = ref<HTMLElement | null>(null)
let resizeObserver: ResizeObserver | null = null

const canEdit = computed(() => props.editable && Boolean(props.workspaceId) && Boolean(props.dictionaryId))
const canLoad = computed(() => Boolean(props.workspaceId) && Boolean(props.dictionaryId))
const hasMore = computed(() => currentPage.value + 1 < totalPages.value)
const virtualCount = computed(() => entries.value.length + (hasMore.value ? 1 : 0))
const fillsParentHeight = computed(() => props.heightClass === 'h-full')

let debounceTimer: ReturnType<typeof setTimeout> | null = null

watch(searchInput, (value) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    debouncedSearch.value = value.trim()
  }, 180)
})

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})

const rowVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: virtualCount.value,
  getScrollElement: () => scrollerRef.value,
  estimateSize: () => ROW_HEIGHT,
  overscan: OVERSCAN,
  getItemKey: index => entries.value[index]?.id ?? `loader-${index}`
})))

const virtualRows = computed<Array<{ item: VirtualItem, entry?: DictionaryEntry }>>(() =>
  rowVirtualizer.value.getVirtualItems().map(item => ({
    item,
    entry: entries.value[item.index]
  }))
)

const totalSize = computed(() => rowVirtualizer.value.getTotalSize())

async function fetchEntries(page: number, append: boolean) {
  if (!canLoad.value || !props.dictionaryId) {
    entries.value = []
    totalEntries.value = 0
    totalPages.value = 0
    currentPage.value = 0
    emit('stats', { totalEntries: 0 })
    return
  }

  if (append ? isLoadingMore.value : isLoading.value) return
  if (append) {
    isLoadingMore.value = true
  } else {
    isLoading.value = true
  }

  try {
    const response = await $fetch<DictionaryEntryPageResponse>(
      `/api/workspaces/${props.workspaceId}/dictionaries/${props.dictionaryId}/entries`,
      {
        query: {
          page,
          size: PAGE_SIZE,
          search: debouncedSearch.value || undefined
        }
      }
    )

    const nextEntries = response.entries ?? []
    entries.value = append
      ? [...entries.value, ...nextEntries.filter(entry => !entries.value.some(existing => existing.id === entry.id))]
      : nextEntries
    totalEntries.value = response.totalEntries ?? entries.value.length
    totalPages.value = response.totalPages ?? 0
    currentPage.value = response.page ?? page
    hasLoaded.value = true
    emit('stats', { totalEntries: totalEntries.value })
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to load dictionary entries',
      description: extractApiErrorMessage(error, 'Failed to load dictionary entries'),
      color: 'error'
    })
  } finally {
    isLoading.value = false
    isLoadingMore.value = false
  }
}

async function refresh(resetScroll = false) {
  await fetchEntries(0, false)
  await nextTick()
  rowVirtualizer.value.measure()
  if (resetScroll) {
    scrollerRef.value?.scrollTo({ top: 0 })
  }
}

async function fetchNextPage() {
  if (!hasMore.value || !canLoad.value) return
  await fetchEntries(currentPage.value + 1, true)
}

watch([() => props.workspaceId, () => props.dictionaryId, debouncedSearch], async () => {
  editingEntryId.value = null
  await refresh(true)
}, { immediate: true })

onMounted(() => {
  if (typeof ResizeObserver === 'undefined' || !scrollerRef.value) return
  resizeObserver = new ResizeObserver(() => {
    rowVirtualizer.value.measure()
  })
  resizeObserver.observe(scrollerRef.value)
})

watch(virtualRows, (rows) => {
  const lastRow = rows.at(-1)
  if (!lastRow) return
  if (lastRow.item.index >= entries.value.length - LOAD_AHEAD_THRESHOLD) {
    void fetchNextPage()
  }
})

function startEdit(entry: DictionaryEntry) {
  editingEntryId.value = entry.id
  editingEntry.form = entry.form
  editingEntry.sourceEntryKey = entry.sourceEntryKey || ''
  editingEntry.metadata = entry.metadata || null
}

async function addEntry() {
  if (!canEdit.value || !props.dictionaryId || !pendingEntry.form.trim()) return
  if (isAddingEntry.value) return
  try {
    isAddingEntry.value = true
    await $fetch(`/api/workspaces/${props.workspaceId}/dictionaries/${props.dictionaryId}/entries`, {
      method: 'POST',
      body: {
        form: pendingEntry.form.trim(),
        sourceEntryKey: pendingEntry.sourceEntryKey?.trim() || null,
        metadata: pendingEntry.metadata || null
      }
    })
    pendingEntry.form = ''
    pendingEntry.sourceEntryKey = ''
    pendingEntry.metadata = null
    emit('changed')
    await Promise.all([
      refresh(false),
      dictionaryKey.value ? refreshNuxtData(dictionaryKey.value) : Promise.resolve(),
      refreshNuxtData(dictionariesListKey.value)
    ])
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to add entry',
      description: extractApiErrorMessage(error, 'Failed to add entry'),
      color: 'error'
    })
  } finally {
    isAddingEntry.value = false
  }
}

async function saveEntry(entryId: string) {
  if (!canEdit.value || !props.dictionaryId) return
  if (savingEntryIds.value.has(entryId)) return
  savingEntryIds.value = new Set(savingEntryIds.value).add(entryId)
  try {
    await $fetch(`/api/workspaces/${props.workspaceId}/dictionaries/${props.dictionaryId}/entries/${entryId}`, {
      method: 'PUT',
      body: {
        form: editingEntry.form.trim(),
        sourceEntryKey: editingEntry.sourceEntryKey?.trim() || null,
        metadata: editingEntry.metadata || null
      }
    })
    editingEntryId.value = null
    emit('changed')
    await Promise.all([
      refresh(false),
      dictionaryKey.value ? refreshNuxtData(dictionaryKey.value) : Promise.resolve(),
      refreshNuxtData(dictionariesListKey.value)
    ])
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to save entry',
      description: extractApiErrorMessage(error, 'Failed to save entry'),
      color: 'error'
    })
  } finally {
    const next = new Set(savingEntryIds.value)
    next.delete(entryId)
    savingEntryIds.value = next
  }
}

async function removeEntry(entryId: string) {
  if (!canEdit.value || !props.dictionaryId) return
  if (deletingEntryIds.value.has(entryId)) return
  deletingEntryIds.value = new Set(deletingEntryIds.value).add(entryId)
  try {
    await $fetch(`/api/workspaces/${props.workspaceId}/dictionaries/${props.dictionaryId}/entries/${entryId}`, {
      method: 'DELETE'
    })
    emit('changed')
    await Promise.all([
      refresh(false),
      dictionaryKey.value ? refreshNuxtData(dictionaryKey.value) : Promise.resolve(),
      refreshNuxtData(dictionariesListKey.value)
    ])
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to delete entry',
      description: extractApiErrorMessage(error, 'Failed to delete entry'),
      color: 'error'
    })
  } finally {
    const next = new Set(deletingEntryIds.value)
    next.delete(entryId)
    deletingEntryIds.value = next
  }
}

defineExpose({
  refresh
})
</script>

<template>
  <div :class="fillsParentHeight ? 'flex h-full min-h-0 flex-col gap-3' : 'space-y-3'">
    <div class="flex items-center gap-3">
      <UInput
        v-model="searchInput"
        icon="i-lucide-search"
        placeholder="Search entries"
        class="w-full"
      />
      <UBadge color="neutral" variant="soft">
        {{ totalEntries }}
      </UBadge>
    </div>

    <div v-if="canEdit" class="grid gap-3 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto]">
      <UInput
        v-model="pendingEntry.form"
        :disabled="isAddingEntry"
        placeholder="Add accepted form"
        @keydown.enter.prevent="addEntry"
      />
      <UInput
        v-model="pendingEntry.sourceEntryKey"
        :disabled="isAddingEntry"
        placeholder="Source entry key (optional)"
        @keydown.enter.prevent="addEntry"
      />
      <UButton
        color="primary"
        icon="i-lucide-plus"
        :loading="isAddingEntry"
        :disabled="isAddingEntry"
        @click="addEntry"
      >
        Add
      </UButton>
    </div>

    <div
      ref="scrollerRef"
      :class="[
        'overflow-auto rounded-lg border border-default bg-default',
        fillsParentHeight ? 'min-h-0 flex-1' : props.heightClass
      ]"
    >
      <div :style="{ height: `${totalSize}px`, position: 'relative' }">
        <div
          v-for="{ item, entry } in virtualRows"
          :key="String(item.key)"
          :style="{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: `${item.size}px`,
            transform: `translateY(${item.start}px)`
          }"
          class="overflow-hidden border-b border-default last:border-b-0"
        >
          <div v-if="entry" class="p-3">
            <div v-if="editingEntryId === entry.id" class="grid gap-3 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto_auto]">
              <UInput v-model="editingEntry.form" :disabled="savingEntryIds.has(entry.id)" @keydown.enter.prevent="saveEntry(entry.id)" />
              <UInput
                v-model="editingEntry.sourceEntryKey"
                :disabled="savingEntryIds.has(entry.id)"
                placeholder="Source key"
                @keydown.enter.prevent="saveEntry(entry.id)"
              />
              <UButton
                color="primary"
                variant="soft"
                :loading="savingEntryIds.has(entry.id)"
                :disabled="savingEntryIds.has(entry.id)"
                @click="saveEntry(entry.id)"
              >
                Save
              </UButton>
              <UButton
                color="neutral"
                variant="ghost"
                :disabled="savingEntryIds.has(entry.id)"
                @click="() => { editingEntryId = null }"
              >
                Cancel
              </UButton>
            </div>
            <div v-else class="flex items-center justify-between gap-3">
              <div class="min-w-0">
                <p class="font-medium break-all">
                  {{ entry.form }}
                </p>
                <p class="text-xs text-muted break-all">
                  {{ entry.normalizedValue }}
                </p>
                <p v-if="entry.sourceEntryKey" class="text-xs text-muted break-all">
                  Key: {{ entry.sourceEntryKey }}
                </p>
              </div>
              <div v-if="canEdit" class="flex items-center gap-2">
                <UButton
                  color="neutral"
                  variant="ghost"
                  icon="i-lucide-pencil"
                  :disabled="deletingEntryIds.has(entry.id)"
                  @click="startEdit(entry)"
                />
                <UButton
                  color="error"
                  variant="ghost"
                  icon="i-lucide-trash"
                  :loading="deletingEntryIds.has(entry.id)"
                  :disabled="deletingEntryIds.has(entry.id)"
                  @click="removeEntry(entry.id)"
                />
              </div>
            </div>
          </div>

          <div v-else class="p-3 text-sm text-muted">
            Loading more entries...
          </div>
        </div>
      </div>
    </div>

    <UAlert
      v-if="hasLoaded && entries.length === 0 && !isLoading"
      color="info"
      variant="subtle"
      icon="i-lucide-search-x"
      :title="debouncedSearch ? `No entries match “${debouncedSearch}”.` : 'No dictionary entries yet.'"
    />
  </div>
</template>
