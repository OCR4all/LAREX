<script setup lang="ts">
type ImageVariantPreview = {
  id: string
  fileName: string
  variant?: string | null
}

const props = withDefaults(defineProps<{
  projectId: string
  pageName: string
  imageCount: number
  images?: ImageVariantPreview[]
}>(), {
  images: () => []
})

const emit = defineEmits<{
  open: []
}>()

const MAX_PREVIEWS = 3
const hasInteracted = ref(false)

const hasImages = computed(() => props.imageCount > 0)
const previewImages = computed(() => props.images.slice(0, MAX_PREVIEWS))
const hiddenImageCount = computed(() => Math.max(0, props.imageCount - previewImages.value.length))
const countLabel = computed(() => `${props.imageCount} ${props.imageCount === 1 ? 'image' : 'images'}`)
const actionLabel = computed(() => `View ${countLabel.value} for ${props.pageName}`)

function getThumbnailUrl(imageId: string) {
  return `/api/projects/${props.projectId}/pages/images/${imageId}/thumbnail`
}

function getPreviewStyle(index: number, total: number) {
  const center = (total - 1) / 2
  const offset = index - center
  const centerDistance = Math.abs(offset)

  return {
    '--preview-rest-x': `${offset}px`,
    '--preview-rest-rotate': `${offset * 2}deg`,
    '--preview-open-x': `${offset * 24}px`,
    '--preview-open-y': `${-8 - Math.max(0, 1 - centerDistance) * 4}px`,
    '--preview-open-rotate': `${offset * 18}deg`,
    '--preview-z': String(index + 2)
  }
}

function revealPreviews() {
  hasInteracted.value = true
}

function openImages() {
  if (!hasImages.value) return
  emit('open')
}
</script>

<template>
  <div class="flex justify-end">
    <button
      type="button"
      class="image-folder-button group relative flex h-8 w-12 items-center justify-start rounded-sm pl-1.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-default"
      :class="hasImages ? 'cursor-pointer hover:bg-elevated/70' : 'text-muted'"
      :disabled="!hasImages"
      :aria-label="actionLabel"
      @click.stop="openImages"
      @focus="revealPreviews"
      @pointerenter="revealPreviews"
    >
      <span
        class="image-folder relative block h-6 w-8 shrink-0 perspective-dramatic"
        :class="{ 'image-folder--empty': !hasImages }"
        aria-hidden="true"
      >
        <span
          class="absolute bottom-[69%] left-0 h-2 w-4 rounded-t-[3px] ring-1 ring-inset"
          :class="hasImages ? 'bg-warning/75 ring-warning/30' : 'bg-muted ring-default'"
        />

        <span
          class="absolute inset-x-0 bottom-0 h-[76%] rounded-[4px] shadow-sm ring-1 ring-inset"
          :class="hasImages ? 'bg-warning/80 ring-warning/30' : 'bg-muted ring-default'"
        />

        <span
          v-for="(image, index) in previewImages"
          :key="image.id"
          class="folder-preview absolute bottom-0.5 left-1/2 overflow-hidden rounded-[2px] border border-white/80 bg-elevated shadow-md dark:border-white/30"
          :style="getPreviewStyle(index, previewImages.length)"
          :title="image.variant || image.fileName"
        >
          <img
            v-if="index === 0 || hasInteracted"
            :src="getThumbnailUrl(image.id)"
            alt=""
            class="h-full w-full object-contain"
            draggable="false"
            loading="lazy"
          >
          <span
            v-if="index === previewImages.length - 1 && hiddenImageCount > 0"
            class="absolute inset-0 flex items-center justify-center bg-inverted/65 text-[9px] font-semibold text-inverted backdrop-blur-[1px]"
          >
            +{{ hiddenImageCount }}
          </span>
        </span>

        <UBadge
          color="neutral"
          :variant="hasImages ? 'solid' : 'subtle'"
          size="xs"
          class="absolute -right-2 -bottom-1 z-20 min-w-4 justify-center px-1! tabular-nums shadow-sm"
        >
          {{ imageCount }}
        </UBadge>

        <span
          class="folder-front absolute inset-x-0 bottom-0 z-10 h-[70%] origin-bottom rounded-[4px] bg-linear-to-b shadow-sm ring-1 ring-inset"
          :class="hasImages
            ? 'from-warning-300 to-warning-500 ring-warning/30 dark:from-warning-400 dark:to-warning-600'
            : 'from-muted to-elevated ring-default'"
        >
          <span
            class="absolute inset-x-1 top-1 h-px"
            :class="hasImages ? 'bg-warning-200/60 dark:bg-warning-300/50' : 'bg-default/20'"
          />
        </span>
      </span>
    </button>
  </div>
</template>

<style scoped>
.image-folder-button {
  transition: background-color 160ms ease;
}

.folder-preview {
  width: 22px;
  height: 16px;
  z-index: var(--preview-z);
  transform: translate3d(calc(-50% + var(--preview-rest-x)), -2px, 0)
    rotate(var(--preview-rest-rotate));
  transform-origin: bottom center;
  transition:
    width 360ms cubic-bezier(0.34, 1.56, 0.64, 1),
    height 360ms cubic-bezier(0.34, 1.56, 0.64, 1),
    transform 360ms cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 200ms ease;
}

.folder-front {
  transform: rotateX(-14deg) scaleY(1);
  transform-style: preserve-3d;
  transition: transform 360ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

.image-folder-button:not(:disabled):hover .folder-preview,
.image-folder-button:not(:disabled):focus-visible .folder-preview {
  width: 44px;
  height: 34px;
  transform: translate3d(calc(-50% + var(--preview-open-x)), var(--preview-open-y), 0)
    rotate(var(--preview-open-rotate));
  box-shadow: 0 6px 12px rgb(0 0 0 / 0.2);
}

.image-folder-button:not(:disabled):hover .folder-front,
.image-folder-button:not(:disabled):focus-visible .folder-front {
  transform: rotateX(-42deg) scaleY(0.86);
}

.image-folder--empty .folder-front {
  transform: none;
}

@media (prefers-reduced-motion: reduce) {
  .image-folder-button,
  .folder-preview,
  .folder-front {
    transition-duration: 0.01ms;
  }
}
</style>
