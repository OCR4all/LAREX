<script setup lang="ts">
import type { RegionKind } from '@/models/editor/region'
import { getRegionKindDisplayName, getRegionKindIcon } from '@/utils/editor/region-colors'
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'

export interface MergeSettings {
  targetKind: RegionKind
  mergeChildren: boolean
}

useBlockEditorCanvasInteractions()

const props = defineProps<{
  availableKinds: RegionKind[]
  defaultKind?: RegionKind
}>()

const emit = defineEmits<{ close: [MergeSettings | null] }>()

const formId = useId()
const targetKind = ref<RegionKind>(props.defaultKind ?? props.availableKinds[0] ?? 'TextRegion')
const mergeChildren = ref(true)

const kindOptions = computed(() =>
  props.availableKinds.map(kind => ({
    label: getRegionKindDisplayName(kind),
    value: kind,
    icon: getRegionKindIcon(kind)
  }))
)

const merge = () => emit('close', { targetKind: targetKind.value, mergeChildren: mergeChildren.value })
const cancel = () => emit('close', null)
</script>

<template>
  <UiResponsiveSlideover :close="{ onClick: cancel }">
    <template #header>
      <UiSlideoverHeader title="Merge Settings" icon="i-lucide-merge" />
    </template>

    <template #body>
      <UForm :id="formId" class="flex flex-col gap-4" @submit="merge">
        <UFormField label="Target Region Type">
          <USelectMenu
            v-model="targetKind"
            :items="kindOptions"
            value-key="value"
            class="w-full"
          />
        </UFormField>

        <UCheckbox v-model="mergeChildren" label="Merge children into new element" />
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="cancel">
          Cancel
        </UButton>
        <UButton
          type="submit"
          :form="formId"
          icon="i-lucide-merge"
          variant="solid"
        >
          Merge
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
