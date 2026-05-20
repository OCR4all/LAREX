<script setup lang="ts">
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { useEditorStore } from '@/stores/editor/editor.store'
import { getEditorSession } from '@/session/editor/editor-session'
import { ChangeRegionKindCommand } from '@/commands/editor/change-region-kind-command'
import { commander } from '@/commands'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { buildMergedCustomForAppliedRegionLabel, createCanonicalRegionSignatureFromRuntimeRegion, findRegionLabelDefinitionForRegion } from '@/utils/editor/page-label-mapping'
import { canContainTextLines } from '@/models/editor'
import type { Region, RegionKind } from '@/models/editor'
import type { LabelDefinition } from '@/models/editor/labels'
import type { LineWidthPreset } from '@/stores/editor/types'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'

const editorUiStore = useEditorUiStore()
const editorStore = useEditorStore()
const toast = useToast()
const dialogs = useOverlayDialogs()
const canEditActiveCanvas = computed(() => {
  const canvasId = editorStore.activeCanvasId
  if (!canvasId) return false
  return getEditorSession(canvasId)?.controls.value?.isCanvasEditable.value ?? false
})

const colorPickerOpen = ref(false)
const BACKGROUND_SAVE_DEBOUNCE_MS = 450
let backgroundSaveTimer: ReturnType<typeof setTimeout> | null = null
let backgroundSavePending = false

const lineWidthOptions = [
  { label: 'Thin', value: 'thin' },
  { label: 'Light', value: 'light' },
  { label: 'Normal', value: 'normal' },
  { label: 'Medium', value: 'medium' },
  { label: 'Bold', value: 'bold' },
  { label: 'Extra Bold', value: 'extraBold' }
]

type ConflictItem = {
  key: string
  kind: RegionKind
  subtype: string | null
  count: number
  regionIds: string[]
}

const conflictItems = ref<ConflictItem[]>([])
const ignoredConflictKeys = ref<Set<string>>(new Set())
const replacementByKey = ref<Record<string, string>>({})

const labelOptions = computed(() => {
  const labelSet = editorStore.labelSet
  if (!labelSet) return []
  return labelSet.labels
    .filter(label => label.scope === 'region')
    .map(label => ({ label: label.name, value: label.id }))
})

const labelDefinitionsById = computed(() => {
  const labelSet = editorStore.labelSet
  const map = new Map<string, LabelDefinition>()
  if (!labelSet) return map
  for (const label of labelSet.labels) {
    map.set(label.id, label)
  }
  return map
})

const buildKeyFromRegion = (region: { kind: RegionKind, type?: string | null, custom?: string | null }) => {
  return createCanonicalRegionSignatureFromRuntimeRegion(region)
}

type RuntimeRegionNode = Pick<Region, 'id' | 'kind' | 'type' | 'custom' | 'regions'> & {
  regions?: RuntimeRegionNode[]
}

const collectRegions = (regions: RuntimeRegionNode[], out: RuntimeRegionNode[]) => {
  for (const region of regions) {
    out.push(region)
    if (region.regions && region.regions.length > 0) {
      collectRegions(region.regions, out)
    }
  }
}

const scanConflicts = () => {
  const canvasId = editorStore.activeCanvasId
  if (!canvasId) return
  const session = getEditorSession(canvasId)
  const labelSet = editorStore.labelSet
  const regions = session?.document.value?.page?.regions
  if (!labelSet || !regions) {
    conflictItems.value = []
    return
  }

  const allRegions: RuntimeRegionNode[] = []
  collectRegions(regions as RuntimeRegionNode[], allRegions)
  const conflicts = new Map<string, ConflictItem>()

  for (const region of allRegions) {
    const matchedLabel = findRegionLabelDefinitionForRegion(labelSet.labels, region)
    if (matchedLabel) continue
    const key = buildKeyFromRegion(region) ?? `${region.kind}:${region.type ?? ''}`
    if (ignoredConflictKeys.value.has(key)) continue
    const existing = conflicts.get(key)
    if (existing) {
      existing.count += 1
      existing.regionIds.push(region.id)
    } else {
      conflicts.set(key, {
        key,
        kind: region.kind,
        subtype: region.type ?? null,
        count: 1,
        regionIds: [region.id]
      })
    }
  }

  conflictItems.value = Array.from(conflicts.values())
  if (conflictItems.value.length > 0) {
    toast.add({ title: `Found ${conflictItems.value.length} label conflict${conflictItems.value.length === 1 ? '' : 's'}`, color: 'warning' })
  }
}

