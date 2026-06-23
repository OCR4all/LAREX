<script setup lang="ts">
const route = useRoute()

const contentPath = () => {
  if (route.path === '') {
    return '/'
  }
  return route.path.endsWith('/') && route.path.length > 1
    ? route.path.slice(0, -1)
    : route.path
}

const { data: navigation } = await useAsyncData('content-navigation', () => {
  return queryCollectionNavigation('content', ['navigation'])
})

const isLanding = computed(() => contentPath() === '/')

const { data: page } = await useAsyncData(
  `content-page:${contentPath()}`,
  () => queryCollection('content').path(contentPath()).first(),
  {
    watch: [() => route.path]
  }
)

const { data: surround } = await useAsyncData(
  `content-surround:${contentPath()}`,
  () => queryCollectionItemSurroundings('content', contentPath(), {
    fields: ['description']
  }),
  {
    watch: [() => route.path]
  }
)

const hasSurround = computed(() => surround.value?.some(Boolean) === true)
const tocLinks = computed(() => page.value?.body?.toc?.links || [])

if (!page.value) {
  throw createError({
    statusCode: 404,
    statusMessage: 'Page not found'
  })
}

useSeoMeta({
  title: () => {
    const seo = page.value?.seo as { title?: string } | undefined
    return seo?.title || page.value?.title || 'LAREX Documentation'
  },
  description: () => {
    const seo = page.value?.seo as { description?: string } | undefined
    return seo?.description || page.value?.description || 'Documentation for LAREX.'
  }
})
</script>

<template>
  <DocsShell :navigation="navigation || []" :toc-links="tocLinks" :landing="isLanding">
    <DocsLanding v-if="isLanding" />
    <article v-else class="docs-content max-w-4xl">
      <ContentRenderer v-if="page" :value="page" />
      <template v-if="hasSurround">
        <USeparator class="my-10" />
        <UContentSurround :surround="(surround as any)" />
      </template>
    </article>
  </DocsShell>
</template>
