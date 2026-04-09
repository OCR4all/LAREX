<script setup lang="ts">
import type { TagNode, FlattenedTag } from '~/types/tag-set'
import UiColorTag from '@/components/ui/color-tag.vue'

interface Props {
  modelValue: string[]
  tagSetId?: string | null
  workspaceId: string
  placeholder?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: 'Select tags...',
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const { data: tagSet, status: tagSetStatus, refresh: refreshTagSet } = await useFetch<{
  id: string
  tags: TagNode[]
}>(() => props.tagSetId ? `/api/workspaces/${props.workspaceId}/tag-sets/${props.tagSetId}` : '', {
  immediate: !!props.tagSetId,
  watch: [() => props.tagSetId]
})

watch(() => props.tagSetId, async (newId, oldId) => {
  if (newId && newId !== oldId) {
    await refreshTagSet()
  }
})

const flattenedTags = computed<FlattenedTag[]>(() => {
  if (!tagSet.value?.tags) return []

  const result: FlattenedTag[] = []

  const flatten = (tags: TagNode[], path: string = '', ancestorIds: string[] = []) => {
    for (const tag of tags) {
      const currentPath = path ? `${path} > ${tag.title}` : tag.title

      const descendantIds: string[] = []
      const collectDescendants = (children: TagNode[] | undefined) => {
        if (!children) return
        for (const child of children) {
          descendantIds.push(child.id)
          collectDescendants(child.children)
        }
      }
      collectDescendants(tag.children)

      result.push({
        id: tag.id,
        title: tag.title,
        path: currentPath,
        color: tag.color,
        ancestorIds: [...ancestorIds],
        descendantIds
      })

      if (tag.children) {
        flatten(tag.children, currentPath, [...ancestorIds, tag.id])
      }
    }
  }

  flatten(tagSet.value.tags)
  return result
})

const selectItems = computed(() => {
  return flattenedTags.value.map(tag => ({
    label: tag.title,
    value: tag.id,
    path: tag.path,
    color: tag.color
  }))
})

const selectedTags = computed({
  get: () => props.modelValue,
  set: (value: string[]) => emit('update:modelValue', value)
})

const freeFormTags = computed({
  get: () => props.modelValue,
  set: (value: string[]) => emit('update:modelValue', value)
})

const getTagDetails = (tagId: string) => {
  return flattenedTags.value.find(t => t.id === tagId)
}

const hasTagSet = computed(() => !!props.tagSetId && !!tagSet.value)
const isLoading = computed(() => tagSetStatus.value === 'pending')
</script>

<template>
  <div>
    <div v-if="isLoading" class="flex items-center gap-2 text-neutral-500 text-sm py-2">
      <div class="w-4 h-4 border-2 border-primary-500 border-t-transparent rounded-sm animate-spin" />
      Loading tag structure...
    </div>

    <USelectMenu
      v-else-if="hasTagSet"
      v-model="selectedTags"
      :items="selectItems"
      value-key="value"
      icon="i-lucide-tags"
      :placeholder="placeholder"
      :disabled="disabled"
      searchable
      searchable-placeholder="Search tags..."
      multiple
      clear-search-on-close
    >
      <template #item="{ item }">
        <div class="flex items-center gap-2 w-full">
          <UIcon
            v-if="props.modelValue.includes(item.value)"
            name="i-lucide-check"
            class="w-4 h-4 text-primary-500 shrink-0"
          />
          <span v-else class="w-4 h-4 shrink-0" />

          <div class="flex-1 min-w-0">
            <UiColorTag
              :color="item.color || '#6b7280'"
              variant="subtle"
              size="sm"
              dot
            >
              {{ item.label }}
            </UiColorTag>
            <div v-if="item.path !== item.label" class="text-xs text-neutral-500 truncate">
              {{ item.path }}
            </div>
          </div>
        </div>
      </template>
    </USelectMenu>

    <UInputTags
      v-else
      v-model="freeFormTags"
      :placeholder="placeholder"
      :disabled="disabled"
    />

    <div v-if="hasTagSet && selectedTags.length > 0" class="mt-2 flex flex-wrap gap-1">
      <UiColorTag
        v-for="tagId in selectedTags"
        :key="tagId"
        :color="getTagDetails(tagId)?.color || '#6b7280'"
        variant="subtle"
        size="sm"
        dot
        :removable="!disabled"
        @remove="selectedTags = selectedTags.filter(t => t !== tagId)"
      >
        <span>{{ getTagDetails(tagId)?.title || tagId }}</span>
      </UiColorTag>
    </div>
  </div>
</template>
