<script setup lang="ts">
const props = withDefaults(defineProps<{
  title?: string
  message?: string
  confirmLabel?: string
  discardLabel?: string
  cancelLabel?: string
  confirmColor?: 'primary' | 'error' | 'warning' | 'success' | 'neutral'
  discardColor?: 'primary' | 'error' | 'warning' | 'success' | 'neutral'
  pages?: string[]
}>(), {
  title: 'Unsaved changes',
  message: 'You have unsaved changes. What would you like to do?',
  confirmLabel: 'Save',
  discardLabel: 'Discard',
  cancelLabel: 'Cancel',
  confirmColor: 'primary',
  discardColor: 'warning',
  pages: () => []
})

const emit = defineEmits<{
  close: [result: 'save' | 'discard' | 'cancel']
}>()

const visiblePages = computed(() => props.pages.slice(0, 6))
const hiddenCount = computed(() => Math.max(0, props.pages.length - visiblePages.value.length))
</script>

<template>
  <UiResponsiveSlideover
    side="right"
    :close="{ onClick: () => emit('close', 'cancel') }"
  >
    <template #header>
      <UiSlideoverHeader :title="title" icon="i-lucide-save" />
    </template>

    <template #body>
      <div class="flex flex-col gap-4 max-w-lg mx-auto">
        <p class="text-muted">
          {{ message }}
        </p>
        <div v-if="visiblePages.length > 0" class="flex flex-wrap gap-2">
          <span
            v-for="page in visiblePages"
            :key="page"
            class="px-2 py-0.5 rounded-sm text-xs font-medium border border-muted bg-muted/40 text-default"
          >
            {{ page }}
          </span>
          <span
            v-if="hiddenCount > 0"
            class="px-2 py-0.5 rounded-sm text-xs font-medium border border-muted bg-muted/20 text-muted"
          >
            +{{ hiddenCount }} more
          </span>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-center gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          @click="emit('close', 'cancel')"
        >
          {{ cancelLabel }}
        </UButton>
        <UButton
          :color="discardColor"
          variant="subtle"
          @click="emit('close', 'discard')"
        >
          {{ discardLabel }}
        </UButton>
        <UButton
          :color="confirmColor"
          variant="solid"
          @click="emit('close', 'save')"
        >
          {{ confirmLabel }}
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
