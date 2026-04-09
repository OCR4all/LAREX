<script setup lang="ts">
import type { DatasetAddItemRequest, DatasetCreateOrUpdateRequest, DatasetItemMode, DatasetSummary } from '@/types/dataset'
import { wsKey } from '@/utils/fetch-keys'
import { extractApiErrorMessage } from '@/utils/api-error'

const props = defineProps<{
  projectId: string
  projectName?: string
  projectTags?: string[]
  pages: Array<{ id: string, name: string }>
}>()

const emit = defineEmits<{
  close: [result: { datasetId: string, addedCount: number, skippedCount: number } | null]
}>()

type DatasetPageImageOption = {
  id: string
  fileName: string
  variant: string
  baseName: string
}

type DatasetPageXmlOption = {
  id: string
  fileName: string
  schema: string
  schemaVersion?: string
  variant?: string
}

type DatasetSelectionEntry = {
  pageId: string
  pageName: string
  xmlOptions: DatasetPageXmlOption[]
  imageOptions: DatasetPageImageOption[]
  selectedXmlId: string
  selectedImageIds: string[]
  useGlobalImageSelection: boolean
  error: string | null
}

type DatasetTargetOption = {
  label: string
  value: string
}

type VariantOption = {
  label: string
  value: string
}

const NEW_DATASET_TARGET = '__new__'

const toast = useToast()
const { selectedWorkspace } = await useWorkspaceBootstrap()

const loading = ref(true)
const submitting = ref(false)
const addToDatasetMode = ref<DatasetItemMode>('LINK')
const addToDatasetTargetId = ref<string>(NEW_DATASET_TARGET)
const availableDatasets = ref<Array<Pick<DatasetSummary, 'id' | 'name' | 'tags'>>>([])
const datasetSelectionEntries = ref<DatasetSelectionEntry[]>([])
const newDatasetName = ref(props.projectName ? `${props.projectName} Dataset` : 'New Dataset')
const newDatasetDescription = ref(props.projectName ? `Selected pages from project ${props.projectName}` : '')
const newDatasetTags = ref<string[]>(props.projectTags ?? [])
const globalImageVariants = ref<string[]>([])

const datasetTargetOptions = computed<DatasetTargetOption[]>(() => [
  ...availableDatasets.value.map(dataset => ({
    label: dataset.name,
    value: dataset.id
  })),
  {
    label: 'Create new dataset',
    value: NEW_DATASET_TARGET
  }
])

const datasetModeOptions: Array<{ label: string, value: DatasetItemMode }> = [
  { label: 'Link to latest annotation state', value: 'LINK' },
  { label: 'Copy and freeze current page state', value: 'COPY' }
]

const globalImageVariantOptions = computed<VariantOption[]>(() => {
  const variants = new Set<string>()
  for (const entry of datasetSelectionEntries.value) {
    for (const imageOption of entry.imageOptions) {
      variants.add(imageVariantKey(imageOption))
    }
  }

  return Array.from(variants)
    .sort((left, right) => left.localeCompare(right))
    .map(variant => ({ label: variant, value: variant }))
})

const blockedEntries = computed(() =>
  datasetSelectionEntries.value.filter(entry => !!entry.error || !entry.selectedXmlId)
)

const skippedEntries = computed(() =>
  datasetSelectionEntries.value.filter(entry => !entry.error && !!entry.selectedXmlId && entry.selectedImageIds.length === 0)
)

const submittableEntries = computed(() =>
  datasetSelectionEntries.value.filter(entry => !entry.error && !!entry.selectedXmlId && entry.selectedImageIds.length > 0)
)

const canSubmit = computed(() =>
  !loading.value
  && !submitting.value
  && submittableEntries.value.length > 0
  && (addToDatasetTargetId.value !== NEW_DATASET_TARGET || !!newDatasetName.value.trim())
)

watch(globalImageVariants, () => {
  for (const entry of datasetSelectionEntries.value) {
    if (entry.useGlobalImageSelection) {
      applyGlobalImageSelection(entry)
    }
  }
}, { deep: true })

