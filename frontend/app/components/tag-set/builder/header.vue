<script setup lang="ts">
interface BreadcrumbItem {
  label: string
  icon?: string
  to?: string
}

defineProps<{
  isNew: boolean
  isReadOnly?: boolean
  breadcrumbItems: BreadcrumbItem[]
}>()

const emit = defineEmits<{
  import: []
  export: []
  save: []
  optimize: []
  openSettings: []
}>()

const { meta, totalErrors } = useTagSetBuilder()
</script>

<template>
  <div>
    <UDashboardNavbar data-tour="tag-builder-header" :title="isNew ? 'New Tag Set' : meta.name || 'Tag Set'">
      <template #leading>
        <LazyUDashboardSidebarCollapse />
      </template>
      <template #right>
        <div class="flex items-center gap-2">
          <UBadge v-if="totalErrors > 0" color="error" variant="soft">
            {{ totalErrors }} error{{ totalErrors > 1 ? 's' : '' }}
          </UBadge>

          <UFieldGroup>
            <UButton
              icon="i-lucide-settings"
              color="neutral"
              variant="outline"
              :disabled="isReadOnly"
              @click="emit('openSettings')"
            />

            <UButton
              label="Save"
              color="neutral"
              variant="outline"
              icon="i-lucide-save"
              :disabled="isReadOnly"
              @click="emit('save')"
            />

            <UDropdownMenu
              v-if="!isReadOnly"
              :items="[[
                { label: 'Import', icon: 'i-lucide-upload', onSelect: () => emit('import') },
                { label: 'Export', icon: 'i-lucide-download', onSelect: () => emit('export') },
                { label: 'Optimize Colors', icon: 'i-lucide-palette', onSelect: () => emit('optimize') }
              ]]"
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
