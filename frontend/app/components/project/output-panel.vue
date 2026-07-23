<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { ActionOutput, ActionOutputFile } from '@/types/action-output'

const props = defineProps<{ outputs: ActionOutput[], pending: boolean, error?: unknown, summary: string, canManage: boolean }>()
const emit = defineEmits<{
  download: [output: ActionOutput]
  downloadFile: [output: ActionOutput, file: ActionOutputFile]
  share: [output: ActionOutput]
  delete: [output: ActionOutput]
}>()
const expanded = ref<Set<string>>(new Set())

function toggle(outputId: string) {
  const next = new Set(expanded.value)
  if (next.has(outputId)) {
    next.delete(outputId)
  } else {
    next.add(outputId)
  }
  expanded.value = next
}
function formatDate(value?: string | null) {
  if (!value) return 'Never'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ${units[unit]}`
}
function items(output: ActionOutput): DropdownMenuItem[] {
  const result: DropdownMenuItem[] = [{ label: 'Download ZIP', icon: 'i-lucide-download', onSelect: () => emit('download', output) }]
  if (props.canManage) result.push(
    { label: output.shareEnabled ? 'Manage share' : 'Share', icon: 'i-lucide-key-round', onSelect: () => emit('share', output) },
    { label: 'Delete', icon: 'i-lucide-trash-2', color: 'error', onSelect: () => emit('delete', output) }
  )
  return result
}
</script>

<template>
  <div class="h-full space-y-5 overflow-y-auto p-4">
    <div class="flex items-center gap-2 text-sm font-semibold text-highlighted">
      <UIcon name="i-lucide-package-open" class="size-4 text-muted" /><span>Outputs</span>
    </div>
    <p class="text-xs text-muted">
      {{ summary }}
    </p>
    <USeparator />
    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-circle"
      :title="extractApiErrorMessage(error, 'Failed to load outputs')"
    />
    <div v-if="pending && outputs.length === 0" class="flex items-center gap-2 py-3 text-sm text-muted">
      <UIcon name="i-lucide-loader-2" class="size-4 animate-spin" />Loading outputs...
    </div>
    <div v-else-if="outputs.length === 0" class="rounded-lg border border-dashed border-default p-4 text-sm text-muted">
      No completed Action outputs yet.
    </div>
    <div v-else class="space-y-3">
      <article v-for="output in outputs" :key="output.id" class="rounded-xl border border-default bg-default p-3">
        <div class="flex items-start justify-between gap-2">
          <button class="min-w-0 text-left" type="button" @click="toggle(output.id)">
            <div class="flex items-center gap-2">
              <UIcon :name="expanded.has(output.id) ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'" class="size-4 text-muted" /><span class="truncate text-sm font-semibold text-highlighted">{{ output.processorName }}</span>
            </div>
          </button>
          <div class="flex items-center gap-1">
            <UBadge
              v-if="output.shareEnabled"
              color="success"
              variant="soft"
              size="sm"
            >
              Shared
            </UBadge>
            <UDropdownMenu :items="items(output)" :content="{ align: 'end' }">
              <UButton
                icon="i-lucide-ellipsis-vertical"
                color="neutral"
                variant="ghost"
                size="xs"
              />
            </UDropdownMenu>
          </div>
        </div>
        <div class="mt-2 grid gap-1 text-xs text-muted">
          <span>{{ formatDate(output.completedAt) }} · {{ output.fileCount }} file{{ output.fileCount === 1 ? '' : 's' }} · {{ formatBytes(output.totalSizeBytes) }}</span>
          <span>Retained until {{ formatDate(output.expiresAt) }}</span>
        </div>
        <div v-if="expanded.has(output.id)" class="mt-3 space-y-1 border-t border-default pt-3">
          <button
            v-for="file in output.files"
            :key="file.id"
            type="button"
            class="flex w-full items-center justify-between gap-3 rounded-md px-2 py-1.5 text-left hover:bg-elevated"
            @click="emit('downloadFile', output, file)"
          >
            <span class="min-w-0 truncate text-xs text-highlighted"><UIcon name="i-lucide-file" class="mr-1 size-3.5" />{{ file.fileName }}</span><span class="shrink-0 text-xs text-muted">{{ formatBytes(file.sizeBytes) }}</span>
          </button>
        </div>
      </article>
    </div>
  </div>
</template>
