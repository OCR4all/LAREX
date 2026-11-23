<script setup lang="ts">
const defaultGtIndex = defineModel<number>('defaultGtIndex', { default: 0 })
const defaultRecognitionIndices = defineModel<number[]>('defaultRecognitionIndices', { default: () => [1] })

const props = withDefaults(defineProps<{
  canEditDefaults?: boolean
  isSavingDefaults?: boolean
  saveError?: string | null
}>(), {
  canEditDefaults: false,
  isSavingDefaults: false,
  saveError: null
})

const emit = defineEmits<{
  saveDefaults: [payload: { defaultGtIndex: number, defaultRecognitionIndices: number[] }]
}>()

const gtIndexInput = ref('')
const gtIndexUndefined = ref(false)
const recognitionIndicesInput = ref('')
const recognitionIndicesUndefined = ref(false)
const localError = ref<string | null>(null)

watch(() => defaultGtIndex.value, (val) => {
  const gtIndex = val ?? 0
  gtIndexUndefined.value = gtIndex === UNINDEXED_TEXT_INDEX
  gtIndexInput.value = gtIndex === UNINDEXED_TEXT_INDEX ? '' : String(gtIndex)
}, { immediate: true })

watch(() => defaultRecognitionIndices.value, (val) => {
  const indices = val ?? [1]
  recognitionIndicesUndefined.value = indices.includes(UNINDEXED_TEXT_INDEX)
  recognitionIndicesInput.value = indices.filter(index => index !== UNINDEXED_TEXT_INDEX).join(', ')
}, { immediate: true })

function parsePayload(): { defaultGtIndex: number, defaultRecognitionIndices: number[] } | null {
  localError.value = null

  if (gtIndexUndefined.value && recognitionIndicesUndefined.value) {
    localError.value = 'Undefined cannot be selected for both GT and recognition.'
    return null
  }

  let gt = UNINDEXED_TEXT_INDEX
  if (!gtIndexUndefined.value) {
    const value = gtIndexInput.value.trim()
    if (!/^\d+$/.test(value)) {
      localError.value = 'GT index must be a non-negative integer.'
      return null
    }
    gt = Number.parseInt(value, 10)
  }

  const values = recognitionIndicesInput.value
    .split(',')
    .map(v => v.trim())
    .filter(Boolean)
  if (values.some(v => !/^\d+$/.test(v))) {
    localError.value = 'Recognition indices must be non-negative integers.'
    return null
  }
  const parsed = values.map(v => Number.parseInt(v, 10))
  if (parsed.includes(gt)) {
    localError.value = 'Recognition indices must not include the GT index.'
    return null
  }
  const recognition = [...new Set(recognitionIndicesUndefined.value ? [UNINDEXED_TEXT_INDEX, ...parsed] : parsed)]
    .sort((a, b) => a - b)
  if (recognition.length === 0) {
    localError.value = 'Add at least one recognition index or enable Undefined.'
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
      <div class="flex items-center gap-3">
        <UInput
          v-model="gtIndexInput"
          size="sm"
          inputmode="numeric"
          :disabled="!props.canEditDefaults || props.isSavingDefaults || gtIndexUndefined"
          placeholder="0"
          class="flex-1 min-w-0"
        />
        <UCheckbox
          v-model="gtIndexUndefined"
          size="sm"
          label="Undefined"
          :disabled="!props.canEditDefaults || props.isSavingDefaults"
        />
      </div>
    </div>

    <div>
      <div class="flex justify-between items-center mb-2">
        <span class="text-sm font-medium">Default Recognition Indices</span>
      </div>
      <div class="flex items-center gap-3">
        <UInput
          v-model="recognitionIndicesInput"
          size="sm"
          :disabled="!props.canEditDefaults || props.isSavingDefaults"
          placeholder="1, 2"
          class="flex-1 min-w-0"
        />
        <UCheckbox
          v-model="recognitionIndicesUndefined"
          size="sm"
          label="Undefined"
          :disabled="!props.canEditDefaults || props.isSavingDefaults"
        />
      </div>
      <p class="text-xs text-muted mt-1">
        Comma-separated list. Undefined uses text without an index.
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
