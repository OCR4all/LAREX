<script setup lang="ts">
const props = withDefaults(defineProps<{
  selectedCount: number
  label?: string
  showClear?: boolean
}>(), {
  label: 'selected',
  showClear: true
})

defineEmits<{
  clear: []
}>()

const isVisible = computed(() => props.selectedCount > 0)
</script>

<template>
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="translate-y-3 opacity-0"
    enter-to-class="translate-y-0 opacity-100"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="translate-y-0 opacity-100"
    leave-to-class="translate-y-3 opacity-0"
  >
    <div
      v-if="isVisible"
      class="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex justify-center px-4 pb-[env(safe-area-inset-bottom)] sm:bottom-6"
    >
      <div
        class="pointer-events-auto inline-flex max-w-full flex-wrap items-center justify-center gap-2 rounded-2xl border border-neutral-800/80 bg-neutral-950/95 px-3 py-2 text-neutral-50 shadow-2xl shadow-neutral-950/20 backdrop-blur-md dark:border-white/10"
        role="toolbar"
        aria-live="polite"
      >
        <span class="whitespace-nowrap px-2 text-sm font-semibold">
          {{ selectedCount }} {{ label }}
        </span>
        <div class="hidden h-5 w-px bg-white/15 sm:block" />

        <div class="flex flex-wrap items-center justify-center gap-2">
          <slot />
        </div>

        <UButton
          v-if="showClear"
          icon="i-lucide-x"
          color="neutral"
          variant="ghost"
          size="sm"
          class="text-neutral-50 hover:bg-white/10"
          aria-label="Clear selection"
          @click="$emit('clear')"
        />
      </div>
    </div>
  </Transition>
</template>
