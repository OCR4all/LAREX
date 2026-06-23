<script setup lang="ts">
import type { ContentNavigationItem } from '@nuxt/content'
import type { ContentTocLink } from '@nuxt/ui'

defineProps<{
  navigation: ContentNavigationItem[]
  tocLinks?: ContentTocLink[]
  landing?: boolean
}>()

const mobileNavOpen = ref(false)
const { open: searchOpen } = useContentSearch()

const openSearch = () => {
  searchOpen.value = true
}

const closeMobileNavOnLink = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null

  if (target?.closest('a[href]')) {
    mobileNavOpen.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-default">
    <header class="sticky top-0 z-40 border-b border-muted bg-default/90 backdrop-blur">
      <UContainer class="flex h-14 items-center justify-between gap-4">
        <NuxtLink to="/" class="flex min-w-0 items-center gap-2" aria-label="LAREX documentation home">
          <AppHeaderLogo size="30" />
        </NuxtLink>

        <div class="flex items-center gap-2">
          <UContentSearchButton
            size="sm"
            :collapsed="false"
            class="hidden min-w-52 justify-between sm:inline-flex"
            label="Search"
          />
          <UButton
            icon="i-lucide-search"
            color="neutral"
            variant="ghost"
            class="sm:hidden"
            aria-label="Search documentation"
            @click="openSearch"
          />
          <UColorModeButton color="neutral" variant="ghost" />
          <UButton
            v-if="!landing"
            icon="i-lucide-menu"
            color="neutral"
            variant="ghost"
            class="lg:hidden"
            aria-label="Open navigation"
            @click="mobileNavOpen = true"
          />
        </div>
      </UContainer>
    </header>

    <UContainer
      :class="landing
        ? 'py-0'
        : 'grid gap-8 py-8 lg:grid-cols-[17rem_minmax(0,1fr)] xl:grid-cols-[17rem_minmax(0,1fr)_14rem]'"
    >
      <aside v-if="!landing" class="hidden lg:block">
        <nav class="sticky top-22 max-h-[calc(100vh-7rem)] overflow-y-auto pr-3" aria-label="Documentation navigation">
          <UContentNavigation :navigation="navigation" highlight color="neutral" />
        </nav>
      </aside>

      <main class="min-w-0">
        <slot />
      </main>

      <aside v-if="!landing && tocLinks?.length" class="hidden xl:block">
        <UContentToc
          :links="tocLinks"
          class="sticky top-22 max-h-[calc(100vh-7rem)] overflow-y-auto"
          title="On this page"
          highlight
          highlight-variant="circuit"
        />
      </aside>
    </UContainer>

    <USlideover v-model:open="mobileNavOpen" title="Documentation">
      <template #body>
        <nav aria-label="Documentation navigation">
          <UContentNavigation
            :navigation="navigation"
            highlight
            color="neutral"
            @click="closeMobileNavOnLink"
          />
        </nav>
      </template>
    </USlideover>
  </div>
</template>
