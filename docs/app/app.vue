<script setup lang="ts">
const searchTerm = ref('')
const toaster = { expand: false, position: 'bottom-right' as const }

const { data: navigation } = await useAsyncData('content-navigation', () => {
  return queryCollectionNavigation('content', ['navigation'])
})

const { data: files } = useLazyAsyncData('content-search', () => {
  return queryCollectionSearchSections('content', {
    minHeading: 'h2',
    maxHeading: 'h3',
    ignoredTags: ['style', 'script']
  })
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
        title="Search documentation"
        description="Find guides, workflows, and reference sections."
        placeholder="Search documentation..."
        :color-mode="false"
        shortcut="meta_k"
        :fuse="{ resultLimit: 16 }"
      />
    </ClientOnly>
  </UApp>
</template>
