<script setup lang="ts">
import type { CollaborationLeaseOwner, CollaborationRoomMember, CollaborationTakeoverRequest } from '@/types/collaboration'
import type { RegionKind, TextRegionSubtype } from '@/models/editor/region'

export type RegionType = RegionKind | 'TextLine' | 'Baseline' | 'Polyline'
export type TextSubtype = TextRegionSubtype | string

export interface EntityInfo {
  regionType: RegionType
  subtype?: TextSubtype
  id: string
  label?: string
  width?: number
  height?: number
  childCount?: number
}

export interface PageSummary {
  pageId: string
  variantLabel?: string
  totalRegions: number
  textRegions: number
  imageRegions: number
  lineDrawings: number
  tableRegions: number
  otherRegions: number
}

const props = withDefaults(defineProps<{
  hoveredEntity?: EntityInfo | null
  selectedEntity?: EntityInfo | null
  pageSummary?: PageSummary | null
  collaborators?: CollaborationRoomMember[]
  editor?: CollaborationLeaseOwner | null
  pendingTakeover?: CollaborationTakeoverRequest | null
  canEdit?: boolean
  annotationMode?: 'PROJECT' | 'DATASET_LINK' | 'DATASET_COPY' | null
}>(), {
  hoveredEntity: null,
  selectedEntity: null,
  pageSummary: null,
  collaborators: () => [],
  editor: null,
  pendingTakeover: null,
  canEdit: true,
  annotationMode: null
})

const tooltipUi = {
  content: 'bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-700 text-xs text-neutral-700 dark:text-neutral-300 shadow-lg'
}

const regionIconMap: Partial<Record<RegionType, string>> = {
  ImageRegion: 'lucide:image',
  TextRegion: 'lucide:type',
  LineDrawingRegion: 'lucide:pen-line',
  SeparatorRegion: 'lucide:minus',
  GraphicRegion: 'lucide:shapes',
  TableRegion: 'lucide:table',
  ChartRegion: 'lucide:bar-chart-2',
  MapRegion: 'lucide:map',
  MathsRegion: 'lucide:sigma',
  ChemRegion: 'lucide:flask-conical',
  MusicRegion: 'lucide:music',
  AdvertRegion: 'lucide:megaphone',
  NoiseRegion: 'lucide:alert-triangle',
  UnknownRegion: 'lucide:help-circle',
  CustomRegion: 'lucide:tag',
  TextLine: 'lucide:type',
  Baseline: 'lucide:minus',
  Polyline: 'lucide:line-chart'
}

const regionColorMap: Partial<Record<RegionType, string>> = {
  ImageRegion: 'text-orange-600 dark:text-orange-400',
  TextRegion: 'text-cyan-600 dark:text-cyan-400',
  LineDrawingRegion: 'text-emerald-600 dark:text-emerald-400',
  SeparatorRegion: 'text-neutral-500 dark:text-neutral-400',
  GraphicRegion: 'text-violet-600 dark:text-violet-400',
  TableRegion: 'text-amber-600 dark:text-amber-400',
  ChartRegion: 'text-sky-600 dark:text-sky-400',
  MapRegion: 'text-teal-600 dark:text-teal-400',
  MathsRegion: 'text-rose-600 dark:text-rose-400',
  ChemRegion: 'text-orange-500 dark:text-orange-300',
  MusicRegion: 'text-pink-600 dark:text-pink-400',
  AdvertRegion: 'text-lime-600 dark:text-lime-400',
  NoiseRegion: 'text-neutral-500 dark:text-neutral-400',
  UnknownRegion: 'text-neutral-500 dark:text-neutral-400',
  CustomRegion: 'text-neutral-500 dark:text-neutral-400',
  TextLine: 'text-cyan-600 dark:text-cyan-400',
  Baseline: 'text-neutral-500 dark:text-neutral-400',
  Polyline: 'text-neutral-500 dark:text-neutral-400'
}

const displayEntity = computed(() => props.hoveredEntity ?? props.selectedEntity ?? null)
const isHovered = computed(() => !!props.hoveredEntity && (!props.selectedEntity || props.hoveredEntity.id !== props.selectedEntity.id))

const iconName = computed(() => {
  const type = displayEntity.value?.regionType
  return (type ? regionIconMap[type] : null) ?? 'lucide:box-select'
})

const iconColorClass = computed(() => {
  const type = displayEntity.value?.regionType
  return (type ? regionColorMap[type] : null) ?? 'text-neutral-500 dark:text-neutral-400'
})

