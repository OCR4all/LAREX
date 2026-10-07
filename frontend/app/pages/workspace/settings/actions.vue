<script setup lang="ts">
import { actionActivationOptions } from '~/utils/action-activation'
import type { ActionAssignmentResponse, ActionDefinitionResponse } from '@/types/action'

const workspaceStore = useWorkspaceStore()
const toast = useToast()
const { allow } = useActionVisibility()
const selectedWorkspace = computed(() => workspaceStore.selectedWorkspaceId)
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)

const { data: workspace } = await useFetch<{ id: string, isPersonal: boolean }>(
  () => `/api/workspaces/${selectedWorkspace.value as string}`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'details')
      : globalKey('pending', 'workspace', 'details')),
    watch: [selectedWorkspace],
    immediate: !!selectedWorkspace.value
  }
)

const isPersonalWorkspace = computed(() =>
  workspace.value?.id === selectedWorkspace.value && workspace.value?.isPersonal === true
)
const canManageWorkspaceActions = computed(() => isPersonalWorkspace.value || allow(workspaceCapabilities.value.canManageProjects))

const loadingActions = ref(false)
const assigningAction = ref(false)
const actionDefinitions = ref<ActionDefinitionResponse[]>([])
const actionAssignments = ref<ActionAssignmentResponse[]>([])
const workspaceActionProjects = ref<Array<{ id: string, name: string }>>([])
const selectedActionDefinitionIds = ref<string[]>([])
const selectedActionProjectId = ref('')

const actionDefinitionOptions = computed(() => actionActivationOptions(
  actionDefinitions.value,
  actionAssignments.value,
  selectedActionProjectId.value || null
))

const actionProjectOptions = computed(() => workspaceActionProjects.value.map(project => ({
  label: project.name,
  value: project.id
})))

async function loadWorkspaceActions() {
  if (!selectedWorkspace.value || !canManageWorkspaceActions.value) return
  loadingActions.value = true
  try {
    const [definitions, assignments] = await Promise.all([
      $fetch<ActionDefinitionResponse[]>(`/api/workspaces/${selectedWorkspace.value}/actions/processors/available`),
      $fetch<ActionAssignmentResponse[]>(`/api/workspaces/${selectedWorkspace.value}/actions/assignments`, {
        query: selectedActionProjectId.value ? { projectId: selectedActionProjectId.value } : undefined
      })
    ])
    actionDefinitions.value = definitions
    actionAssignments.value = assignments
    if (selectedActionDefinitionIds.value.length === 0 && actionDefinitionOptions.value[0]) {
      selectedActionDefinitionIds.value = [actionDefinitionOptions.value[0].value]
    }
    selectedActionDefinitionIds.value = selectedActionDefinitionIds.value.filter(id =>
      actionDefinitionOptions.value.some(option => option.value === id)
    )
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load Actions.'
    toast.add({ title: 'Failed to load Actions', description: message, color: 'error' })
  } finally {
    loadingActions.value = false
  }
}

