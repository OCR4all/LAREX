<script setup lang="ts">
import type { Glyph } from '~/types/virtual-keyboard'
import { GlyphService } from '~/utils/glyph-service'
import { isPUA } from '~/utils/unicode'

const props = defineProps<{ title?: string }>()
const emit = defineEmits<{ close: [Glyph | null] }>()

const query = ref('')
const sources = reactive({ mufi: true, unicode: false })
const results = ref<Glyph[]>([])
const loading = ref(false)
const page = ref(0)
const hasMore = ref(false)
const total = ref(0)

const performSearch = async (reset = false) => {
  if (reset) { page.value = 0; results.value = [] }
  loading.value = true
  try {
    const res = await GlyphService.search(query.value, sources, page.value)
    results.value = reset ? res.data : [...results.value, ...res.data]
    total.value = res.total
    hasMore.value = res.hasMore
  } finally { loading.value = false }
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(query, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => performSearch(true), 300)
})

onMounted(() => performSearch(true))

const headerTitle = computed(() => props.title || `Glyph Picker${total.value ? ` (${total.value})` : ''}`)
</script>

<template>
  <USlideover
    :modal="false"
    side="left"
    :title="headerTitle"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #body>
      <div class="flex flex-col gap-4 h-full font-junicode">
        <div class="space-y-3 px-2">
          <UInput v-model="query" placeholder="Search by name, hex, or char..." autofocus />
          <div class="flex gap-4 text-xs">
            <UCheckbox v-model="sources.mufi" label="MUFI" @change="performSearch(true)" />
            <UCheckbox v-model="sources.unicode" label="Unicode" @change="performSearch(true)" />
          </div>
          <div class="flex gap-3 text-sm">
            <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-sm bg-yellow-500" />MUFI</span>
            <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-sm bg-blue-500" />Unicode</span>
            <span class="flex items-center gap-1"><span class="w-2 h-2 rounded-sm bg-orange-500" />PUA</span>
          </div>
        </div>

        <div class="flex-1 p-2 overflow-y-auto">
          <div v-if="loading && results.length === 0" class="text-center text-muted p-4">
            Loading...
          </div>
          <div class="grid grid-cols-[repeat(auto-fill,minmax(60px,1fr))] gap-2 max-h-[25vh]">
            <button
              v-for="g in results"
              :key="g.codepoint + g.source"
              class="relative aspect-square bg-elevated border-2 rounded-sm hover:bg-accented flex flex-col items-center justify-center"
              :class="[g.source === 'mufi' ? 'border-yellow-600 hover:border-yellow-500' : 'border-blue-600 hover:border-blue-500', isPUA(g.codepoint) ? 'bg-orange-500/10' : '']"
              :title="g.description"
              @click="emit('close', g)"
            >
              <span v-if="isPUA(g.codepoint)" class="absolute -top-1 -right-1 w-2.5 h-2.5 z-100 rounded-sm bg-orange-500 ring-2 ring-elevated" />
              <span class="text-xl font-junicode">{{ g.utf8 }}</span>
              <span class="text-[9px] text-muted truncate w-full px-1">{{ g.codepoint }}</span>
            </button>
          </div>
        </div>
      </div>
    </template>
    <template #footer>
      <div v-if="hasMore" class="flex justify-center p-4">
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          @click="page++; performSearch(false)"
        >
          Load More
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
