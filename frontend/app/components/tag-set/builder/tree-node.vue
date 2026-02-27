<script setup lang="ts">
import type { TagNode } from '~/types/tag-set'
import UiColorTag from '@/components/ui/color-tag.vue'

interface BuilderTag extends TagNode {
  expanded?: boolean
  errors?: string[]
}

const props = defineProps<{
  tag: BuilderTag
  depth: number
}>()

const emit = defineEmits<{
  select: [tagId: string]
  delete: [tagId: string]
  duplicate: [tagId: string]
  create: [parentId: string]
}>()

const { activeTag, expandedIds, toggleExpand } = useTagSetBuilder()

const isExpanded = computed(() => expandedIds.value.has(props.tag.id))
const isActive = computed(() => activeTag.value?.id === props.tag.id)
const hasChildren = computed(() => props.tag.children && props.tag.children.length > 0)
const hasErrors = computed(() => props.tag.errors && props.tag.errors.length > 0)

const contextMenuItems = computed(() => [
  [
    {
      label: 'Add Child',
      icon: 'i-lucide-plus',
      onSelect: () => emit('create', props.tag.id)
    },
    {
      label: 'Duplicate',
      icon: 'i-lucide-copy',
      onSelect: () => emit('duplicate', props.tag.id)
    }
  ],
  [
    {
      label: 'Delete',
      icon: 'i-lucide-trash',
      color: 'error' as const,
      onSelect: () => emit('delete', props.tag.id)
    }
  ]
])
</script>

<template>
  <div>
    <UContextMenu :items="contextMenuItems">
      <div
        class="flex items-center gap-1.5 px-2 py-1.5 rounded-sm cursor-pointer transition-colors group"
        :class="[
          isActive ? 'bg-primary-100 dark:bg-primary-900/30' : 'hover:bg-neutral-100 dark:hover:bg-neutral-800',
          { 'ring-1 ring-error': hasErrors }
        ]"
        :style="{ paddingLeft: `${depth * 16 + 8}px` }"
        @click="emit('select', tag.id)"
      >
        <button
          v-if="hasChildren"
          class="w-4 h-4 flex items-center justify-center text-neutral-400 hover:text-neutral-600 dark:hover:text-neutral-300"
          @click.stop="toggleExpand(tag.id)"
        >
          <UIcon
            :name="isExpanded ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
            class="w-3 h-3"
          />
        </button>
        <span v-else class="w-4" />

        <UiColorTag
          :color="tag.color"
          variant="subtle"
          size="sm"
          dot
          class="flex-1 min-w-0"
        >
          <span class="block truncate" :class="isActive ? 'font-medium' : ''">{{ tag.title }}</span>
        </UiColorTag>

        <UIcon
          v-if="hasErrors"
          name="i-lucide-alert-circle"
          class="w-3.5 h-3.5 text-error shrink-0"
        />

        <span
          v-if="hasChildren"
          class="text-xs text-neutral-400 shrink-0"
        >
          {{ tag.children?.length }}
        </span>
      </div>
    </UContextMenu>

    <div v-if="hasChildren && isExpanded">
      <TagSetBuilderTreeNode
        v-for="child in tag.children"
        :key="child.id"
        :tag="child as BuilderTag"
        :depth="depth + 1"
        @select="emit('select', $event)"
        @delete="emit('delete', $event)"
        @duplicate="emit('duplicate', $event)"
        @create="emit('create', $event)"
      />
    </div>
  </div>
</template>