const summarySegments = computed(() => {
  const summary = props.pageSummary
  if (!summary) return [] as Array<{ label: string, count: number, color: string }>

  return [
    { label: 'Text', count: summary.textRegions, color: 'text-cyan-600 dark:text-cyan-400' },
    { label: 'Image', count: summary.imageRegions, color: 'text-orange-600 dark:text-orange-400' },
    { label: 'Line', count: summary.lineDrawings, color: 'text-emerald-600 dark:text-emerald-400' },
    { label: 'Table', count: summary.tableRegions, color: 'text-amber-600 dark:text-amber-400' },
    { label: 'Other', count: summary.otherRegions, color: 'text-neutral-500 dark:text-neutral-400' }
  ].filter(segment => segment.count > 0)
})

const editorStatusLabel = computed(() => {
  if (props.pendingTakeover) {
    return `${props.pendingTakeover.requester.displayName} requested edit access`
  }
  if (props.editor) {
    return props.canEdit ? 'You\'re editing' : `${props.editor.user.displayName} is editing`
  }
  return 'No active editor'
})

const collaborationStatusVisible = computed(() => {
  return props.collaborators.length > 0 || !!props.pendingTakeover || (!!props.editor && !props.canEdit)
})

const collaborationPill = computed(() => {
  if (props.pendingTakeover) {
    return {
      label: 'Request',
      icon: 'i-lucide-arrow-right-left',
      color: 'info' as const
    }
  }

  if (!props.canEdit) {
    return {
      label: 'Locked',
      icon: 'i-lucide-lock',
      color: 'warning' as const
    }
  }

  return {
    label: 'Live',
    icon: 'i-lucide-radio',
    color: 'success' as const
  }
})

const collaborationActor = computed(() => {
  if (props.pendingTakeover) return props.pendingTakeover.requester
  if (props.editor) return props.editor.user
  return null
})

const collaborationActorInitials = computed(() => {
  const actor = collaborationActor.value
  if (!actor) return ''

  return getAvatarInitials({
    name: actor.displayName,
    username: actor.username
  })
})

const annotationModeInfo = computed<{
  label: string
  icon: string
  color: 'info' | 'warning'
  tooltip: string
} | null>(() => {
  if (props.annotationMode === 'DATASET_LINK') {
    return {
      label: 'LINK',
      icon: 'i-lucide-link-2',
      color: 'info',
      tooltip: 'Linked dataset item: annotations are saved to the source project XML.'
    }
  }
  if (props.annotationMode === 'DATASET_COPY') {
    return {
      label: 'COPY (frozen source)',
      icon: 'i-lucide-copy',
      color: 'warning',
      tooltip: 'Dataset copy mode: source project stays frozen; edits are stored in the dataset copy XML.'
    }
  }
  return null
})
</script>

