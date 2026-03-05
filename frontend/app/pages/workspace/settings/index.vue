<script setup lang="ts">
import { wsKey, globalKey } from '@/utils/fetch-keys'
import type { WorkspaceMember } from '~/types/index'
import { LazyUiDeleteSlideover } from '#components'

interface Workspace {
  id: string
  name: string
  description?: string
  avatar?: string
  ownerUserId: string
  isPersonal: boolean
  type?: 'personal' | 'team'
  codecId?: string
  labelSetId?: string
  tagSetId?: string
  defaultGtIndex?: number | null
  defaultRecognitionIndices?: number[] | null
  created?: string
  updated?: string
}

interface StorageQuota {
  usagePercentage: number
  currentUsageFormatted: string
  quotaLimitFormatted: string
}

const workspaceStore = useWorkspaceStore()
const toast = useToast()
const overlay = useOverlay()
const { refreshWorkspaceDetails, refreshWorkspaceList } = useDataRefresh()

const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

const selectedWorkspace = computed(() => workspaceStore.selectedWorkspaceId)

const { user } = useUserSession()
const currentUserId = computed(() => user.value?.id || '')

const { data: workspace } = await useFetch<Workspace>(
  `/api/workspaces/${selectedWorkspace.value}`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'details')
      : globalKey('pending', 'workspace', 'details')),
    watch: [selectedWorkspace],
    immediate: !!selectedWorkspace.value
  }
)

const { data: storageQuota } = await useFetch<StorageQuota>(
  `/api/storage/quotas/workspace/${selectedWorkspace.value}`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'storage', 'quota')
      : globalKey('pending', 'storage', 'quota')),
    watch: [selectedWorkspace],
    immediate: !!selectedWorkspace.value
  }
)

const storageColor = computed(() => {
  if (!storageQuota.value) return 'primary'
  const pct = storageQuota.value.usagePercentage
  if (pct >= 90) return 'error'
  if (pct >= 80) return 'warning'
  return 'primary'
})

const { data: members } = await useFetch<WorkspaceMember[]>(
  `/api/workspaces/${selectedWorkspace.value}/members`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'members', 'list')
      : globalKey('pending', 'members', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const currentUserMember = computed(() => {
  if (!members.value) return undefined
  return members.value.find((m: WorkspaceMember) => m.userId === currentUserId.value)
})

const isCurrentUserAdmin = computed(() => {
  if (!workspace.value) return false
  if (workspace.value.isPersonal) return true
  return currentUserMember.value?.role === 'ADMINISTRATOR'
    && currentUserMember.value?.invitationStatus === 'ACCEPTED'
})

const isEditing = ref(false)
const isSaving = ref(false)

const form = reactive({
  name: '',
  description: '',
  codecId: '',
  labelSetId: '',
  tagSetId: '',
  defaultGtIndexInput: '0',
  defaultRecognitionIndicesInput: '1'
})

function formatRecognitionIndices(value?: number[] | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(', ') : '1'
}

function parseDefaultGtIndex(value: string | undefined): number {
  const parsed = Number.parseInt(String(value ?? '').trim(), 10)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error('Default GT index must be a non-negative integer.')
  }
  return parsed
}

function parseRecognitionIndices(value: string | undefined, gtIndex: number): number[] {
  const parsed = String(value ?? '')
    .split(',')
    .map(v => Number.parseInt(v.trim(), 10))
    .filter(v => Number.isFinite(v) && v >= 0)
  const unique = [...new Set(parsed)].sort((a, b) => a - b).filter(v => v !== gtIndex)
  if (unique.length === 0) {
    throw new Error('Provide at least one recognition index different from the GT index.')
  }
  return unique
}

const { data: codecs, error: codecsError } = await useFetch<any[]>(
  `/api/workspaces/${selectedWorkspace.value}/codecs`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'codecs', 'list')
      : globalKey('pending', 'codecs', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    transform: (codecs: any[]) => codecs.map(codec => ({
      label: codec.name,
      value: codec.id
    })),
    immediate: !!selectedWorkspace.value
  }
)

