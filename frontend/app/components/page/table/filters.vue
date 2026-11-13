<script setup lang="ts">
import type { TagFilterOperator } from '@/composables/use-table-filters'
import {
  PAGE_ANNOTATION_STATUS_OPTIONS,
  PAGE_TAG_OPERATOR_OPTIONS,
  type PageAnnotationStatusFilter,
  type PageTableTagOption,
  type PageWorkflowStateFilter
} from '@/composables/use-page-table-filters'

defineProps<{
  tagOptions: PageTableTagOption[]
  workflowStateOptions: Array<{ value: PageWorkflowStateFilter, label: string }>
  active: boolean
  searchClass?: string
}>()

const emit = defineEmits<{ clear: [] }>()

const search = defineModel<string>('search', { required: true })
const tags = defineModel<string[]>('tags', { required: true })
const tagOperator = defineModel<TagFilterOperator>('tagOperator', { required: true })
const annotationStatus = defineModel<PageAnnotationStatusFilter>('annotationStatus', { required: true })
const workflowState = defineModel<PageWorkflowStateFilter>('workflowState', { required: true })
</script>

<template>
  <UInput
    v-model="search"
    icon="i-lucide-search"
    placeholder="Search pages..."
    :class="searchClass ?? 'w-64'"
    size="md"
  >
    <template v-if="search" #trailing>
      <UButton
        color="neutral"
        variant="link"
        icon="i-lucide-x"
        aria-label="Clear search"
        :padded="false"
        @click="search = ''"
      />
    </template>
  </UInput>

  <slot />

  <USelectMenu
    v-model="tags"
    :items="tagOptions"
    value-key="value"
    placeholder="Filter by tag"
    multiple
    class="w-48"
  >
    <template #leading>
      <UIcon name="i-lucide-tag" />
    </template>
    <template #item="{ item }">
      <div class="flex items-center justify-between w-full gap-2">
        <div class="flex items-center gap-2">
          <UIcon
            v-if="tags.includes(item.value)"
            name="i-lucide-check"
            class="text-primary w-4 h-4"
          />
          <span v-else class="w-4 h-4" />
          <span>{{ item.label }}</span>
        </div>
        <UBadge variant="solid" color="neutral" size="xs">
          {{ item.count }}
        </UBadge>
      </div>
    </template>
  </USelectMenu>

  <USelectMenu
    v-if="tags.length > 1"
    v-model="tagOperator"
    :items="PAGE_TAG_OPERATOR_OPTIONS"
    value-key="value"
    class="w-36"
  >
    <template #leading>
      <UIcon name="i-lucide-git-merge" />
    </template>
  </USelectMenu>

  <USelectMenu
    v-model="annotationStatus"
    :items="PAGE_ANNOTATION_STATUS_OPTIONS"
    value-key="value"
    class="w-40"
  >
    <template #leading>
      <UIcon name="i-lucide-file-text" />
    </template>
  </USelectMenu>
  <USelectMenu
    v-model="workflowState"
    :items="workflowStateOptions"
    value-key="value"
    class="w-40"
  >
    <template #leading>
      <UIcon name="i-lucide-list-checks" />
    </template>
  </USelectMenu>
  <AppTableClearFiltersButton
    :active="active"
    @clear="emit('clear')"
  />
</template>
