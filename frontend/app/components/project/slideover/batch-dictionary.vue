<script setup lang="ts">
import type {
  DictionarySummary,
  DictionaryValidateAgainstSourcesResponse
} from '@/types/dictionary'

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
const selectedDictionaryId = ref('')
const isRunning = ref(false)
const result = ref<DictionaryValidateAgainstSourcesResponse | null>(null)

const dictionariesKey = computed(() => wsKey(props.workspaceId, 'dictionaries', 'list'))
const { data: dictionaries, status, error } = await useFetch<DictionarySummary[]>(
  () => `/api/workspaces/${props.workspaceId}/dictionaries`,
  {
    key: dictionariesKey,
    default: () => []
  }
)

const dictionaryOptions = computed(() => (dictionaries.value ?? []).map(dictionary => ({
  label: dictionary.name,
  value: dictionary.id,
  description: `${dictionary.entryCount} entr${dictionary.entryCount === 1 ? 'y' : 'ies'}`
})))

const projectsById = computed(() => new Map(props.projects.map(project => [project.id, project])))
const selectedDictionary = computed(() => (dictionaries.value ?? [])
  .find(dictionary => dictionary.id === selectedDictionaryId.value) ?? null)

watch(selectedDictionaryId, () => {
  result.value = null
})

async function runDictionaryCheck() {
  if (!selectedDictionaryId.value || isRunning.value) return

  isRunning.value = true
  result.value = null
  try {
    result.value = await $fetch<DictionaryValidateAgainstSourcesResponse>(
      `/api/workspaces/${props.workspaceId}/dictionaries/${selectedDictionaryId.value}/validate-against-sources`,
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
      title: 'Dictionary check failed',
      description: extractApiErrorMessage(requestError, 'Could not check the selected projects.'),
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
        title="Check Dictionary"
        icon="i-lucide-spell-check-2"
        :description="`Check all pages and text variants in ${projects.length} selected project${projects.length === 1 ? '' : 's'} against one dictionary.`"
      />
    </template>

    <template #body>
      <div class="space-y-5">
        <UAlert
          v-if="error"
          color="error"
          variant="subtle"
          title="Dictionaries could not be loaded"
          :description="extractApiErrorMessage(error, 'Reload the page and try again.')"
        />

        <UFormField label="Dictionary" required>
          <USelectMenu
            v-model="selectedDictionaryId"
            :items="dictionaryOptions"
            value-key="value"
            label-key="label"
            searchable
            class="w-full"
            placeholder="Select a dictionary"
            :search-input="{ placeholder: 'Search dictionaries...' }"
            :loading="status === 'pending'"
            :disabled="status === 'pending' || dictionaryOptions.length === 0 || isRunning"
          />
          <template #hint>
            {{ selectedDictionary ? `${selectedDictionary.entryCount} entries` : `${projects.length} projects selected` }}
          </template>
        </UFormField>

        <UAlert
          v-if="!error && status !== 'pending' && dictionaryOptions.length === 0"
          color="warning"
          variant="subtle"
          title="No dictionaries available"
          description="Create a dictionary in the workspace toolkit before running this batch check."
        />

        <template v-if="result">
          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <UPageCard title="Projects" :description="String(result.analyzedProjectCount)" variant="subtle" />
            <UPageCard title="Pages" :description="String(result.analyzedPageCount)" variant="subtle" />
            <UPageCard title="Tokens" :description="String(result.analyzedTokenCount)" variant="subtle" />
            <UPageCard title="Unknown Tokens" :description="String(result.unknownTokenCount)" variant="subtle" />
          </div>

          <UAlert
            :color="result.valid ? 'success' : 'warning'"
            variant="subtle"
            :icon="result.valid ? 'i-lucide-circle-check' : 'i-lucide-circle-alert'"
            :title="result.valid ? 'All tokens are known' : 'Unknown tokens found'"
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
                    Pages checked
                  </th>
                  <th class="px-4 py-3 font-medium">
                    Unknown tokens
                  </th>
                  <th class="px-4 py-3 font-medium">
                    Result
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-default">
                <tr v-for="projectResult in result.projectResults" :key="projectResult.projectId">
                  <td class="px-4 py-3 font-medium text-highlighted">
                    {{ projectsById.get(projectResult.projectId)?.name || projectResult.projectName || projectResult.projectId }}
                  </td>
                  <td class="px-4 py-3">
                    {{ projectResult.analyzedPageCount }}
                  </td>
                  <td class="px-4 py-3">
                    {{ projectResult.unknownTokenCount }}
                  </td>
                  <td class="px-4 py-3">
                    <UBadge :color="projectResult.valid ? 'success' : 'warning'" variant="subtle">
                      {{ projectResult.valid ? 'Passed' : 'Issues' }}
                    </UBadge>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <UCard v-if="result.unknownTokenResults.length > 0">
            <template #header>
              <div>
                <p class="font-medium">
                  Unknown tokens
                </p>
                <p class="text-xs text-muted">
                  Showing up to 100 tokens with their occurrence counts.
                </p>
              </div>
            </template>
            <div class="flex flex-wrap gap-2">
              <UBadge
                v-for="token in result.unknownTokenResults.slice(0, 100)"
                :key="token.normalizedToken"
                color="warning"
                variant="subtle"
              >
                {{ token.token }} · {{ token.occurrenceCount }}
              </UBadge>
            </div>
          </UCard>
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
        :disabled="!selectedDictionaryId || isRunning"
        @click="runDictionaryCheck"
      >
        {{ result ? 'Run again' : 'Check all pages' }}
      </UButton>
    </template>
  </UiResponsiveSlideover>
</template>
