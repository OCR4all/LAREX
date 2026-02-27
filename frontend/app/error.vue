<script setup lang="ts">
import type { NuxtError } from '#app'

defineProps<{
  error: NuxtError
}>()

const gradientColors
  = [
    '#e1552e',
    '#d23e24',
    '#af2e1f',
    '#8c2720',
    '#33332f',
    '#1c1c19'
  ]

useSeoMeta({
  title: 'An Error Occurred',
  description: 'We are sorry but something went wrong.'
})

useHead({
  htmlAttrs: {
    lang: 'en'
  }
})
</script>

<template>
  <UMain class="min-h-screen flex items-center justify-center overflow-hidden bg-neutral-700">
    <UiAnimatedGradient
      :colors="gradientColors"
      :speed="45"
      blur="heavy"
    />

    <div class="absolute inset-0 halftone-overlay pointer-events-none" />

    <UError :ui="{ root: 'z-50', statusCode: 'text-white', statusMessage: 'text-white', message: 'text-white' }" :error="error" />
  </UMain>
</template>

<style scoped>
.halftone-overlay {
  background-image: radial-gradient(circle, rgba(0, 0, 0, 0.15) 1px, transparent 1px);
  background-size: 4px 4px;
}
</style>
