<script setup lang="ts">
import { provideSSRWidth } from '@vueuse/core'

provideSSRWidth(1024)

const colorMode = useColorMode()
const { instanceName } = useInstance()
const { loggedIn } = useUserSession()
const route = useRoute()
const router = useRouter()
const { openSettings, closeSettings } = useUserSettings()

// Preserve old settings links and account-security callbacks.
onMounted(() => {
  watch(() => route.query.settings, (section) => {
    if (!loggedIn.value || typeof section !== 'string') return
    openSettings(isUserSettingsSection(section) ? section : 'profile')
    const { settings: _settings, ...query } = route.query
    void router.replace({ path: route.path, query, hash: route.hash })
  }, { immediate: true })
})

watch(loggedIn, (isLoggedIn) => {
  if (!isLoggedIn) closeSettings()
})
const { initialize: initializeAvatarSettings } = useAvatarSettings()

if (loggedIn.value) {
  await initializeAvatarSettings()
}

const color = computed(() => colorMode.value === 'dark' ? '#1b1718' : 'white')

const toaster = { expand: false, position: 'top-center' as const }

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
  ogDescription: description
})
</script>

<template>
  <UApp :toaster="toaster">
    <AppHealthStatusBanner />
    <NuxtLoadingIndicator color="#1678E4FF" />

    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>

    <UserSettingsModal v-if="loggedIn" />
  </UApp>
</template>
