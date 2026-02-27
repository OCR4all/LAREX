<script setup lang="ts">
interface Props {
  colors: string[]
  speed?: number
  blur?: 'light' | 'medium' | 'heavy'
}

const props = withDefaults(defineProps<Props>(), {
  speed: 5,
  blur: 'light'
})

const containerRef = ref<HTMLDivElement | null>(null)
const width = ref(0)
const height = ref(0)

const randomInt = (min: number, max: number) => {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

const updateDimensions = () => {
  if (containerRef.value) {
    width.value = containerRef.value.offsetWidth
    height.value = containerRef.value.offsetHeight
  }
}

const circleSize = computed(() => Math.max(width.value, height.value))

const blurClass = computed(() => {
  if (props.blur === 'light') return 'blur-2xl'
  if (props.blur === 'medium') return 'blur-3xl'
  return 'blur-[100px]'
})

const circleData = computed(() => {
  return props.colors.map(color => ({
    color,
    size: circleSize.value * randomInt(0.5, 1.5),
    top: `${Math.random() * 50}%`,
    left: `${Math.random() * 50}%`,
    tx1: Math.random() - 0.5,
    ty1: Math.random() - 0.5,
    tx2: Math.random() - 0.5,
    ty2: Math.random() - 0.5,
    tx3: Math.random() - 0.5,
    ty3: Math.random() - 0.5,
    tx4: Math.random() - 0.5,
    ty4: Math.random() - 0.5
  }))
})

const getAnimationStyle = (circle: any) => ({
  'animation': `background-gradient ${props.speed}s infinite ease-in-out`,
  'animationDuration': `${props.speed}s`,
  'top': circle.top,
  'left': circle.left,
  '--tx-1': circle.tx1,
  '--ty-1': circle.ty1,
  '--tx-2': circle.tx2,
  '--ty-2': circle.ty2,
  '--tx-3': circle.tx3,
  '--ty-3': circle.ty3,
  '--tx-4': circle.tx4,
  '--ty-4': circle.ty4
})

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  updateDimensions()

  if (containerRef.value) {
    resizeObserver = new ResizeObserver(updateDimensions)
    resizeObserver.observe(containerRef.value)
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})
</script>

<template>
  <div ref="containerRef" class="absolute inset-0 overflow-hidden">
    <div :class="['absolute inset-0', blurClass]">
      <svg
        v-for="(circle, index) in circleData"
        :key="index"
        class="absolute animate-background-gradient"
        :width="circle.size"
        :height="circle.size"
        viewBox="0 0 100 100"
        :style="getAnimationStyle(circle)"
      >
        <circle
          cx="50"
          cy="50"
          r="50"
          :fill="circle.color"
        />
      </svg>
    </div>
  </div>
</template>

<style scoped>
@keyframes background-gradient {
  0%, 100% {
    transform: translate(0, 0);
  }
  25% {
    transform: translate(
      calc(var(--tx-1) * 100vw),
      calc(var(--ty-1) * 100vh)
    );
  }
  50% {
    transform: translate(
      calc(var(--tx-2) * 100vw),
      calc(var(--ty-2) * 100vh)
    );
  }
  75% {
    transform: translate(
      calc(var(--tx-3) * 100vw),
      calc(var(--ty-3) * 100vh)
    );
  }
}

.animate-background-gradient {
  animation: background-gradient 5s infinite ease-in-out;
}
</style>
