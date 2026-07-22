<script setup lang="ts">
defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<{
  containerClass?: string
  contentClass?: string
  gradientColors?: string[]
  noiseIntensity?: number
  speed?: number
  backdropBlur?: boolean
  animating?: boolean
}>(), {
  containerClass: '',
  contentClass: '',
  gradientColors: () => [
    'rgb(255, 100, 150)',
    'rgb(100, 150, 255)',
    'rgb(255, 200, 100)'
  ],
  noiseIntensity: 0.2,
  speed: 0.1,
  backdropBlur: false,
  animating: true
})

const container = ref<HTMLElement>()
const prefersReducedMotion = ref(false)

const colors = computed(() => [
  props.gradientColors[0] || 'rgb(255, 100, 150)',
  props.gradientColors[1] || props.gradientColors[0] || 'rgb(100, 150, 255)',
  props.gradientColors[2] || props.gradientColors[0] || 'rgb(255, 200, 100)'
])

const containerStyle = computed(() => ({
  '--noise-opacity': String(Math.max(0, Math.min(1, props.noiseIntensity))),
  '--gradient-strip': `linear-gradient(to right, ${colors.value.join(', ')})`
}))

let animationFrame = 0
let resizeObserver: ResizeObserver | undefined
let motionPreference: MediaQueryList | undefined
let targetX = 0
let targetY = 0
let displayX = 0
let displayY = 0
let velocityX = 0
let velocityY = 0
let lastFrame = 0
let nextDirectionChange = 0

function randomizeVelocity() {
  const angle = Math.random() * Math.PI * 2
  const magnitude = Math.max(0, props.speed) * (0.5 + Math.random() * 0.5)
  velocityX = Math.cos(angle) * magnitude
  velocityY = Math.sin(angle) * magnitude
}

function centerGradient() {
  if (!container.value) return

  const rect = container.value.getBoundingClientRect()
  targetX = rect.width / 2
  targetY = rect.height / 2
  displayX = targetX
  displayY = targetY
  updateGradientVariables()
}

function updateGradientVariables() {
  if (!container.value) return

  container.value.style.setProperty('--gradient-x-1', `${displayX}px`)
  container.value.style.setProperty('--gradient-y-1', `${displayY}px`)
  container.value.style.setProperty('--gradient-x-2', `${displayX * 0.7}px`)
  container.value.style.setProperty('--gradient-y-2', `${displayY * 0.7}px`)
  container.value.style.setProperty('--gradient-x-3', `${displayX * 1.2}px`)
  container.value.style.setProperty('--gradient-y-3', `${displayY * 1.2}px`)
  container.value.style.setProperty('--gradient-strip-x', `${displayX * 0.1 - 50}px`)
}

function animate(time: number) {
  if (!container.value) return

  const deltaTime = lastFrame ? Math.min(time - lastFrame, 32) : 16
  lastFrame = time

  if (props.animating && !prefersReducedMotion.value) {
    const rect = container.value.getBoundingClientRect()
    const padding = Math.min(20, rect.width / 4, rect.height / 4)

    if (time >= nextDirectionChange) {
      randomizeVelocity()
      nextDirectionChange = time + 1500 + Math.random() * 1500
    }

    targetX += velocityX * deltaTime
    targetY += velocityY * deltaTime

    if (
      targetX < padding
      || targetX > rect.width - padding
      || targetY < padding
      || targetY > rect.height - padding
    ) {
      targetX = Math.max(padding, Math.min(rect.width - padding, targetX))
      targetY = Math.max(padding, Math.min(rect.height - padding, targetY))
      randomizeVelocity()
      nextDirectionChange = time + 1500 + Math.random() * 1500
    }

    const smoothing = 1 - Math.exp(-deltaTime / 110)
    displayX += (targetX - displayX) * smoothing
    displayY += (targetY - displayY) * smoothing
    updateGradientVariables()
  }

  animationFrame = requestAnimationFrame(animate)
}

function updateMotionPreference(event: MediaQueryListEvent) {
  prefersReducedMotion.value = event.matches
}

onMounted(() => {
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  prefersReducedMotion.value = motionPreference.matches
  motionPreference.addEventListener('change', updateMotionPreference)
  resizeObserver = new ResizeObserver(centerGradient)
  if (container.value) resizeObserver.observe(container.value)

  centerGradient()
  randomizeVelocity()
  nextDirectionChange = performance.now() + 1500 + Math.random() * 1500
  animationFrame = requestAnimationFrame(animate)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  motionPreference?.removeEventListener('change', updateMotionPreference)
})
</script>

<template>
  <div
    ref="container"
    v-bind="$attrs"
    class="group relative overflow-hidden rounded-2xl bg-smoke-200 p-2 shadow-[0px_0.5px_1px_0px_var(--color-smoke-400)_inset,0px_1px_0px_0px_var(--color-smoke-100)] backdrop-blur-sm dark:bg-smoke-800 dark:shadow-[0px_1px_0px_0px_var(--color-smoke-950)_inset,0px_1px_0px_0px_var(--color-smoke-800)]"
    :class="containerClass"
    :style="containerStyle"
  >
    <div
      class="absolute inset-0 opacity-40"
      :style="{ background: `radial-gradient(circle at var(--gradient-x-1) var(--gradient-y-1), ${colors[0]} 0%, transparent 50%)` }"
    />
    <div
      class="absolute inset-0 opacity-30"
      :style="{ background: `radial-gradient(circle at var(--gradient-x-2) var(--gradient-y-2), ${colors[1]} 0%, transparent 50%)` }"
    />
    <div
      class="absolute inset-0 opacity-25"
      :style="{ background: `radial-gradient(circle at var(--gradient-x-3) var(--gradient-y-3), ${colors[2]} 0%, transparent 50%)` }"
    />

    <div
      class="absolute -inset-x-16 top-0 h-1 rounded-t-2xl opacity-80 blur-sm"
      :style="{ background: 'var(--gradient-strip)', transform: 'translateX(var(--gradient-strip-x))' }"
    />

    <div v-if="backdropBlur" class="pointer-events-none absolute inset-0 backdrop-blur-lg" />
    <div class="noise-background-texture pointer-events-none absolute inset-0" />

    <div class="relative z-10" :class="contentClass">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.noise-background-texture {
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 180 180' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='.9'/%3E%3C/svg%3E");
  background-size: 180px 180px;
  mix-blend-mode: overlay;
  opacity: var(--noise-opacity);
}
</style>
