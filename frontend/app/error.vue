<script setup lang="ts">
import type { NuxtError } from '#app'

defineProps<{
  error: NuxtError
}>()

const colorMode = useColorMode()

const gradientColors = computed(() => colorMode.value === 'dark'
  ? [
      '#1678e4', // navy-600
      '#1062bc', // navy-700
      '#0a4f9a', // navy-800
      '#073d78', // navy-900
      '#282828', // smoke-800
      '#181818' // smoke-900
    ]
  : [
      '#1678e4', // navy-600
      '#1062bc', // navy-700
      '#73a2fd', // navy-400
      '#9ab8fd', // navy-300
      '#f2f2f2', // smoke-50
      '#ebebeb' // smoke-100
    ])

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
  <UMain class="min-h-screen flex items-center justify-center overflow-hidden bg-navy-600 dark:bg-navy-800">
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
