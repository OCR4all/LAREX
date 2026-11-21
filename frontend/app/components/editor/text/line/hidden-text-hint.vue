<script setup lang="ts">
const props = defineProps<{
  indices: Array<number | undefined>
}>()
const emit = defineEmits<{
  reveal: []
}>()

const MAX_SHOWN = 3

function formatIndex(index: number | undefined): string {
  return typeof index === 'number' ? String(index) : '–'
}

const shortLabel = computed(() => {
  const shown = props.indices.slice(0, MAX_SHOWN).map(formatIndex).join(', ')
  const rest = props.indices.length - MAX_SHOWN
  return rest > 0 ? `${shown} +${rest}` : shown
})

const description = computed(() => {
  const names = props.indices.map(index => typeof index === 'number' ? String(index) : 'unindexed')
  const subject = props.indices.length === 1 ? 'index' : 'indices'
  return `Text hidden in ${subject} ${names.join(', ')}. Click to show it.`
})
</script>

<template>
  <button
    type="button"
    class="inline-flex shrink-0 items-center gap-1 rounded-sm px-1 h-5 text-muted bg-muted/60 ring ring-inset ring-default tabular-nums leading-none cursor-pointer transition-colors hover:text-highlighted hover:bg-accented/60 focus-visible:outline-2 focus-visible:outline-primary"
    style="font-size: var(--text-font-size-2xs)"
    :data-tooltip="description"
    :aria-label="description"
    @click.stop="emit('reveal')"
  >
    <Icon name="i-lucide-eye-off" class="size-3" aria-hidden="true" />
    {{ shortLabel }}
  </button>
</template>
