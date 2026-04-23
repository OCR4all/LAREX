<script setup lang="ts">
import type { RegionKind } from '@/models/editor/region'
import { getRegionKindDisplayName, getRegionKindIcon } from '@/utils/editor/region-colors'

export interface MergeSettings {
  targetKind: RegionKind
  mergeChildren: boolean
}

const props = defineProps<{
  availableKinds: RegionKind[]
  defaultKind?: RegionKind
}>()

const emit = defineEmits<{ close: [MergeSettings | null] }>()

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
  <USlideover title="Merge Settings" :close="{ onClick: cancel }">
    <template #body>
      <div class="flex flex-col gap-4">
        <UFormField label="Target Region Type">
          <USelectMenu
            v-model="targetKind"
            :items="kindOptions"
            value-key="value"
            class="w-full"
          />
        </UFormField>

        <UCheckbox v-model="mergeChildren" label="Merge children into new element" />
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="cancel">
          Cancel
        </UButton>
        <UButton icon="i-lucide-merge" variant="solid" @click="merge">
          Merge
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
