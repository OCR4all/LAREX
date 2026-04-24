<script setup lang="ts">
const props = defineProps<{
  modelValue: string
  opacity: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:opacity': [value: number]
  'save': []
}>()

const hexInput = ref(props.modelValue || '#D9D9D9')
const hue = ref(0)
const saturation = ref(0)
const brightness = ref(85)
const localOpacity = ref(props.opacity)

const gradientRef = ref<HTMLElement>()
const hueRef = ref<HTMLElement>()
const opacityRef = ref<HTMLElement>()

function hexToHsb(hex: string) {
  const r = parseInt(hex.slice(1, 3), 16) / 255
  const g = parseInt(hex.slice(3, 5), 16) / 255
  const b = parseInt(hex.slice(5, 7), 16) / 255
  const max = Math.max(r, g, b), min = Math.min(r, g, b)
  const d = max - min
  let h = 0
  if (d !== 0) {
    if (max === r) h = ((g - b) / d + 6) % 6
    else if (max === g) h = (b - r) / d + 2
    else h = (r - g) / d + 4
    h *= 60
  }
  return { h, s: max === 0 ? 0 : d / max * 100, b: max * 100 }
}

function hsbToHex(h: number, s: number, b: number) {
  s /= 100
  b /= 100

  const c = b * s
  const x = c * (1 - Math.abs((h / 60) % 2 - 1))
  const m = b - c

  const toHex = (v: number) => Math.round((v + m) * 255).toString(16).padStart(2, '0')

  const sectors = [
    [c, x, 0], // 0–60
    [x, c, 0], // 60–120
    [0, c, x], // 120–180
    [0, x, c], // 180–240
    [x, 0, c], // 240–300
    [c, 0, x] // 300–360
  ]
  const [r, g, bl] = sectors[Math.floor(h / 60)]

  return `#${toHex(r)}${toHex(g)}${toHex(bl)}`
}

watch(() => props.modelValue, (val) => {
  if (val && /^#[0-9A-Fa-f]{6}$/.test(val)) {
    hexInput.value = val
    const hsb = hexToHsb(val)
    hue.value = hsb.h
    saturation.value = hsb.s
    brightness.value = hsb.b
  }
}, { immediate: true })

watch(() => props.opacity, (val) => {
  localOpacity.value = val
}, { immediate: true })

watch([hue, saturation, brightness], () => {
  hexInput.value = hsbToHex(hue.value, saturation.value, brightness.value)
  emit('update:modelValue', hexInput.value)
})

watch(localOpacity, (val) => {
  emit('update:opacity', val)
})

function onHexInput(e: Event) {
  const val = (e.target as HTMLInputElement).value
  if (/^#[0-9A-Fa-f]{6}$/.test(val)) {
    hexInput.value = val
    const hsb = hexToHsb(val)
    hue.value = hsb.h
    saturation.value = hsb.s
    brightness.value = hsb.b
  }
}

function onGradientMouseDown(e: MouseEvent) {
  updateGradient(e)
  const onMove = (ev: MouseEvent) => updateGradient(ev)
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp) }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function updateGradient(e: MouseEvent) {
  if (!gradientRef.value) return
  const rect = gradientRef.value.getBoundingClientRect()
  saturation.value = Math.max(0, Math.min(100, (e.clientX - rect.left) / rect.width * 100))
  brightness.value = Math.max(0, Math.min(100, 100 - (e.clientY - rect.top) / rect.height * 100))
}

function onHueMouseDown(e: MouseEvent) {
  updateHue(e)
  const onMove = (ev: MouseEvent) => updateHue(ev)
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function updateHue(e: MouseEvent) {
  if (!hueRef.value) return
  const rect = hueRef.value.getBoundingClientRect()
  hue.value = Math.max(0, Math.min(360, (e.clientX - rect.left) / rect.width * 360))
}

function onOpacityMouseDown(e: MouseEvent) {
  updateOpacity(e)
  const onMove = (ev: MouseEvent) => updateOpacity(ev)
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function updateOpacity(e: MouseEvent) {
  if (!opacityRef.value) return
  const rect = opacityRef.value.getBoundingClientRect()
  localOpacity.value = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
}

const hueColor = computed(() => hsbToHex(hue.value, 100, 100))
</script>

<template>
  <div class="w-56 p-3 flex flex-col gap-3">
    <div
      ref="gradientRef"
      class="relative h-36 rounded-sm cursor-crosshair"
      :style="{ background: `linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, ${hueColor})` }"
      @mousedown="onGradientMouseDown"
    >
      <div
        class="absolute w-3 h-3 border-2 border-white rounded-sm -translate-x-1/2 -translate-y-1/2 shadow pointer-events-none"
        :style="{ left: `${saturation}%`, top: `${100 - brightness}%` }"
      />
    </div>

    <div
      ref="hueRef"
      class="relative h-3 rounded-sm cursor-pointer"
      style="background: linear-gradient(to right, #f00, #ff0, #0f0, #0ff, #00f, #f0f, #f00)"
      @mousedown="onHueMouseDown"
    >
      <div
        class="absolute w-3 h-3 border-2 border-white rounded-sm -translate-x-1/2 top-0 shadow pointer-events-none"
        :style="{ left: `${hue / 360 * 100}%` }"
      />
    </div>

    <div
      ref="opacityRef"
      class="relative h-3 rounded-sm cursor-pointer checkerboard-bg"
      @mousedown="onOpacityMouseDown"
    >
      <div
        class="absolute inset-0 rounded"
        :style="{ background: `linear-gradient(to right, transparent, ${hexInput})` }"
      />
      <div
        class="absolute w-3 h-3 border-2 border-white rounded-sm -translate-x-1/2 top-0 shadow pointer-events-none"
        :style="{ left: `${localOpacity * 100}%` }"
      />
    </div>

    <div class="flex items-center gap-2">
      <div class="w-8 h-8 rounded-sm border border-default checkerboard-bg">
        <div class="w-full h-full rounded" :style="{ backgroundColor: hexInput, opacity: localOpacity }" />
      </div>
      <input
        :value="hexInput.toUpperCase().slice(1)"
        class="flex-1 h-8 px-2 text-sm bg-muted rounded-sm border border-default font-mono"
        maxlength="6"
        @input="(e) => onHexInput({ target: { value: '#' + (e.target as HTMLInputElement).value } } as any)"
      >
      <span class="text-xs text-muted">{{ Math.round(localOpacity * 100) }}%</span>
    </div>
  </div>
</template>

<style scoped>
.checkerboard-bg {
  background-image: linear-gradient(45deg, #ccc 25%, transparent 25%),
    linear-gradient(-45deg, #ccc 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #ccc 75%),
    linear-gradient(-45deg, transparent 75%, #ccc 75%);
  background-size: 8px 8px;
  background-position: 0 0, 0 4px, 4px -4px, -4px 0;
}
</style>
