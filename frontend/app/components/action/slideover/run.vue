<script setup lang="ts">
import { parse } from 'yaml'
import type {
  ActionParameterDefinition,
  ActionRun,
  StartActionRunResponse,
  ExecutableActionProcessorResponse
} from '@/types/action'

const props = defineProps<{
  workspaceId: string
  projectId: string
  projectName?: string | null
  pageIds?: string[]
}>()

const emit = defineEmits<{
  close: [changed: boolean]
}>()

const toast = useToast()
const actionRunsStore = useActionRunsStore()

const processors = ref<ExecutableActionProcessorResponse[]>([])
const runs = ref<ActionRun[]>([])
const selectedProcessorId = ref('')
const parameterValues = reactive<Record<string, unknown>>({})
const scope = ref<'all' | 'selection'>((props.pageIds?.length ?? 0) > 0 ? 'selection' : 'all')
const loading = ref(false)
const starting = ref(false)
const cancellingRunId = ref<string | null>(null)
const changed = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

const selectedPageIds = computed(() => props.pageIds ?? [])
const selectedProcessor = computed(() => processors.value.find(item => item.processor.id === selectedProcessorId.value) ?? null)
const hasSelection = computed(() => selectedPageIds.value.length > 0)
const submittedPageIds = computed(() => scope.value === 'selection' ? selectedPageIds.value : [])
const parameterEntries = computed(() => {
  const yaml = selectedProcessor.value?.processor.yaml
  if (!yaml) return [] as Array<{ key: string, definition: ActionParameterDefinition }>
  try {
    const parsed = parse(yaml) as { parameters?: Record<string, ActionParameterDefinition> } | null
    return Object.entries(parsed?.parameters ?? {}).map(([key, definition]) => ({ key, definition }))
  } catch {
    return []
  }
})

const activeRuns = computed(() => runs.value.filter(run =>
  ['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS'].includes(run.status)
))

const canStart = computed(() =>
  Boolean(selectedProcessor.value?.executable)
  && !starting.value
  && (scope.value === 'all' || selectedPageIds.value.length > 0)
)

onMounted(async () => {
  await Promise.all([loadProcessors(), loadRuns()])
  pollTimer = setInterval(() => {
    if (activeRuns.value.length > 0) {
      void loadRuns()
    }
  }, 5000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})

watch(selectedProcessorId, () => {
  resetParameters()
})

async function loadProcessors() {
  loading.value = true
  try {
    processors.value = await $fetch<ExecutableActionProcessorResponse[]>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/processors`
    )
    if (!selectedProcessorId.value && processors.value[0]) {
      selectedProcessorId.value = processors.value[0].processor.id
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load assigned Actions.'
    toast.add({ title: 'Failed to load Actions', description: message, color: 'error' })
  } finally {
    loading.value = false
  }
}

async function loadRuns() {
  try {
    runs.value = await $fetch<ActionRun[]>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs`
    )
    actionRunsStore.upsertRuns(runs.value, props.projectName || props.projectId)
  } catch {
    // Keep the current history visible if a polling request fails.
  }
}

function resetParameters() {
  Object.keys(parameterValues).forEach((key) => {
    delete parameterValues[key]
  })
  parameterEntries.value.forEach(({ key, definition }) => {
    parameterValues[key] = definition.defaultValue ?? definition.default ?? defaultParameterValue(definition)
  })
}

function defaultParameterValue(definition: ActionParameterDefinition) {
  if (definition.type === 'boolean') return false
  if (definition.type === 'number' || definition.type === 'integer') return 0
  return ''
}

