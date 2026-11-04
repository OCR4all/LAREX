<script setup lang="ts">
import type { ShortcutCommandId } from '@/composables/editor/shortcut-registry'
import { useShortcutBindings } from '@/composables/editor/use-shortcut-bindings'

const props = defineProps<{ command: ShortcutCommandId }>()

const { getTooltipProps } = useShortcutBindings()
const kbds = computed(() => getTooltipProps(props.command).kbds)
</script>

<template>
  <span v-if="kbds.length > 0" class="inline-flex items-center gap-0.5">
    <UKbd
      v-for="kbd in kbds"
      :key="kbd"
      :value="kbd"
      size="sm"
    />
  </span>
</template>
