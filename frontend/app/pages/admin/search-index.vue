<script setup lang="ts">
definePageMeta({ layout: 'admin', middleware: 'admin' })

const toast = useToast()

const isRebuilding = ref(false)
const rebuildStatus = ref<'idle' | 'running' | 'success' | 'error'>('idle')
const lastRebuildTime = ref<Date | null>(null)
const technicalSections = ref<string[]>([])

const indexCapabilities = [
  {
    title: 'Text Content',
    description: 'Search for pages containing specific text in their transcriptions.',
    icon: 'i-lucide-file-text'
  },
  {
    title: 'Workspace Search',
    description: 'Rank text hits across projects for workspace-wide search.',
    icon: 'i-lucide-globe'
  },
  {
    title: 'Labels',
    description: 'Filter pages by regions or text lines with specific labels.',
    icon: 'i-lucide-tag'
  },
  {
    title: 'Tags',
    description: 'Filter pages by their assigned tags.',
    icon: 'i-lucide-search'
  }
] as const

const databaseTables = [
  {
    name: 'page_text_content',
    description: 'Stores indexed text content for filtering and workspace text search.'
  },
  {
    name: 'search_lexicon_entries',
    description: 'Stores normalized tokens for fuzzy search expansion.'
  },
  {
    name: 'page_label_index',
    description: 'Stores label assignments for fast label filtering.'
  }
] as const

const rebuildReasons = [
  'Imported pages from external sources that bypassed the normal save flow.',
  'The index becomes corrupted or out of sync.',
  'You upgraded from an older version that did not include search indexing.'
] as const

const rebuildStatusMeta = computed(() => {
  switch (rebuildStatus.value) {
    case 'running':
      return {
        title: 'Rebuilding indexes...',
        icon: 'i-lucide-loader-circle',
        iconClass: 'animate-spin text-warning',
        iconWrapClass: 'bg-warning/10 text-warning'
      }
    case 'success':
      return {
        title: 'Last rebuild completed successfully',
        icon: 'i-lucide-check-circle',
        iconClass: 'text-success',
        iconWrapClass: 'bg-success/10 text-success'
      }
    case 'error':
      return {
        title: 'Last rebuild failed',
        icon: 'i-lucide-alert-circle',
        iconClass: 'text-error',
        iconWrapClass: 'bg-error/10 text-error'
      }
    default:
      return {
        title: 'Search index ready',
        icon: 'i-lucide-database',
        iconClass: 'text-muted',
        iconWrapClass: 'bg-elevated text-muted'
      }
  }
})

function getErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message.length > 0) {
      return message
    }
  }

  return fallback
}

async function rebuildGlobalIndex() {
  if (isRebuilding.value) return

  isRebuilding.value = true
  rebuildStatus.value = 'running'

  try {
    await $fetch('/api/admin/rebuild-search-index', {
      method: 'POST'
    })
    rebuildStatus.value = 'success'
    lastRebuildTime.value = new Date()
    toast.add({
      title: 'Index rebuild started',
      description: 'The search index is being rebuilt in the background. This may take a few minutes.',
      color: 'success'
    })
  } catch (error: unknown) {
    rebuildStatus.value = 'error'
    toast.add({
      title: 'Rebuild failed',
      description: getErrorMessage(error, 'Failed to start index rebuild'),
      color: 'error'
    })
  } finally {
    isRebuilding.value = false
  }
}
</script>