const ignoreConflict = (key: string) => {
  const next = new Set(ignoredConflictKeys.value)
  next.add(key)
  ignoredConflictKeys.value = next
  conflictItems.value = conflictItems.value.filter(item => item.key !== key)
}

const applyReplacement = async (conflict: ConflictItem) => {
  if (!canEditActiveCanvas.value) return

  const labelId = replacementByKey.value[conflict.key]
  if (!labelId) {
    toast.add({ title: 'Select a replacement label', color: 'warning' })
    return
  }
  const labelDef = labelDefinitionsById.value.get(labelId)
  if (!labelDef) return
  const mapping = labelDef.mapping?.pageXml
  if (!mapping?.regionType) return

  const newKind = mapping.regionType as RegionKind
  const newSubtype = newKind === 'TextRegion'
    ? (mapping.textType === 'custom' ? 'other' : (mapping.textType || undefined))
    : (mapping.customSubType || undefined)

  const canvasId = editorStore.activeCanvasId
  if (!canvasId) return
  const session = getEditorSession(canvasId)
  if (!session) return
  const document = session.document.value
  if (!document) return
  const commandCtx = { canvasId, session }

  let linesToRemove = 0
  for (const regionId of conflict.regionIds) {
    const hit = findRegionRecursive(document.page.regions, regionId)
    if (!hit) continue
    if (hit.region.kind === 'TextRegion' && !canContainTextLines(newKind)) {
      linesToRemove += hit.region.textLines?.length ?? 0
    }
  }

  if (linesToRemove > 0) {
    const confirmed = await dialogs.confirm({
      title: 'Replace Labels',
      message: `Replacing to ${newKind} will remove ${linesToRemove} text line${linesToRemove === 1 ? '' : 's'}. Continue?`,
      confirmLabel: 'Continue',
      cancelLabel: 'Cancel',
      confirmColor: 'warning'
    })
    if (!confirmed) return
  }

  for (const regionId of conflict.regionIds) {
    const hit = findRegionRecursive(document.page.regions, regionId)
    const currentCustom = hit?.region?.custom
    const newCustom = buildMergedCustomForAppliedRegionLabel(currentCustom, labelDef)
    const command = new ChangeRegionKindCommand({
      regionId,
      newKind,
      newSubtype,
      updateCustom: true,
      newCustom
    })
    commander.execute(command, commandCtx)
  }

  scanConflicts()
  toast.add({ title: 'Labels replaced', color: 'success' })
}

function showSavedToast() {
  toast.add({ title: 'Setting saved', icon: 'i-lucide-check', color: 'success' })
}

function toggleSetting(toggleFn: () => void) {
  toggleFn()
  showSavedToast()
}

function onColorChange(color: string) {
  editorUiStore.setBackgroundColor(color, { persist: false })
  queueBackgroundSave()
}

function onOpacityChange(opacity: number) {
  editorUiStore.setBackgroundOpacity(opacity, { persist: false })
  queueBackgroundSave()
}

function onLineWidthChange(value: string) {
  const presets: LineWidthPreset[] = ['thin', 'light', 'normal', 'medium', 'bold', 'extraBold']
  if (!presets.includes(value as LineWidthPreset)) return
  editorUiStore.setDefaultLineWidth(value as LineWidthPreset)
  showSavedToast()
}

function clearBackgroundSaveTimer() {
  if (!backgroundSaveTimer) return
  clearTimeout(backgroundSaveTimer)
  backgroundSaveTimer = null
}

async function persistBackgroundSettings() {
  if (!backgroundSavePending) return
  backgroundSavePending = false
  const saved = await editorUiStore.saveBackgroundAppearance()
  if (saved) showSavedToast()
}

function queueBackgroundSave() {
  backgroundSavePending = true
  clearBackgroundSaveTimer()
  backgroundSaveTimer = setTimeout(() => {
    backgroundSaveTimer = null
    void persistBackgroundSettings()
  }, BACKGROUND_SAVE_DEBOUNCE_MS)
}

