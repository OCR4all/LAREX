<script setup lang="ts">
import { getProjectNameError } from '@/utils/directory-project-upload'

const props = defineProps<{
  initialName: string
  existingNames: string[]
  reason: string
}>()

const emit = defineEmits<{ close: [result: string | null] }>()
const name = ref(props.initialName)
const error = computed(() => getProjectNameError(name.value, props.existingNames))

function submit() {
  if (!error.value) emit('close', name.value.trim())
}
</script>

<template>
  <UiResponsiveSlideover :close="{ onClick: () => emit('close', null) }">
    <template #header>
      <UiSlideoverHeader
        title="Choose a project name"
        icon="i-lucide-folder-pen"
        :description="reason"
      />
    </template>

    <template #body>
      <UFormField label="Project name" :error="error" required>
        <UInput
          v-model="name"
          autofocus
          maxlength="255"
          class="w-full"
          @keydown.enter.prevent="submit"
        />
      </UFormField>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton icon="i-lucide-folder-up" :disabled="!!error" @click="submit">
          Create and upload
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