async function assignWorkspaceAction() {
  if (!selectedWorkspace.value || selectedActionDefinitionIds.value.length === 0) return
  assigningAction.value = true
  try {
    await Promise.all(selectedActionDefinitionIds.value.map(processorDefinitionId =>
      $fetch(`/api/workspaces/${selectedWorkspace.value}/actions/assignments`, {
        method: 'POST',
        body: {
          processorDefinitionId,
          projectId: selectedActionProjectId.value || null,
          enabled: true
        }
      })
    ))
    selectedActionDefinitionIds.value = []
    await loadWorkspaceActions()
    toast.add({ title: 'Action enabled', color: 'success', icon: 'i-lucide-circle-play' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not enable Action.'
    toast.add({ title: 'Activation failed', description: message, color: 'error' })
  } finally {
    assigningAction.value = false
  }
}

async function unassignWorkspaceAction(assignmentId: string) {
  if (!selectedWorkspace.value) return
  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/actions/assignments/${assignmentId}`, {
      method: 'DELETE'
    })
    await loadWorkspaceActions()
    toast.add({ title: 'Action disabled', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not disable Action.'
    toast.add({ title: 'Disable failed', description: message, color: 'error' })
  }
}

async function loadWorkspaceActionProjects() {
  if (!selectedWorkspace.value || !canManageWorkspaceActions.value) return
  try {
    workspaceActionProjects.value = await $fetch<Array<{ id: string, name: string }>>(`/api/workspaces/${selectedWorkspace.value}/projects`)
  } catch {
    workspaceActionProjects.value = []
  }
}

let workspaceActionsMounted = false

onMounted(() => {
  workspaceActionsMounted = true
  void loadWorkspaceActionProjects()
  void loadWorkspaceActions()
})

watch([selectedWorkspace, canManageWorkspaceActions], () => {
  if (workspaceActionsMounted && canManageWorkspaceActions.value) {
    void loadWorkspaceActionProjects()
    void loadWorkspaceActions()
  }
})

watch(selectedActionProjectId, () => {
  selectedActionDefinitionIds.value = []
  if (workspaceActionsMounted) void loadWorkspaceActions()
})
</script>

<template>
  <div>
    <UPageCard
      v-if="canManageWorkspaceActions"
      title="Actions"
      description="Enable Actions for the workspace or selected projects. Global availability also requires manual activation."
      variant="subtle"
    >
      <div class="flex flex-col gap-4">
        <div class="grid gap-3 lg:grid-cols-[220px_minmax(0,1fr)_auto] lg:items-end">
          <UFormField label="Activation Scope">
            <USelectMenu
              v-model="selectedActionProjectId"
              :items="actionProjectOptions"
              value-key="value"
              clear
              searchable
              placeholder="Entire workspace"
            />
          </UFormField>
          <UFormField label="Available Actions">
            <USelectMenu
              v-model="selectedActionDefinitionIds"
              :items="actionDefinitionOptions"
              value-key="value"
              multiple
              searchable
              :disabled="loadingActions || actionDefinitionOptions.length === 0"
              placeholder="Select Actions"
            />
          </UFormField>
          <UButton
            label="Enable"
            icon="i-lucide-plus"
            :loading="assigningAction"
            :disabled="selectedActionDefinitionIds.length === 0"
            @click="assignWorkspaceAction"
          />
        </div>

        <p class="text-sm text-muted">
          Training and evaluation Actions must be enabled for the entire workspace.
          Disabled assignments can be re-enabled using the selector above.
        </p>

        <p v-if="!loadingActions && actionDefinitions.length === 0" class="text-sm text-muted">
          No Actions are available. A global administrator can make Actions available from the Actions admin page.
        </p>

        <div v-if="loadingActions" class="space-y-2">
          <USkeleton class="h-10 w-full" />
          <USkeleton class="h-10 w-full" />
        </div>

        <div v-else-if="actionAssignments.length === 0" class="rounded-sm border border-default p-3 text-sm text-muted">
          No Actions are enabled for this scope.
        </div>

        <div v-else class="divide-y divide-default rounded-sm border border-default">
          <div
            v-for="assignment in actionAssignments"
            :key="assignment.id"
            class="flex items-center justify-between gap-3 p-3"
          >
            <div class="min-w-0">
              <p class="truncate text-sm font-medium">
                {{ assignment.processor.name }}
              </p>
              <p class="truncate text-xs text-muted">
                {{ assignment.processor.processorKey }} · {{ assignment.processor.executeRole }} · {{ assignment.processor.lockMode }}
              </p>
            </div>
            <div class="flex items-center gap-2">
              <UBadge :color="assignment.processor.global ? 'primary' : 'neutral'" variant="soft">
                {{ assignment.processor.global ? 'Global availability' : 'Workspace availability' }}
              </UBadge>
              <UBadge :color="assignment.enabled ? 'success' : 'neutral'" variant="soft">
                {{ assignment.enabled ? 'Enabled' : 'Disabled' }}
              </UBadge>
            </div>
            <UButton
              color="error"
              variant="ghost"
              label="Disable"
              icon="i-lucide-x"
              @click="unassignWorkspaceAction(assignment.id)"
            />
          </div>
        </div>
      </div>
    </UPageCard>
  </div>
</template>
