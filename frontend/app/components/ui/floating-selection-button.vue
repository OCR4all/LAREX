<script setup lang="ts">
import type { ButtonProps } from '@nuxt/ui'

/**
 * Action button for `UiFloatingSelectionMenu`.
 *
 * Shows its label only on wide viewports (2xl and up) and collapses to an icon
 * below that. The label stays in the DOM as screen-reader text, so the accessible
 * name is preserved. Everything else (click handlers, `loading`, `disabled`,
 * `aria-label`, `trailing-icon`, ...) falls through to the underlying `UButton`.
 */
const props = withDefaults(defineProps<{
  label: string
  icon?: ButtonProps['icon']
  color?: ButtonProps['color']
  ui?: ButtonProps['ui']
}>(), {
  icon: undefined,
  color: 'neutral',
  ui: undefined
})

const colorClass = computed(() => props.color === 'error'
  ? 'hover:bg-error/20'
  : 'text-neutral-950 dark:text-neutral-50 hover:dark:bg-white/10 hover:bg-black/10'
)

const mergedUi = computed(() => ({
  ...props.ui,
  // Square padding once the label is hidden, matching the other icon-only buttons.
  base: ['max-2xl:px-1.5', props.ui?.base],
  label: ['max-2xl:sr-only', props.ui?.label]
}) as ButtonProps['ui'])
</script>

<template>
  <UButton
    :label="label"
    :icon="icon"
    :color="color"
    variant="ghost"
    size="sm"
    :title="label"
    :class="colorClass"
    :ui="mergedUi"
  />
</template>