<template>
  <div class="h-8 flex items-stretch border-t border-neutral-200 dark:border-neutral-800 bg-white/95 dark:bg-neutral-900/95 backdrop-blur-sm overflow-hidden">
    <div class="flex items-center justify-between h-full w-full px-3 gap-4 min-w-0">
      <div
        v-if="displayEntity"
        :class="[
          'flex items-center h-full gap-3 transition-colors duration-150 min-w-0',
          isHovered ? 'bg-neutral-100/60 dark:bg-neutral-800/60' : 'bg-transparent'
        ]"
      >
        <div class="flex items-center gap-2 min-w-0">
          <UTooltip :delay-duration="300" :content="{ side: 'top' }" :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }">
            <template #default>
              <div :class="['flex items-center justify-center w-5 h-5 rounded', iconColorClass]">
                <Icon :name="iconName" class="w-3.5 h-3.5" />
              </div>
            </template>
            <template #content>
              <span>{{ displayEntity.regionType }}</span>
            </template>
          </UTooltip>

          <div class="flex items-center gap-1 text-xs min-w-0">
            <span :class="['font-medium', iconColorClass]">{{ displayEntity.regionType }}</span>
            <template v-if="displayEntity.subtype">
              <span class="text-neutral-400 dark:text-neutral-600">/</span>
              <span class="text-neutral-700 dark:text-neutral-300 truncate max-w-[140px]">{{ displayEntity.subtype }}</span>
            </template>
          </div>
        </div>

        <div class="h-4 w-px bg-neutral-300/50 dark:bg-neutral-700/50" />

        <div class="flex items-center gap-1.5 min-w-0">
          <span class="text-[10px] uppercase tracking-wider text-neutral-500 font-medium">ID</span>
          <span class="text-xs font-mono text-neutral-700 dark:text-neutral-300 truncate max-w-40">{{ displayEntity.id }}</span>
        </div>

        <template v-if="displayEntity.label">
          <div class="h-4 w-px bg-neutral-300/50 dark:bg-neutral-700/50" />
          <div class="flex items-center gap-1.5 min-w-0">
            <span class="text-[10px] uppercase tracking-wider text-neutral-500 font-medium">Label</span>
            <span class="text-xs text-neutral-700 dark:text-neutral-300 truncate max-w-40">{{ displayEntity.label }}</span>
          </div>
        </template>

        <template v-if="displayEntity.childCount != null && displayEntity.childCount > 0">
          <div class="h-4 w-px bg-neutral-300/50 dark:bg-neutral-700/50" />
          <span class="text-xs text-neutral-500">
            {{ displayEntity.childCount }} {{ displayEntity.childCount === 1 ? 'child' : 'children' }}
          </span>
        </template>

        <span v-if="isHovered" class="text-[10px] uppercase tracking-wider text-neutral-400 dark:text-neutral-600 font-medium">
          Hovered
        </span>
      </div>

      <div v-else-if="pageSummary" class="flex items-center h-full gap-3 min-w-0">
        <div class="flex items-center gap-1.5 min-w-0">
          <Icon name="lucide:list-ordered" class="w-3.5 h-3.5 text-neutral-500" />
          <span class="text-xs text-neutral-500 dark:text-neutral-400 truncate">
            Page <span class="font-mono text-neutral-700 dark:text-neutral-300">{{ pageSummary.pageId }}</span>
          </span>
        </div>

        <div class="h-4 w-px bg-neutral-300/50 dark:bg-neutral-700/50" />

        <span class="text-xs text-neutral-500 dark:text-neutral-400">
          <span class="font-mono text-neutral-700 dark:text-neutral-300">{{ pageSummary.totalRegions }}</span> regions
        </span>

        <template v-if="pageSummary.variantLabel">
          <div class="h-4 w-px bg-neutral-300/50 dark:bg-neutral-700/50" />
          <div class="flex items-center gap-1.5 min-w-0">
            <Icon name="lucide:layers" class="w-3.5 h-3.5 text-neutral-500" />
            <span class="text-xs text-neutral-500 dark:text-neutral-400 truncate">
              Variant <span class="font-mono text-neutral-700 dark:text-neutral-300">{{ pageSummary.variantLabel }}</span>
            </span>
          </div>
        </template>

        <div class="h-4 w-px bg-neutral-300/50 dark:bg-neutral-700/50" />

        <div class="flex items-center gap-2.5 min-w-0">
          <span v-for="segment in summarySegments" :key="segment.label" class="flex items-center gap-1 text-xs">
            <span :class="['font-mono', segment.color]">{{ segment.count }}</span>
            <span class="text-neutral-500">{{ segment.label }}</span>
          </span>
        </div>
      </div>

      <div v-else class="flex items-center h-full min-w-0">
        <span class="text-xs text-neutral-400 dark:text-neutral-600">Hover or select a region to inspect</span>
      </div>

      <div v-if="annotationModeInfo" class="flex items-center shrink-0 min-w-0">
        <UTooltip
          :delay-duration="200"
          :text="annotationModeInfo.tooltip"
          :content="{ side: 'top' }"
          :ui="{ ...tooltipUi, content: `${tooltipUi.content} px-2 py-1` }"
        >
          <UBadge
            :icon="annotationModeInfo.icon"
            :color="annotationModeInfo.color"
            variant="subtle"
            size="sm"
          >
            {{ annotationModeInfo.label }}
          </UBadge>
        </UTooltip>
      </div>

      <div v-if="collaborationStatusVisible" class="flex items-center shrink-0 min-w-0 justify-end">
        <div class="flex items-center gap-2 min-w-0">
          <UBadge
            :color="collaborationPill.color"
            variant="subtle"
            size="sm"
            class="inline-flex items-center gap-1"
          >
            <Icon :name="collaborationPill.icon" class="h-3.5 w-3.5" />
            <span>{{ collaborationPill.label }}</span>
          </UBadge>

          <div v-if="collaborationActor" class="flex items-center gap-2 min-w-0">
            <div class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-indigo-500 text-[10px] font-semibold text-white">
              {{ collaborationActorInitials }}
            </div>
            <span class="text-xs text-neutral-600 dark:text-neutral-300 truncate">
              {{ editorStatusLabel }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
