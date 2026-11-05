<script setup lang="ts">
import type {
  ApplySourcesResponse,
  NormalizationProfileSummary,
  NormalizeSourcesRequest
} from '@/types/normalization-profile'

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
const selectedProfileId = ref('')
const isApplying = ref(false)
const {
  rows: previewRows,
  summary: previewSummary,
  isLoading: isPreviewing,
  isComplete: isPreviewComplete,
  load: loadPreview,
  reset: resetPreview
} = useNormalizationPreview()
const applyResult = ref<ApplySourcesResponse | null>(null)

const profilesKey = computed(() => wsKey(props.workspaceId, 'normalization-profiles', 'list'))
const { data: profiles, status, error } = await useFetch<NormalizationProfileSummary[]>(
  () => `/api/workspaces/${props.workspaceId}/normalization-profiles`,
  {
    key: profilesKey,
    default: () => []
  }
)

const profileOptions = computed(() => (profiles.value ?? []).map(profile => ({
  label: profile.name,
  value: profile.id,
  description: profile.description || undefined
})))

const selectedProfile = computed(() => (profiles.value ?? [])
  .find(profile => profile.id === selectedProfileId.value) ?? null)
const hasCompleted = computed(() => Boolean(applyResult.value))
const isBusy = computed(() => isPreviewing.value || isApplying.value)
const canApply = computed(() => isPreviewComplete.value && (previewSummary.value?.changedRowCount ?? 0) > 0)

watch(selectedProfileId, () => {
  resetPreview()
  applyResult.value = null
})

function requestBody(): NormalizeSourcesRequest {
  return {
    sources: props.projects.map(project => ({ projectId: project.id, pageIds: [] })),
    variantScope: 'ALL',
    variantIndex: null,
    unindexedOnly: false
  }
}

async function previewNormalization() {
  if (!selectedProfileId.value || isBusy.value) return

  applyResult.value = null
  try {
    await loadPreview(
      `/api/workspaces/${props.workspaceId}/normalization-profiles/${selectedProfileId.value}/normalize-sources`,
      requestBody()
    )
  } catch (requestError) {
    toast.add({
      title: 'Normalization preview failed',
      description: extractApiErrorMessage(requestError, 'Could not preview normalization for the selected projects.'),
      color: 'error'
    })
  }
}

async function applyNormalization() {
  if (!selectedProfileId.value || !canApply.value || isBusy.value) return

  isApplying.value = true
  try {
    applyResult.value = await $fetch<ApplySourcesResponse>(
      `/api/workspaces/${props.workspaceId}/normalization-profiles/${selectedProfileId.value}/apply-sources`,
      {
        method: 'POST',
        body: requestBody()
      }
    )
    resetPreview()
    toast.add({
      title: applyResult.value.changedRowCount > 0 ? 'Normalization applied' : 'No changes applied',
      description: applyResult.value.message,
      color: applyResult.value.changedRowCount > 0 ? 'success' : 'info'
    })
  } catch (requestError) {
    toast.add({
      title: 'Normalization failed',
      description: extractApiErrorMessage(requestError, 'Could not apply normalization to the selected projects.'),
      color: 'error'
    })
  } finally {
    isApplying.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-6xl' }"
    :close="{ onClick: () => emit('close', hasCompleted) }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Normalize Text"
        icon="i-lucide-wand-sparkles"
        :description="`Preview and apply one normalization profile to every page and text variant in ${projects.length} selected project${projects.length === 1 ? '' : 's'}.`"
      />
    </template>

    <template #body>
      <div class="space-y-5">
        <UAlert
          v-if="error"
          color="error"
          variant="subtle"
          title="Normalization profiles could not be loaded"
          :description="extractApiErrorMessage(error, 'Reload the page and try again.')"
        />

        <UFormField label="Normalization profile" required>
          <USelectMenu
            v-model="selectedProfileId"
            :items="profileOptions"
            value-key="value"
            label-key="label"
            searchable
            class="w-full"
            placeholder="Select a normalization profile"
            :search-input="{ placeholder: 'Search normalization profiles...' }"
            :loading="status === 'pending'"
            :disabled="status === 'pending' || profileOptions.length === 0 || isBusy"
          />
          <template #hint>
            {{ selectedProfile?.description || `${projects.length} projects selected` }}
          </template>
        </UFormField>

        <UAlert
          v-if="!error && status !== 'pending' && profileOptions.length === 0"
          color="warning"
          variant="subtle"
          title="No normalization profiles available"
          description="Create a normalization profile in the workspace toolkit before running this batch action."
        />

        <UAlert
          v-if="!previewSummary && !isPreviewing && !applyResult"
          color="info"
          variant="subtle"
          icon="i-lucide-info"
          title="Preview before applying"
          description="The preview is read-only. Applying the result writes normalized text back to every matching PAGE XML row in the selected projects."
        />

        <NormalizationPreviewResults
          v-if="previewSummary"
          :rows="previewRows"
          :summary="previewSummary"
          :is-loading="isPreviewing"
          :is-complete="isPreviewComplete"
          :project-count="projects.length"
        />
        <div v-else-if="isPreviewing" class="space-y-3">
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-32 w-full rounded-lg" />
        </div>

        <template v-if="applyResult">
          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            <UPageCard title="Projects" :description="String(applyResult.analyzedProjectCount)" variant="subtle" />
            <UPageCard title="Pages" :description="String(applyResult.analyzedPageCount)" variant="subtle" />
            <UPageCard title="Targeted Rows" :description="String(applyResult.targetedRowCount)" variant="subtle" />
            <UPageCard title="Changed Rows" :description="String(applyResult.changedRowCount)" variant="subtle" />
            <UPageCard title="Changed Pages" :description="String(applyResult.changedPageCount)" variant="subtle" />
          </div>
          <UAlert
            color="success"
            variant="subtle"
            icon="i-lucide-circle-check"
            title="Normalization completed"
            :description="applyResult.message"
          />
        </template>
      </div>
    </template>

    <template #footer>
      <UButton color="neutral" variant="ghost" @click="emit('close', hasCompleted)">
        Close
      </UButton>
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-scan-search"
        :loading="isPreviewing"
        :disabled="!selectedProfileId || isBusy"
        @click="previewNormalization"
      >
        {{ previewSummary || applyResult ? 'Preview again' : 'Preview changes' }}
      </UButton>
      <UButton
        v-if="canApply"
        icon="i-lucide-wand-sparkles"
        :loading="isApplying"
        :disabled="isBusy"
        @click="applyNormalization"
      >
        Apply {{ previewSummary?.changedRowCount }} change{{ previewSummary?.changedRowCount === 1 ? '' : 's' }}
      </UButton>
    </template>
  </UiResponsiveSlideover>
</template>
