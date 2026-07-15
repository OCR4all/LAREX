<script setup lang="ts">
import type {
  ValidateAgainstSourcesResponse,
  ValidationRulesetSummary,
  ValidationSeverity
} from '@/types/validation-ruleset'

type BatchProject = {
  id: string
  name: string
}

const props = defineProps<{
  workspaceId: string
  projects: BatchProject[]
}>()

const emit = defineEmits<{ close: [completed: boolean] }>()
const toast = useToast()
const selectedRulesetId = ref('')
const isRunning = ref(false)
const result = ref<ValidateAgainstSourcesResponse | null>(null)

const rulesetsKey = computed(() => wsKey(props.workspaceId, 'validation-rulesets', 'list'))
const { data: rulesets, status, error } = await useFetch<ValidationRulesetSummary[]>(
  () => `/api/workspaces/${props.workspaceId}/validation-rulesets`,
  {
    key: rulesetsKey,
    default: () => []
  }
)

const rulesetOptions = computed(() => (rulesets.value ?? []).map(ruleset => ({
  label: ruleset.name,
  value: ruleset.id,
  description: `${ruleset.ruleCount} rule${ruleset.ruleCount === 1 ? '' : 's'}`
})))

const selectedRuleset = computed(() => (rulesets.value ?? [])
  .find(ruleset => ruleset.id === selectedRulesetId.value) ?? null)

const projectRows = computed(() => props.projects.map((project) => {
  const matchedRules = new Set<string>()
  const matchedPages = new Set<string>()
  for (const rule of result.value?.ruleResults ?? []) {
    for (const page of rule.pages) {
      if (page.projectId !== project.id) continue
      matchedRules.add(rule.ruleId)
      matchedPages.add(page.pageId)
    }
  }
  return {
    ...project,
    matchedRuleCount: matchedRules.size,
    matchedPageCount: matchedPages.size
  }
}))

watch(selectedRulesetId, () => {
  result.value = null
})

function severityColor(severity: ValidationSeverity): 'neutral' | 'warning' | 'error' {
  if (severity === 'ERROR') return 'error'
  if (severity === 'WARNING') return 'warning'
  return 'neutral'
}

