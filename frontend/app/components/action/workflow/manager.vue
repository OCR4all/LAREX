<script setup lang="ts">
import type { ActionDefinition } from '~/types/action'
import type { ActionWorkflow } from '~/types/action-workflow'

const props = defineProps<{ workspaceId?: string, processors: ActionDefinition[], embedded?: boolean }>()
const emit = defineEmits<{ saved: [workflow: ActionWorkflow] }>()
const busy = ref(false)
const actionError = ref('')
const open = ref(false)
const editing = ref<ActionWorkflow | null>(null)
const editor = ref<{ dirty: boolean, busy: boolean } | null>(null)
const deleteId = ref('')
const requestFetch = useRequestFetch()
const base = computed(() => props.workspaceId ? `/api/workspaces/${props.workspaceId}/actions/workflows` : '/api/admin/actions/workflows')
const workflowsKey = computed(() => props.workspaceId ? wsKey(props.workspaceId, 'action-workflows') : globalKey('admin', 'action-workflows'))
const { data: workflows, pending: loading, error: loadError, refresh } = useAsyncData(workflowsKey, () => requestFetch<ActionWorkflow[]>(base.value), { default: () => [] })
const error = computed(() => actionError.value || (loadError.value ? extractApiErrorDetails(loadError.value, 'Could not load workflows.').message : ''))
function load() {
  actionError.value = ''
  return refresh()
}
watch(() => props.workspaceId, () => {
  open.value = false
})
function edit(workflow: ActionWorkflow | null) {
  editing.value = workflow
  open.value = true
}
async function editById(id: string) {
  try {
    edit(await $fetch<ActionWorkflow>(`${base.value}/${id}`))
  } catch (cause) {
    actionError.value = extractApiErrorDetails(cause, 'Could not load workflow.').message
  }
}
defineExpose({ create: () => edit(null), editById, canEdit: (id: string) => workflows.value.some(workflow => workflow.id === id && !workflow.global) })
function setOpen(value: boolean) {
  if (!value && (editor.value?.busy || (editor.value?.dirty && !window.confirm('Discard unsaved workflow changes?')))) return
  open.value = value
}
function saved(workflow: ActionWorkflow) {
  workflows.value = [...workflows.value.filter(item => item.id !== workflow.id), workflow].sort((a, b) => a.name.localeCompare(b.name))
  open.value = false
  emit('saved', workflow)
}
async function deleteWorkflow(id: string) {
  busy.value = true
  actionError.value = ''
  try {
    await $fetch(`${base.value}/${id}`, { method: 'DELETE' })
    workflows.value = workflows.value.filter(workflow => workflow.id !== id)
    deleteId.value = ''
  } catch (cause) {
    actionError.value = extractApiErrorDetails(cause, 'Could not delete workflow.').message
  } finally { busy.value = false }
}
</script>

<template>
  <div>
    <section v-if="!embedded" class="mt-6 space-y-4 rounded-lg border border-default bg-default p-4" aria-label="Action workflows">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h3 class="font-semibold text-highlighted">
            Workflows
          </h3>
          <p class="mt-1 text-sm text-muted">
            Combine page-processing Actions in a linear sequence. Each step uses the project’s latest imported results.
          </p>
        </div>
        <UButton
          label="Build workflow"
          icon="i-lucide-workflow"
          :disabled="loading || busy"
          @click="edit(null)"
        />
      </div>
      <UAlert
        v-if="error"
        title="Workflows unavailable"
        :description="error"
        color="error"
      >
        <template #actions>
          <UButton label="Retry" variant="outline" @click="load" />
        </template>
      </UAlert>
      <p v-else-if="loading" role="status" class="text-sm text-muted">
        Loading workflows…
      </p>
      <p v-else-if="!workflows.length" class="py-4 text-center text-sm text-muted">
        No workflows yet. Build one from the Actions available in this workspace.
      </p>
      <div v-for="workflow in workflows" :key="workflow.id" class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-default p-4">
        <div>
          <p class="flex flex-wrap items-center gap-2 font-medium">
            {{ workflow.name }}
            <ActionExecuteRoleBadge :role="workflowExecuteRole(workflow.steps, processors)" />
            <UBadge v-if="!workflow.enabled" color="neutral" variant="subtle">
              Disabled
            </UBadge>
          </p>
          <p class="mt-1 text-sm text-muted">
            {{ workflow.steps.length }} steps<span v-if="workflow.description"> · {{ workflow.description }}</span>
          </p>
        </div>
        <div class="flex gap-2">
          <UButton
            label="Edit"
            icon="i-lucide-pencil"
            color="neutral"
            variant="outline"
            :disabled="busy"
            @click="edit(workflow)"
          />
          <UButton
            v-if="deleteId !== workflow.id"
            icon="i-lucide-trash-2"
            :aria-label="`Delete ${workflow.name}`"
            color="error"
            variant="ghost"
            :disabled="busy"
            @click="deleteId = workflow.id"
          />
          <template v-else>
            <UButton
              label="Confirm delete"
              color="error"
              :loading="busy"
              @click="deleteWorkflow(workflow.id)"
            />
            <UButton
              label="Cancel"
              color="neutral"
              variant="ghost"
              :disabled="busy"
              @click="deleteId = ''"
            />
          </template>
        </div>
      </div>
    </section>
    <UAlert
      v-if="embedded && error"
      title="Workflow unavailable"
      :description="error"
      color="error"
    />
    <UModal
      :open="open"
      :title="editing ? 'Edit workflow' : 'Build workflow'"
      description="Build a linear processing sequence and configure each step."
      :dismissible="!editor?.busy"
      :ui="{ content: 'sm:max-w-6xl', body: 'p-0 sm:p-0' }"
      @update:open="setOpen"
    >
      <template #body>
        <ActionWorkflowEditor
          ref="editor"
          :workflow="editing"
          :processors="props.processors"
          :workspace-id="workspaceId"
          @saved="saved"
        />
      </template>
    </UModal>
  </div>
</template>
