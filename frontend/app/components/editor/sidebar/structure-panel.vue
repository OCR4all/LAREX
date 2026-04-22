<script setup lang="ts">
import type { TreeItemData } from '@/components/editor/sidebar/tree-item.vue'

defineProps<{
  polygons: TreeItemData[]
  polylines: TreeItemData[]
  regions: TreeItemData[]
  selectedPolygonIds: string[]
  selectedPolylineIds: string[]
  hoveredPolygonId?: string | null
  hiddenPolygonIds: string[]
  hiddenPolylineIds: string[]
  expandedRegions: Set<string>
  selectedCount: number
  deleteSelectedDisabled?: boolean
}>()

const emit = defineEmits<{
  'select-polygon': [id: string]
  'select-polyline': [id: string]
  'hover-polygon': [id: string]
  'hover-polyline': [id: string]
  'unhover-polygon': []
  'delete-item': [id: string]
  'toggle-visibility': [id: string]
  'toggle-expanded': [id: string]
  'delete-selected': []
  'hide-selected': []
  'show-selected': []
}>()
</script>

<template>
  <div>
    <div class="px-4 py-2 flex justify-between items-center bg-muted/10">
      <span class="text-xs text-muted font-medium">
        {{ polygons.length }} items
      </span>

      <div class="flex items-center gap-1">
        <UButton
          variant="outline"
          size="sm"
          class="h-7 px-2 text-xs"
          :disabled="selectedCount === 0"
          @click="emit('delete-selected')"
        >
          Delete ({{ selectedCount }})
        </UButton>
        <UButton
          variant="outline"
          size="sm"
          class="h-7 px-2 text-xs"
          :disabled="selectedCount === 0"
          @click="emit('hide-selected')"
        >
          Hide ({{ selectedCount }})
        </UButton>
        <UButton
          variant="outline"
          size="sm"
          class="h-7 px-2 text-xs"
          :disabled="selectedCount === 0"
          @click="emit('show-selected')"
        >
          Show ({{ selectedCount }})
        </UButton>
      </div>
    </div>
    <USeparator />
    <div class="flex-1">
      <div class="p-2 space-y-1">
        <EditorSidebarTreeItem
          v-for="region in regions"
          :key="region.id"
          :item="region"
          :level="0"
          :selected-polygon-ids="selectedPolygonIds"
          :selected-polyline-ids="selectedPolylineIds"
          :hovered-id="hoveredPolygonId"
          :polygons="polygons"
          :polylines="polylines"
          :expanded-regions="expandedRegions"
          :hidden-polygon-ids="hiddenPolygonIds"
          :hidden-polyline-ids="hiddenPolylineIds"
          @select-item="(id) => emit('select-polygon', id)"
          @select-polyline="(id) => emit('select-polyline', id)"
          @hover-item="(id) => emit('hover-polygon', id)"
          @hover-polyline="(id) => emit('hover-polyline', id)"
          @unhover-item="emit('unhover-polygon')"
          @delete-item="(id) => emit('delete-item', id)"
          @toggle-visibility="(id) => emit('toggle-visibility', id)"
          @toggle-expanded="(id) => emit('toggle-expanded', id)"
        />

        <div v-if="polygons.length === 0" class="flex flex-col items-center justify-center py-12 text-center px-4">
          <div class="bg-muted rounded-sm p-3 mb-3">
            <Icon name="i-lucide-file-text" class="h-6 w-6 text-muted" />
          </div>
          <h3 class="text-sm font-medium mb-1">
            No document structure
          </h3>
          <p class="text-xs text-muted">
            Start drawing to create regions and textlines
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
