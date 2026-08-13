<script setup lang="ts">
import type { LabelDefinition } from '@/models/editor/labels'
import { getEditorSession } from '@/session/editor/editor-session'
import { useEditorDocumentStore } from '@/stores/editor/editor.document.store'
import { createCanonicalRegionMappingSignatureFromLabel } from '@/utils/editor/page-label-mapping'
import { findRegionLabelConflicts, type RegionLabelConflictGroup } from '@/utils/editor/region-label-conflicts'
import {
  countTextLinesRemovedForLabelConflictReplacements,
  createRegionLabelConflictResolutionPlan,
  type RegionLabelConflictReplacements
} from '@/utils/editor/region-label-conflict-resolution'
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'
import { useRegionLabelConflicts } from '@/composables/editor/use-region-label-conflicts'

useBlockEditorCanvasInteractions()

const props = defineProps<{
  canvasId: string
  projectId: string
}>()

const emit = defineEmits<{ close: [applied: boolean] }>()

const documentStore = useEditorDocumentStore()
const toast = useToast()
const dialogs = useOverlayDialogs()
const replacements = ref<Record<string, string>>({})
const isApplying = ref(false)

const session = computed(() => getEditorSession(props.canvasId))
const document = computed(() => session.value?.document.value ?? null)
const labelSet = computed(() => documentStore.labelSetByProjectId[props.projectId] ?? null)
const regions = computed(() => document.value?.page.regions ?? [])
const { groups, totalRegions } = useRegionLabelConflicts(regions, labelSet)
const canApply = computed(() => session.value?.controls.value?.isCanvasEditable.value ?? false)

const labelDefinitionsById = computed(() => {
  const definitions = new Map<string, LabelDefinition>()
  for (const label of labelSet.value?.labels ?? []) {
    if (label.mapping?.pageXml?.regionType) {
      definitions.set(label.id, label)
    }
  }
  return definitions
})

const labelOptions = computed(() => [...labelDefinitionsById.value.values()].map(label => ({
  label: label.name,
  value: label.id,
  color: label.color,
  description: label.description
})))

const allGroupsMapped = computed(() => groups.value.length > 0 && groups.value.every((group) => {
  const labelId = replacements.value[group.key]
  return Boolean(labelId && labelDefinitionsById.value.has(labelId))
}))

function selectedReplacementDefinitions(): RegionLabelConflictReplacements {
  const selected: Record<string, LabelDefinition | undefined> = {}
  for (const group of groups.value) {
    selected[group.key] = labelDefinitionsById.value.get(replacements.value[group.key] ?? '')
  }
  return selected
}

function replacementSnapshot(conflictGroups: RegionLabelConflictGroup[]): string[] {
  return conflictGroups.map((group) => {
    const label = labelDefinitionsById.value.get(replacements.value[group.key] ?? '')
    const signature = label ? createCanonicalRegionMappingSignatureFromLabel(label) : null
    return `${group.key}|${label?.id ?? ''}|${signature ?? ''}`
  })
}

const destructiveTextLineCount = computed(() => {
  const currentDocument = document.value
  if (!currentDocument) return 0
  return countTextLinesRemovedForLabelConflictReplacements(
    currentDocument.page.regions,
    groups.value,
    selectedReplacementDefinitions()
  )
})

function close(): void {
  emit('close', false)
}

function sameConflictGroups(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((key, index) => key === right[index])
}

async function applyReplacements(): Promise<void> {
  if (isApplying.value || !canApply.value || !allGroupsMapped.value) return

  const currentSession = session.value
  const currentDocument = document.value
  const currentLabelSet = labelSet.value
  const currentControls = currentSession?.controls.value
  if (!currentSession || !currentControls || !currentDocument || !currentLabelSet) return

  const refreshed = findRegionLabelConflicts(currentDocument.page.regions, currentLabelSet)
  if (!sameConflictGroups(refreshed.groups.map(group => group.key), groups.value.map(group => group.key))) {
    toast.add({
      title: 'Label conflicts changed',
      description: 'Review the updated conflicts before applying replacements.',
      color: 'warning'
    })
    return
  }
  const initialReplacementSnapshot = replacementSnapshot(refreshed.groups)

  let plan: ReturnType<typeof createRegionLabelConflictResolutionPlan>
  try {
    plan = createRegionLabelConflictResolutionPlan(
      currentDocument.page.regions,
      refreshed.groups,
      selectedReplacementDefinitions()
    )
  } catch (error: unknown) {
    toast.add({
      title: 'Could not prepare label replacements',
      description: error instanceof Error ? error.message : 'Review the selected replacements.',
      color: 'error'
    })
    return
  }

  if (plan.textLinesToRemove > 0) {
    const confirmed = await dialogs.confirm({
      title: 'Replace labels and remove text lines?',
      message: `These replacements convert text regions to non-text regions and will remove ${plan.textLinesToRemove} text line${plan.textLinesToRemove === 1 ? '' : 's'}. This can be undone before saving.`,
      confirmLabel: 'Apply replacements',
      cancelLabel: 'Cancel',
      confirmColor: 'warning'
    })
    if (!confirmed) return
  }

  const latestDocument = document.value
  const latestLabelSet = labelSet.value
  if (!latestDocument || !latestLabelSet) return
  const latestConflicts = findRegionLabelConflicts(latestDocument.page.regions, latestLabelSet)
  if (!sameConflictGroups(latestConflicts.groups.map(group => group.key), refreshed.groups.map(group => group.key))) {
    toast.add({
      title: 'Label conflicts changed',
      description: 'Review the updated conflicts before applying replacements.',
      color: 'warning'
    })
    return
  }
  if (!sameConflictGroups(replacementSnapshot(latestConflicts.groups), initialReplacementSnapshot)) {
    toast.add({
      title: 'Replacement labels changed',
      description: 'Review the current label mappings before applying replacements.',
      color: 'warning'
    })
    return
  }

  try {
    plan = createRegionLabelConflictResolutionPlan(
      latestDocument.page.regions,
      latestConflicts.groups,
      selectedReplacementDefinitions()
    )
  } catch (error: unknown) {
    toast.add({
      title: 'Could not prepare label replacements',
      description: error instanceof Error ? error.message : 'Review the selected replacements.',
      color: 'error'
    })
    return
  }

  isApplying.value = true
  try {
    if (!currentControls.isCanvasEditable.value) {
      throw new Error('This page is no longer editable.')
    }
    currentControls.commander.execute(
      plan.command,
      { canvasId: props.canvasId, session: currentSession }
    )
    toast.add({
      title: 'Label conflicts resolved',
      description: `${plan.affectedRegionCount} region${plan.affectedRegionCount === 1 ? '' : 's'} updated. Save the page to persist the changes.`,
      color: 'success',
      icon: 'i-lucide-check'
    })
    emit('close', true)
  } catch (error: unknown) {
    toast.add({
      title: 'Could not replace labels',
      description: error instanceof Error ? error.message : 'The batch operation failed.',
      color: 'error'
    })
  } finally {
    isApplying.value = false
  }
}

