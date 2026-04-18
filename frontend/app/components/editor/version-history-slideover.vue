<script setup lang="ts">
import type { PageXmlVersion } from '@/types/version'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'

const props = defineProps<{
  projectId: string
  pageId: string
  xmlId: string
  annotationBasePath?: string
}>()

const emit = defineEmits<{
  close: [result: 'restored' | 'closed']
}>()

const toast = useToast()
const { confirm } = useOverlayDialogs()

const annotationBasePath = computed(() =>
  props.annotationBasePath || `/api/projects/${props.projectId}/pages/${props.pageId}/annotations`
)

const { data: versions, status, refresh } = useFetch<PageXmlVersion[]>(
  () => `${annotationBasePath.value}/${props.xmlId}/versions`,
  { lazy: true }
)

const restoringId = ref<string | null>(null)

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins}m ago`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}h ago`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 30) return `${diffDays}d ago`
  return date.toLocaleDateString()
}

function actorLabel(version: PageXmlVersion): string {
  return version.userDisplayName || version.username || version.userId
}

function actorHandle(version: PageXmlVersion): string | null {
  if (!version.username || version.username === actorLabel(version)) return null
  return `@${version.username}`
}

function versionMessage(version: PageXmlVersion): string {
  const base = version.comment?.trim()
  const actor = actorLabel(version)
  if (base && actor) return `${base} by ${actor}`
  return base || actor
}

async function handleRestore(version: PageXmlVersion) {
  const confirmed = await confirm({
    title: `Restore to version ${version.versionNumber}?`,
    message: 'Your current work will be saved as a new version first.',
    confirmLabel: 'Restore',
    confirmColor: 'warning',
    confirmIcon: 'i-lucide-history'
  })

  if (!confirmed) return

  restoringId.value = version.id
  try {
    await $fetch(
      `${annotationBasePath.value}/${props.xmlId}/versions/${version.id}/restore`,
      { method: 'POST' }
    )
    toast.add({
      title: 'Version restored',
      description: `Restored to version ${version.versionNumber}`,
      color: 'success',
      icon: 'i-lucide-check'
    })
    emit('close', 'restored')
  } catch (error: any) {
    toast.add({
      title: 'Restore failed',
      description: error.data?.message || 'Failed to restore version',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    restoringId.value = null
  }
}

</script>

<template>
  <USlideover
    side="right"
    title="Version History"
    :close="{ onClick: () => emit('close', 'closed') }"
  >
    <template #body>
      <div v-if="status === 'pending'" class="flex flex-col gap-3">
        <USkeleton v-for="i in 3" :key="i" class="h-20 w-full" />
      </div>

      <UEmpty
        v-else-if="!versions?.length"
        icon="i-lucide-history"
        title="No versions yet"
        description="Versions are created automatically each time you save."
      />

      <div v-else class="flex flex-col gap-3">
        <div
          v-for="version in versions"
          :key="version.id"
          class="rounded-sm border border-default p-3 hover:bg-elevated/50 transition-colors"
        >
          <div class="flex items-start justify-between gap-2">
            <div class="flex items-center gap-2">
              <UBadge color="neutral" variant="subtle" size="sm">
                v{{ version.versionNumber }}
              </UBadge>
              <span class="text-sm text-muted">
                {{ formatRelativeTime(version.created) }}
              </span>
            </div>
            <UButton
              size="xs"
              color="neutral"
              variant="subtle"
              icon="i-lucide-undo-2"
              label="Restore"
              :loading="restoringId === version.id"
              :disabled="restoringId !== null"
              @click="handleRestore(version)"
            />
          </div>

          <div class="mt-1.5 flex flex-col gap-0.5">
            <p v-if="versionMessage(version)" class="text-xs text-muted truncate">
              {{ versionMessage(version) }}
            </p>
            <div class="flex items-center gap-3 text-xs text-dimmed">
              <span>{{ formatFileSize(version.fileSize) }}</span>
              <span v-if="actorHandle(version)" class="truncate">{{ actorHandle(version) }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-center">
        <UButton
          color="neutral"
          variant="ghost"
          @click="emit('close', 'closed')"
        >
          Close
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