function imageVariantKey(option: DatasetPageImageOption): string {
  return option.variant?.trim() || option.baseName?.trim() || option.fileName?.trim() || option.id
}

function preferredDatasetXmlId(options: DatasetPageXmlOption[]): string {
  const pageXml = options.find(option => option.schema === 'PAGE_XML')
  return (pageXml ?? options[0])?.id ?? ''
}

function formatDatasetXmlLabel(option: DatasetPageXmlOption): string {
  const parts = [
    option.variant?.trim(),
    option.schema?.trim(),
    option.fileName?.trim()
  ].filter(Boolean)
  return parts.join(' · ')
}

function formatDatasetImageLabel(option: DatasetPageImageOption): string {
  const parts = [
    option.variant?.trim(),
    option.baseName?.trim(),
    option.fileName?.trim()
  ].filter(Boolean)
  return parts.join(' · ')
}

function entryStatus(entry: DatasetSelectionEntry): { label: string, color: 'error' | 'warning' | 'success' | 'neutral' } {
  if (entry.error || !entry.selectedXmlId) {
    return { label: 'Blocked', color: 'error' }
  }
  if (entry.selectedImageIds.length === 0) {
    return { label: 'Skipped', color: 'warning' }
  }
  if (entry.useGlobalImageSelection) {
    return { label: 'Using global', color: 'neutral' }
  }
  return { label: 'Override', color: 'success' }
}

function entryHint(entry: DatasetSelectionEntry): string | undefined {
  if (entry.error) {
    return entry.error
  }
  if (!entry.selectedXmlId) {
    return 'Choose an XML annotation source.'
  }
  if (entry.imageOptions.length === 0) {
    return 'This page has no image variants and will be skipped.'
  }
  if (entry.selectedImageIds.length === 0) {
    return 'No selected image variant is available on this page, so it will be skipped.'
  }
  return `${entry.selectedImageIds.length} image variant${entry.selectedImageIds.length === 1 ? '' : 's'} selected.`
}

function applyGlobalImageSelection(entry: DatasetSelectionEntry) {
  const selected = entry.imageOptions
    .filter(option => globalImageVariants.value.includes(imageVariantKey(option)))
    .map(option => option.id)
  entry.selectedImageIds = selected
}

function setEntryUseGlobal(entry: DatasetSelectionEntry, enabled: boolean | 'indeterminate') {
  entry.useGlobalImageSelection = enabled === true
  if (entry.useGlobalImageSelection) {
    applyGlobalImageSelection(entry)
  }
}

async function loadDatasetSelectionEntry(page: { id: string, name: string }): Promise<DatasetSelectionEntry> {
  try {
    const [xmlOptions, imageOptions] = await Promise.all([
      $fetch<DatasetPageXmlOption[]>(`/api/projects/${props.projectId}/pages/${page.id}/xml`),
      $fetch<DatasetPageImageOption[]>(`/api/projects/${props.projectId}/pages/${page.id}/images`)
    ])

    if (!xmlOptions.length) {
      return {
        pageId: page.id,
        pageName: page.name,
        xmlOptions,
        imageOptions,
        selectedXmlId: '',
        selectedImageIds: [],
        useGlobalImageSelection: true,
        error: 'This page has no XML annotation variants.'
      }
    }

    return {
      pageId: page.id,
      pageName: page.name,
      xmlOptions,
      imageOptions,
      selectedXmlId: preferredDatasetXmlId(xmlOptions),
      selectedImageIds: [],
      useGlobalImageSelection: true,
      error: null
    }
  } catch (error: unknown) {
    return {
      pageId: page.id,
      pageName: page.name,
      xmlOptions: [],
      imageOptions: [],
      selectedXmlId: '',
      selectedImageIds: [],
      useGlobalImageSelection: true,
      error: extractApiErrorMessage(error, 'Failed to load XML and image variants for this page.')
    }
  }
}

function buildDatasetCreatePayload(): DatasetCreateOrUpdateRequest {
  return {
    name: newDatasetName.value.trim(),
    description: newDatasetDescription.value.trim() || null,
    tags: newDatasetTags.value,
    splitTemplate: 'TRAIN_VAL_TEST',
    splitAlgorithm: 'RANDOM_SEEDED',
    splitSeed: 42,
    trainPercentage: 70,
    valPercentage: 15,
    testPercentage: 15,
    stratifyTagIds: []
  }
}