watch(colorPickerOpen, (isOpen, wasOpen) => {
  if (!wasOpen || isOpen) return
  clearBackgroundSaveTimer()
  void persistBackgroundSettings()
})

onBeforeUnmount(() => {
  clearBackgroundSaveTimer()
  void persistBackgroundSettings()
})
</script>

<template>
  <div class="p-3 space-y-3">
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm">Comments Overlay</span>
        <USwitch
          :model-value="editorUiStore.commentsOverlay.visible"
          @update:model-value="toggleSetting(() => editorUiStore.toggleCommentsOverlay())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Image Bounds</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.constrainToImage"
          @update:model-value="toggleSetting(() => editorUiStore.toggleConstrainToImage())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Parent Bounds</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.constrainToParent"
          @update:model-value="toggleSetting(() => editorUiStore.toggleConstrainToParent())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Auto-Select</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.autoSelect"
          @update:model-value="toggleSetting(() => editorUiStore.toggleAutoSelect())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Prevent Overlap</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.preventOverlapOnCreate"
          @update:model-value="toggleSetting(() => editorUiStore.togglePreventOverlapOnCreate())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Move with Children</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.moveWithChildren"
          @update:model-value="toggleSetting(() => editorUiStore.toggleMoveWithChildren())"
        />
      </div>
    </div>

    <USeparator />

    <div class="flex items-center justify-between">
      <span class="text-sm">Line Width</span>
      <USelect
        :model-value="editorUiStore.globalSettings.defaultLineWidth"
        :items="lineWidthOptions"
        class="w-32"
        @update:model-value="onLineWidthChange"
      />
    </div>

    <USeparator />

    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm">Label Set Conflicts</span>
        <UButton
          size="xs"
          color="primary"
          variant="soft"
          :disabled="!editorStore.labelSet || !editorStore.activeCanvasId || !canEditActiveCanvas"
          @click="scanConflicts"
        >
          Scan
        </UButton>
      </div>
      <div v-if="conflictItems.length === 0" class="text-xs text-muted">
        No conflicts detected
      </div>
      <div v-for="conflict in conflictItems" :key="conflict.key" class="rounded-sm border border-neutral-200 dark:border-neutral-700 p-2 space-y-2">
        <div class="flex items-center justify-between gap-2">
          <div class="text-xs font-semibold truncate">
            {{ conflict.kind }}<span v-if="conflict.subtype">: {{ conflict.subtype }}</span>
          </div>
          <UBadge size="xs" color="warning" variant="soft">
            {{ conflict.count }}
          </UBadge>
        </div>
        <USelect
          v-model="replacementByKey[conflict.key]"
          :items="labelOptions"
          placeholder="Replace with..."
          class="w-full"
          size="xs"
        />
        <div class="flex items-center gap-2">
          <UButton
            size="xs"
            color="primary"
            variant="soft"
            :disabled="!canEditActiveCanvas"
            @click="applyReplacement(conflict)"
          >
            Replace
          </UButton>
          <UButton
            size="xs"
            color="neutral"
            variant="ghost"
            @click="ignoreConflict(conflict.key)"
          >
            Ignore
          </UButton>
        </div>
      </div>
    </div>

    <USeparator />

    <div class="flex items-center justify-between">
      <span class="text-sm">Background Color</span>
      <UPopover v-model:open="colorPickerOpen">
        <button
          class="w-7 h-7 rounded-sm border border-default cursor-pointer checkerboard-bg"
          title="Pick background color"
        >
          <div
            class="w-full h-full rounded-sm"
            :style="{ backgroundColor: editorUiStore.backgroundColor, opacity: editorUiStore.backgroundOpacity }"
          />
        </button>
        <template #content>
          <EditorBackgroundColorPicker
            :model-value="editorUiStore.backgroundColor"
            :opacity="editorUiStore.backgroundOpacity"
            @update:model-value="onColorChange"
            @update:opacity="onOpacityChange"
          />
        </template>
      </UPopover>
    </div>
  </div>
</template>

<style scoped>
.checkerboard-bg {
  background-image: linear-gradient(45deg, #ccc 25%, transparent 25%),
    linear-gradient(-45deg, #ccc 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #ccc 75%),
    linear-gradient(-45deg, transparent 75%, #ccc 75%);
  background-size: 8px 8px;
  background-position: 0 0, 0 4px, 4px -4px, -4px 0px;
}
</style>
