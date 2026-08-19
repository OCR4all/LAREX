<script setup lang="ts">
import { provideSSRWidth } from '@vueuse/core'

provideSSRWidth(1024)

const colorMode = useColorMode()
const { instanceName } = useInstance()
const { loggedIn } = useUserSession()
const { initialize: initializeAvatarSettings } = useAvatarSettings()

if (loggedIn.value) {
  await initializeAvatarSettings()
}

const color = computed(() => colorMode.value === 'dark' ? '#1b1718' : 'white')

const toaster = { expand: false, position: 'bottom-right' as const }

useHead({
  meta: [
    { charset: 'utf-8' },
    { name: 'viewport', content: 'width=device-width, initial-scale=1' },
    { key: 'theme-color', name: 'theme-color', content: color }
  ],
  link: [
    { rel: 'icon', href: '/favicon.ico' }
  ],
  htmlAttrs: {
    lang: 'en'
  }
})

const title = instanceName
const description = `${instanceName} - Layout Analysis and Region Extraction for historical documents`

useSeoMeta({
  title,
  description,
  ogTitle: title,
  ogDescription: description,
  ogImage: 'https://ui4.nuxt.com/assets/templates/nuxt/dashboard-light.png',
  twitterImage: 'https://ui4.nuxt.com/assets/templates/nuxt/dashboard-light.png',
  twitterCard: 'summary_large_image'
})
</script>

<template>
  <UApp :toaster="toaster">
    <AppHealthStatusBanner />
    <NuxtLoadingIndicator color="#1678E4FF" />

    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </UApp>
</template>
