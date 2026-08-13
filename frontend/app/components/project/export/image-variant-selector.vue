<script setup lang="ts">
import type { ImageVariantSelection, Page } from '@/types/project-page'

const props = withDefaults(defineProps<{
  pages?: Page[]
  modelValue?: ImageVariantSelection
}>(), {
  pages: () => []
})

const emit = defineEmits<{
  'update:modelValue': [value: ImageVariantSelection | undefined]
}>()

const mode = ref<'global' | 'perPage'>(props.modelValue?.mode === 'PER_PAGE' ? 'perPage' : 'global')
const selectedVariant = ref(props.modelValue?.variant ?? '')
const fallbackImage = ref(Boolean(props.modelValue?.fallbackImage))
const pageVariants = reactive<Record<string, string>>({
  ...(props.modelValue?.pageVariants ?? {})
})

const modeItems = [
  { label: 'Global', value: 'global', icon: 'i-lucide-globe' },
  { label: 'Per page', value: 'perPage', icon: 'i-lucide-files' }
]

const variantOptions = computed(() => {
  const variants = new Set<string>()
  for (const page of props.pages) {
    for (const image of page.imageVariants ?? []) {
      const variant = image.variant?.trim()
      if (variant) variants.add(variant)
    }
  }
  return Array.from(variants)
    .sort((left, right) => left.localeCompare(right))
    .map(variant => ({ label: variant, value: variant }))
})

const variantsByPageId = computed(() => {
  const result: Record<string, Set<string>> = {}
  for (const page of props.pages) {
    result[page.id] = new Set((page.imageVariants ?? [])
      .map(image => image.variant?.trim())
      .filter((variant): variant is string => Boolean(variant)))
  }
  return result
})

const missingPages = computed(() => props.pages.filter((page) => {
  const available = variantsByPageId.value[page.id] ?? new Set<string>()
  const wanted = mode.value === 'global' ? selectedVariant.value : pageVariants[page.id]
  return !wanted || !available.has(wanted)
}))

const selectionSummary = computed(() => {
  const missing = missingPages.value.length
  if (mode.value === 'global') {
    return `${selectedVariant.value || 'No variant'} · ${fallbackImage.value ? 'fallback enabled' : 'missing pages skipped'}${missing > 0 ? ` · ${missing} missing` : ''}`
  }
  return `${props.pages.length} page variants · ${fallbackImage.value ? 'fallback enabled' : 'missing pages skipped'}${missing > 0 ? ` · ${missing} missing` : ''}`
})

const selection = computed<ImageVariantSelection | undefined>(() => {
  if (variantOptions.value.length === 0) return undefined
  if (mode.value === 'global') {
    if (!selectedVariant.value) return undefined
    return {
      mode: 'GLOBAL',
      variant: selectedVariant.value,
      fallbackImage: fallbackImage.value
    }
  }

  const selectedByPage: Record<string, string> = {}
  for (const page of props.pages) {
    const variant = pageVariants[page.id]
    if (variant) selectedByPage[page.id] = variant
  }
  if (Object.keys(selectedByPage).length === 0) return undefined
  return {
    mode: 'PER_PAGE',
    pageVariants: selectedByPage,
    fallbackImage: fallbackImage.value
  }
})

watch([() => props.pages, variantOptions], reconcileSelection, { immediate: true })
watch(selection, value => emit('update:modelValue', value), { immediate: true })

function reconcileSelection() {
  const options = variantOptions.value
  if (options.length === 0) {
    selectedVariant.value = ''
    Object.keys(pageVariants).forEach(key => Reflect.deleteProperty(pageVariants, key))
    return
  }

  if (!options.some(item => item.value === selectedVariant.value)) {
    selectedVariant.value = options[0]?.value ?? ''
  }

  const pageIds = new Set(props.pages.map(page => page.id))
  Object.keys(pageVariants).forEach((pageId) => {
    if (!pageIds.has(pageId)) Reflect.deleteProperty(pageVariants, pageId)
  })

  for (const page of props.pages) {
    const available = Array.from(variantsByPageId.value[page.id] ?? [])
    if (available.length === 0) continue
    const current = pageVariants[page.id]
    if (!current || !available.includes(current)) {
      pageVariants[page.id] = available.includes(selectedVariant.value)
        ? selectedVariant.value
        : (available[0] ?? selectedVariant.value)
    }
  }
}

function optionsForPage(page: Page) {
  return Array.from(variantsByPageId.value[page.id] ?? [])
    .sort((left, right) => left.localeCompare(right))
    .map(variant => ({ label: variant, value: variant }))
}
</script>

<template>
  <div v-if="props.pages.length > 0" class="space-y-4 border-t border-default pt-4">
    <UAlert
      v-if="variantOptions.length === 0"
      color="neutral"
      variant="subtle"
      icon="i-lucide-image-off"
      title="No image variants found for this scope."
    />

    <template v-else>
      <div class="grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
        <UFormField label="Variant scope">
          <UTabs
            v-model="mode"
            :items="modeItems"
            variant="pill"
            color="neutral"
            :content="false"
          />
        </UFormField>

        <UFormField label="Fallback image">
          <USwitch v-model="fallbackImage" />
        </UFormField>
      </div>

      <UFormField
        v-if="mode === 'global'"
        label="Image variant"
        :hint="selectionSummary"
      >
        <USelectMenu
          v-model="selectedVariant"
          :items="variantOptions"
          value-key="value"
          searchable
          searchable-placeholder="Filter variants..."
          class="w-full"
        />
      </UFormField>

      <div v-else class="space-y-2">
        <div
          v-for="page in props.pages"
          :key="page.id"
          class="grid gap-2 rounded-sm border border-default p-3 sm:grid-cols-[minmax(0,1fr)_minmax(12rem,18rem)] sm:items-center"
        >
          <div class="min-w-0">
            <p class="truncate text-sm font-medium">
              {{ page.name }}
            </p>
            <p class="truncate text-xs text-muted">
              {{ optionsForPage(page).length }} variant{{ optionsForPage(page).length === 1 ? '' : 's' }}
            </p>
          </div>
          <USelectMenu
            v-if="optionsForPage(page).length > 0"
            v-model="pageVariants[page.id]"
            :items="optionsForPage(page)"
            value-key="value"
            searchable
            searchable-placeholder="Filter variants..."
          />
          <UBadge v-else color="warning" variant="soft">
            No images
          </UBadge>
        </div>
      </div>

      <UAlert
        v-if="missingPages.length > 0"
        color="warning"
        variant="soft"
        icon="i-lucide-triangle-alert"
        :title="fallbackImage
          ? `${missingPages.length} page${missingPages.length === 1 ? '' : 's'} will use a fallback image.`
          : `${missingPages.length} page${missingPages.length === 1 ? '' : 's'} will be skipped.`"
      />
    </template>
  </div>
</template>
