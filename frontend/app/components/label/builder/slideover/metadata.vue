<script setup lang="ts">
const props = defineProps<{ onSave?: () => void }>()
const { meta } = useLabelBuilder()

const emit = defineEmits<{ close: [] }>()

const handleSave = () => {
  props.onSave?.()
}
</script>

<template>
  <USlideover
    :close="{ onClick: () => emit('close') }"
  >
    <template #header>
      <UiSlideoverHeader title="Label Set Settings" icon="i-lucide-settings" />
    </template>

    <template #body>
      <div class="space-y-6">
        <UFormField label="Name" required>
          <UInput v-model="meta.name" placeholder="e.g. Medieval Layout" />
        </UFormField>

        <UFormField label="Description">
          <UTextarea v-model="meta.description" placeholder="Describe this label set..." :rows="3" />
        </UFormField>

        <UFormField label="Tags">
          <UInputTags v-model="meta.tags" placeholder="Add tags..." />
        </UFormField>

        <USeparator />

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <div>
              <div class="font-medium text-sm">
                ALTO XML Support
              </div>
              <div class="text-xs text-muted">
                Enable ALTO XML mappings for labels
              </div>
            </div>
            <USwitch v-model="meta.altoEnabled" disabled />
          </div>
          <p v-if="!meta.altoEnabled" class="text-xs text-muted bg-muted/50 rounded-sm p-2">
            PAGE XML is the primary format. Enable this to also configure ALTO XML mappings for export compatibility.
          </p>
        </div>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton variant="ghost" color="neutral" @click="emit('close')">
          Close
        </UButton>
        <UButton icon="i-lucide-save" @click="handleSave">
          Save
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
