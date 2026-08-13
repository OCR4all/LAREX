<script setup lang="ts">
import type { BreadcrumbItem, DropdownMenuItem } from '@nuxt/ui'
import { isEditableLabelDefinition } from '@/composables/use-label-builder'

const props = defineProps<{
  isNew: boolean
  isSystem?: boolean
  isDirty?: boolean
  isSaving?: boolean
  canShare?: boolean
  breadcrumbItems: BreadcrumbItem[]
  helpTitle?: string
  helpDescription?: string
  helpItems?: string[]
}>()

const emit = defineEmits(['import', 'export', 'save', 'share', 'optimize', 'openSettings'])

const { labels, totalErrors } = useLabelBuilder()
const labelCount = computed(() => labels.value.filter(isEditableLabelDefinition).length)

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = [
    { label: 'Import', icon: 'i-lucide-upload', disabled: props.isSystem, onSelect: () => emit('import') },
    { label: 'Export', icon: 'i-lucide-download', disabled: props.isSystem, onSelect: () => emit('export') }
  ]

  if (!props.isNew && props.canShare) {
    items.push({ label: 'Share', icon: 'i-lucide-share-2', onSelect: () => emit('share') })
  }

  items.push({ label: 'Auto-Color', icon: 'i-lucide-palette', disabled: props.isSystem, onSelect: () => emit('optimize') })
  return items
})
</script>

<template>
  <UDashboardNavbar data-tour="label-builder-header" :title="isNew ? 'New Label Set' : (isSystem ? 'View Label Set' : 'Edit Label Set')">
    <template #right>
      <div class="flex items-center gap-2">
        <ToolkitHelpPopover
          v-if="helpTitle"
          :title="helpTitle"
          :description="helpDescription"
          :items="helpItems"
        />
        <UFieldGroup>
          <UButton
            icon="i-lucide-settings"
            variant="outline"
            color="neutral"
            :disabled="isSystem"
            aria-label="Label set settings"
            @click="$emit('openSettings')"
          />
          <UButton
            label="Save"
            icon="i-lucide-save"
            :variant="totalErrors > 0 ? 'subtle' : 'outline'"
            :color="totalErrors > 0 ? 'error' : 'neutral'"
            :loading="isSaving"
            :disabled="totalErrors > 0 || isSystem || isSaving || (!isNew && !isDirty)"
            @click="$emit('save')"
          >
            <template v-if="totalErrors > 0" #trailing>
              <UBadge :label="String(totalErrors)" color="error" size="xs" />
            </template>
          </UButton>
          <UDropdownMenu v-if="!isSystem" :items="actionItems" :content="{ align: 'end' }">
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-chevron-down"
              aria-label="More label set actions"
            />
          </UDropdownMenu>
        </UFieldGroup>
      </div>
    </template>
  </UDashboardNavbar>
  <UDashboardToolbar>
    <template #left>
      <UBreadcrumb :items="breadcrumbItems" />
    </template>
    <template #right>
      <div class="flex items-center gap-2">
        <UBadge
          v-if="isDirty"
          label="Modified"
          color="warning"
          variant="subtle"
          size="sm"
        />
        <span class="text-xs text-muted">{{ labelCount }} label{{ labelCount === 1 ? '' : 's' }}</span>
      </div>
    </template>
  </UDashboardToolbar>
</template>
