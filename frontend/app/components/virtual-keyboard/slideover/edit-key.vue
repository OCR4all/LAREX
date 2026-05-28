<script setup lang="ts">
import { LazyVirtualKeyboardSlideoverGlyphPicker } from '#components'

import type { KeyboardItem } from '@/types/virtual-keyboard'

const props = defineProps<{
  item: KeyboardItem
  isValidPlacement: (x: number, y: number, w: number, excludeId: number | null) => boolean
}>()

const emit = defineEmits<{ close: [KeyboardItem | null] }>()

const draft = reactive({ ...props.item })
const overlay = useOverlay()
const glyphPickerSlideover = overlay.create(LazyVirtualKeyboardSlideoverGlyphPicker)

const resizeItem = (delta: number) => {
  const newW = draft.w + delta
  if (newW >= 1 && props.isValidPlacement(draft.x, draft.y, newW, draft.id)) draft.w = newW
}

const openGlyphPicker = async (field: 'char' | 'shiftChar') => {
  const instance = glyphPickerSlideover.open({ title: `Select ${field === 'char' ? 'Character' : 'Shift Character'}` })
  const result = await instance.result
  if (result) draft[field] = result.utf8
}

const save = () => emit('close', { ...draft })
const deleteKey = () => emit('close', { ...draft, _delete: true } as KeyboardItem & { _delete: boolean })
const cancel = () => emit('close', null)
</script>

<template>
  <UiResponsiveSlideover
    :modal="false"
    side="left"
    :close="{ onClick: cancel }"
  >
    <template #header>
      <UiSlideoverHeader title="Edit Key" icon="i-lucide-keyboard" />
    </template>

    <template #body>
      <div class="flex flex-col gap-4">
        <div class="grid grid-cols-2 gap-3">
          <UFormField label="Char">
            <UFieldGroup class="w-full">
              <UInput v-model="draft.char" class="rounded-r-none font-junicode text-center" />
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-search"
                class="rounded-l-none"
                @click="openGlyphPicker('char')"
              />
            </UFieldGroup>
          </UFormField>
          <UFormField label="Shift">
            <UFieldGroup class="w-full">
              <UInput v-model="draft.shiftChar" class="rounded-r-none font-junicode text-center" />
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-search"
                class="rounded-l-none"
                @click="openGlyphPicker('shiftChar')"
              />
            </UFieldGroup>
          </UFormField>
        </div>

        <div class="flex items-center justify-between bg-elevated p-3 rounded-sm border border-default">
          <span class="text-sm">Width: {{ draft.w }}</span>
          <div class="flex gap-2">
            <UButton
              color="neutral"
              variant="outline"
              size="xs"
              @click="resizeItem(-1)"
            >
              -
            </UButton>
            <UButton
              color="neutral"
              variant="outline"
              size="xs"
              @click="resizeItem(1)"
            >
              +
            </UButton>
          </div>
        </div>

        <UButton
          v-if="item.id !== 0"
          color="error"
          variant="soft"
          block
          @click="deleteKey"
        >
          Delete Key
        </UButton>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="cancel">
          Cancel
        </UButton>
        <UButton icon="i-lucide-save" variant="solid" @click="save">
          Save
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
