<script setup lang="ts">
import type { SlideoverProps } from '@nuxt/ui'
import { useMediaQuery } from '@vueuse/core'

defineOptions({
  inheritAttrs: false
})

const props = withDefaults(defineProps<{
  side?: SlideoverProps['side']
  inset?: SlideoverProps['inset']
  ui?: SlideoverProps['ui']
}>(), {
  side: 'right',
  inset: true
})

const isDesktop = useMediaQuery('(min-width: 1280px)')
const responsiveSide = computed<SlideoverProps['side']>(() => isDesktop.value ? props.side : 'bottom')
const mergedUi = computed<SlideoverProps['ui']>(() => {
  const customContent = props.ui?.content
  const hasCustomMaxWidth = String(customContent ?? '').includes('max-w-')
  const responsiveMinHeight = responsiveSide.value === 'bottom' ? 'min-h-[50svh]' : undefined

  return {
    ...props.ui,
    content: (defaults: string) => {
      const content = typeof customContent === 'function'
        ? customContent(defaults)
        : [defaults, customContent]

      return [
        hasCustomMaxWidth ? undefined : 'max-w-none xl:max-w-xl',
        content,
        responsiveMinHeight
      ]
    }
  }
})
const slots = useSlots()
</script>

<template>
  <USlideover
    v-bind="$attrs"
    :side="responsiveSide"
    :inset="props.inset"
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
