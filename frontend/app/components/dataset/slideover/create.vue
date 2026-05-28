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
  splitTemplate: z.enum(['TRAIN_VAL', 'TRAIN_VAL_TEST']),
  splitAlgorithm: z.enum(['RANDOM_SEEDED', 'GROUP_BY_SOURCE_PROJECT', 'MULTILABEL_STRATIFIED_BY_TAGS']),
  splitSeed: z.number().int().min(0),
  stratifyTagIds: z.array(z.string()).optional()
})

type Schema = z.output<typeof schema>
type SelectOption = { label: string, value: Schema['splitTemplate'] | Schema['splitAlgorithm'] }

const state = reactive<Schema>({
  name: '',
  description: '',
  tags: [],
  splitTemplate: 'TRAIN_VAL_TEST',
  splitAlgorithm: 'RANDOM_SEEDED',
  splitSeed: 42,
  stratifyTagIds: []
})

const splitTemplateOptions: Array<{ label: string, value: Schema['splitTemplate'] }> = [
  {
    label: 'Train / Validation / Test',
    value: 'TRAIN_VAL_TEST'
  },
  {
    label: 'Train / Validation',
    value: 'TRAIN_VAL'
  }
]

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

const formRef = ref<HTMLFormElement | null>(null)
const creating = ref(false)
const trainPercentage = ref(70)
const valPercentage = ref(15)

const submit = () => formRef.value?.submit()

const testPercentage = computed(() => {
  if (state.splitTemplate === 'TRAIN_VAL') return 0
  return Math.max(0, 100 - trainPercentage.value - valPercentage.value)
})

const splitSliderValue = computed<number[]>({
  get: () => state.splitTemplate === 'TRAIN_VAL'
    ? [trainPercentage.value]
    : [trainPercentage.value, trainPercentage.value + valPercentage.value],
  set: (value) => {
    const values = Array.isArray(value) ? value.map(entry => Number(entry)) : [Number(value)]
    const firstValue = values[0] ?? Number.NaN
    const firstThumb = Math.min(95, Math.max(5, Number.isFinite(firstValue) ? firstValue : 70))

    if (state.splitTemplate === 'TRAIN_VAL') {
      trainPercentage.value = firstThumb
      valPercentage.value = 100 - firstThumb
      return
    }

    const secondValue = values[1] ?? Number.NaN
    const secondThumb = Math.min(95, Math.max(firstThumb + 5, Number.isFinite(secondValue) ? secondValue : firstThumb + 15))
    trainPercentage.value = firstThumb
    valPercentage.value = secondThumb - firstThumb
  }
})

watch(() => state.splitTemplate, (value) => {
  if (value === 'TRAIN_VAL') {
    trainPercentage.value = 80
    valPercentage.value = 20
    return
  }

  if (trainPercentage.value + valPercentage.value >= 100 || testPercentage.value === 0) {
    trainPercentage.value = 70
    valPercentage.value = 15
  }
})

const splitTemplateSelectOptions = computed<SelectOption[]>(() =>
  splitTemplateOptions.map(option => ({ label: option.label, value: option.value }))
)

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
    splitTemplate: event.data.splitTemplate,
    splitAlgorithm: event.data.splitAlgorithm,
    splitSeed: Number(event.data.splitSeed) || 42,
    trainPercentage: trainPercentage.value,
    valPercentage: valPercentage.value,
    testPercentage: testPercentage.value,
    stratifyTagIds: event.data.stratifyTagIds ?? []
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
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #header>
      <UiSlideoverHeader title="Create Dataset" icon="i-lucide-plus" />
    </template>

    <template #body>
      <UForm
        ref="formRef"
        :schema="schema"
        :state="state"
        class="space-y-5"
        @submit="onSubmit"
      >
        <UiFormSectionHeader title="Basics" />

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

        <UFormField label="Tags" name="tags" hint="Use tags to group or search datasets later.">
          <UInputTags
            v-model="state.tags"
            icon="i-lucide-tags"
            placeholder="e.g. handwriting, layout, german"
          />
        </UFormField>

        <UiFormSectionHeader title="Split Strategy" />

        <UFormField label="Split layout" name="splitTemplate">
          <USelect
            v-model="state.splitTemplate"
            :items="splitTemplateSelectOptions"
            value-key="value"
          />
        </UFormField>

        <UFormField label="Assignment algorithm" name="splitAlgorithm">
          <USelect
            v-model="state.splitAlgorithm"
            :items="splitAlgorithmSelectOptions"
            value-key="value"
          />
        </UFormField>

        <UFormField label="Random seed" name="splitSeed" hint="Use the same seed to reproduce the same assignment.">
          <UInput
            v-model.number="state.splitSeed"
            type="number"
            min="0"
            step="1"
          />
        </UFormField>

        <div class="space-y-4 rounded-lg border border-default p-4">
          <div class="flex flex-wrap gap-2">
            <UBadge color="primary" variant="soft">
              Train {{ trainPercentage }}%
            </UBadge>
            <UBadge color="neutral" variant="soft">
              Validation {{ valPercentage }}%
            </UBadge>
            <UBadge :color="state.splitTemplate === 'TRAIN_VAL' ? 'neutral' : 'warning'" variant="soft">
              Test {{ testPercentage }}%
            </UBadge>
          </div>
          <USlider
            v-model="splitSliderValue"
            :min="5"
            :max="95"
            :step="1"
            :min-steps-between-thumbs="5"
            tooltip
          />
        </div>

        <UFormField
          label="Stratify tags"
          name="stratifyTagIds"
          hint="Only used by the tag-stratified algorithm. Leave empty for a plain seeded split."
        >
          <UInputTags
            v-model="state.stratifyTagIds"
            icon="i-lucide-tag"
            placeholder="e.g. print, marginalia, rubric"
          />
        </UFormField>
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton
          color="primary"
          icon="i-lucide-database"
          :loading="creating"
          @click="submit"
        >
          Create Dataset
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