async function runRulesetValidation() {
  if (!selectedRulesetId.value || isRunning.value) return

  isRunning.value = true
  result.value = null
  try {
    result.value = await $fetch<ValidateAgainstSourcesResponse>(
      `/api/workspaces/${props.workspaceId}/validation-rulesets/${selectedRulesetId.value}/validate-against-sources`,
      {
        method: 'POST',
        body: {
          sources: props.projects.map(project => ({ projectId: project.id, pageIds: [] })),
          variantScope: 'ALL',
          variantIndex: null,
          unindexedOnly: false
        }
      }
    )
  } catch (requestError) {
    toast.add({
      title: 'Ruleset validation failed',
      description: extractApiErrorMessage(requestError, 'Could not validate the selected projects.'),
      color: 'error'
    })
  } finally {
    isRunning.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-5xl' }"
    :close="{ onClick: () => emit('close', Boolean(result)) }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Validate with Ruleset"
        icon="i-lucide-list-checks"
        :description="`Run one validation ruleset on every page and text variant in ${projects.length} selected project${projects.length === 1 ? '' : 's'}.`"
      />
    </template>

    <template #body>
      <div class="space-y-5">
        <UAlert
          v-if="error"
          color="error"
          variant="subtle"
          title="Validation rulesets could not be loaded"
          :description="extractApiErrorMessage(error, 'Reload the page and try again.')"
        />

        <UFormField label="Validation ruleset" required>
          <USelectMenu
            v-model="selectedRulesetId"
            :items="rulesetOptions"
            value-key="value"
            label-key="label"
            searchable
            class="w-full"
            placeholder="Select a ruleset"
            :search-input="{ placeholder: 'Search validation rulesets...' }"
            :loading="status === 'pending'"
            :disabled="status === 'pending' || rulesetOptions.length === 0 || isRunning"
          />
          <template #hint>
            {{ selectedRuleset ? `${selectedRuleset.ruleCount} rules` : `${projects.length} projects selected` }}
          </template>
        </UFormField>

        <UAlert
          v-if="!error && status !== 'pending' && rulesetOptions.length === 0"
          color="warning"
          variant="subtle"
          title="No validation rulesets available"
          description="Create a validation ruleset in the workspace toolkit before running this batch validation."
        />

        <template v-if="result">
          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <UPageCard title="Projects" :description="String(result.analyzedProjectCount)" variant="subtle" />
            <UPageCard title="Pages" :description="String(result.analyzedPageCount)" variant="subtle" />
            <UPageCard title="Matched Rules" :description="String(result.ruleResults.length)" variant="subtle" />
            <UPageCard title="Occurrences" :description="String(result.totalOccurrenceCount)" variant="subtle" />
          </div>

          <UAlert
            :color="result.valid ? 'success' : 'warning'"
            variant="subtle"
            :icon="result.valid ? 'i-lucide-circle-check' : 'i-lucide-circle-alert'"
            :title="result.valid ? 'Validation passed' : 'Validation issues found'"
            :description="result.message"
          />

          <div class="overflow-x-auto rounded-lg border border-default">
            <table class="w-full min-w-2xl text-sm">
              <thead class="bg-elevated text-left text-xs text-muted">
                <tr>
                  <th class="px-4 py-3 font-medium">
                    Project
                  </th>
                  <th class="px-4 py-3 font-medium">
                    Matched rules
                  </th>
                  <th class="px-4 py-3 font-medium">
                    Affected pages
                  </th>
                  <th class="px-4 py-3 font-medium">
                    Result
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-default">
                <tr v-for="project in projectRows" :key="project.id">
                  <td class="px-4 py-3 font-medium text-highlighted">
                    {{ project.name }}
                  </td>
                  <td class="px-4 py-3">
                    {{ project.matchedRuleCount }}
                  </td>
                  <td class="px-4 py-3">
                    {{ project.matchedPageCount }}
                  </td>
                  <td class="px-4 py-3">
                    <UBadge :color="project.matchedRuleCount === 0 ? 'success' : 'warning'" variant="subtle">
                      {{ project.matchedRuleCount === 0 ? 'Passed' : 'Issues' }}
                    </UBadge>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="result.ruleResults.length > 0" class="space-y-3">
            <UCard v-for="rule in result.ruleResults" :key="rule.ruleId">
              <template #header>
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p class="font-medium">
                      {{ rule.ruleName }}
                    </p>
                    <p class="text-sm text-muted">
                      {{ rule.message }}
                    </p>
                  </div>
                  <div class="flex items-center gap-2">
                    <UBadge :color="severityColor(rule.severity)" variant="subtle">
                      {{ rule.severity }}
                    </UBadge>
                    <UBadge color="neutral" variant="subtle">
                      {{ rule.occurrenceCount }} occurrences
                    </UBadge>
                  </div>
                </div>
              </template>
              <div v-if="rule.matchedSamples.length > 0" class="flex flex-wrap gap-2">
                <code
                  v-for="sample in rule.matchedSamples.slice(0, 20)"
                  :key="sample"
                  class="rounded bg-elevated px-2 py-1 text-xs"
                >{{ sample }}</code>
              </div>
            </UCard>
          </div>
        </template>
      </div>
    </template>

    <template #footer>
      <UButton color="neutral" variant="ghost" @click="emit('close', Boolean(result))">
        Close
      </UButton>
      <UButton
        icon="i-lucide-play"
        :loading="isRunning"
        :disabled="!selectedRulesetId || isRunning"
        @click="runRulesetValidation"
      >
        {{ result ? 'Run again' : 'Validate all pages' }}
      </UButton>
    </template>
  </UiResponsiveSlideover>
</template>
