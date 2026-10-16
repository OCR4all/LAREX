<script setup lang="ts">
const { isReadOnly = false } = defineProps<{
  isReadOnly?: boolean
}>()

const emit = defineEmits<{
  create: [parentId?: string]
  select: [tagId: string]
  delete: [tagId: string]
  duplicate: [tagId: string]
}>()

const {
  meta,
  tags,
  searchQuery,
  filteredTags,
  expandAll,
  collapseAll
} = useTagSetBuilder()

const openMetadataPanels = ref<string[]>([])
const metadataPanelItems = [{
  label: 'Tag set details',
  icon: 'i-lucide-settings-2',
  slot: 'metadata',
  value: 'metadata'
}]

const descriptionModel = computed({
  get: () => meta.description ?? '',
  set: (value: string) => {
    meta.description = value
  }
})
</script>

<template>
  <aside data-tour="tag-builder-sidebar" class="flex w-80 shrink-0 flex-col border-r border-default bg-muted/20">
    <div class="space-y-2 border-b border-default p-3">
      <UAccordion
        v-model="openMetadataPanels"
        :items="metadataPanelItems"
        type="multiple"
        :ui="{
          item: 'border-b-0',
          trigger: 'px-1 py-2',
          body: 'px-1 pb-1'
        }"
      >
        <template #metadata>
          <div class="space-y-3">
            <UFormField label="Name" required>
              <UInput
                v-model="meta.name"
                placeholder="e.g. Document Types"
                :disabled="isReadOnly"
                size="sm"
              />
            </UFormField>

            <UFormField label="Description">
              <UTextarea
                v-model="descriptionModel"
                placeholder="Describe this tag set and its purpose..."
                :rows="2"
                :disabled="isReadOnly"
                size="sm"
              />
            </UFormField>

            <UFormField label="Tags">
              <UInputTags
                v-model="meta.tags"
                placeholder="Add tags..."
                :disabled="isReadOnly"
                size="sm"
              />
            </UFormField>
          </div>
        </template>
      </UAccordion>
      <USeparator />
      <div class="flex items-center gap-2">
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
              @click="() => { searchQuery = '' }"
            />
          </template>
        </UInput>
      </div>

      <div class="flex items-center justify-between">
        <UButton
          v-if="!isReadOnly"
          label="Add Tag"
          icon="i-lucide-plus"
          size="xs"
          variant="soft"
          @click="emit('create')"
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

    <div class="min-h-0 flex-1 overflow-y-auto p-2">
      <div v-if="filteredTags.length === 0" class="text-center text-sm text-neutral-500 py-8">
        <UIcon name="i-lucide-folder-open" class="w-8 h-8 mx-auto mb-2 opacity-50" />
        <p>No tags yet</p>
        <p v-if="!isReadOnly" class="text-xs">
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

    <div class="border-t border-default p-4 text-xs text-muted">
      {{ tags.length }} root tag{{ tags.length !== 1 ? 's' : '' }}
    </div>
  </aside>
</template>