<template>
  <UDashboardPanel id="admin-search-index">
    <template #header>
      <UDashboardNavbar title="Search Index">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="space-y-6 p-6">
        <p class="max-w-4xl text-muted">
          Manage the search index used for filtering pages by text content and labels. The index updates automatically when annotations are saved.
        </p>

        <UCard>
          <div class="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div class="flex items-start gap-4">
              <div
                class="flex size-16 shrink-0 items-center justify-center rounded-2xl"
                :class="rebuildStatusMeta.iconWrapClass"
              >
                <UIcon :name="rebuildStatusMeta.icon" class="size-7" :class="rebuildStatusMeta.iconClass" />
              </div>

              <div class="space-y-2">
                <div class="text-lg font-semibold text-highlighted">
                  {{ rebuildStatusMeta.title }}
                </div>

                <div class="flex flex-wrap items-center gap-2 text-sm text-muted">
                  <UIcon name="i-lucide-clock-3" class="size-4" />
                  <span v-if="lastRebuildTime">Last rebuilt: {{ lastRebuildTime.toLocaleString() }}</span>
                  <span v-else>No rebuild triggered in this session.</span>
                </div>
              </div>
            </div>

            <div>
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-refresh-cw"
                :loading="isRebuilding"
                @click="rebuildGlobalIndex"
              >
                Rebuild Index
              </UButton>
            </div>
          </div>
        </UCard>

        <UCard>
          <div class="space-y-6">
            <div class="space-y-2">
              <h2 class="text-lg font-semibold text-highlighted">
                What the Index Powers
              </h2>
              <p class="text-muted">
                Fast filtering and search capabilities enabled by the search index.
              </p>
            </div>

            <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div
                v-for="capability in indexCapabilities"
                :key="capability.title"
                class="rounded-xl border border-default bg-elevated/20 p-5"
              >
                <div class="flex items-start gap-4">
                  <div class="flex size-14 shrink-0 items-center justify-center rounded-xl border border-default bg-default">
                    <UIcon :name="capability.icon" class="size-6 text-muted" />
                  </div>

                  <div class="space-y-1">
                    <h3 class="text-base font-medium text-highlighted">
                      {{ capability.title }}
                    </h3>
                    <p class="text-sm leading-6 text-muted">
                      {{ capability.description }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </UCard>

        <UCard>
          <div class="space-y-6">
            <h2 class="text-lg font-semibold text-highlighted">
              Technical Details
            </h2>

            <UAccordion
              v-model="technicalSections"
              type="multiple"
              :items="[
                { value: 'database-tables', label: 'Database Tables', icon: 'i-lucide-database' },
                { value: 'when-to-rebuild', label: 'When to Rebuild', icon: 'i-lucide-refresh-cw' }
              ]"
              :ui="{
                item: 'border-b border-default last:border-b-0',
                trigger: 'px-0 py-4 hover:bg-transparent',
                content: 'px-0 pb-4'
              }"
            >
              <template #leading="{ item }">
                <div class="flex min-w-0 items-center gap-3">
                  <UIcon :name="item.icon" class="size-5 text-muted" />
                  <span class="truncate text-base font-medium text-highlighted">{{ item.label }}</span>
                </div>
              </template>

              <template #content="{ item }">
                <div v-if="item.value === 'database-tables'" class="space-y-3 pt-2">
                  <div
                    v-for="table in databaseTables"
                    :key="table.name"
                    class="rounded-lg bg-elevated/30 px-4 py-3"
                  >
                    <div class="font-mono text-base text-highlighted">
                      {{ table.name }}
                    </div>
                    <div class="mt-1 text-sm text-muted">
                      {{ table.description }}
                    </div>
                  </div>
                </div>

                <div v-else-if="item.value === 'when-to-rebuild'" class="space-y-4 pt-2">
                  <p class="text-sm leading-6 text-muted">
                    The index is automatically updated when you save annotations. A full rebuild is only needed if:
                  </p>

                  <div class="space-y-3">
                    <div
                      v-for="(reason, index) in rebuildReasons"
                      :key="reason"
                      class="flex items-start gap-3"
                    >
                      <div class="flex size-8 shrink-0 items-center justify-center rounded-full bg-elevated text-sm font-semibold text-muted">
                        {{ index + 1 }}
                      </div>
                      <p class="pt-0.5 text-sm leading-6 text-muted">
                        {{ reason }}
                      </p>
                    </div>
                  </div>
                </div>
              </template>
            </UAccordion>
          </div>
        </UCard>

        <UAlert
          color="warning"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Rebuilding takes time"
          description="Rebuilding the global index processes all XML files in all projects. This can take several minutes for large installations. The process runs in the background and won't block other operations."
        />
      </div>
    </template>
  </UDashboardPanel>
</template>
