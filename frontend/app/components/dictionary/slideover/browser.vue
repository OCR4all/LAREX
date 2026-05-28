<script setup lang="ts">
import type { Dictionary } from '@/types/dictionary'

const props = defineProps<{
  workspaceId: string
  dictionaryId: string
}>()

const emit = defineEmits<{
  close: []
}>()

const { data: dictionary } = await useFetch<Dictionary>(
  `/api/workspaces/${props.workspaceId}/dictionaries/${props.dictionaryId}`,
  {
    key: `dictionary-slideover-${props.workspaceId}-${props.dictionaryId}`
  }
)
</script>

<template>
  <UiResponsiveSlideover @close="emit('close')">
    <template #header>
      <UiSlideoverHeader :title="dictionary?.name || 'Dictionary'" icon="i-lucide-book-open" />
    </template>

    <template #body>
      <div class="space-y-4">
        <div class="flex items-center justify-between gap-3">
          <div class="min-w-0">
            <p class="text-sm font-medium truncate">{{ dictionary?.name || 'Dictionary' }}</p>
            <p v-if="dictionary?.description" class="text-xs text-muted line-clamp-2">{{ dictionary.description }}</p>
          </div>
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-arrow-up-right"
            :to="`/dictionaries/${props.dictionaryId}`"
            @click="emit('close')"
          >
            Open Full Page
          </UButton>
        </div>

        <DictionaryEntryBrowser
          :workspace-id="props.workspaceId"
          :dictionary-id="props.dictionaryId"
          :editable="false"
          height-class="h-[70vh]"
        />
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