const { data: labelSets, error: labelSetsError } = await useFetch<any[]>(
  `/api/workspaces/${selectedWorkspace.value}/label-sets`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'label-sets', 'list')
      : globalKey('pending', 'label-sets', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    transform: (labelSets: any[]) => labelSets.map(ls => ({
      label: ls.meta.name,
      value: ls.id
    })),
    immediate: !!selectedWorkspace.value
  }
)

const { data: tagSets, error: tagSetsError } = await useFetch<any[]>(
  `/api/workspaces/${selectedWorkspace.value}/tag-sets`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'tag-sets', 'list')
      : globalKey('pending', 'tag-sets', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    transform: (tagSets: any[]) => tagSets.map(t => ({
      label: t.meta.name,
      value: t.id
    })),
    immediate: !!selectedWorkspace.value
  }
)

watchEffect(() => {
  if (workspace.value) {
    form.name = workspace.value.name || ''
    form.description = workspace.value.description || ''
    form.codecId = workspace.value.codecId || ''
    form.labelSetId = workspace.value.labelSetId || ''
    form.tagSetId = workspace.value.tagSetId || ''
    form.defaultGtIndexInput = String(workspace.value.defaultGtIndex ?? 0)
    form.defaultRecognitionIndicesInput = formatRecognitionIndices(workspace.value.defaultRecognitionIndices)
  }
})

const startEditing = () => {
  isEditing.value = true
}

const cancelEditing = () => {
  if (workspace.value) {
    form.name = workspace.value.name || ''
    form.description = workspace.value.description || ''
    form.codecId = workspace.value.codecId || ''
    form.labelSetId = workspace.value.labelSetId || ''
    form.tagSetId = workspace.value.tagSetId || ''
    form.defaultGtIndexInput = String(workspace.value.defaultGtIndex ?? 0)
    form.defaultRecognitionIndicesInput = formatRecognitionIndices(workspace.value.defaultRecognitionIndices)
  }
  isEditing.value = false
}

const saveWorkspace = async () => {
  if (!selectedWorkspace.value) return

  isSaving.value = true
  try {
    const defaultGtIndex = parseDefaultGtIndex(form.defaultGtIndexInput)
    const defaultRecognitionIndices = parseRecognitionIndices(form.defaultRecognitionIndicesInput, defaultGtIndex)
    await $fetch(`/api/workspaces/${selectedWorkspace.value}`, {
      method: 'PUT',
      body: {
        name: form.name.trim(),
        description: form.description.trim() || null,
        codecId: form.codecId || null,
        labelSetId: form.labelSetId || null,
        tagSetId: form.tagSetId || null,
        defaultGtIndex,
        defaultRecognitionIndices
      }
    })

    await Promise.all([
      refreshWorkspaceDetails(selectedWorkspace.value),
      refreshWorkspaceList()
    ])

    isEditing.value = false

    toast.add({
      title: 'Workspace updated',
      description: 'Workspace settings have been saved',
      color: 'success'
    })
  } catch (err: any) {
    toast.add({
      title: 'Failed to update workspace',
      description: err?.data?.message || err?.message || 'An error occurred',
      color: 'error'
    })
  } finally {
    isSaving.value = false
  }
}

const leaveWorkspace = async () => {
  if (!selectedWorkspace.value || workspace.value?.isPersonal) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/leave`, {
      method: 'POST'
    })

    toast.add({
      title: 'Left workspace',
      description: 'You have left the workspace',
      color: 'success'
    })

    await refreshWorkspaceList()
    await workspaceStore.validateAndSelectWorkspace()
    navigateTo('/')
  } catch (err: any) {
    toast.add({
      title: 'Failed to leave workspace',
      description: err?.data?.message || 'You may be the last administrator',
      color: 'error'
    })
  }
}

async function openDeleteSlideover() {
  if (!workspace.value) return
  const instance = deleteSlideover.open({
    name: workspace.value.name,
    entityType: 'Workspace',
    warningMessage: 'This action cannot be undone. This will permanently delete the workspace, all projects, and remove all member associations.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspace.value.id}`, { method: 'DELETE' })
    toast.add({ title: 'Workspace deleted', color: 'success' })
    await refreshWorkspaceList()
    await workspaceStore.validateAndSelectWorkspace()
    navigateTo('/')
  } catch (err: any) {
    toast.add({ title: 'Failed to delete', description: err?.data?.message || 'An error occurred', color: 'error' })
  }
}
</script>

