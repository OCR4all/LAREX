<script setup lang="ts">
interface Props {
  leftImage: string
  rightImage: string
  leftAlt?: string
  rightAlt?: string
  leftLabel?: string
  rightLabel?: string
  initialPosition?: number
}

const props = withDefaults(defineProps<Props>(), {
  leftAlt: '',
  rightAlt: '',
  leftLabel: '',
  rightLabel: '',
  initialPosition: 50
})

type ImageSide = 'left' | 'right'
const states = reactive<Record<ImageSide, 'loading' | 'loaded' | 'error'>>({ left: 'loading', right: 'loading' })
const attempts = reactive({ left: 0, right: 0 })
const hasError = computed(() => states.left === 'error' || states.right === 'error')
const isReady = computed(() => states.left === 'loaded' && states.right === 'loaded')
watch(() => props.leftImage, () => {
  states.left = 'loading'
  attempts.left = 0
})
watch(() => props.rightImage, () => {
  states.right = 'loading'
  attempts.right = 0
})
function imageSource(side: ImageSide) {
  const src = side === 'left' ? props.leftImage : props.rightImage
  return attempts[side] ? `${src}${src.includes('?') ? '&' : '?'}_retry=${attempts[side]}` : src
}
function retry() {
  for (const side of ['left', 'right'] as const) {
    if (states[side] === 'error') {
      states[side] = 'loading'
      attempts[side]++
    }
  }
}

const containerRef = ref<HTMLDivElement | null>(null)
const sliderPosition = ref(props.initialPosition)
const isDragging = ref(false)

const { left: containerLeft, width: containerWidth } = useElementBounding(containerRef)
const { x: mouseX } = useMouse({ type: 'client' })

const onPointerUp = () => {
  isDragging.value = false
}
useEventListener(document, 'pointerup', onPointerUp)

watch(mouseX, (x) => {
  if (!isDragging.value || containerWidth.value === 0) return

  const relativeX = x - containerLeft.value
  sliderPosition.value = Math.max(0, Math.min(100, (relativeX / containerWidth.value) * 100))
})

const handlePointerDown = (e: PointerEvent) => {
  if (!isReady.value) return
  isDragging.value = true
  const relativeX = e.clientX - containerLeft.value
  sliderPosition.value = Math.max(0, Math.min(100, (relativeX / containerWidth.value) * 100))
}

const handleKeyDown = (e: KeyboardEvent) => {
  const step = e.shiftKey ? 10 : 1

  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    sliderPosition.value = Math.max(0, sliderPosition.value - step)
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    sliderPosition.value = Math.min(100, sliderPosition.value + step)
  }
}

const clipPath = computed(() => `inset(0 ${100 - sliderPosition.value}% 0 0)`)
const sliderLeft = computed(() => `${sliderPosition.value}%`)
</script>

<template>
  <div
    ref="containerRef"
    class="group relative h-full w-full cursor-ew-resize select-none overflow-hidden rounded-sm bg-neutral-100 shadow-lg touch-pan-y focus:outline-2 focus:outline-offset-2 focus:outline-primary-500 dark:bg-neutral-800"
    role="slider"
    :aria-valuenow="Math.round(sliderPosition)"
    aria-valuemin="0"
    aria-valuemax="100"
    aria-label="Image comparison slider"
    tabindex="0"
    @pointerdown="handlePointerDown"
    @keydown="handleKeyDown"
  >
    <div
      v-if="!isReady"
      class="absolute inset-0 z-20 flex flex-col items-center justify-center gap-3 bg-default/90 text-muted"
      :role="hasError ? 'alert' : 'status'"
      @pointerdown.stop
    >
      <template v-if="hasError">
        <UIcon name="i-lucide-image-off" class="size-6" />
        <p class="text-sm">
          Comparison image could not be loaded.
        </p>
        <UButton size="sm" color="neutral" @click.stop="retry">
          Retry failed images
        </UButton>
      </template>
      <template v-else>
        <UIcon name="i-lucide-loader" class="size-6 animate-spin" />
        <span class="text-sm">Loading comparison images…</span>
      </template>
    </div>
    <div class="absolute inset-0 overflow-hidden">
      <img
        :key="`${rightImage}:${attempts.right}`"
        :src="imageSource('right')"
        :alt="rightAlt"
        draggable="false"
        class="pointer-events-none absolute top-0 left-1/2 h-full w-auto max-w-none -translate-x-1/2"
        @load="states.right = 'loaded'"
        @error="states.right = 'error'"
      >
    </div>

    <div
      class="absolute inset-0 overflow-hidden"
      :style="{ clipPath }"
    >
      <img
        :key="`${leftImage}:${attempts.left}`"
        :src="imageSource('left')"
        :alt="leftAlt"
        draggable="false"
        class="pointer-events-none absolute top-0 left-1/2 h-full w-auto max-w-none -translate-x-1/2"
        @load="states.left = 'loaded'"
        @error="states.left = 'error'"
      >
    </div>

    <div
      class="absolute top-0 bottom-0 z-10 flex w-0 -translate-x-1/2 items-center justify-center"
      :style="{ left: sliderLeft }"
    >
      <div class="absolute top-0 bottom-0 w-0.5 bg-neutral-500/50 shadow-lg" />

      <div
        class="z-1 flex size-13 items-center justify-center rounded-sm transition-transform duration-200 ease-[cubic-bezier(0.34,1.56,0.64,1)] group-hover:scale-110"
        :class="{ 'scale-110': isDragging }"
        style="background: linear-gradient(145deg, #ffffff 0%, #f3f4f6 100%); box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.2), 0 0 0 3px rgba(255, 255, 255, 0.9), inset 0 1px 2px rgba(255, 255, 255, 1);"
      >
        <div class="flex items-center justify-center gap-0.5 text-neutral-700" />
      </div>
    </div>

    <span
      v-if="leftLabel"
      class="pointer-events-none absolute bottom-4 left-4 rounded-sm border border-neutral-200/10 bg-inverted/50 p-1! text-xs font-medium tracking-wider text-white backdrop-blur-2xl"
    >
      {{ leftLabel }}
    </span>
    <span
      v-if="rightLabel"
      class="pointer-events-none absolute right-4 bottom-4 rounded-sm border border-neutral-200/10 bg-inverted/50 p-1! text-xs font-medium tracking-wider text-white backdrop-blur-2xl"
    >
      {{ rightLabel }}
    </span>
  </div>
</template>
