<script setup lang="ts">
import type { PageMovePreview, PageMoveResult } from '@/types/page-move'
import type { ProjectData } from '@/types/project-page'

const props = defineProps<{
  workspaceId: string
  projectId: string
  projectName: string
  pageIds: string[]
  operation?: 'move' | 'copy'
}>()
const emit = defineEmits<{ close: [result: PageMoveResult | null] }>()
const toast = useToast()
const operation = props.operation ?? 'move'
const verb = operation === 'copy' ? 'Copy' : 'Move'
const pastVerb = operation === 'copy' ? 'copied' : 'moved'
const icon = operation === 'copy' ? 'i-lucide-copy' : 'i-lucide-folder-input'
const endpoint = `/api/workspaces/${props.workspaceId}/projects/${props.projectId}/pages/${operation}`
const {
  destinationProjectId, conflictPolicy, prefix, suffix,
  preview, previewPending, moving, error, canMove, refreshPreview, move
} = usePageMove(props.pageIds, {
  preview: body => $fetch<PageMovePreview>(`${endpoint}/preview`, { method: 'POST', body }),
  move: body => $fetch<PageMovePreview>(endpoint, { method: 'POST', body })
}, operation)
const transferCount = computed(() => (preview.value?.movedCount ?? 0) + (preview.value?.copiedCount ?? 0))
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
    ? `1 destination page will be deleted with all its images, XML, and version history. ${operation === 'copy' ? 'A copy of the source page' : 'The source page'} will take its place.`
    : `${count} destination pages will be deleted with all their images, XML, and version history. ${operation === 'copy' ? 'Copies of the source pages' : 'The source pages'} will take their place.`
})

async function submit() {
  const result = await move()
  if (!result) return
  toast.add({
    title: `Pages ${pastVerb}`,
    description: `${result.movedCount + (result.copiedCount ?? 0)} ${pastVerb}, ${result.skippedCount} skipped, ${result.overwrittenCount} overwritten, ${result.renamedCount} renamed.`,
    color: 'success',
    icon
  })
  emit('close', result)
}
</script>

<template>
  <UiResponsiveSlideover
    :title="`${verb} pages`"
    :description="`${verb} ${pageIds.length} selected ${pageIds.length === 1 ? 'page' : 'pages'} from ${projectName} to another project in this workspace.`"
    :dismissible="!moving"
    :close="moving ? false : { onClick: () => emit('close', null) }"
  >
    <template #header>
      <UiSlideoverHeader
        :title="`${verb} pages`"
        :icon="icon"
        :description="`${verb} ${pageIds.length} selected ${pageIds.length === 1 ? 'page' : 'pages'} from ${projectName} to another project in this workspace.`"
      />
    </template>
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
        <p v-if="operation === 'copy'" class="text-sm text-muted">
          Copies keep images, current annotations, and metadata, and start as open pages. Task links and version history stay with the originals.
        </p>
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
          :title="`${verb} needs attention`"
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
            :title="`${verb} blocked`"
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
            {{ transferCount }} to {{ operation }} · {{ preview.skippedCount }} skipped · {{ preview.overwrittenCount }} overwritten · {{ preview.renamedCount }} renamed
          </p>
          <ProjectPageMovePreview :items="preview.items" :operation="operation" />
          <p v-if="transferCount === 0" class="text-sm text-muted">
            No pages would {{ operation }} with these settings.
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
          :label="preview?.overwrittenCount ? `${verb} ${transferCount} ${transferCount === 1 ? 'page' : 'pages'} · replace ${preview.overwrittenCount}` : `${verb} ${preview ? transferCount : pageIds.length} ${(preview ? transferCount : pageIds.length) === 1 ? 'page' : 'pages'}`"
          :color="preview?.overwrittenCount ? 'warning' : 'primary'"
          :icon="icon"
          :disabled="!canMove"
          :loading="moving"
          @click="submit"
        />
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
