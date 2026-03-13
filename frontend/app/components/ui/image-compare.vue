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

const leftImageLoaded = ref(false)
const rightImageLoaded = ref(false)
const imageDimensions = ref<{ width: number, height: number } | null>(null)

function handleImageLoad(e: Event) {
  const img = e.target as HTMLImageElement
  if (!imageDimensions.value && img.naturalWidth && img.naturalHeight) {
    imageDimensions.value = { width: img.naturalWidth, height: img.naturalHeight }
  }
  if (img.src === props.leftImage) leftImageLoaded.value = true
  if (img.src === props.rightImage) rightImageLoaded.value = true
}

const computedAspectRatio = computed(() => {
  if (imageDimensions.value) {
    return `${imageDimensions.value.width} / ${imageDimensions.value.height}`
  }
  return '4 / 3' // Default fallback
})

watch(() => [props.leftImage, props.rightImage], () => {
  imageDimensions.value = null
  leftImageLoaded.value = false
  rightImageLoaded.value = false
})

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
    class="group relative w-full h-full overflow-hidden rounded-sm cursor-ew-resize select-none touch-pan-y shadow-lg focus:outline-2 focus:outline-primary-500 focus:outline-offset-2 bg-neutral-100 dark:bg-neutral-800"
    role="slider"
    :aria-valuenow="Math.round(sliderPosition)"
    aria-valuemin="0"
    aria-valuemax="100"
    aria-label="Image comparison slider"
    tabindex="0"
    @pointerdown="handlePointerDown"
    @keydown="handleKeyDown"
  >
    <div class="absolute inset-0">
      <img
        :src="rightImage"
        :alt="rightAlt"
        draggable="false"
        class="w-full h-full object-contain pointer-events-none"
        @load="handleImageLoad"
      >
    </div>

    <div
      class="absolute inset-0"
      :style="{ clipPath }"
    >
      <img
        :src="leftImage"
        :alt="leftAlt"
        draggable="false"
        class="w-full h-full object-contain pointer-events-none"
        @load="handleImageLoad"
      >
    </div>

    <div
      class="absolute top-0 bottom-0 w-0 flex items-center justify-center -translate-x-1/2 z-10"
      :style="{ left: sliderLeft }"
    >
      <div class="bg-neutral-500/50 absolute top-0 bottom-0 w-0.5 shadow-lg" />

      <div
        class="flex items-center justify-center size-13 rounded-sm z-1 transition-transform duration-200 ease-[cubic-bezier(0.34,1.56,0.64,1)] group-hover:scale-110"
        :class="{ 'scale-110': isDragging }"
        style="background: linear-gradient(145deg, #ffffff 0%, #f3f4f6 100%); box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.2), 0 0 0 3px rgba(255, 255, 255, 0.9), inset 0 1px 2px rgba(255, 255, 255, 1);"
      >
        <div class="flex items-center justify-center gap-0.5 text-neutral-700" />
      </div>
    </div>

    <span
      v-if="leftLabel"
      class="absolute bottom-4 left-4 p-1! bg-inverted/50 text-white text-xs font-medium tracking-wider rounded-sm pointer-events-none backdrop-blur-2xl border border-neutral-200/10"
    >
      {{ leftLabel }}
    </span>
    <span
      v-if="rightLabel"
      class="absolute bottom-4 right-4 p-1! bg-inverted/50 text-white text-xs font-medium tracking-wider rounded-sm pointer-events-none backdrop-blur-2xl border border-neutral-200/10"
    >
      {{ rightLabel }}
    </span>
  </div>
</template>
