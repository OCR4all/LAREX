<script setup lang="ts">
const props = defineProps<{
  isNew?: boolean
  onSave?: () => Promise<boolean>
}>()
const { meta, isDirty } = useLabelBuilder()

const emit = defineEmits<{ close: [] }>()
const formId = useId()
const isSaving = ref(false)

const handleSave = async () => {
  if ((!props.isNew && !isDirty.value) || isSaving.value) return
  isSaving.value = true
  try {
    await props.onSave?.()
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    :close="{ onClick: () => emit('close') }"
  >
    <template #header>
      <UiSlideoverHeader title="Label Set Settings" icon="i-lucide-settings" />
    </template>

    <template #body>
      <UForm :id="formId" class="space-y-6" @submit="handleSave">
        <UFormField label="Name" required>
          <UInput v-model="meta.name" placeholder="e.g. Medieval Layout" />
        </UFormField>

        <UFormField label="Description">
          <UTextarea v-model="meta.description" placeholder="Describe this label set..." :rows="3" />
        </UFormField>

        <UFormField label="Tags">
          <UInputTags v-model="meta.tags" placeholder="Add tags..." />
        </UFormField>
      </UForm>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton variant="ghost" color="neutral" @click="emit('close')">
          Close
        </UButton>
        <UButton
          type="submit"
          :form="formId"
          icon="i-lucide-save"
          :loading="isSaving"
          :disabled="isSaving || (!isNew && !isDirty)"
        >
          Save
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
