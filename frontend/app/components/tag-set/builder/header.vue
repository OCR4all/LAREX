<script setup lang="ts">
import type { BreadcrumbItem } from '@nuxt/ui'

const props = defineProps<{
  isNew: boolean
  isReadOnly?: boolean
  canShare?: boolean
  breadcrumbItems: BreadcrumbItem[]
  helpTitle?: string
  helpDescription?: string
  helpItems?: string[]
}>()

const emit = defineEmits<{
  import: []
  export: []
  save: []
  share: []
  optimize: []
  openSettings: []
}>()

const { meta, totalErrors } = useTagSetBuilder()

const dropdownItems = computed(() => {
  const actions = [
    { label: 'Import', icon: 'i-lucide-upload', onSelect: () => emit('import') },
    { label: 'Export', icon: 'i-lucide-download', onSelect: () => emit('export') }
  ]

  if (!props.isNew && props.canShare) {
    actions.push({ label: 'Share', icon: 'i-lucide-share-2', onSelect: () => emit('share') })
  }

  actions.push({ label: 'Optimize Colors', icon: 'i-lucide-palette', onSelect: () => emit('optimize') })
  return [actions]
})
</script>

<template>
  <div>
    <UDashboardNavbar data-tour="tag-builder-header" :title="props.isNew ? 'New Tag Set' : meta.name || 'Tag Set'">
      <template #right>
        <div class="flex items-center gap-2">
          <UBadge v-if="totalErrors > 0" color="error" variant="soft">
            {{ totalErrors }} error{{ totalErrors > 1 ? 's' : '' }}
          </UBadge>

          <ToolkitHelpPopover
            v-if="helpTitle"
            :title="helpTitle"
            :description="helpDescription"
            :items="helpItems"
          />

          <UFieldGroup>
            <UButton
              icon="i-lucide-settings"
              color="neutral"
              variant="outline"
              :disabled="props.isReadOnly"
              @click="emit('openSettings')"
            />

            <UButton
              label="Save"
              color="neutral"
              variant="outline"
              icon="i-lucide-save"
              :disabled="props.isReadOnly"
              @click="emit('save')"
            />

            <UDropdownMenu
              v-if="!props.isReadOnly"
              :items="dropdownItems"
            >
              <UButton
                icon="i-lucide-chevron-down"
                color="neutral"
                variant="outline"
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
    </UDashboardToolbar>
  </div>
</template>
