<script setup lang="ts">
const props = withDefaults(defineProps<{
  title?: string
  message?: string
  confirmLabel?: string
  cancelLabel?: string
  confirmColor?: 'primary' | 'error' | 'warning' | 'success' | 'neutral'
  confirmIcon?: string
  loading?: boolean
  showCancel?: boolean
}>(), {
  title: 'Confirm Action',
  message: 'Are you sure you want to proceed?',
  confirmLabel: 'Confirm',
  cancelLabel: 'Cancel',
  confirmColor: 'primary',
  loading: false,
  showCancel: true
})

const emit = defineEmits<{
  close: [result: boolean]
}>()
</script>

<template>
  <USlideover
    side="right"
    :title="title"
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #body>
      <div class="flex flex-col gap-4 max-w-lg mx-auto">
        <p class="text-muted">
          {{ message }}
        </p>
        <slot />
      </div>
    </template>

    <template #footer>
      <div class="flex justify-center gap-2">
        <UButton
          v-if="showCancel"
          color="neutral"
          variant="ghost"
          :disabled="loading"
          @click="emit('close', false)"
        >
          {{ cancelLabel }}
        </UButton>
        <UButton
          variant="subtle"
          :color="confirmColor"
          :icon="confirmIcon"
          :loading="loading"
          @click="emit('close', true)"
        >
          {{ confirmLabel }}
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
