<script setup lang="ts">
const circles = [
  {
    size: '86%',
    top: '-18%',
    left: '-10%',
    light: '#1678e4',
    dark: '#1678e4',
    tx1: 0.24,
    ty1: 0.16,
    tx2: 0.08,
    ty2: 0.34,
    tx3: 0.36,
    ty3: -0.14,
    tx4: -0.12,
    ty4: 0.2,
    delay: '-5s'
  },
  {
    size: '78%',
    top: '8%',
    left: '34%',
    light: '#73a2fd',
    dark: '#1062bc',
    tx1: -0.18,
    ty1: 0.18,
    tx2: 0.2,
    ty2: -0.22,
    tx3: -0.28,
    ty3: -0.08,
    tx4: 0.16,
    ty4: 0.26,
    delay: '-18s'
  },
  {
    size: '66%',
    top: '42%',
    left: '4%',
    light: '#9ab8fd',
    dark: '#0a4f9a',
    tx1: 0.18,
    ty1: -0.2,
    tx2: -0.24,
    ty2: 0.12,
    tx3: 0.26,
    ty3: 0.2,
    tx4: -0.16,
    ty4: -0.3,
    delay: '-28s'
  },
  {
    size: '74%',
    top: '35%',
    left: '52%',
    light: '#ebebeb',
    dark: '#282828',
    tx1: -0.2,
    ty1: -0.16,
    tx2: 0.26,
    ty2: 0.18,
    tx3: -0.12,
    ty3: 0.3,
    tx4: 0.2,
    ty4: -0.18,
    delay: '-9s'
  },
  {
    size: '58%',
    top: '-8%',
    left: '68%',
    light: '#f2f2f2',
    dark: '#181818',
    tx1: -0.26,
    ty1: 0.24,
    tx2: 0.12,
    ty2: -0.32,
    tx3: -0.22,
    ty3: 0.1,
    tx4: 0.28,
    ty4: 0.18,
    delay: '-36s'
  },
  {
    size: '72%',
    top: '58%',
    left: '46%',
    light: '#1678e4',
    dark: '#073d78',
    tx1: 0.12,
    ty1: -0.28,
    tx2: -0.3,
    ty2: -0.1,
    tx3: 0.22,
    ty3: 0.26,
    tx4: -0.18,
    ty4: 0.12,
    delay: '-23s'
  }
]
</script>

<template>
  <div class="absolute inset-0 overflow-hidden bg-navy-600 dark:bg-navy-800" aria-hidden="true">
    <div class="absolute inset-0 blur-[100px]">
      <svg
        v-for="circle in circles"
        :key="`${circle.top}-${circle.left}`"
        class="docs-hero-gradient-circle absolute"
        viewBox="0 0 100 100"
        :width="circle.size"
        :height="circle.size"
        :style="{
          top: circle.top,
          left: circle.left,
          '--circle-light': circle.light,
          '--circle-dark': circle.dark,
          '--tx-1': circle.tx1,
          '--ty-1': circle.ty1,
          '--tx-2': circle.tx2,
          '--ty-2': circle.ty2,
          '--tx-3': circle.tx3,
          '--ty-3': circle.ty3,
          '--tx-4': circle.tx4,
          '--ty-4': circle.ty4,
          '--animation-delay': circle.delay
        }"
      >
        <circle cx="50" cy="50" r="50" />
      </svg>
    </div>

    <div class="absolute inset-0 docs-hero-halftone opacity-45 dark:opacity-35" />
    <div class="absolute inset-0 bg-[radial-gradient(circle_at_20%_25%,rgba(255,255,255,0.2),transparent_32%),linear-gradient(90deg,rgba(10,10,10,0.76)_0%,rgba(10,10,10,0.56)_46%,rgba(10,10,10,0.24)_100%)]" />
  </div>
</template>

<style scoped>
.docs-hero-gradient-circle {
  animation: background-gradient 45s infinite ease-in-out;
  animation-delay: var(--animation-delay, 0s);
  color: var(--circle-light);
}

.dark .docs-hero-gradient-circle {
  color: var(--circle-dark);
}

.docs-hero-gradient-circle circle {
  fill: currentColor;
}

.docs-hero-halftone {
  background-image: radial-gradient(circle, rgba(0, 0, 0, 0.22) 1px, transparent 1px);
  background-size: 4px 4px;
}
</style>
