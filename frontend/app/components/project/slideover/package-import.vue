<script setup lang="ts">
import type {
  ProjectPackageImportOptions,
  ProjectPackageImportPreview,
  ProjectPackageProjectImportAction,
  ProjectPackageResourceImportAction,
  ProjectPackageResourcePreview,
  ProjectPackageToolkitType
} from '@/types/project-package-import'
import {
  projectPackageRenameNameError,
  resolveProjectPackageRenameName
} from '@/utils/project-package-import'

const props = defineProps<{
  fileName: string
  preview: ProjectPackageImportPreview
}>()

const emit = defineEmits<{
  close: [ProjectPackageImportOptions | null]
}>()

const projectAction = ref<ProjectPackageProjectImportAction>(
  props.preview.existingProjectId ? 'RENAME' : 'AUTO'
)
const renamedProjectName = ref(resolveProjectPackageRenameName(
  props.preview.projectName,
  props.preview.suggestedProjectName
))
const importResources = ref(true)
const resourceActions = reactive<Partial<Record<ProjectPackageToolkitType, ProjectPackageResourceImportAction>>>({})

for (const resource of props.preview.resources) {
  resourceActions[resource.type] = resource.existingId
    ? (resource.identical ? 'REUSE' : 'RENAME')
    : 'AUTO'
}

const projectActionItems = [
  { label: 'Replace existing project', value: 'REPLACE' },
  { label: 'Import as renamed copy', value: 'RENAME' },
  { label: 'Skip this project', value: 'SKIP' }
]

const hasResourceReplacement = computed(() =>
  importResources.value
  && props.preview.resources.some(resource => resourceActions[resource.type] === 'REPLACE')
)
const renamedProjectNameError = computed(() => {
  if (projectAction.value !== 'RENAME') return undefined
  return projectPackageRenameNameError(renamedProjectName.value)
})

function resourceActionItems(resource: ProjectPackageResourcePreview) {
  const items: Array<{ label: string, value: ProjectPackageResourceImportAction }> = []
  if (!resource.existingId) {
    items.push({ label: 'Upload resource', value: 'AUTO' })
  } else {
    items.push({
      label: resource.identical ? 'Reuse identical resource' : 'Use existing unchanged',
      value: 'REUSE'
    })
    if (resource.replaceAllowed) {
      items.push({ label: 'Replace existing resource', value: 'REPLACE' })
    }
    items.push({ label: 'Upload as renamed copy', value: 'RENAME' })
  }
  items.push({ label: 'Skip resource', value: 'SKIP' })
  return items
}

function setResourceAction(type: ProjectPackageToolkitType, value: unknown) {
  resourceActions[type] = value as ProjectPackageResourceImportAction
}