watch([groups, labelDefinitionsById], () => {
  const activeKeys = new Set(groups.value.map(group => group.key))
  const availableLabelIds = new Set(labelDefinitionsById.value.keys())
  const next: Record<string, string> = {}

  for (const [key, labelId] of Object.entries(replacements.value)) {
    if (activeKeys.has(key) && availableLabelIds.has(labelId)) {
      next[key] = labelId
    }
  }
  replacements.value = next
})
</script>

<template>
  <UiResponsiveSlideover
    inset
    :close="{ onClick: close }"
    :ui="{ content: 'max-w-none xl:max-w-xl' }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Resolve Label Conflicts"
        icon="i-lucide-tags"
        description="Map every unmatched PAGE region label to a label from this project's active label set."
      />
    </template>

    <template #body>
      <div class="space-y-5">
        <UAlert
          v-if="!canApply"
          color="warning"
          variant="subtle"
          icon="i-lucide-lock"
          title="Read-only page"
          description="You can review these conflicts, but you need edit access to apply replacements."
        />

        <div class="flex items-center justify-between gap-3 rounded-lg bg-elevated px-4 py-3 ring ring-default">
          <div>
            <p class="text-sm font-medium text-highlighted">
              {{ totalRegions }} affected region{{ totalRegions === 1 ? '' : 's' }}
            </p>
            <p class="mt-0.5 text-xs text-muted">
              {{ groups.length }} distinct unmatched mapping{{ groups.length === 1 ? '' : 's' }} in {{ labelSet?.name ?? 'the active label set' }}
            </p>
          </div>
          <UBadge color="error" variant="subtle">
            {{ groups.length }} to map
          </UBadge>
        </div>

        <div class="space-y-3">
          <UCard
            v-for="group in groups"
            :key="group.key"
            variant="subtle"
            :ui="{ body: 'space-y-3' }"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold text-highlighted">
                  {{ group.displayName }}
                </p>
                <p class="mt-0.5 text-xs text-muted">
                  {{ group.mappingDescription }}
                </p>
              </div>
              <UBadge color="error" variant="soft" size="sm">
                {{ group.count }} region{{ group.count === 1 ? '' : 's' }}
              </UBadge>
            </div>

            <UFormField label="Replacement label" required>
              <USelectMenu
                v-model="replacements[group.key]"
                :items="labelOptions"
                value-key="value"
                label-key="label"
                placeholder="Select a replacement"
                class="w-full"
                :disabled="isApplying || !canApply"
              >
                <template #item-leading="{ item }">
                  <span
                    class="size-3 shrink-0 rounded-full ring ring-default"
                    :style="{ backgroundColor: item.color }"
                  />
                </template>
              </USelectMenu>
            </UFormField>
          </UCard>
        </div>

        <UAlert
          v-if="destructiveTextLineCount > 0"
          color="warning"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          title="Some replacements change the region kind"
          :description="`${destructiveTextLineCount} text line${destructiveTextLineCount === 1 ? '' : 's'} will be removed after confirmation.`"
        />
      </div>
    </template>

    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          :disabled="isApplying"
          @click="close"
        >
          Cancel
        </UButton>
        <UButton
          color="error"
          icon="i-lucide-wand-sparkles"
          :loading="isApplying"
          :disabled="!canApply || !allGroupsMapped || groups.length === 0"
          @click="applyReplacements"
        >
          Apply replacements
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
