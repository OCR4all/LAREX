<script setup lang="ts">
const emit = defineEmits<{
  create: [parentId?: string]
  select: [tagId: string]
  delete: [tagId: string]
  duplicate: [tagId: string]
}>()

const {
  tags,
  searchQuery,
  filteredTags,
  createTag,
  expandAll,
  collapseAll
} = useTagSetBuilder()
</script>

<template>
  <aside data-tour="tag-builder-sidebar" class="w-80 bg-neutral-50/30 dark:bg-neutral-800/50 border-r border-neutral-200 dark:border-neutral-700 flex flex-col shrink-0">
    <div class="p-4 border-b border-neutral-200/50 dark:border-neutral-700/50">
      <div class="flex items-center gap-2 mb-3">
        <UInput
          v-model="searchQuery"
          placeholder="Search tags..."
          icon="i-lucide-search"
          class="flex-1"
          size="sm"
        >
          <template v-if="searchQuery" #trailing>
            <UButton
              color="neutral"
              variant="link"
              icon="i-lucide-x"
              :padded="false"
              size="xs"
              @click="searchQuery = ''"
            />
          </template>
        </UInput>
      </div>

      <div class="flex items-center justify-between">
        <UButton
          label="Add Tag"
          icon="i-lucide-plus"
          size="xs"
          variant="soft"
          @click="createTag()"
        />

        <div class="flex items-center gap-1">
          <UButton
            icon="i-lucide-unfold-vertical"
            size="xs"
            variant="ghost"
            color="neutral"
            title="Expand all"
            @click="expandAll"
          />
          <UButton
            icon="i-lucide-fold-vertical"
            size="xs"
            variant="ghost"
            color="neutral"
            title="Collapse all"
            @click="collapseAll"
          />
        </div>
      </div>
    </div>

    <div class="flex-1 overflow-y-auto p-2">
      <div v-if="filteredTags.length === 0" class="text-center text-sm text-neutral-500 py-8">
        <UIcon name="i-lucide-folder-open" class="w-8 h-8 mx-auto mb-2 opacity-50" />
        <p>No tags yet</p>
        <p class="text-xs">
          Click "Add Tag" to create one
        </p>
      </div>

      <TagSetBuilderTreeNode
        v-for="tag in filteredTags"
        :key="tag.id"
        :tag="tag"
        :depth="0"
        @select="emit('select', $event)"
        @delete="emit('delete', $event)"
        @duplicate="emit('duplicate', $event)"
        @create="emit('create', $event)"
      />
    </div>

    <div class="p-4 border-t border-neutral-200/50 dark:border-neutral-700/50 text-xs text-neutral-500">
      {{ tags.length }} root tag{{ tags.length !== 1 ? 's' : '' }}
    </div>
  </aside>
</template>
