<script setup lang="ts">
const features = [
  'Manage document datasets',
  'Run OCR on your documents',
  'Annotate page layouts',
  'Review and correct text'
]

const currentIndex = ref(0)
const currentFeature = computed(() => features[currentIndex.value])
const nextFeature = computed(() => features[(currentIndex.value + 1) % features.length])
const currentLayer = useTemplateRef<HTMLSpanElement>('currentLayer')
const nextLayer = useTemplateRef<HTMLSpanElement>('nextLayer')
const stage = useTemplateRef<HTMLSpanElement>('stage')
const filterId = `docs-text-morph-${useId().replace(/[^\w-]/g, '')}`
let dispose: (() => void) | undefined

onMounted(() => {
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  let timer: ReturnType<typeof setTimeout> | undefined
  let frame = 0
  let disposed = false

  function setLayer(element: HTMLSpanElement | null, opacity: number, blur: number) {
    if (!element) return
    element.style.opacity = String(opacity)
    element.style.filter = blur > 0.01 ? `blur(${blur}px)` : 'none'
  }

  function settle() {
    setLayer(currentLayer.value, 1, 0)
    setLayer(nextLayer.value, 0, 0)
    if (stage.value) stage.value.style.filter = 'none'
  }

  function schedule() {
    if (disposed || document.hidden || reducedMotion.matches) return
    timer = setTimeout(morph, 3200)
  }

  function morph() {
    // Match nxui's overlapping blur/alpha-threshold effect, without resizing
    // its stage: the hidden phrases below reserve the largest wrapped size.
    const startedAt = performance.now()
    if (stage.value) stage.value.style.filter = `url(#${filterId})`

    function animate(now: number) {
      const progress = Math.min(1, (now - startedAt) / 680)
      const smoothstep = (value: number) => value * value * (3 - 2 * value)
      const incoming = smoothstep(Math.min(1, progress / 0.82))
      const outgoing = smoothstep(Math.max(0, (progress - 0.18) / 0.82))
      setLayer(currentLayer.value, (1 - outgoing) ** 0.55, 12 * outgoing)
      setLayer(nextLayer.value, incoming ** 0.55, 12 * (1 - incoming))

      if (progress < 1) {
        frame = requestAnimationFrame(animate)
        return
      }

      currentIndex.value = (currentIndex.value + 1) % features.length
      nextTick(() => {
        if (disposed) return
        settle()
        schedule()
      })
    }

    frame = requestAnimationFrame(animate)
  }

  function updateAnimation() {
    clearTimeout(timer)
    cancelAnimationFrame(frame)
    settle()
    schedule()
  }

  document.addEventListener('visibilitychange', updateAnimation)
  reducedMotion.addEventListener('change', updateAnimation)
  schedule()

  dispose = () => {
    disposed = true
    clearTimeout(timer)
    cancelAnimationFrame(frame)
    document.removeEventListener('visibilitychange', updateAnimation)
    reducedMotion.removeEventListener('change', updateAnimation)
  }
})

onBeforeUnmount(() => dispose?.())
</script>

<template>
  <h1 class="max-w-6xl text-4xl leading-[1.12] font-bold tracking-tight text-white sm:text-5xl lg:text-6xl">
    <span class="sr-only">Manage document datasets, run OCR, annotate page layouts, and review and correct text with LAREX.</span>
    <span class="relative grid" aria-hidden="true">
      <!-- Every phrase participates in layout, including before hydration and
           after font loads or viewport changes. Switching cannot move the suffix. -->
      <span v-for="feature in features" :key="feature" class="invisible [grid-area:1/1]">{{ feature }}</span>
      <span ref="stage" class="absolute inset-0">
        <span ref="currentLayer" class="absolute inset-0">{{ currentFeature }}</span>
        <span ref="nextLayer" class="absolute inset-0 opacity-0">{{ nextFeature }}</span>
      </span>
      <svg class="pointer-events-none absolute h-0 w-0" focusable="false">
        <defs>
          <filter :id="filterId" x="-10%" y="-50%" width="120%" height="200%" color-interpolation-filters="sRGB">
            <feColorMatrix in="SourceGraphic" type="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 18 -8.28" result="thresholded" />
            <feComposite in="SourceGraphic" in2="thresholded" operator="atop" />
          </filter>
        </defs>
      </svg>
    </span>
    <span class="mt-1 block" aria-hidden="true">with <span class="font-black">LAREX</span></span>
  </h1>
</template>
