<script setup lang="ts">
import type { WorkspaceTextSearchHit } from '@/types/search'

const emit = defineEmits<{
  'layout-change': []
}>()

const props = withDefaults(defineProps<{
  hit: WorkspaceTextSearchHit
  compact?: boolean
  showProjectName?: boolean
  showPageName?: boolean
  textFilter?: string | null
}>(), {
  compact: false,
  showProjectName: true,
  showPageName: true,
  textFilter: null
})

const imageLoaded = ref(false)
const imageFailed = ref(false)

watch(() => props.hit.previewUrl, () => {
  imageLoaded.value = false
  imageFailed.value = false
}, { immediate: true })

function handleImageLoad() {
  imageLoaded.value = true
  emit('layout-change')
}

function handleImageError() {
  imageFailed.value = true
  emit('layout-change')
}

const editorLink = computed(() => {
  const params = new URLSearchParams({
    projectId: props.hit.projectId,
    pageId: props.hit.pageId,
    editorMode: 'text',
    textView: 'textline'
  })
  if (props.textFilter?.trim()) {
    params.set('textSearch', props.textFilter.trim())
  }
  return `/editor?${params.toString()}`
})
const projectLink = computed(() => `/project/${props.hit.projectId}`)

const matchColor = computed(() => {
  switch (props.hit.matchKind) {
    case 'fuzzy':
      return 'warning'
    case 'phrase':
      return 'secondary'
    default:
      return 'primary'
  }
})
</script>

<template>
  <article
    :class="compact
      ? 'rounded-xl bg-muted/10 p-3 font-junicode'
      : 'rounded-2xl bg-muted/10 p-5 font-junicode'"
  >
    <div :class="compact ? 'grid gap-3 md:grid-cols-[320px_minmax(0,1fr)]' : 'grid gap-5 lg:grid-cols-[minmax(0,520px)_minmax(0,1fr)]'">
      <div :class="compact ? 'relative overflow-hidden rounded-xl bg-elevated ring-1 ring-inset ring-default/60 min-h-44' : 'relative overflow-hidden rounded-xl bg-elevated ring-1 ring-inset ring-default/60 min-h-60'">
        <div v-if="hit.previewUrl && !imageFailed" :class="compact ? 'relative h-full min-h-40' : 'relative h-full min-h-52'">
          <USkeleton
            v-if="!imageLoaded"
            class="absolute inset-0 h-full w-full rounded-none"
          />
          <img
            :src="hit.previewUrl"
            alt=""
            class="h-full w-full object-contain bg-default/80 p-3"
            :class="imageLoaded ? 'opacity-100' : 'opacity-0'"
            @load="handleImageLoad"
            @error="handleImageError"
          >
        </div>
        <div v-else class="flex h-full min-h-32 items-center justify-center text-sm text-muted">
          No preview
        </div>
      </div>

      <div :class="compact ? 'min-w-0 space-y-2.5' : 'min-w-0 space-y-3.5'">
        <div class="flex flex-wrap items-center gap-2">
          <UBadge :color="matchColor" variant="subtle">
            {{ hit.matchKind }}
          </UBadge>
          <span v-if="showPageName" class="text-sm font-medium text-highlighted truncate">{{ hit.pageName }}</span>
          <span v-if="showPageName && showProjectName" class="text-sm text-muted">/</span>
          <span v-if="showProjectName" class="text-sm text-muted truncate">{{ hit.projectName }}</span>
        </div>

        <div
          :class="compact ? 'text-sm leading-5.5 text-toned break-words' : 'text-sm leading-6 text-toned break-words'"
          v-html="hit.snippetHtml"
        />

        <div class="flex flex-wrap gap-2">
          <UButton
            size="sm"
            icon="i-lucide-square-arrow-out-up-right"
            :to="editorLink"
            class="font-sans"
          >
            Open in Editor
          </UButton>
          <UButton
            size="sm"
            color="neutral"
            variant="outline"
            icon="i-lucide-folder-open"
            :to="projectLink"
            class="font-sans"
          >
            Open Project
          </UButton>
        </div>
      </div>
    </div>
  </article>
</template>
