<script setup lang="ts">
defineProps<{
  defaultLabel: string
  matches: boolean
  createMode?: boolean
  showReset?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{ reset: [] }>()
</script>

<template>
  <div class="mt-2 flex min-h-7 items-center justify-between gap-2 text-xs">
    <div class="flex min-w-0 items-center gap-1.5 text-muted">
      <UIcon
        :name="createMode ? 'i-lucide-building-2' : matches ? 'i-lucide-circle-check' : 'i-lucide-git-compare-arrows'"
        :class="matches && !createMode ? 'text-success' : 'text-dimmed'"
        class="size-3.5 shrink-0"
      />
      <span v-if="createMode" class="truncate">
        Workspace default: <span class="font-medium text-default">{{ defaultLabel }}</span>
      </span>
      <span v-else class="truncate">
        {{ matches ? 'Matches current workspace default' : 'Project value differs' }}
        <span class="text-dimmed">· {{ defaultLabel }}</span>
      </span>
    </div>
    <UButton
      v-if="showReset && !matches"
      type="button"
      size="xs"
      color="primary"
      variant="soft"
      icon="i-lucide-rotate-ccw"
      class="shrink-0"
      :disabled="disabled"
      aria-label="Reset to workspace default"
      @click="emit('reset')"
    >
      Reset
    </UButton>
  </div>
</template>
