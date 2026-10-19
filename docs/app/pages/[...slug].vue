<script setup lang="ts">
import type { ContentNavigationItem } from '@nuxt/content'
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

const breadcrumbs = computed(() => {
  function findTrail(items: ContentNavigationItem[]): ContentNavigationItem[] {
    for (const item of items) {
      if (item.path === contentPath()) return [item]
      const trail = findTrail(item.children || [])
      if (trail.length) return [item, ...trail]
    }
    return []
  }
  return findTrail(navigation.value || [])
})

const editUrl = computed(() => page.value?.stem
  ? `https://github.com/OCR4all/larex/edit/main/docs/content/${page.value.stem}.md`
  : undefined)

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
      <nav aria-label="Breadcrumb" class="mb-6 text-sm text-muted">
        <ol class="flex flex-wrap items-center gap-x-2 gap-y-1">
          <li><NuxtLink to="/" class="hover:text-highlighted">Docs</NuxtLink></li>
          <li v-for="(crumb, index) in breadcrumbs" :key="crumb.path" class="flex items-center gap-2">
            <span aria-hidden="true">/</span>
            <span v-if="index === breadcrumbs.length - 1" aria-current="page">{{ crumb.title }}</span>
            <span v-else-if="crumb.page === false">{{ crumb.title }}</span>
            <NuxtLink v-else :to="crumb.path" class="hover:text-highlighted">{{ crumb.title }}</NuxtLink>
          </li>
        </ol>
      </nav>
      <nav v-if="tocLinks.length" aria-label="On this page" class="mb-6 rounded border border-default px-4 py-3 xl:hidden">
        <details>
          <summary class="cursor-pointer text-sm font-medium">On this page</summary>
          <ul class="mt-3 space-y-2 text-sm text-muted">
            <li v-for="link in tocLinks" :key="link.id">
              <NuxtLink :to="`#${link.id}`" class="hover:text-highlighted">{{ link.text }}</NuxtLink>
            </li>
          </ul>
        </details>
      </nav>
      <ContentRenderer v-if="page" :value="page" />
      <footer class="mt-10 flex flex-wrap gap-x-6 gap-y-2 border-t border-default pt-5 text-sm text-muted">
        <a v-if="editUrl" :href="editUrl" target="_blank" rel="noopener noreferrer" class="underline underline-offset-4 hover:text-highlighted">Edit this page on GitHub</a>
        <a href="https://github.com/OCR4all/larex/issues/new" target="_blank" rel="noopener noreferrer" class="underline underline-offset-4 hover:text-highlighted">Report a documentation issue</a>
      </footer>
      <template v-if="hasSurround">
        <USeparator class="my-10" />
        <UContentSurround :surround="(surround as any)" />
      </template>
    </article>
  </DocsShell>
</template>