async function startRun() {
  if (!selectedProcessor.value || !canStart.value) return
  starting.value = true
  try {
    const result = await $fetch<StartActionRunResponse>(`/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs`, {
      method: 'POST',
      body: {
        processorDefinitionId: selectedProcessor.value.processor.id,
        pageIds: submittedPageIds.value,
        parameters: normalizedParameters()
      }
    })
    actionRunsStore.upsertRun(result.run, props.projectName || props.projectId)
    changed.value = true
    await loadRuns()
    toast.add({ title: 'Action run started', color: 'success', icon: 'i-lucide-play' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not start Action run.'
    toast.add({ title: 'Run failed', description: message, color: 'error' })
  } finally {
    starting.value = false
  }
}

async function cancelRun(run: ActionRun) {
  cancellingRunId.value = run.id
  try {
    const updated = await $fetch<ActionRun>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs/${run.id}/cancel`,
      { method: 'POST' }
    )
    actionRunsStore.upsertRun(updated, props.projectName || props.projectId)
    changed.value = true
    await loadRuns()
    toast.add({ title: 'Action cancellation requested', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not cancel Action run.'
    toast.add({ title: 'Cancel failed', description: message, color: 'error' })
  } finally {
    cancellingRunId.value = null
  }
}

function normalizedParameters() {
  const values: Record<string, unknown> = {}
  parameterEntries.value.forEach(({ key, definition }) => {
    const value = parameterValues[key]
    if (definition.type === 'number') {
      values[key] = Number(value)
    } else if (definition.type === 'integer') {
      values[key] = Number.parseInt(String(value), 10)
    } else {
      values[key] = value
    }
  })
  return values
}

function statusColor(status: ActionRun['status']) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  if (status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function close() {
  emit('close', changed.value)
}
</script>

<template>
  <USlideover
    side="right"
    :ui="{ content: 'max-w-3xl' }"
    title="Run LAREX Action"
    :close="{ onClick: close }"
  >
    <template #body>
      <div class="flex flex-col gap-5">
        <div class="rounded-sm border border-default p-3">
          <p class="text-sm font-medium">
            {{ projectName || projectId }}
          </p>
          <p class="text-xs text-muted">
            {{ scope === 'selection' ? `${selectedPageIds.length} selected pages` : 'Full project scope' }}
          </p>
        </div>

        <div class="flex flex-col gap-3">
          <UFormField label="Processor">
            <USelect
              v-model="selectedProcessorId"
              :items="processors.map(item => ({
                label: item.processor.name,
                value: item.processor.id,
                disabled: !item.executable
              }))"
              :loading="loading"
              placeholder="Select an Action processor"
            />
          </UFormField>

          <UAlert
            v-if="!loading && processors.length === 0"
            color="neutral"
            variant="subtle"
            icon="i-lucide-bolt"
            title="No Actions are assigned to this project or workspace."
          />

          <UAlert
            v-if="selectedProcessor?.blockedReason"
            color="warning"
            variant="subtle"
            icon="i-lucide-lock"
            :title="selectedProcessor.blockedReason"
          />

          <div class="flex rounded-sm border border-default p-1">
            <UButton
              class="flex-1 justify-center"
              :variant="scope === 'all' ? 'subtle' : 'ghost'"
              color="neutral"
              @click="scope = 'all'"
            >
              All pages
            </UButton>
            <UButton
              class="flex-1 justify-center"
              :variant="scope === 'selection' ? 'subtle' : 'ghost'"
              color="neutral"
              :disabled="!hasSelection"
              @click="scope = 'selection'"
            >
              Selected pages
            </UButton>
          </div>

          <UAlert
            v-if="selectedProcessor"
            color="neutral"
            variant="subtle"
            icon="i-lucide-lock-keyhole"
            :title="selectedProcessor.processor.lockMode === 'PROJECT' ? 'This Action locks the full project while it runs.' : 'This Action locks the selected pages while it runs.'"
          />
        </div>

        <div class="rounded-sm border border-default p-3">
          <div class="mb-3 flex items-center justify-between gap-2">
            <p class="text-sm font-medium">
              Parameters
            </p>
            <UBadge size="sm" variant="soft" color="neutral">
              {{ parameterEntries.length }}
            </UBadge>
          </div>

          <p v-if="parameterEntries.length === 0" class="text-sm text-muted">
            This processor does not declare parameters.
          </p>

          <div v-else class="grid gap-3">
            <UFormField
              v-for="entry in parameterEntries"
              :key="entry.key"
              :label="entry.key"
              :hint="entry.definition.description"
            >
              <USwitch
                v-if="entry.definition.type === 'boolean'"
                :model-value="Boolean(parameterValues[entry.key])"
                @update:model-value="parameterValues[entry.key] = $event"
              />
              <UInput
                v-else
                :model-value="String(parameterValues[entry.key] ?? '')"
                :type="entry.definition.type === 'number' || entry.definition.type === 'integer' ? 'number' : 'text'"
                :min="entry.definition.min"
                :max="entry.definition.max"
                @update:model-value="parameterValues[entry.key] = $event"
              />
            </UFormField>
          </div>
        </div>

        <div class="rounded-sm border border-default p-3">
          <div class="mb-3 flex items-center justify-between gap-2">
            <p class="text-sm font-medium">
              Run History
            </p>
            <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" size="sm" @click="loadRuns" />
          </div>

          <p v-if="runs.length === 0" class="text-sm text-muted">
            No Action runs for this project yet.
          </p>

          <div v-else class="divide-y divide-default">
            <div
              v-for="run in runs"
              :key="run.id"
              class="flex flex-col gap-2 py-3"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate text-sm font-medium">
                    {{ run.processorName }}
                  </p>
                  <p class="truncate text-xs text-muted">
                    {{ run.pageIds.length }} pages · {{ run.statusMessage || run.processorKey }}
                  </p>
                </div>
                <div class="flex items-center gap-2">
                  <UBadge size="sm" variant="soft" :color="statusColor(run.status)">
                    {{ run.status }}
                  </UBadge>
                  <UButton
                    v-if="['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED'].includes(run.status)"
                    color="warning"
                    variant="ghost"
                    icon="i-lucide-ban"
                    size="sm"
                    :loading="cancellingRunId === run.id"
                    @click="cancelRun(run)"
                  />
                </div>
              </div>
              <UProgress :model-value="run.progressPercent" />
              <p v-if="run.errorMessage" class="text-xs text-error">
                {{ run.errorMessage }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="close">
          Close
        </UButton>
        <UButton icon="i-lucide-play" :loading="starting" :disabled="!canStart" @click="startRun">
          Start Action
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
