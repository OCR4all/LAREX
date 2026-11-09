<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { PageWorkflowAction, PageWorkflowState } from '@/types/project-page'

const props = withDefaults(defineProps<{
  state: PageWorkflowState
  actions: PageWorkflowAction[]
  updating?: boolean
  side?: 'top' | 'bottom'
}>(), {
  updating: false,
  side: 'bottom'
})

const emit = defineEmits<{ action: [action: PageWorkflowAction] }>()

const meta = computed(() => PAGE_WORKFLOW_STATE_META[props.state])
const canChange = computed(() => props.actions.length > 0)
const items = computed<DropdownMenuItem[]>(() => props.actions.map(action => ({
  label: PAGE_WORKFLOW_ACTION_META[action].label,
  icon: PAGE_WORKFLOW_ACTION_META[action].icon,
  disabled: props.updating,
  onSelect: () => {
    if (!props.updating) emit('action', action)
  }
})))
</script>

<template>
  <UDropdownMenu v-if="canChange" :items="items" :content="{ side, align: 'start' }">
    <UBadge
      as="button"
      type="button"
      :color="meta.color"
      variant="subtle"
      size="sm"
      :icon="updating ? 'i-lucide-loader-circle' : meta.icon"
      trailing-icon="i-lucide-chevron-down"
      :disabled="updating"
      :aria-label="`Change page state. Current state: ${meta.label}`"
      title="Change page state"
      class="shrink-0 cursor-pointer select-none transition-opacity hover:opacity-80 rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-wait"
      :ui="updating ? { leadingIcon: 'animate-spin' } : undefined"
      @click.stop
    >
      {{ meta.label }}
    </UBadge>
  </UDropdownMenu>
  <UBadge
    v-else
    :color="meta.color"
    variant="subtle"
    size="sm"
    :icon="meta.icon"
    class="shrink-0"
  >
    {{ meta.label }}
  </UBadge>
</template>