function formatToolkitType(type: ProjectPackageToolkitType) {
  return type.toLowerCase().split('_').map(part => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(' ')
}

function resourceStatus(resource: ProjectPackageResourcePreview) {
  if (!resource.existingId) return { label: 'New', color: 'success' as const }
  if (resource.identical) return { label: 'Identical existing', color: 'info' as const }
  return { label: 'Name conflict', color: 'warning' as const }
}

function submit() {
  emit('close', {
    previewToken: props.preview.previewToken,
    projectAction: projectAction.value,
    renamedProjectName: projectAction.value === 'RENAME'
      ? String(renamedProjectName.value ?? '').trim()
      : null,
    importResources: importResources.value,
    resourceActions: { ...resourceActions }
  })
}
</script>

<template>
  <UiResponsiveSlideover :close="{ onClick: () => emit('close', null) }">
    <template #header>
      <UiSlideoverHeader title="Review Project Package" icon="i-lucide-package-search" />
    </template>

    <template #body>
      <div class="space-y-5">
        <div class="rounded-lg border border-default bg-elevated/40 p-4">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <p class="truncate text-xs text-muted">
                {{ props.fileName }}
              </p>
              <h3 class="mt-1 text-base font-semibold text-highlighted">
                {{ props.preview.projectName }}
              </h3>
              <p v-if="props.preview.projectDescription" class="mt-1 text-sm text-muted">
                {{ props.preview.projectDescription }}
              </p>
            </div>
            <UBadge
              :color="props.preview.existingProjectId ? 'warning' : 'success'"
              variant="subtle"
            >
              {{ props.preview.existingProjectId ? 'Project exists' : 'New project' }}
            </UBadge>
          </div>

          <div class="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div>
              <p class="text-xs text-muted">
                Pages
              </p>
              <p class="text-sm font-medium text-highlighted">
                {{ props.preview.pageNames.length }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted">
                Images
              </p>
              <p class="text-sm font-medium text-highlighted">
                {{ props.preview.imageCount }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted">
                XML files
              </p>
              <p class="text-sm font-medium text-highlighted">
                {{ props.preview.xmlCount }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted">
                XML versions
              </p>
              <p class="text-sm font-medium text-highlighted">
                {{ props.preview.xmlVersionCount }}
              </p>
            </div>
          </div>
        </div>

        <div v-if="props.preview.existingProjectId" class="space-y-2">
          <label class="text-sm font-medium text-highlighted">Project name conflict</label>
          <USelect
            :model-value="projectAction"
            :items="projectActionItems"
            value-key="value"
            class="w-full"
            @update:model-value="projectAction = $event as ProjectPackageProjectImportAction"
          />
          <UFormField
            v-if="projectAction === 'RENAME'"
            label="New project name"
            name="renamedProjectName"
            required
            :error="renamedProjectNameError"
          >
            <UInput
              v-model="renamedProjectName"
              placeholder="Enter a unique project name"
              :maxlength="100"
              class="w-full"
            />
          </UFormField>
          <UAlert
            v-if="projectAction === 'REPLACE'"
            color="warning"
            variant="subtle"
            icon="i-lucide-triangle-alert"
            title="The existing project will be replaced"
            description="Its pages and files will be removed after the new project has imported successfully. The replacement receives a new internal identity."
          />
        </div>

        <div>
          <div class="mb-2 flex items-center justify-between gap-3">
            <h3 class="text-sm font-medium text-highlighted">
              Pages in package
            </h3>
            <UBadge color="neutral" variant="subtle">
              {{ props.preview.pageNames.length }}
            </UBadge>
          </div>
          <div class="max-h-40 overflow-y-auto rounded-lg border border-default">
            <div
              v-for="(pageName, index) in props.preview.pageNames"
              :key="`${index}-${pageName}`"
              class="flex items-center gap-2 border-b border-default px-3 py-2 text-sm last:border-b-0"
            >
              <UIcon name="i-lucide-file" class="size-4 shrink-0 text-muted" />
              <span class="truncate">{{ pageName }}</span>
            </div>
          </div>
        </div>

        <div class="space-y-3">
          <div class="flex items-start justify-between gap-4">
            <div>
              <h3 class="text-sm font-medium text-highlighted">
                Assigned resources
              </h3>
              <p class="mt-0.5 text-xs text-muted">
                Choose how toolkit resources are handled in this workspace.
              </p>
            </div>
            <USwitch v-model="importResources" label="Import resources" />
          </div>

          <div v-if="props.preview.resources.length === 0" class="rounded-lg border border-dashed border-default p-4 text-sm text-muted">
            This package has no assigned toolkit resources.
          </div>

          <div
            v-for="resource in props.preview.resources"
            :key="resource.type"
            class="rounded-lg border border-default p-3"
            :class="{ 'opacity-55': !importResources }"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="min-w-0">
                <p class="text-xs text-muted">
                  {{ formatToolkitType(resource.type) }}
                </p>
                <p class="truncate text-sm font-medium text-highlighted">
                  {{ resource.name }}
                </p>
              </div>
              <UBadge
                :color="resourceStatus(resource).color"
                variant="subtle"
              >
                {{ resourceStatus(resource).label }}
              </UBadge>
            </div>
            <USelect
              :model-value="resourceActions[resource.type]"
              :items="resourceActionItems(resource)"
              :disabled="!importResources"
              value-key="value"
              class="mt-3 w-full"
              @update:model-value="setResourceAction(resource.type, $event)"
            />
            <p
              v-if="resource.existingId && !resource.replaceAllowed"
              class="mt-2 text-xs text-warning"
            >
              This protected resource cannot be replaced.
            </p>
          </div>

          <UAlert
            v-if="hasResourceReplacement"
            color="warning"
            variant="subtle"
            icon="i-lucide-triangle-alert"
            title="Replacing resources affects other projects"
            description="Any other project assigned to a replaced resource will immediately use the imported definition."
          />
        </div>

        <UAlert
          v-for="warning in props.preview.warnings"
          :key="warning"
          color="warning"
          variant="subtle"
          icon="i-lucide-info"
          :description="warning"
        />
      </div>
    </template>

    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton
          :color="projectAction === 'SKIP' ? 'neutral' : 'primary'"
          :icon="projectAction === 'SKIP' ? 'i-lucide-ban' : 'i-lucide-upload'"
          :disabled="Boolean(renamedProjectNameError)"
          @click="submit"
        >
          {{ projectAction === 'SKIP' ? 'Skip Import' : 'Import Package' }}
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
