<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { ActionActivationRequest, ActionAssignmentResponse, ActionDefinitionResponse } from '~/types/action'
import { filterWorkspaceActions, groupWorkspaceActions, unconfiguredActions } from '~/utils/workspace-action-activation'
import type { WorkspaceActionRow } from '~/utils/workspace-action-activation'

const workspaceStore = useWorkspaceStore()
const toast = useToast()
const { allow } = useActionVisibility()
const selectedWorkspace = computed(() => workspaceStore.selectedWorkspaceId)
const { capabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManage = computed(() => workspaceStore.currentWorkspace?.isPersonal === true || allow(capabilities.value.canManageProjects))

const definitions = ref<ActionDefinitionResponse[]>([])
const assignments = ref<ActionAssignmentResponse[]>([])
const projects = ref<Array<{ id: string, name: string }>>([])
const loading = ref(true)
const loadError = ref('')
const search = ref('')
const projectFilter = ref('ALL')
const expanded = ref<Record<string, boolean>>({})
const addOpen = ref(false)
const addSearch = ref('')
const selectedIds = ref<string[]>([])
const adding = ref(false)
const addError = ref('')
const editRow = ref<WorkspaceActionRow | null>(null)
const editOpen = ref(false)
const editScope = ref<ActionActivationRequest['scope']>('WORKSPACE')
const editProjectIds = ref<string[]>([])
const editError = ref('')
const pendingIds = ref<string[]>([])
const removingIds = ref<string[]>([])
let generation = 0
let loadSequence = 0
let mounted = false

const rows = computed(() => groupWorkspaceActions(assignments.value))
const filteredRows = computed(() => filterWorkspaceActions(rows.value, search.value, projectFilter.value === 'ALL' ? '' : projectFilter.value))
const available = computed(() => unconfiguredActions(definitions.value, assignments.value))
const filteredAvailable = computed(() => {
  const query = addSearch.value.trim().toLowerCase()
  return available.value.filter(action => !query || [action.name, action.processorKey, action.description]
    .some(value => value?.toLowerCase().includes(query)))
})
const projectOptions = computed(() => projects.value.map(project => ({ label: project.name, value: project.id })))
const filterOptions = computed(() => [{ label: 'All projects', value: 'ALL' }, ...projectOptions.value])
const columns: TableColumn<WorkspaceActionRow>[] = [
  { id: 'details', header: '' },
  { id: 'action', header: 'Action' },
  { id: 'kind', header: 'Kind' },
  { id: 'availability', header: 'Availability' },
  { id: 'scope', header: 'Activation scope' },
  { id: 'status', header: 'Status' },
  { id: 'controls', header: '' }
]
const addColumns: TableColumn<ActionDefinitionResponse>[] = [
  { id: 'select', header: 'Select' },
  { id: 'action', header: 'Action' },
  { id: 'kind', header: 'Kind' },
  { id: 'availability', header: 'Availability' }
]
const scopeOptions = [{ label: 'Entire workspace', value: 'WORKSPACE' }, { label: 'Selected projects', value: 'PROJECTS' }]

function message(error: unknown) {
  const response = error as { data?: { statusMessage?: string, data?: { message?: string } } }
  return response?.data?.data?.message || response?.data?.statusMessage || 'Please try again.'
}

function scopeLabel(row: WorkspaceActionRow) {
  if (row.scope === 'WORKSPACE') return 'Entire workspace'
  return row.projectIds.map(id => projects.value.find(project => project.id === id)?.name ?? 'Unavailable project').join(', ')
}

async function loadActions() {
  const workspaceId = selectedWorkspace.value
  if (!workspaceId || !canManage.value) return
  const version = generation
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  try {
    const [loadedDefinitions, loadedAssignments, loadedProjects] = await Promise.all([
      $fetch<ActionDefinitionResponse[]>(`/api/workspaces/${workspaceId}/actions/processors/available`),
      $fetch<ActionAssignmentResponse[]>(`/api/workspaces/${workspaceId}/actions/assignments`, { query: { allScopes: true } }),
      $fetch<Array<{ id: string, name: string }>>(`/api/workspaces/${workspaceId}/projects`)
    ])
    if (version !== generation || sequence !== loadSequence) return
    definitions.value = loadedDefinitions
    assignments.value = loadedAssignments
    projects.value = loadedProjects
  } catch (error) {
    if (version === generation && sequence === loadSequence) loadError.value = message(error)
  } finally {
    if (version === generation && sequence === loadSequence) loading.value = false
  }
}

function reset() {
  generation++
  definitions.value = []
  assignments.value = []
  projects.value = []
  search.value = ''
  projectFilter.value = 'ALL'
  expanded.value = {}
  addOpen.value = false
  addSearch.value = ''
  selectedIds.value = []
  adding.value = false
  addError.value = ''
  editOpen.value = false
  editRow.value = null
  editProjectIds.value = []
  editScope.value = 'WORKSPACE'
  editError.value = ''
  pendingIds.value = []
  removingIds.value = []
  loading.value = !!selectedWorkspace.value && canManage.value
  loadError.value = ''
}

onMounted(() => {
  mounted = true
  void loadActions()
})
watch([selectedWorkspace, canManage], () => {
  reset()
  if (mounted) void loadActions()
})
onBeforeUnmount(() => {
  generation++
})

function openAdd() {
  addSearch.value = ''
  selectedIds.value = []
  addError.value = ''
  addOpen.value = true
}

function selectAction(id: string, checked: boolean) {
  selectedIds.value = checked ? [...new Set([...selectedIds.value, id])] : selectedIds.value.filter(value => value !== id)
}

async function addActions() {
  const workspaceId = selectedWorkspace.value
  if (!workspaceId || !canManage.value || adding.value || !selectedIds.value.length) return
  const version = generation
  adding.value = true
  addError.value = ''
  const succeeded: string[] = []
  try {
    // Each Action saves atomically; retain failed selections if a later addition fails.
    for (const id of [...selectedIds.value]) {
      if (version !== generation) return
      const result = await $fetch<ActionAssignmentResponse[]>(`/api/workspaces/${workspaceId}/actions/processors/${id}/activation`, {
        method: 'PUT', body: { scope: 'WORKSPACE', projectIds: [], enabled: true } satisfies ActionActivationRequest
      })
      if (version !== generation) return
      assignments.value = [...assignments.value.filter(assignment => assignment.processor.id !== id), ...result]
      succeeded.push(id)
      selectedIds.value = selectedIds.value.filter(value => value !== id)
    }
    addOpen.value = false
    toast.add({ title: succeeded.length === 1 ? 'Action added' : 'Actions added', color: 'success' })
  } catch (error) {
    if (version === generation) addError.value = `${succeeded.length ? `${succeeded.length} Action(s) added. ` : ''}${message(error)}`
  } finally {
    if (version === generation) adding.value = false
  }
}

function openScope(row: WorkspaceActionRow) {
  editRow.value = row
  editScope.value = row.scope
  editProjectIds.value = [...row.projectIds]
  editError.value = ''
  editOpen.value = true
}

async function updateActivation(row: WorkspaceActionRow, request: ActionActivationRequest, editing = false) {
  const workspaceId = selectedWorkspace.value
  if (!workspaceId || !canManage.value || pendingIds.value.includes(row.id)) return
  const version = generation
  pendingIds.value = [...pendingIds.value, row.id]
  editError.value = ''
  try {
    const result = await $fetch<ActionAssignmentResponse[]>(`/api/workspaces/${workspaceId}/actions/processors/${row.id}/activation`, {
      method: 'PUT', body: request
    })
    if (version !== generation) return
    assignments.value = [...assignments.value.filter(assignment => assignment.processor.id !== row.id), ...result]
    if (editing) editOpen.value = false
    toast.add({ title: editing ? 'Activation scope saved' : request.enabled ? 'Action enabled' : 'Action disabled', color: 'success' })
  } catch (error) {
    if (version !== generation) return
    if (editing) editError.value = message(error)
    else toast.add({ title: 'Could not update Action', description: message(error), color: 'error' })
  } finally {
    if (version === generation) pendingIds.value = pendingIds.value.filter(id => id !== row.id)
  }
}

async function removeAction(row: WorkspaceActionRow) {
  const workspaceId = selectedWorkspace.value
  if (!workspaceId || !canManage.value || pendingIds.value.includes(row.id)) return
  const version = generation
  pendingIds.value = [...pendingIds.value, row.id]
  removingIds.value = [...removingIds.value, row.id]
  try {
    await $fetch(`/api/workspaces/${workspaceId}/actions/processors/${row.id}/activation`, { method: 'DELETE' })
    if (version !== generation) return
    assignments.value = assignments.value.filter(assignment => assignment.processor.id !== row.id)
    delete expanded.value[row.id]
    if (editRow.value?.id === row.id) {
      editOpen.value = false
      editRow.value = null
    }
    toast.add({ title: 'Action removed', color: 'success' })
  } catch (error) {
    if (version === generation) toast.add({ title: 'Could not remove Action', description: message(error), color: 'error' })
  } finally {
    if (version === generation) {
      pendingIds.value = pendingIds.value.filter(id => id !== row.id)
      removingIds.value = removingIds.value.filter(id => id !== row.id)
    }
  }
}

function saveScope() {
  if (!editRow.value || (editScope.value === 'PROJECTS' && !editProjectIds.value.length)) return
  void updateActivation(editRow.value, {
    scope: editScope.value,
    projectIds: editScope.value === 'WORKSPACE' ? [] : editProjectIds.value,
    enabled: editRow.value.enabled
  }, true)
}
</script>

<template>
  <div class="min-w-0">
    <UPageCard
      v-if="canManage"
      :ui="{ container: 'min-w-0 gap-y-6 lg:grid-cols-[minmax(0,1fr)]', body: 'w-full' }"
      class="min-w-0"
      variant="subtle"
    >
      <template #body>
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 class="text-base font-semibold text-highlighted">
              Actions
            </h2>
            <p class="mt-1 text-[15px] text-toned">
              Manage which Actions are enabled and where they can run.
            </p>
          </div>
          <UButton
            label="Add Actions"
            icon="i-lucide-plus"
            :disabled="loading || !!loadError"
            @click="openAdd"
          />
        </div>
      </template>
      <section aria-label="Configured Actions" class="min-w-0 rounded-lg border border-default bg-default">
        <div class="flex flex-wrap items-end gap-3 border-b border-default p-4">
          <UFormField label="Search configured Actions" class="flex-1 min-w-48">
            <UInput
              v-model="search"
              icon="i-lucide-search"
              placeholder="Name, key, or description…"
              class="w-full"
            />
          </UFormField>
          <UFormField label="Filter by project" class="w-full sm:w-56">
            <USelectMenu
              v-model="projectFilter"
              :items="filterOptions"
              value-key="value"
              :disabled="loading || !!loadError"
              class="w-full"
            />
          </UFormField>
        </div>
        <UAlert
          v-if="loadError"
          title="Could not load Actions"
          :description="loadError"
          color="error"
        >
          <template #actions>
            <UButton
              label="Retry"
              color="error"
              variant="outline"
              @click="loadActions"
            />
          </template>
        </UAlert>
        <div
          v-else-if="loading && !rows.length"
          role="status"
          aria-label="Loading Actions"
          class="space-y-3 p-4"
        >
          <USkeleton class="h-10 w-full" />
          <USkeleton class="h-20 w-full" />
          <USkeleton class="h-20 w-full" />
          <span class="sr-only">Loading Actions…</span>
        </div>
        <div v-else-if="!loading && !rows.length" class="px-4 py-8 text-center text-muted">
          No Actions are configured. Add an Action to enable it for the entire workspace.
        </div>
        <div v-else-if="!loading && !filteredRows.length" class="px-4 py-8 text-center text-muted">
          No Actions match your filters.
          <UButton label="Clear filters" variant="link" @click="search = ''; projectFilter = 'ALL'" />
        </div>
        <AppTable
          v-else
          v-model:expanded="expanded"
          table-id="workspace-action-settings"
          :data="filteredRows"
          :columns="columns"
          :loading="loading"
          :get-row-id="(row: WorkspaceActionRow) => row.id"
          :ui="{ base: 'table-auto border-separate border-spacing-0', thead: '[&>tr]:bg-default [&>tr]:after:content-none', th: 'py-3 border-b border-default', td: 'align-top border-b border-default' }"
          class="w-full min-w-0"
        >
          <template #details-cell="{ row }">
            <UButton
              :icon="row.getIsExpanded() ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
              :aria-label="`${row.getIsExpanded() ? 'Hide' : 'Show'} details for ${row.original.processor.name}`"
              color="neutral"
              variant="ghost"
              @click="row.toggleExpanded()"
            />
          </template>
          <template #action-cell="{ row }">
            <div class="min-w-56 max-w-xl whitespace-normal break-words">
              <p class="font-medium">
                {{ row.original.processor.name }}
              </p>
              <p class="text-sm text-muted mt-1">
                {{ row.original.processor.description || 'No description provided.' }}
              </p>
            </div>
          </template>
          <template #kind-cell="{ row }">
            <span class="capitalize">{{ row.original.processor.kind.toLowerCase() }}</span>
          </template>
          <template #availability-cell="{ row }">
            <UBadge color="neutral" variant="soft">
              {{ row.original.processor.global ? 'Global' : 'Workspace' }}
            </UBadge>
          </template>
          <template #scope-cell="{ row }">
            <p class="min-w-36 max-w-64 whitespace-normal break-words">
              {{ scopeLabel(row.original) }}
            </p>
          </template>
          <template #status-cell="{ row }">
            <UBadge :color="row.original.enabled ? 'success' : 'neutral'" variant="soft">
              {{ row.original.enabled ? 'Enabled' : 'Disabled' }}
            </UBadge>
          </template>
          <template #controls-cell="{ row }">
            <div class="flex items-center justify-end gap-1">
              <UTooltip text="Edit scope">
                <UButton
                  icon="i-lucide-pencil"
                  :aria-label="`Edit scope for ${row.original.processor.name}`"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  :disabled="pendingIds.includes(row.original.id)"
                  @click="openScope(row.original)"
                />
              </UTooltip>
              <UTooltip :text="row.original.enabled ? 'Disable Action' : 'Enable Action'">
                <UButton
                  :icon="row.original.enabled ? 'i-lucide-circle-pause' : 'i-lucide-circle-play'"
                  :aria-label="`${row.original.enabled ? 'Disable' : 'Enable'} ${row.original.processor.name}`"
                  :color="row.original.enabled ? 'neutral' : 'primary'"
                  variant="ghost"
                  size="sm"
                  :loading="pendingIds.includes(row.original.id) && !removingIds.includes(row.original.id)"
                  @click="updateActivation(row.original, { scope: row.original.scope, projectIds: row.original.projectIds, enabled: !row.original.enabled })"
                />
              </UTooltip>
              <UTooltip text="Remove Action from workspace">
                <UButton
                  icon="i-lucide-trash-2"
                  :aria-label="`Remove ${row.original.processor.name} from workspace`"
                  color="error"
                  variant="ghost"
                  size="sm"
                  :disabled="pendingIds.includes(row.original.id)"
                  :loading="removingIds.includes(row.original.id)"
                  @click="removeAction(row.original)"
                />
              </UTooltip>
            </div>
          </template>
          <template #expanded="{ row }">
            <dl class="grid gap-6 rounded-lg border border-default bg-default p-4 sm:grid-cols-3 sm:p-5 whitespace-normal">
              <div class="flex min-w-0 items-start gap-3">
                <div class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-elevated">
                  <UIcon name="i-lucide-code" class="size-4 text-muted" />
                </div>
                <div class="min-w-0 space-y-2">
                  <dt class="text-xs font-medium text-muted opacity-70">
                    Action key
                  </dt>
                  <dd>
                    <code class="break-all text-sm font-medium">{{ row.original.processor.processorKey }}</code>
                  </dd>
                </div>
              </div>
              <div class="flex min-w-0 items-start gap-3">
                <div class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-elevated">
                  <UIcon name="i-lucide-shield-user" class="size-4 text-muted" />
                </div>
                <div class="min-w-0 space-y-2">
                  <dt class="text-xs font-medium text-muted opacity-70">
                    Execution role
                  </dt>
                  <dd>
                    <UBadge color="neutral" variant="soft">
                      {{ row.original.processor.executeRole === 'CURATOR' ? 'Curator' : 'Editor' }}
                    </UBadge>
                  </dd>
                </div>
              </div>
              <div class="flex min-w-0 items-start gap-3">
                <div class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-elevated">
                  <UIcon :name="row.original.processor.lockMode === 'NONE' ? 'i-lucide-lock-open' : 'i-lucide-lock'" class="size-4 text-muted" />
                </div>
                <div class="min-w-0 space-y-2">
                  <dt class="text-xs font-medium text-muted opacity-70">
                    Locking
                  </dt>
                  <dd>
                    <UBadge color="neutral" variant="soft">
                      {{ row.original.processor.lockMode === 'NONE' ? 'No locking' : row.original.processor.lockMode === 'PAGES' ? 'Pages' : 'Project' }}
                    </UBadge>
                  </dd>
                </div>
              </div>
            </dl>
          </template>
        </AppTable>
      </section>
    </UPageCard>
    <UAlert
      v-else
      title="Actions management unavailable"
      description="You do not have permission to manage Actions for this workspace."
      color="neutral"
    />

    <UModal
      v-model:open="addOpen"
      title="Add Actions"
      description="Selected Actions will be enabled for the entire workspace. You can edit their scope afterward."
      :dismissible="!adding"
      :close="!adding"
      :ui="{ content: 'sm:max-w-4xl' }"
    >
      <template #body>
        <div class="space-y-4">
          <UInput
            v-model="addSearch"
            icon="i-lucide-search"
            aria-label="Search available Actions"
            placeholder="Search available Actions…"
            class="w-full"
          />
          <UAlert
            v-if="addError"
            title="Could not add all Actions"
            :description="addError"
            color="error"
          />
          <p v-if="!available.length" class="text-muted">
            No additional Actions are available. A global administrator can make more Actions available.
          </p>
          <p v-else-if="!filteredAvailable.length" class="text-muted">
            No available Actions match your search.
          </p>
          <AppTable
            v-else
            table-id="workspace-action-settings-available"
            :data="filteredAvailable"
            :columns="addColumns"
            :ui="{ base: 'table-auto border-separate border-spacing-0', thead: '[&>tr]:bg-default [&>tr]:after:content-none', td: 'align-top border-b border-default' }"
          >
            <template #select-cell="{ row }">
              <UCheckbox
                :model-value="selectedIds.includes(row.original.id)"
                :aria-label="`Select ${row.original.name}`"
                :disabled="adding"
                @update:model-value="selectAction(row.original.id, $event === true)"
              />
            </template>
            <template #action-cell="{ row }">
              <div class="min-w-56 whitespace-normal break-words">
                <p class="font-medium">
                  {{ row.original.name }}
                </p><p class="text-muted text-sm mt-1">
                  {{ row.original.description || 'No description provided.' }}
                </p>
              </div>
            </template>
            <template #kind-cell="{ row }">
              <span class="capitalize">{{ row.original.kind.toLowerCase() }}</span>
            </template>
            <template #availability-cell="{ row }">
              <UBadge color="neutral" variant="soft">
                {{ row.original.global ? 'Global' : 'Workspace' }}
              </UBadge>
            </template>
          </AppTable>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton
            label="Cancel"
            color="neutral"
            variant="outline"
            :disabled="adding"
            @click="addOpen = false"
          />
          <UButton
            :label="`Add${selectedIds.length ? ` (${selectedIds.length})` : ''}`"
            :loading="adding"
            :disabled="!selectedIds.length"
            @click="addActions"
          />
        </div>
      </template>
    </UModal>

    <UModal
      v-model:open="editOpen"
      title="Edit activation scope"
      :description="editRow?.processor.name"
      :dismissible="!editRow || !pendingIds.includes(editRow.id)"
      :close="!editRow || !pendingIds.includes(editRow.id)"
    >
      <template #body>
        <div v-if="editRow" class="space-y-4">
          <UAlert
            v-if="editError"
            title="Could not save scope"
            :description="editError"
            color="error"
          />
          <URadioGroup v-model="editScope" :items="scopeOptions" :disabled="editRow.processor.kind !== 'PROCESSING' || pendingIds.includes(editRow.id)" />
          <p v-if="editRow.processor.kind !== 'PROCESSING'" class="text-sm text-muted">
            Training and evaluation Actions must be enabled for the entire workspace.
          </p>
          <UFormField v-if="editScope === 'PROJECTS'" label="Projects" required>
            <USelectMenu
              v-model="editProjectIds"
              :items="projectOptions"
              value-key="value"
              multiple
              placeholder="Select projects"
              :disabled="pendingIds.includes(editRow.id)"
              class="w-full"
            />
          </UFormField>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton
            label="Cancel"
            color="neutral"
            variant="outline"
            :disabled="!!editRow && pendingIds.includes(editRow.id)"
            @click="editOpen = false"
          />
          <UButton
            label="Save scope"
            :loading="!!editRow && pendingIds.includes(editRow.id)"
            :disabled="!editRow || (editScope === 'PROJECTS' && !editProjectIds.length)"
            @click="saveScope"
          />
        </div>
      </template>
    </UModal>
  </div>
</template>
