<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { PageWorkflowState } from '@/types/project-page'
import { PAGE_WORKFLOW_STATES, PAGE_WORKFLOW_STATE_META } from '@/utils/page-workflow-state'

const state = defineModel<PageWorkflowState>({ required: true })

const props = withDefaults(defineProps<{
  canChange: boolean
  updating?: boolean
  side?: 'top' | 'bottom'
}>(), {
  updating: false,
  side: 'bottom'
})

const meta = computed(() => PAGE_WORKFLOW_STATE_META[state.value])
const items = computed<DropdownMenuItem[]>(() => PAGE_WORKFLOW_STATES.map(option => ({
  label: PAGE_WORKFLOW_STATE_META[option].label,
  icon: PAGE_WORKFLOW_STATE_META[option].icon,
  type: 'checkbox',
  checked: state.value === option,
  disabled: props.updating || state.value === option,
  onSelect: () => {
    if (props.canChange && !props.updating) state.value = option
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