async function createDatasetForSelectionFlow(): Promise<string> {
  if (!selectedWorkspace.value) {
    throw new Error('No workspace selected.')
  }

  const created = await $fetch<Pick<DatasetSummary, 'id' | 'name' | 'tags'>>(
    `/api/workspaces/${selectedWorkspace.value}/datasets`,
    {
      method: 'POST',
      body: buildDatasetCreatePayload()
    }
  )

  availableDatasets.value = [
    ...availableDatasets.value,
    { id: created.id, name: created.name, tags: created.tags ?? [] }
  ].sort((left, right) => left.name.localeCompare(right.name))

  await refreshNuxtData(wsKey(selectedWorkspace.value, 'datasets', 'list'))
  return created.id
}

async function load() {
  if (!selectedWorkspace.value) {
    loading.value = false
    return
  }

  loading.value = true

  try {
    const [datasets, entries] = await Promise.all([
      $fetch<Array<Pick<DatasetSummary, 'id' | 'name' | 'tags'>>>(`/api/workspaces/${selectedWorkspace.value}/datasets`),
      Promise.all(props.pages.map(page => loadDatasetSelectionEntry(page)))
    ])

    availableDatasets.value = datasets
      .map(dataset => ({ id: dataset.id, name: dataset.name, tags: dataset.tags ?? [] }))
      .sort((left, right) => left.name.localeCompare(right.name))
    addToDatasetTargetId.value = availableDatasets.value[0]?.id ?? NEW_DATASET_TARGET
    datasetSelectionEntries.value = entries
    globalImageVariants.value = globalImageVariantOptions.value.map(option => option.value)

    for (const entry of datasetSelectionEntries.value) {
      applyGlobalImageSelection(entry)
    }
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to load dataset options',
      description: extractApiErrorMessage(error, 'Could not load datasets or page variants for the selected pages.'),
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!selectedWorkspace.value || !canSubmit.value) return

  submitting.value = true

  try {
    let targetDatasetId = addToDatasetTargetId.value
    if (targetDatasetId === NEW_DATASET_TARGET) {
      targetDatasetId = await createDatasetForSelectionFlow()
    }

    const items: DatasetAddItemRequest[] = submittableEntries.value.map(entry => ({
      sourceProjectId: props.projectId,
      sourcePageId: entry.pageId,
      mode: addToDatasetMode.value,
      sourceXmlId: entry.selectedXmlId,
      sourceImageIds: entry.selectedImageIds
    }))

    await $fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${targetDatasetId}/items`, {
      method: 'POST',
      body: { items }
    })

    const skippedCount = datasetSelectionEntries.value.length - items.length
    toast.add({
      title: 'Dataset updated',
      description: skippedCount > 0
        ? `${items.length} page${items.length === 1 ? '' : 's'} added. ${skippedCount} skipped because they were unavailable or had no selected image variant.`
        : `${items.length} page${items.length === 1 ? ' was' : 's were'} added to the dataset.`,
      color: 'success'
    })

    await refreshNuxtData(wsKey(selectedWorkspace.value, 'datasets', 'list'))
    emit('close', {
      datasetId: targetDatasetId,
      addedCount: items.length,
      skippedCount
    })
  } catch (error: unknown) {
    toast.add({
      title: 'Add to dataset failed',
      description: extractApiErrorMessage(error, 'Could not add the selected pages to the dataset.'),
      color: 'error'
    })
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <USlideover
    side="right"
    title="Add Selected Pages To Dataset"
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-6xl' }"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #body>
      <div v-if="loading" class="space-y-4">
        <USkeleton class="h-20 w-full rounded-lg" />
        <USkeleton class="h-24 w-full rounded-lg" />
        <USkeleton class="h-32 w-full rounded-lg" />
      </div>

      <div v-else class="space-y-5">
        <div class="grid gap-4 lg:grid-cols-2">
          <UFormField label="Target dataset">
            <USelect
              v-model="addToDatasetTargetId"
              :items="datasetTargetOptions"
              value-key="value"
            />
          </UFormField>

          <UFormField label="Storage mode">
            <USelect
              v-model="addToDatasetMode"
              :items="datasetModeOptions"
              value-key="value"
            />
          </UFormField>
        </div>

        <div v-if="addToDatasetTargetId === NEW_DATASET_TARGET" class="space-y-4 rounded-lg border border-default p-4">
          <div class="grid gap-4 lg:grid-cols-2">
            <UFormField label="Name" required>
              <UInput v-model="newDatasetName" placeholder="Dataset name" />
            </UFormField>

            <UFormField label="Tags">
              <UInputTags
                v-model="newDatasetTags"
                icon="i-lucide-tags"
                placeholder="Add dataset tags"
              />
            </UFormField>
          </div>

          <UFormField label="Description">
            <UTextarea
              v-model="newDatasetDescription"
              :rows="3"
              placeholder="Optional description"
            />
          </UFormField>
        </div>

        <UFormField
          label="Global image variants"
          :help="globalImageVariants.length > 0
            ? 'Rows inherit these variants by default. Disable inheritance on a row to override it.'
            : 'No global variants selected. Inherited rows will be skipped until you choose at least one.'"
        >
          <USelectMenu
            v-model="globalImageVariants"
            :items="globalImageVariantOptions"
            value-key="value"
            multiple
            searchable
            clear-search-on-close
          />
        </UFormField>

        <UAlert
          v-if="blockedEntries.length > 0"
          color="warning"
          variant="subtle"
          title="Some pages cannot be added"
          :description="`${blockedEntries.length} selected ${blockedEntries.length === 1 ? 'page is' : 'pages are'} missing XML variants or could not be loaded.`"
        />

        <UAlert
          v-if="skippedEntries.length > 0"
          color="neutral"
          variant="subtle"
          title="Some pages will be skipped"
          :description="`${skippedEntries.length} selected ${skippedEntries.length === 1 ? 'page has' : 'pages have'} no currently selected image variant.`"
        />

        <div class="space-y-3 overflow-y-auto pr-1">
          <div
            v-for="entry in datasetSelectionEntries"
            :key="entry.pageId"
            class="space-y-4 rounded-lg border border-default p-4"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="truncate font-medium">
                  {{ entry.pageName }}
                </p>
                <p class="text-xs text-muted">
                  {{ entry.pageId }}
                </p>
              </div>

              <UBadge :color="entryStatus(entry).color" variant="soft">
                {{ entryStatus(entry).label }}
              </UBadge>
            </div>

            <div class="grid gap-4 lg:grid-cols-2">
              <UFormField label="XML annotation source" required>
                <USelect
                  v-model="entry.selectedXmlId"
                  :items="entry.xmlOptions.map(option => ({ label: formatDatasetXmlLabel(option), value: option.id }))"
                  value-key="value"
                  :disabled="!!entry.error"
                />
              </UFormField>

              <div class="space-y-3">
                <UCheckbox
                  :model-value="entry.useGlobalImageSelection"
                  label="Use global image variants"
                  @update:model-value="setEntryUseGlobal(entry, $event)"
                />

                <UFormField
                  label="Image variants"
                  :help="entryHint(entry)"
                >
                  <USelectMenu
                    v-model="entry.selectedImageIds"
                    :items="entry.imageOptions.map(option => ({ label: formatDatasetImageLabel(option), value: option.id }))"
                    value-key="value"
                    multiple
                    searchable
                    clear-search-on-close
                    :disabled="!!entry.error || entry.useGlobalImageSelection"
                  />
                </UFormField>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex w-full items-center justify-between gap-3">
        <p class="text-xs text-muted">
          {{ submittableEntries.length }} ready, {{ skippedEntries.length }} skipped, {{ blockedEntries.length }} blocked
        </p>

        <div class="flex items-center gap-2">
          <UButton color="neutral" variant="ghost" @click="emit('close', null)">
            Cancel
          </UButton>
          <UButton
            color="primary"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="submit"
          >
            Add Pages
          </UButton>
        </div>
      </div>
    </template>
  </USlideover>
</template>
