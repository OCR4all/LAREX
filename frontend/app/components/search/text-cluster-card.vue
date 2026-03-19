<script setup lang="ts">
import type { WorkspaceTextSearchClusterGroup } from '@/types/search'

const emit = defineEmits<{
  'layout-change': []
}>()

defineProps<{
  group: WorkspaceTextSearchClusterGroup
  textFilter?: string | null
}>()

const openPages = ref<string[]>([])

watch(() => [...openPages.value], async () => {
  await nextTick()
  emit('layout-change')
})
</script>

<template>
  <section class="rounded-2xl bg-muted/10 p-5">
    <div class="space-y-4">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="min-w-0 space-y-1">
          <div class="text-sm font-semibold tracking-wide text-highlighted truncate">
            {{ group.projectName }}
          </div>
          <div class="flex flex-wrap items-center gap-2 text-xs text-muted">
            <span>{{ group.hitCount }} hits</span>
            <span>{{ group.pages.length }} pages</span>
          </div>
        </div>

        <UButton
          size="sm"
          color="neutral"
          variant="outline"
          icon="i-lucide-folder-open"
          :to="`/project/${group.projectId}`"
          class="font-sans"
        >
          Open Project
        </UButton>
      </div>

      <UAccordion
        v-model="openPages"
        type="multiple"
        :items="group.pages.map(page => ({
          value: page.pageId,
          label: page.pageName,
          icon: 'i-lucide-file-text',
          trailing: `${page.hitCount} hits`
        }))"
        :ui="{
          item: 'rounded-xl bg-elevated ring-1 ring-inset ring-default/60 overflow-hidden',
          trigger: 'px-4 py-3 hover:bg-muted/40',
          content: 'px-4 pb-4'
        }"
      >
        <template #leading="{ item }">
          <div class="flex min-w-0 items-center gap-2">
            <UIcon :name="item.icon" class="size-4 text-muted" />
            <span class="truncate font-medium text-highlighted">{{ item.label }}</span>
          </div>
        </template>

        <template #trailing="{ item }">
          <span class="text-xs text-muted">{{ item.trailing }}</span>
        </template>

        <template #content="{ item }">
          <div class="space-y-3">
            <SearchTextHitCard
              v-for="hit in group.pages.find(page => page.pageId === item.value)?.hits ?? []"
              :key="`${group.projectId}-${hit.pageId}-${hit.textLineId || hit.regionId || hit.pageId}-${hit.score}`"
              :hit="hit"
              :text-filter="textFilter"
              compact
              :show-project-name="false"
              :show-page-name="false"
              @layout-change="emit('layout-change')"
            />
          </div>
        </template>
      </UAccordion>
    </div>
  </section>
</template>
