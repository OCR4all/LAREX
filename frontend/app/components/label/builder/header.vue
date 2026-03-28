<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'

const props = defineProps<{
  isNew: boolean
  isSystem?: boolean
  breadcrumbItems: { label: string, icon?: string, to?: string }[]
  helpTitle?: string
  helpDescription?: string
  helpItems?: string[]
}>()

const emit = defineEmits(['import', 'export', 'save', 'optimize', 'openSettings'])

const { labels, totalErrors } = useLabelBuilder()

const actionItems = computed<DropdownMenuItem[]>(() => [
  { label: 'Import', icon: 'i-lucide-upload', disabled: props.isSystem, onSelect: () => emit('import') },
  { label: 'Export', icon: 'i-lucide-download', disabled: props.isSystem, onSelect: () => emit('export') },
  { label: 'Auto-Color', icon: 'i-lucide-palette', disabled: props.isSystem, onSelect: () => emit('optimize') }
])
</script>

<template>
  <UDashboardNavbar data-tour="label-builder-header" :title="isNew ? 'New Label Set' : (isSystem ? 'View Label Set' : 'Edit Label Set')">
    <template #leading>
      <LazyUDashboardSidebarCollapse />
    </template>
    <template #right>
      <div class="flex items-center gap-2">
        <UtilityHelpPopover
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
            @click="$emit('openSettings')"
          />
          <UButton
            label="Save"
            icon="i-lucide-save"
            :variant="totalErrors > 0 ? 'subtle' : 'outline'"
            :color="totalErrors > 0 ? 'error' : 'neutral'"
            :disabled="totalErrors > 0 || isSystem"
            @click="$emit('save')"
          >
            <template v-if="totalErrors > 0" #trailing>
              <UBadge :label="String(totalErrors)" color="error" size="xs" />
            </template>
          </UButton>
          <UDropdownMenu v-if="!isSystem" :items="actionItems" :content="{ align: 'end' }">
            <UButton color="neutral" variant="outline" icon="i-lucide-chevron-down" />
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
      <span class="text-xs text-muted">{{ labels.length }} labels</span>
    </template>
  </UDashboardToolbar>
</template>
