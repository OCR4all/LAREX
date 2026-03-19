<script setup lang="ts">
definePageMeta({ layout: 'admin', middleware: 'admin' })

const toast = useToast()

const isRebuilding = ref(false)
const rebuildStatus = ref<'idle' | 'running' | 'success' | 'error'>('idle')
const lastRebuildTime = ref<Date | null>(null)

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
        <template #right>
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            :loading="isRebuilding"
            @click="rebuildGlobalIndex"
          >
            Rebuild All Indexes
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="p-6 space-y-6">
        <div>
          <h1 class="text-2xl font-bold mb-2">
            Search Index Management
          </h1>
          <p class="text-muted">
            Manage the search index used for filtering pages by text content and labels.
            The index is automatically updated when annotations are saved, but you can manually rebuild it here if needed.
            It also powers workspace text search and fuzzy lookup across persisted transcriptions.
          </p>
        </div>

        <UCard>
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-database" class="w-5 h-5" />
              <h3 class="font-semibold">
                Index Status
              </h3>
            </div>
          </template>

          <div class="space-y-4">
            <div class="flex items-center gap-3">
              <div
                class="w-3 h-3 rounded-full"
                :class="{
                  'bg-neutral-400': rebuildStatus === 'idle',
                  'bg-yellow-500 animate-pulse': rebuildStatus === 'running',
                  'bg-green-500': rebuildStatus === 'success',
                  'bg-red-500': rebuildStatus === 'error'
                }"
              />
              <span class="text-sm">
                <template v-if="rebuildStatus === 'idle'">Ready</template>
                <template v-else-if="rebuildStatus === 'running'">Rebuilding indexes...</template>
                <template v-else-if="rebuildStatus === 'success'">Last rebuild completed successfully</template>
                <template v-else-if="rebuildStatus === 'error'">Last rebuild failed</template>
              </span>
            </div>

            <p v-if="lastRebuildTime" class="text-sm text-muted">
              Last rebuild triggered: {{ lastRebuildTime.toLocaleString() }}
            </p>
          </div>
        </UCard>

        <UCard>
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-info" class="w-5 h-5" />
              <h3 class="font-semibold">
                About the Search Index
              </h3>
            </div>
          </template>

          <div class="prose prose-sm max-w-none text-muted">
            <p>
              The search index enables fast filtering of pages in the editor by:
            </p>
            <ul>
              <li><strong>Text Content</strong>: Search for pages containing specific text in their transcriptions</li>
              <li><strong>Workspace Search</strong>: Rank text hits across projects and pages for workspace-wide transcription search</li>
              <li><strong>Labels</strong>: Filter pages that have regions or text lines with specific labels assigned</li>
              <li><strong>Tags</strong>: Filter pages by their assigned tags</li>
            </ul>
            <p>
              The index is stored in multiple database tables:
            </p>
            <ul>
              <li><code>page_text_content</code>: Stores indexed text content for filtering and workspace text search</li>
              <li><code>search_lexicon_entries</code>: Stores normalized tokens for fuzzy search expansion</li>
              <li><code>page_label_index</code>: Stores label assignments for fast label filtering</li>
            </ul>
            <p>
              <strong>When to rebuild:</strong> The index is automatically updated when you save annotations.
              A full rebuild is only needed if:
            </p>
            <ul>
              <li>You've imported pages from external sources that bypassed the normal save flow</li>
              <li>The index becomes corrupted or out of sync</li>
              <li>You've upgraded from an older version that didn't have search indexing</li>
            </ul>
          </div>
        </UCard>

        <UAlert
          color="info"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Rebuilding takes time"
          description="Rebuilding the global index processes all XML files in all projects. This can take several minutes for large installations. The process runs in the background and won't block other operations."
        />
      </div>
    </template>
  </UDashboardPanel>
</template>
