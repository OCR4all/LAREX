<template>
  <svg
    :width="size"
    :height="size"
    :class="[className]"
    :viewBox="viewBox"
    xmlns="http://www.w3.org/2000/svg"
    role="img"
    :aria-label="ariaLabel"
  >
    <path
      v-if="showBackground"
      :fill="backgroundColor"
      :stroke="strokeColor"
      stroke-width="0"
      d="m 480,0 h 2400 c 265.92,0 480,214.08 480,480 v 2400 c 0,265.92 -214.08,480 -480,480 H 480 C 214.08,3360 0,3145.92 0,2880 V 480 C 0,214.08 214.08,0 480,0 Z"
    />
    <path
      :fill="textColor"
      :stroke="strokeColor"
      stroke-width="0"
      :d="letterPath"
    />
  </svg>
</template>

<script setup lang="ts">
interface Props {
  size?: string | number
  className?: string
  ariaLabel?: string
  variant?: 'default' | 'monochrome' | 'letter-only' | 'splash-screen'
  color?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 48,
  className: '',
  ariaLabel: 'Logo L',
  variant: 'default'
})

const colorMode = useColorMode()

const showBackground = computed(() => {
  return !['letter-only', 'splash-screen'].includes(props.variant)
})

const viewBox = computed(() => {
  return ['letter-only', 'splash-screen'].includes(props.variant)
    ? '800 550 1400 2200'
    : '0 0 3360 3360'
})

const letterPath = computed(() => {
  return 'm 840.6445,600.41017 q 90.8203,0 360.3516,0 0,448.24223 0,1798.82813 -90.8203,0 -360.3516,0 0,-448.2422 0,-1798.82813 z m 360.3516,1798.82813 q 331.0547,0 1318.3594,0 0,90.8203 0,360.3516 -246.0938,0 -987.3047,0 -84.961,0 -331.0547,0 0,-90.8204 0,-360.3516 z'
})

const backgroundColor = computed(() => {
  if (props.variant === 'monochrome') {
    return 'currentColor'
  }
  return '#3D8DFCFF'
})

const textColor = computed(() => {
  switch (props.variant) {
    case 'monochrome':
      return colorMode.value === 'dark' ? '#000000' : '#ffffff'
    case 'letter-only':
      return colorMode.value === 'dark' ? '#ffffff' : '#000000'
    case 'splash-screen':
      return '#000000'
    default:
      return '#f0eee6'
  }
})

const strokeColor = computed(() => {
  return 'transparent'
})
</script>

<style scoped>
svg {
  display: inline-block;
  vertical-align: middle;
}
</style>
