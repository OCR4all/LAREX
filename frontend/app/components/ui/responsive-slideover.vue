<script setup lang="ts">
import type { SlideoverProps } from '@nuxt/ui'
import { useMediaQuery } from '@vueuse/core'

defineOptions({
  inheritAttrs: false
})

const props = withDefaults(defineProps<{
  side?: SlideoverProps['side']
  ui?: SlideoverProps['ui']
}>(), {
  side: 'right'
})

const isDesktop = useMediaQuery('(min-width: 1280px)')
const responsiveSide = computed<SlideoverProps['side']>(() => isDesktop.value ? props.side : 'bottom')
const mergedUi = computed<SlideoverProps['ui']>(() => ({
  ...props.ui,
  content: [
    props.ui?.content,
    responsiveSide.value === 'bottom' ? 'min-h-[50svh]' : undefined
  ]
}))
const slots = useSlots()
</script>

<template>
  <USlideover
    v-bind="$attrs"
    :side="responsiveSide"
    :ui="mergedUi"
  >
    <template
      v-for="(_, name) in slots"
      #[name]="slotProps"
    >
      <slot
        :name="name"
        v-bind="slotProps ?? {}"
      />
    </template>
  </USlideover>
</template>
