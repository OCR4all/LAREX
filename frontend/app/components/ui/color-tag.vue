<script setup lang="ts">
interface ColorTagProps {
  color?: string | null
  variant?: TagVariant
  removable?: boolean
  size?: 'sm' | 'md' | 'lg'
}

const props = withDefaults(defineProps<ColorTagProps>(), {
  color: null,
  variant: 'subtle',
  removable: false,
  size: 'md'
})

const emit = defineEmits<{
  remove: []
}>()

const safeColor = computed(() => props.color && isValidHex(props.color) ? props.color : DEFAULT_TAG_COLOR)
const normalizedHex = computed(() => safeColor.value.startsWith('#') ? safeColor.value : `#${safeColor.value}`)
const lightTagStyles = computed(() => getTagColors(normalizedHex.value, props.variant, false))
const darkTagStyles = computed(() => getTagColors(normalizedHex.value, props.variant, true))
const tagStyleVars = computed(() => ({
  '--tag-bg-light': lightTagStyles.value.backgroundColor,
  '--tag-bg-dark': darkTagStyles.value.backgroundColor,
  '--tag-color-light': lightTagStyles.value.color,
  '--tag-color-dark': darkTagStyles.value.color,
  '--tag-border-light': lightTagStyles.value.borderColor,
  '--tag-border-dark': darkTagStyles.value.borderColor
}))

function handleRemove(e: MouseEvent) {
  e.stopPropagation()
  emit('remove')
}
</script>

<template>
  <span
    :class="[
      'ui-color-tag',
      'inline-flex items-center gap-1.5 rounded-sm border font-medium whitespace-nowrap align-middle',
      size === 'sm' ? 'px-1.5 py-0.5 text-[11px]' : size === 'lg' ? 'px-2.5 py-1 text-sm' : 'px-2 py-0.5 text-xs'
    ]"
    :style="tagStyleVars"
  >
    <slot />

    <button
      v-if="removable"
      type="button"
      class="inline-flex items-center justify-center rounded-sm opacity-70 hover:opacity-100"
      aria-label="Remove tag"
      @click="handleRemove"
    >
      <UIcon
        name="i-lucide-x"
        :class="[
          size === 'sm' ? 'size-3' : size === 'lg' ? 'size-4' : 'size-3.5'
        ]"
      />
    </button>
  </span>
</template>

<style scoped>
.ui-color-tag {
  background-color: var(--tag-bg-light);
  color: var(--tag-color-light);
  border-color: var(--tag-border-light);
}

:global(.dark) .ui-color-tag {
  background-color: var(--tag-bg-dark);
  color: var(--tag-color-dark);
  border-color: var(--tag-border-dark);
}
</style>
