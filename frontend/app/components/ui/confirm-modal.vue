<script setup lang="ts">
const props = withDefaults(defineProps<{
  title?: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  confirmColor?: 'primary' | 'error' | 'warning' | 'success' | 'neutral'
}>(), {
  title: 'Confirm Action',
  description: 'Are you sure you want to proceed?',
  confirmLabel: 'Confirm',
  cancelLabel: 'Cancel',
  confirmColor: 'primary'
})

const emit = defineEmits<{
  close: [result: boolean]
}>()

const isOpen = ref(true)

function confirm() {
  emit('close', true)
  isOpen.value = false
}

function cancel() {
  emit('close', false)
  isOpen.value = false
}
</script>

<template>
  <UModal
    v-model:open="isOpen"
    :title="title"
    class=""
    @close="cancel"
  >
    <template #body>
      <p class="text-muted">
        {{ description }}
      </p>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          color="neutral"
          variant="outline"
          @click="cancel"
        >
          {{ cancelLabel }}
        </UButton>
        <UButton
          :color="confirmColor"
          @click="confirm"
        >
          {{ confirmLabel }}
        </UButton>
      </div>
    </template>
  </UModal>
</template>
