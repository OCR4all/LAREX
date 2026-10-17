<script setup lang="ts">
import type { NuxtError } from '#app'

defineProps<{
  error: NuxtError
}>()

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
  <UMain class="relative isolate min-h-screen flex items-center justify-center overflow-hidden bg-default">
    <UiDitherWaves variant="background" />
    <UError
      as="div"
      :ui="{
        root: 'px-6',
        statusCode: 'text-8xl leading-none tracking-tight text-toned sm:text-[10rem]',
        statusMessage: 'text-highlighted',
        message: 'text-muted'
      }"
      :error="error"
    />
  </UMain>
</template>
