<script setup lang="ts">
import type { PageMovePreview, PageMoveResult } from '@/types/page-move'
import type { ProjectData } from '@/types/project-page'

const props = defineProps<{
  workspaceId: string
  projectId: string
  projectName: string
  pageIds: string[]
}>()
const emit = defineEmits<{ close: [result: PageMoveResult | null] }>()
const toast = useToast()
const endpoint = `/api/workspaces/${props.workspaceId}/projects/${props.projectId}/pages/move`
const {
  destinationProjectId, conflictPolicy, prefix, suffix,
  preview, previewPending, moving, error, canMove, refreshPreview, move
} = usePageMove(props.pageIds, {
  preview: body => $fetch<PageMovePreview>(`${endpoint}/preview`, { method: 'POST', body }),
  move: body => $fetch<PageMovePreview>(endpoint, { method: 'POST', body })
})
const { data: projects, pending: projectsPending, error: projectsError, refresh: refreshProjects } = await useFetch<ProjectData[]>(
  `/api/workspaces/${props.workspaceId}/projects`
)
const destinations = computed(() => (projects.value ?? [])
  .filter(project => project.id !== props.projectId && !project.locked && project.capabilities?.canUpload !== false)
  .map(project => ({ label: project.name, value: project.id })))
const policies = [
  { label: 'Skip clashing pages', value: 'SKIP' },
  { label: 'Overwrite destination pages', value: 'OVERWRITE' },
  { label: 'Rename clashing pages', value: 'RENAME' }
]

const overwriteDescription = computed(() => {
  const count = preview.value?.overwrittenCount ?? 0
  return count === 1
    ? '1 destination page will be deleted with all its images, XML, and version history. The source page will take its place.'
    : `${count} destination pages will be deleted with all their images, XML, and version history. The source pages will take their place.`
})

async function submit() {
  const result = await move()
  if (!result) return
  toast.add({
    title: 'Pages moved',
    description: `${result.movedCount} moved, ${result.skippedCount} skipped, ${result.overwrittenCount} overwritten, ${result.renamedCount} renamed.`,
    color: 'success',
    icon: 'i-lucide-folder-input'
  })
  emit('close', result)
}
</script>

<template>
  <UiResponsiveSlideover
    title="Move pages"
    :description="`Move ${pageIds.length} selected ${pageIds.length === 1 ? 'page' : 'pages'} from ${projectName} to another project in this workspace.`"
    :dismissible="!moving"
    :close="moving ? false : { onClick: () => emit('close', null) }"
  >
    <template #body>
      <div class="space-y-5">
        <UAlert v-if="projectsError" color="error" title="Could not load destination projects">
          <template #actions>
            <UButton
              label="Retry"
              color="neutral"
              variant="outline"
              @click="refreshProjects()"
            />
          </template>
        </UAlert>
        <UFormField label="Destination project" required>
          <USelectMenu
            v-model="destinationProjectId"
            :items="destinations"
            value-key="value"
            placeholder="Choose a project"
            aria-label="Destination project"
            :loading="projectsPending"
            :disabled="moving || projectsPending"
            class="w-full"
          />
        </UFormField>
        <p v-if="!projectsPending && !projectsError && !destinations.length" class="text-sm text-muted">
          No other unlocked projects are available in this workspace.
        </p>
        <UFormField label="When page names clash">
          <USelect
            v-model="conflictPolicy"
            :items="policies"
            :disabled="moving"
            class="w-full"
          />
        </UFormField>
        <div v-if="conflictPolicy === 'RENAME'" class="space-y-3">
          <p class="text-sm text-muted">
            Prefix and suffix apply only to clashing page names. Asset filenames stay the same.
          </p>
          <div class="grid grid-cols-2 gap-3">
            <UFormField label="Prefix">
              <UInput
                v-model="prefix"
                :disabled="moving"
                :maxlength="255"
                class="w-full"
              />
            </UFormField>
            <UFormField label="Suffix">
              <UInput
                v-model="suffix"
                :disabled="moving"
                :maxlength="255"
                class="w-full"
              />
            </UFormField>
          </div>
        </div>
        <UAlert
          v-if="error"
          color="error"
          title="Move needs attention"
          :description="error"
          role="alert"
        >
          <template #actions>
            <UButton
              label="Refresh preview"
              color="neutral"
              variant="outline"
              :disabled="moving || previewPending"
              @click="refreshPreview()"
            />
          </template>
        </UAlert>
        <p v-if="previewPending" class="flex items-center gap-2 text-sm text-muted" role="status">
          <UIcon name="i-lucide-loader-circle" class="animate-spin" /> Checking selected pages…
        </p>
        <template v-if="preview">
          <UAlert
            v-for="blocker in preview.blockers"
            :key="blocker"
            color="error"
            title="Move blocked"
            :description="blocker"
          />
          <UAlert
            v-if="preview.overwrittenCount > 0"
            color="warning"
            variant="subtle"
            icon="i-lucide-triangle-alert"
            title="Destination pages will be permanently replaced"
            :description="overwriteDescription"
          />
          <p class="text-sm" aria-live="polite">
            {{ preview.movedCount }} to move · {{ preview.skippedCount }} skipped · {{ preview.overwrittenCount }} overwritten · {{ preview.renamedCount }} renamed
          </p>
          <ProjectPageMovePreview :items="preview.items" />
          <p v-if="preview.movedCount === 0" class="text-sm text-muted">
            No pages would move with these settings.
          </p>
        </template>
      </div>
    </template>
    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton
          label="Cancel"
          color="neutral"
          variant="ghost"
          :disabled="moving"
          @click="emit('close', null)"
        />
        <UButton
          :label="preview?.overwrittenCount ? `Move ${preview.movedCount} ${preview.movedCount === 1 ? 'page' : 'pages'} · replace ${preview.overwrittenCount}` : `Move ${preview?.movedCount ?? pageIds.length} ${(preview?.movedCount ?? pageIds.length) === 1 ? 'page' : 'pages'}`"
          :color="preview?.overwrittenCount ? 'warning' : 'primary'"
          icon="i-lucide-folder-input"
          :disabled="!canMove"
          :loading="moving"
          @click="submit"
        />
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
