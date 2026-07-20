<script setup lang="ts">
const defaultGtIndex = defineModel<number>('defaultGtIndex', { default: 0 })
const defaultRecognitionIndices = defineModel<number[]>('defaultRecognitionIndices', { default: () => [1] })
const showDiff = defineModel<boolean>('showDiff', { default: false })

const props = withDefaults(defineProps<{
  canEditDefaults?: boolean
  isSavingDefaults?: boolean
  saveError?: string | null
  showDiffToggle?: boolean
}>(), {
  canEditDefaults: false,
  isSavingDefaults: false,
  saveError: null,
  showDiffToggle: true
})

const emit = defineEmits<{
  saveDefaults: [payload: { defaultGtIndex: number, defaultRecognitionIndices: number[] }]
}>()

const gtIndexInput = ref(String(defaultGtIndex.value ?? 0))
const recognitionIndicesInput = ref((defaultRecognitionIndices.value ?? [1]).join(', '))
const localError = ref<string | null>(null)

watch(() => defaultGtIndex.value, (val) => {
  gtIndexInput.value = String(val ?? 0)
})

watch(() => defaultRecognitionIndices.value, (val) => {
  recognitionIndicesInput.value = (val ?? [1]).join(', ')
}, { deep: true })

function parsePayload(): { defaultGtIndex: number, defaultRecognitionIndices: number[] } | null {
  localError.value = null

  const gt = Number.parseInt(gtIndexInput.value.trim(), 10)
  if (!Number.isFinite(gt) || gt < 0) {
    localError.value = 'GT index must be a non-negative integer.'
    return null
  }

  const parsed = recognitionIndicesInput.value
    .split(',')
    .map(v => Number.parseInt(v.trim(), 10))
    .filter(v => Number.isFinite(v) && v >= 0)
  const recognition = [...new Set(parsed)].sort((a, b) => a - b).filter(v => v !== gt)
  if (recognition.length === 0) {
    localError.value = 'Provide at least one recognition index different from the GT index.'
    return null
  }

  return {
    defaultGtIndex: gt,
    defaultRecognitionIndices: recognition
  }
}

function handleSave() {
  const payload = parsePayload()
  if (!payload) return

  defaultGtIndex.value = payload.defaultGtIndex
  defaultRecognitionIndices.value = payload.defaultRecognitionIndices
  emit('saveDefaults', payload)
}
</script>

<template>
  <div class="p-4 space-y-4">
    <div>
      <div class="flex justify-between items-center mb-2">
        <span class="text-sm font-medium">Default Ground Truth Index</span>
      </div>
      <UInput
        v-model="gtIndexInput"
        size="sm"
        :disabled="!props.canEditDefaults || props.isSavingDefaults"
        placeholder="0"
      />
    </div>

    <div>
      <div class="flex justify-between items-center mb-2">
        <span class="text-sm font-medium">Default Recognition Indices</span>
      </div>
      <UInput
        v-model="recognitionIndicesInput"
        size="sm"
        :disabled="!props.canEditDefaults || props.isSavingDefaults"
        placeholder="1, 2"
      />
      <p class="text-xs text-muted mt-1">
        Comma-separated list (multiple recognition indices allowed).
      </p>
    </div>

    <p v-if="!props.canEditDefaults" class="text-xs text-muted">
      Only the workspace owner can change project text-index defaults.
    </p>
    <p v-if="localError" class="text-xs text-error">
      {{ localError }}
    </p>
    <p v-else-if="props.saveError" class="text-xs text-error">
      {{ props.saveError }}
    </p>

    <div
      v-if="props.showDiffToggle"
      class="flex items-center justify-between cursor-pointer"
      @click="showDiff = !showDiff"
    >
      <span class="text-sm font-medium">Show Diff</span>
      <UCheckbox :model-value="showDiff" @click.stop @update:model-value="showDiff = ($event === true)" />
    </div>

    <div class="flex justify-end">
      <UButton
        size="sm"
        color="primary"
        :loading="props.isSavingDefaults"
        :disabled="!props.canEditDefaults"
        @click="handleSave"
      >
        Save Defaults
      </UButton>
    </div>
  </div>
</template>
