<script setup lang="ts">
import {
  FlowFieldAvatar,
  GradientAvatar,
  IdenticonAvatar,
  InitialsAvatar
} from '@maxnth/gestalt'
import type { AvatarBaseProps } from '@maxnth/gestalt'
import type { AvatarSize, AvatarStyle } from '~/types/avatar'
import { AVATAR_SIZE_PIXELS, getGeneratedAvatarSeed } from '~/utils/avatar-rendering'

const props = withDefaults(defineProps<{
  seed: string | number
  alt: string
  src?: string | null
  size?: AvatarSize
  avatarStyle?: AvatarStyle
  ring?: AvatarBaseProps['ring']
}>(), {
  src: undefined,
  size: 'md',
  avatarStyle: undefined,
  ring: undefined
})

const avatarComponents = {
  GRADIENT: GradientAvatar,
  IDENTICON: IdenticonAvatar,
  FLOW_FIELD: FlowFieldAvatar,
  INITIALS: InitialsAvatar
} satisfies Record<AvatarStyle, unknown>

const { defaultStyle } = useAvatarSettings()
const { resolve: resolveAvatarSource, invalidate: invalidateAvatarSource } = useManagedAvatarSources()
const effectiveStyle = computed(() => props.avatarStyle || defaultStyle.value)
const pixels = computed(() => AVATAR_SIZE_PIXELS[props.size])
const managedSrc = computed(() => resolveAvatarSource(props.src))
const generatedComponent = computed(() => avatarComponents[effectiveStyle.value])
const generatedSeed = computed(() => getGeneratedAvatarSeed(effectiveStyle.value, props.seed, props.alt))
const rootStyle = computed(() => {
  let boxShadow: string | undefined
  if (managedSrc.value && props.ring) {
    boxShadow = props.ring === true
      ? '0 0 0 2px rgb(255,255,255)'
      : typeof props.ring === 'number'
        ? `0 0 0 ${Number.isFinite(props.ring) && props.ring > 0 ? props.ring : 2}px rgb(255,255,255)`
        : props.ring
  }

  return {
    width: `${pixels.value}px`,
    height: `${pixels.value}px`,
    boxShadow
  }
})

const handleImageError = () => {
  invalidateAvatarSource(managedSrc.value)
}
</script>

<template>
  <span
    class="inline-flex shrink-0 select-none items-center justify-center rounded-full align-middle"
    :style="rootStyle"
    :role="managedSrc ? undefined : 'img'"
    :aria-label="managedSrc ? undefined : alt"
  >
    <img
      v-if="managedSrc"
      :src="managedSrc"
      :alt="alt"
      class="size-full rounded-[inherit] object-cover"
      @error="handleImageError"
    >
    <component
      :is="generatedComponent"
      v-else
      :seed="generatedSeed"
      :size="pixels"
      radius="9999px"
      :ring="ring"
      aria-hidden="true"
    />
  </span>
</template>
