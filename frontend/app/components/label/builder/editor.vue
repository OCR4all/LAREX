<script setup lang="ts">
const props = defineProps<{
  isSystem?: boolean
}>()
defineEmits(['changeScope'])
const {
  meta, activeLabel, PRESET_COLORS, PAGE_REGIONS, PAGE_TEXT_TYPES, ALTO_BLOCK_TYPES,
  getErrors
} = useLabelBuilder()
const autoFillTag = () => {
  if (activeLabel.value && !activeLabel.value.mapping.altoXml.tag) {
    activeLabel.value.mapping.altoXml.tag = activeLabel.value.name.replace(/\s+/g, '')
  }
}

const errors = computed(() => getErrors(activeLabel.value))
const conflicts = computed(() => {
  if (!activeLabel.value) return []
  return [] // Placeholder to keep SFC clean, logic in composable recommended
})
</script>

<template>
  <div data-tour="label-builder-editor" class="flex-1 p-8 overflow-y-auto custom-scroll">
    <div class="max-w-2xl mx-auto space-y-8">
      <div v-if="errors.length > 0" class="bg-red-500/10 border border-red-500/50 rounded-sm p-4">
        <div class="flex items-start gap-3">
          <Icon name="lucide-message-square-warning" size="32" class="text-red-400" />
          <div class="flex-1">
            <h3 class="text-sm font-bold text-red-400 mb-1">
              Conflicts Detected
            </h3>
            <ul class="list-disc list-inside text-[11px] text-red-800/80 dark:text-red-200/80 space-y-1">
              <li v-for="err in errors" :key="err.code">
                {{ err.message }}
              </li>
            </ul>
          </div>
        </div>
      </div>

      <div class="bg-neutral-300/50 dark:bg-neutral-800/50 p-1 rounded-sm border border-neutral-400/50 dark:border-neutral-700 inline-flex">
        <button
          class="px-4 py-1.5 text-xs font-bold rounded-sm transition-all flex items-center gap-2"
          :class="activeLabel.scope === 'region' ? 'bg-primary-600 text-white shadow' : 'text-neutral-400 hover:text-white'"
          :disabled="props.isSystem"
          @click="activeLabel.scope !== 'region' && $emit('changeScope', 'region')"
        >
          Region (Block)
        </button>
        <button
          class="px-4 py-1.5 text-xs font-bold rounded-sm transition-all flex items-center gap-2"
          :class="activeLabel.scope === 'line' ? 'bg-emerald-600 text-white shadow' : 'text-neutral-400 hover:text-white'"
          :disabled="props.isSystem"
          @click="activeLabel.scope !== 'line' && $emit('changeScope', 'line')"
        >
          <span>≡</span> Text Line
        </button>
      </div>

      <div class="space-y-4">
        <UFormField label="Name">
          <UInput
            v-model="activeLabel.name"
            variant="subtle"
            placeholder="Give the label a name"
            :disabled="props.isSystem"
            @input="autoFillTag"
          />
        </UFormField>
        <UFormField label="Description">
          <UTextarea
            v-model="activeLabel.description"
            placeholder="Short label description"
            variant="subtle"
            :disabled="props.isSystem"
          />
        </UFormField>
      </div>

      <div class="bg-neutral-200/30 dark:bg-neutral-800 p-6 rounded-sm border border-neutral-300 dark:border-neutral-700 space-y-6">
        <div class="space-y-4">
          <div class="flex items-center gap-2 border-b border-neutral-700/50 pb-2">
            <span class="text-xs font-bold text-white bg-neutral-700 px-1.5 py-0.5 rounded">PAGE XML</span>
          </div>
          <div v-if="activeLabel.scope === 'region'" class="grid grid-cols-2 gap-4">
            <UFormField label="Region Type">
              <USelectMenu
                v-model="activeLabel.mapping.pageXml.regionType"
                :items="PAGE_REGIONS"
                variant="outline"
                :disabled="props.isSystem"
              />
            </UFormField>
            <div v-if="activeLabel.mapping.pageXml.regionType === 'TextRegion'" class="space-y-2">
              <UFormField label="Subtype">
                <USelectMenu
                  v-model="activeLabel.mapping.pageXml.textType"
                  :items="PAGE_TEXT_TYPES"
                  variant="outline"
                  clear
                  :disabled="props.isSystem"
                />
              </UFormField>
              <UFormField v-if="activeLabel.mapping.pageXml.textType === 'custom'" label="Custom Subtype">
                <UInput
                  v-model="activeLabel.mapping.pageXml.customSubType"
                  placeholder="custom"
                  color="primary"
                  variant="outline"
                  :disabled="props.isSystem"
                />
              </UFormField>
            </div>
          </div>

          <div v-if="activeLabel.scope === 'line'">
            <label class="label-title flex justify-between"><span>Custom Attributes</span><span class="text-neutral-600">key { value; }</span></label>
            <div class="flex items-center bg-neutral-100 dark:bg-neutral-900 border border-neutral-400 dark:border-neutral-600 rounded-sm overflow-hidden">
              <input
                v-model="activeLabel.mapping.pageXml.customKey"
                type="text"
                placeholder="structure"
                class="w-1/3 bg-transparent px-2 py-2 text-xs text-primary-600 dark:text-primary-400 font-mono text-right border-r border-neutral-700 focus:outline-none"
                :disabled="props.isSystem"
              >
              <span class="pl-2 text-neutral-500 text-xs">{</span>
              <input
                v-model="activeLabel.mapping.pageXml.customData"
                type="text"
                placeholder="type:val"
                class="w-full bg-transparent px-2 py-2 text-sm text-black dark:text-white focus:outline-none"
                :disabled="props.isSystem"
              >
              <span class="pr-2 text-neutral-500 text-xs">; }</span>
            </div>
          </div>
        </div>

        <div v-if="meta.altoEnabled" class="space-y-4">
          <div class="flex items-center gap-2 border-b border-neutral-700/50 pb-2">
            <span class="text-xs font-bold text-white bg-neutral-700 px-1.5 py-0.5 rounded">ALTO XML</span>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div v-if="activeLabel.scope === 'region'">
              <label class="label-title">Block Type</label>
              <select v-model="activeLabel.mapping.altoXml.blockType" class="input-std" :disabled="props.isSystem">
                <option v-for="type in ALTO_BLOCK_TYPES" :key="type" :value="type">
                  {{ type }}
                </option>
              </select>
            </div>
            <div class="grid grid-cols-2 gap-2 col-span-2 md:col-span-1">
              <div>
                <label class="label-title">Tag Role</label>
                <select v-model="activeLabel.mapping.altoXml.role" class="input-std text-xs" :disabled="props.isSystem">
                  <option value="TAGREFS">
                    Structure
                  </option>
                  <option value="STYLEREFS">
                    Style
                  </option>
                </select>
              </div>
              <div>
                <label class="label-title">Value ID</label>
                <input
                  v-model="activeLabel.mapping.altoXml.tag"
                  type="text"
                  class="input-std font-mono"
                  :disabled="props.isSystem"
                >
              </div>
            </div>
          </div>
        </div>
      </div>

      <div>
        <label class="label-title mb-3">Color Code</label>
        <div class="bg-neutral-200/30 dark:bg-neutral-800 p-4 rounded-sm border border-neutral-300 dark:border-neutral-700">
          <div class="flex flex-wrap gap-3 mb-4">
            <button
              v-for="color in PRESET_COLORS"
              :key="color"
              class="w-8 h-8 rounded-sm shadow-sm"
              :class="{ 'ring-2 ring-white ring-offset-2 ring-offset-neutral-800': activeLabel.color === color }"
              :style="{ backgroundColor: color }"
              :disabled="props.isSystem"
              @click="activeLabel.color = color"
            />
          </div>
          <div class="flex items-center gap-3">
            <input
              v-model="activeLabel.color"
              type="color"
              class="h-10 w-10 bg-transparent rounded-sm cursor-pointer border-0 p-0"
              :disabled="props.isSystem"
            >
            <input
              v-model="activeLabel.color"
              type="text"
              class="bg-transparent text-sm font-mono text-black dark:text-white outline-none uppercase"
              :disabled="props.isSystem"
            >
          </div>
          <div v-if="conflicts.length > 0" class="mt-3 text-amber-500 text-[10px]">
            Similar to: {{ conflicts.join(', ') }}
          </div>
        </div>
      </div>

      <!-- TODO: Reintroduce PAGE-relevant semantic flags only if/when we support non-PAGE schemas again. -->
    </div>
  </div>
</template>

<style scoped>

</style>
