<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import type { DatasetCreateOrUpdateRequest, DatasetSummary } from '@/types/dataset'

const emit = defineEmits<{ close: [string | null] }>()

const toast = useToast()
const { selectedWorkspace } = await useWorkspaceBootstrap()

const schema = z.object({
  name: z.preprocess(
    value => typeof value === 'string' ? value : '',
    z.string().trim().min(1, { error: 'Dataset name is required' }).max(255, { error: 'Name is too long' })
  ),
  description: z.string().max(2000, { error: 'Description is too long' }).optional().or(z.literal('')),
  tags: z.array(z.string()).optional(),
  splitAlgorithm: z.enum(['RANDOM_SEEDED', 'GROUP_BY_SOURCE_PROJECT', 'MULTILABEL_STRATIFIED_BY_TAGS']),
  splitSeed: z.number().int().min(0),
  retainOutputsForever: z.boolean(),
  outputRetentionDays: z.coerce.number().int().positive()
})

type Schema = z.output<typeof schema>
type SelectOption = { label: string, value: Schema['splitAlgorithm'] }

const state = reactive<Schema>({
  name: '',
  description: '',
  tags: [],
  splitAlgorithm: 'RANDOM_SEEDED',
  splitSeed: 42,
  retainOutputsForever: true,
  outputRetentionDays: 30
})

const splitAlgorithmOptions: Array<{ label: string, value: Schema['splitAlgorithm'] }> = [
  {
    label: 'Random seeded split',
    value: 'RANDOM_SEEDED'
  },
  {
    label: 'Group by source project',
    value: 'GROUP_BY_SOURCE_PROJECT'
  },
  {
    label: 'Stratify by selected tags',
    value: 'MULTILABEL_STRATIFIED_BY_TAGS'
  }
]

const formId = useId()
const creating = ref(false)
const { template: splitTemplate, splits, percentages } = useDatasetSplitLayout()

const splitAlgorithmSelectOptions = computed<SelectOption[]>(() =>
  splitAlgorithmOptions.map(option => ({ label: option.label, value: option.value }))
)

async function onSubmit(event: FormSubmitEvent<Schema>) {
  if (!selectedWorkspace.value) return

  creating.value = true
  const payload: DatasetCreateOrUpdateRequest = {
    name: event.data.name.trim(),
    description: event.data.description?.trim() || null,
    tags: event.data.tags ?? [],
    splitTemplate: splitTemplate.value,
    splitAlgorithm: event.data.splitAlgorithm,
    splitSeed: Number(event.data.splitSeed) || 42,
    trainPercentage: percentages.value.TRAIN,
    valPercentage: percentages.value.VAL,
    testPercentage: percentages.value.TEST,
    stratifyTagIds: [],
    outputRetentionDays: event.data.retainOutputsForever ? null : event.data.outputRetentionDays
  }

  try {
    const created = await $fetch<DatasetSummary>(`/api/workspaces/${selectedWorkspace.value}/datasets`, {
      method: 'POST',
      body: payload
    })

    toast.add({
      title: 'Dataset created',
      description: 'The dataset is ready for curation and split tuning.',
      color: 'success'
    })

    emit('close', created.id)
  } catch (error: unknown) {
    toast.add({
      title: 'Create failed',
      description: extractApiErrorMessage(error, 'Failed to create dataset'),
      color: 'error'
    })
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    header-accent
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Create Dataset"
        icon="i-lucide-database"
        description="Define the dataset and how its items should be split."
      />
    </template>

    <template #body>
      <UForm
        :id="formId"
        :schema="schema"
        :state="state"
        class="space-y-5"
        @submit="onSubmit"
      >
        <UiSlideoverSection
          title="Basics"
          description="Name and organize the dataset within this workspace."
          icon="i-lucide-file-plus-2"
        >
          <div class="space-y-5">
            <UFormField label="Name" name="name" required>
              <UInput v-model="state.name" placeholder="Dataset name" />
            </UFormField>

            <UFormField label="Description" name="description">
              <UTextarea
                v-model="state.description"
                :rows="3"
                placeholder="What does this dataset contain and what is it for?"
              />
            </UFormField>

            <UFormField label="Evaluation file retention" name="outputRetentionDays" help="Days after completion before automatic deletion. Applies to new runs.">
              <UCheckbox v-model="state.retainOutputsForever" label="Keep files indefinitely" />
              <UInput
                v-if="!state.retainOutputsForever"
                v-model="state.outputRetentionDays"
                type="number"
                :min="1"
                :step="1"
                class="mt-2"
              />
            </UFormField>

            <UFormField label="Tags" name="tags" help="Use tags to group or search datasets later.">
              <UInputTags
                v-model="state.tags"
                icon="i-lucide-tags"
                placeholder="e.g. handwriting, layout, german"
              />
            </UFormField>
          </div>
        </UiSlideoverSection>

        <UiSlideoverSection
          title="Split Strategy"
          description="Choose the splits this dataset uses and how pages are assigned to them."
          icon="i-lucide-git-branch"
        >
          <div class="space-y-5">
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <UFormField label="Splits">
                <DatasetSplitSelect v-model="splits" />
              </UFormField>

              <UFormField label="Assignment algorithm" name="splitAlgorithm">
                <USelect
                  v-model="state.splitAlgorithm"
                  :items="splitAlgorithmSelectOptions"
                  value-key="value"
                />
              </UFormField>
            </div>

            <UFormField label="Random seed" name="splitSeed" help="Use the same seed to reproduce the same assignment.">
              <UInput
                v-model.number="state.splitSeed"
                type="number"
                min="0"
                step="1"
              />
            </UFormField>

            <DatasetSplitDistribution v-model="percentages" :splits="splits" class="bg-default/50" />

            <p v-if="state.splitAlgorithm === 'MULTILABEL_STRATIFIED_BY_TAGS'" class="text-sm text-muted">
              Choose the tags to stratify by on the dataset page once it has pages.
            </p>
          </div>
        </UiSlideoverSection>
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton
          type="submit"
          :form="formId"
          color="primary"
          icon="i-lucide-database"
          :loading="creating"
        >
          Create Dataset
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
