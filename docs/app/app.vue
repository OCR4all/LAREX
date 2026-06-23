<script setup lang="ts">
const searchTerm = ref('')
const toaster = { expand: false, position: 'bottom-right' as const }

const { data: navigation } = await useAsyncData('content-navigation', () => {
  return queryCollectionNavigation('content', ['navigation'])
})

const { data: files } = useLazyAsyncData('content-search', () => {
  return queryCollectionSearchSections('content')
}, {
  server: false
})
</script>

<template>
  <UApp :toaster="toaster">
    <NuxtPage />

    <ClientOnly>
      <LazyUContentSearch
        v-model:search-term="searchTerm"
        :files="files || []"
        :navigation="navigation || []"
        placeholder="Search documentation..."
        shortcut="meta_k"
        :fuse="{ resultLimit: 32 }"
      />
    </ClientOnly>
  </UApp>
</template>