<template>
  <div class="flex flex-col gap-y-6">
    <UPageCard
      v-if="storageQuota"
      title="Storage"
      description="Storage usage for this workspace."
      variant="subtle"
    >
      <div class="flex flex-col gap-2 w-full">
        <div class="flex items-center justify-between text-sm">
          <span class="text-muted">Used</span>
          <span>{{ storageQuota.currentUsageFormatted }} / {{ storageQuota.quotaLimitFormatted }}</span>
        </div>
        <UProgress
          v-model="storageQuota.usagePercentage"
          :max="100"
          :color="storageColor"
          size="sm"
        />
        <p class="text-xs text-muted mt-1">
          {{ storageQuota.usagePercentage.toFixed(1) }}% of quota used
        </p>
        <UAlert
          v-if="storageQuota.usagePercentage >= 90"
          color="error"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Storage almost full"
          :description="`You're using ${storageQuota.usagePercentage.toFixed(0)}% of your storage quota. Consider removing unused projects or files.`"
          class="mt-2"
        />
        <UAlert
          v-else-if="storageQuota.usagePercentage >= 80"
          color="warning"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Storage quota approaching limit"
          :description="`You're using ${storageQuota.usagePercentage.toFixed(0)}% of your storage quota.`"
          class="mt-2"
        />
      </div>
    </UPageCard>

    <UPageCard
      data-tour="workspace-general-panel"
      v-if="workspace?.isPersonal"
      title="Personal Workspace"
      description="This is your personal workspace. It cannot be renamed or deleted."
      variant="subtle"
    >
      <div class="flex flex-col gap-4">
        <UiFormSectionHeader title="Metadata" />
        <UFormField label="Name">
          <UInput :model-value="workspace?.name" disabled class="max-w-md" />
        </UFormField>
        <UFormField label="Description">
          <UTextarea
            v-model="form.description"
            :disabled="!isEditing"
            placeholder="Optional description"
            class="max-w-md"
          />
        </UFormField>
        <UiFormSectionHeader data-tour="workspace-general-presets" title="Presets" />
        <UFormField label="Default Codec" hint="Default Codec for all newly created projects in this workspace">
          <USelect
            v-model="form.codecId"
            :items="codecs"
            icon="i-lucide-case-lower"
            :disabled="!isEditing || !!codecsError"
            placeholder="Select a codec"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Label Set" hint="Default Label Set for all newly created projects in this workspace">
          <USelect
            v-model="form.labelSetId"
            :items="labelSets"
            icon="i-lucide-tags"
            :disabled="!isEditing || !!labelSetsError"
            placeholder="Select a label set"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Tag Set" hint="Default Tag Set for all newly created projects in this workspace">
          <USelect
            v-model="form.tagSetId"
            :items="tagSets"
            icon="i-lucide-network"
            :disabled="!isEditing || !!tagSetsError"
            placeholder="Select a tag set"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default GT Index" hint="Single Ground Truth index used in the text editor for new projects.">
          <UInput
            v-model="form.defaultGtIndexInput"
            :disabled="!isEditing"
            placeholder="0"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Recognition Indices" hint="Comma-separated indices for recognition variants (e.g. 1, 2).">
          <UInput
            v-model="form.defaultRecognitionIndicesInput"
            :disabled="!isEditing"
            placeholder="1"
            class="max-w-md"
          />
        </UFormField>

        <div class="flex gap-2">
          <template v-if="isEditing">
            <UButton
              label="Save changes"
              icon="i-lucide-save"
              color="primary"
              variant="solid"
              :loading="isSaving"
              @click="saveWorkspace"
            />
            <UButton
              label="Cancel"
              color="neutral"
              variant="subtle"
              @click="cancelEditing"
            />
          </template>
          <UButton
            v-else
            label="Edit"
            color="neutral"
            variant="outline"
            icon="i-lucide-pencil"
            @click="startEditing"
          />
        </div>
      </div>
    </UPageCard>

    <template v-else>
      <UPageCard
        data-tour="workspace-general-panel"
        title="Workspace Details"
        :description="isCurrentUserAdmin ? 'Update your workspace name, description and default utilities.' : 'View workspace details.'"
        variant="subtle"
      >
        <div class="flex flex-col gap-4">
          <UFormField label="Name" :hint="isEditing ? 'Required' : ''">
            <UInput
              v-model="form.name"
              :disabled="!isEditing"
              placeholder="Workspace name"
            />
          </UFormField>
          <UFormField label="Description">
            <UTextarea
              v-model="form.description"
              :disabled="!isEditing"
              placeholder="Optional description"
            />
          </UFormField>

          <USeparator />

          <UFormField data-tour="workspace-general-presets" v-if="isCurrentUserAdmin" label="Default Codec" hint="Default codec for new projects">
            <USelect
              v-model="form.codecId"
              :items="codecs"
              :disabled="!isEditing || !!codecsError || codecs.length === 0"
              placeholder="Select a codec"
            />
          </UFormField>
          <UFormField v-if="isCurrentUserAdmin" label="Default Label Set" hint="Default label set for new projects">
            <USelect
              v-model="form.labelSetId"
              :items="labelSets"
              :disabled="!isEditing || !!labelSetsError || labelSets.length === 0"
              placeholder="Select a label set"
            />
          </UFormField>
          <UFormField v-if="isCurrentUserAdmin" label="Default Tag Set" hint="Default tag set for new projects">
            <USelect
              v-model="form.tagSetId"
              :items="tagSets"
              :disabled="!isEditing || !!tagSetsError || tagSets.length === 0"
              placeholder="Select a tag set"
            />
          </UFormField>
          <UFormField v-if="isCurrentUserAdmin" label="Default GT Index" hint="Single Ground Truth index for new projects">
            <UInput
              v-model="form.defaultGtIndexInput"
              :disabled="!isEditing || !workspaceStore.isCurrentUserOwner"
              placeholder="0"
            />
          </UFormField>
          <UFormField v-if="isCurrentUserAdmin" label="Default Recognition Indices" hint="Comma-separated recognition indices for new projects">
            <UInput
              v-model="form.defaultRecognitionIndicesInput"
              :disabled="!isEditing || !workspaceStore.isCurrentUserOwner"
              placeholder="1"
            />
          </UFormField>
          <p v-if="isCurrentUserAdmin && !workspaceStore.isCurrentUserOwner" class="text-xs text-muted">
            Only the workspace owner can change default text-index settings.
          </p>

          <div class="flex gap-2">
            <template v-if="isCurrentUserAdmin">
              <template v-if="isEditing">
                <UButton
                  label="Save changes"
                  icon="i-lucide-save"
                  color="primary"
                  variant="solid"
                  :loading="isSaving"
                  :disabled="!form.name.trim()"
                  @click="saveWorkspace"
                />
                <UButton
                  label="Cancel"
                  color="neutral"
                  variant="outline"
                  @click="cancelEditing"
                />
              </template>
              <UButton
                v-else
                label="Edit"
                color="neutral"
                variant="outline"
                icon="i-lucide-pencil"
                @click="startEditing"
              />
            </template>
          </div>
        </div>
      </UPageCard>

      <UPageCard
        data-tour="workspace-danger-zone"
        title="Danger Zone"
        description="Irreversible actions for this workspace."
        variant="subtle"
        class="bg-linear-to-tl from-error/5 from-5% to-default"
      >
        <template #footer>
          <div class="flex flex-col gap-4">
            <div v-if="!isCurrentUserAdmin" class="flex items-center justify-between">
              <div>
                <h4 class="font-medium">
                  Leave workspace
                </h4>
                <p class="text-sm text-muted">
                  Remove yourself from this workspace.
                </p>
              </div>
              <UButton
                label="Leave"
                color="error"
                variant="outline"
                icon="i-lucide-log-out"
                @click="leaveWorkspace"
              />
            </div>

            <div v-if="isCurrentUserAdmin" class="flex flex-col gap-y-2 items-start justify-between">
              <div>
                <h4 class="font-medium">
                  Delete workspace
                </h4>
                <p class="text-sm text-muted">
                  Permanently delete this workspace and all its data.
                </p>
              </div>
              <UButton
                label="Delete workspace"
                color="error"
                variant="solid"
                icon="i-lucide-trash-2"
                @click="openDeleteSlideover"
              />
            </div>
          </div>
        </template>
      </UPageCard>
    </template>
  </div>
</template>
