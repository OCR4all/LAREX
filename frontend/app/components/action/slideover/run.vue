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
const scopeSummary = computed(() => scope.value === 'selection' ? `${selectedPageIds.value.length} selected pages` : 'Total project')
const scopeItems = computed(() => [
  { label: 'All pages', value: 'all', icon: 'i-lucide-files' },
  { label: 'Selected pages', value: 'selection', icon: 'i-lucide-check-square', disabled: !hasSelection.value }
])
const processorOptions = computed(() => processors.value.map(item => ({
  label: item.processor.name,
  value: item.processor.id,
  disabled: !item.executable
})))
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
  ['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED'].includes(run.status)
))
const openPanels = ref<string[]>(['parameters'])
const accordionItems = computed(() => [
  {
    label: `Parameters (${parameterEntries.value.length})`,
    value: 'parameters',
    slot: 'parameters',
    icon: 'i-lucide-sliders-horizontal'
  },
  {
    label: `Run History (${runs.value.length})`,
    value: 'run-history',
    slot: 'run-history',
    icon: 'i-lucide-history'
  }
])

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
        pageIds: submittedPageIds.value
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

function statusColor(status: ActionRun['status']) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  if (status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function isActiveRun(run: ActionRun) {
  return ['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED'].includes(run.status)
}

function close() {
  emit('close', changed.value)
}
</script>

<template>
  <USlideover
    side="right"
    :ui="{ content: 'max-w-3xl' }"
    title="Run Action"
    icon="i-lucide-play"
    :close="{ onClick: close }"
  >
    <template #body>
      <div class="space-y-5">
        <div class="space-y-4">
          <UFormField label="Action">
            <USelectMenu
              v-model="selectedProcessorId"
              :items="processorOptions"
              value-key="value"
              searchable
              searchable-placeholder="Filter Actions..."
              :loading="loading"
              placeholder="Select an Action"
              class="w-full"
            />
          </UFormField>

          <UAlert
            v-if="!loading && processors.length === 0"
            color="neutral"
            variant="subtle"
            icon="i-lucide-circle-play"
            title="No Actions are assigned to this project or workspace."
          />

          <UAlert
            v-if="selectedProcessor?.blockedReason"
            color="warning"
            variant="subtle"
            icon="i-lucide-lock"
            :title="selectedProcessor.blockedReason"
          />

          <UAlert
            v-if="selectedProcessor"
            color="neutral"
            variant="subtle"
            icon="i-lucide-lock-keyhole"
            :title="selectedProcessor.processor.lockMode === 'PROJECT' ? 'This Action locks the full project while it runs.' : 'This Action locks the selected pages while it runs.'"
          />

          <UFormField label="Scope">
            <div class="space-y-2">
              <UTabs
                v-model="scope"
                :items="scopeItems"
                variant="pill"
                color="neutral"
                :content="false"
                class="w-full"
              />
              <p class="text-xs text-muted">
                {{ scopeSummary }}
              </p>
            </div>
          </UFormField>
        </div>

        <USeparator />

        <UAccordion
          v-model="openPanels"
          :items="accordionItems"
          type="multiple"
          :ui="{
            item: 'border-b border-default last:border-b-0',
            trigger: 'px-0 py-3 hover:bg-transparent',
            content: 'px-0 pb-4'
          }"
        >
          <template #parameters>
            <div class="space-y-3 p-1">
              <p v-if="parameterEntries.length > 0" class="text-sm text-muted">
                Parameter values are fixed by the Action definition.
              </p>

              <p v-if="parameterEntries.length === 0" class="text-sm text-muted">
                This Action does not declare parameters.
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
                    disabled
                  />
                  <UInput
                    v-else
                    :model-value="String(parameterValues[entry.key] ?? '')"
                    :type="entry.definition.type === 'number' || entry.definition.type === 'integer' ? 'number' : 'text'"
                    :min="entry.definition.min"
                    :max="entry.definition.max"
                    disabled
                  />
                </UFormField>
              </div>
            </div>
          </template>

          <template #run-history>
            <div class="space-y-3 p-1">
              <div class="flex justify-end">
                <UButton
                  icon="i-lucide-refresh-cw"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  @click="loadRuns"
                >
                  Refresh
                </UButton>
              </div>

              <p v-if="runs.length === 0" class="text-sm text-muted">
                No Action runs for this project yet.
              </p>

              <div v-else class="divide-y divide-default">
                <div
                  v-for="run in runs"
                  :key="run.id"
                  class="space-y-2 py-3 first:pt-0 last:pb-0"
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
                        v-if="isActiveRun(run)"
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
          </template>
        </UAccordion>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="close">
          Close
        </UButton>
        <UButton
          icon="i-lucide-play"
          :loading="starting"
          :disabled="!canStart"
          @click="startRun"
        >
          Start Action
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
