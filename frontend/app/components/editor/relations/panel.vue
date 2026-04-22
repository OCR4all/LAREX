<script setup lang="ts">
import type { Commander } from '@/commands/editor/commander'
import type { CommandContext } from '@/commands/editor/types'
import type { Page } from '@/models/editor'
import type { PcGts } from '@/models/editor/document'
import { CreateRelationCommand, DeleteRelationCommand, UpdateRelationCommand } from '@/commands'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { useEditorStore } from '@/stores/editor/editor.store'
import { getEditorSession } from '@/session/editor/editor-session'
import {
  createEmptyRelationDraft,
  getRelationDisplayLabel,
  getRelationTypeOptions,
  normalizeRelation,
  normalizeOptionalRelationString,
  relationToDraft,
  type RelationDraftState
} from '@/utils/editor/relations'
import MetadataLabelGroupsForm from '@/components/editor/sidebar/metadata/label-groups-form.vue'

interface RegionOption {
  value: string
  label: string
}

interface Props {
  document?: PcGts | null
  page: Page | null
  commander: Commander | null
  regions: RegionOption[]
}

const props = defineProps<Props>()

const editorUiStore = useEditorUiStore()
const editorStore = useEditorStore()
const toast = useToast()

const currentDocument = computed(() => props.document ?? null)
const relations = computed(() => currentDocument.value?.page?.relations ?? props.page?.relations ?? [])
const selectedRelationId = computed(() => editorUiStore.relationsEditor.selectedRelationId)
const selectedRelation = computed(() => relations.value.find(relation => relation.id === selectedRelationId.value) ?? null)
const selectedDraft = ref<RelationDraftState>(createEmptyRelationDraft())

const relationsOverlayVisible = computed(() => editorUiStore.relationsOverlay.visible)
const relationsOverlaySettings = computed(() => editorUiStore.relationsOverlay)
const draft = computed(() => editorUiStore.relationsEditor.draft)
const pickerMode = computed(() => editorUiStore.relationsEditor.pickerMode)

const relationTypeOptions = computed(() => getRelationTypeOptions(selectedDraft.value.type))
const draftTypeOptions = computed(() => getRelationTypeOptions(draft.value.type))
const isCreateSectionOpen = ref(false)

const canCreateDraft = computed(() =>
  Boolean(
    normalizeOptionalRelationString(draft.value.sourceRegionRef)
    && normalizeOptionalRelationString(draft.value.targetRegionRef)
  )
)

const showCreateSection = computed(() => isCreateSectionOpen.value || !selectedRelation.value)
const showEditSection = computed(() => Boolean(selectedRelation.value) && !isCreateSectionOpen.value)

const isPickingCreateSource = computed(() => pickerMode.value === 'pick-source')
const isPickingCreateTarget = computed(() => pickerMode.value === 'pick-target')
const isRepickingSource = computed(() => pickerMode.value === 'repick-source')
const isRepickingTarget = computed(() => pickerMode.value === 'repick-target')

function getCommandContext(): CommandContext | undefined {
  const canvasId = editorStore.activeCanvasId
  if (!canvasId) return undefined
  const session = getEditorSession(canvasId)
  return session ? { canvasId, session } : undefined
}

function showSuccessToast(title: string) {
  toast.add({ title, color: 'success', icon: 'i-lucide-check' })
}

function setSelectedRelation(relationId: string | null) {
  isCreateSectionOpen.value = false
  editorUiStore.setSelectedRelationId(relationId)
}

function toggleOverlay(): void {
  editorUiStore.toggleRelationsOverlay()
}

function toggleOverlayLabels(): void {
  editorUiStore.updateRelationsOverlaySettings({
    showLabels: !relationsOverlaySettings.value.showLabels
  })
}

function updateDraftField<K extends keyof RelationDraftState>(key: K, value: RelationDraftState[K]) {
  editorUiStore.updateRelationDraft({ [key]: value } as Partial<RelationDraftState>)
}

function updateSelectedDraftField<K extends keyof RelationDraftState>(key: K, value: RelationDraftState[K]) {
  selectedDraft.value = {
    ...selectedDraft.value,
    [key]: value
  }
}

function createRelationFromDraft(): void {
  if (!props.commander) return

  const ctx = getCommandContext()
  const result = props.commander.execute(
    new CreateRelationCommand({
      relation: normalizeRelation(draft.value)
    }),
    ctx
  )

  if (!result?.id) return

  isCreateSectionOpen.value = false
  editorUiStore.setSelectedRelationId(result.id)
  editorUiStore.resetRelationDraft()
  editorUiStore.cancelRelationPicking()
  showSuccessToast('Relation created')
}

function saveSelectedRelation(): void {
  if (!props.commander || !selectedRelation.value?.id) return

  const ctx = getCommandContext()
  const result = props.commander.execute(
    new UpdateRelationCommand({
      relationId: selectedRelation.value.id,
      relation: normalizeRelation({
        ...selectedRelation.value,
        ...selectedDraft.value
      })
    }),
    ctx
  )

  if (result?.id) {
    editorUiStore.setSelectedRelationId(result.id)
    showSuccessToast('Relation updated')
  }
}

function deleteSelectedRelation(): void {
  if (!props.commander || !selectedRelation.value?.id) return

  const deletedId = selectedRelation.value.id
  const ctx = getCommandContext()
  props.commander.execute(
    new DeleteRelationCommand({
      relationId: deletedId
    }),
    ctx
  )

  if (editorUiStore.relationsEditor.selectedRelationId === deletedId) {
    const nextRelation = relations.value.find(relation => relation.id !== deletedId)
    editorUiStore.setSelectedRelationId(nextRelation?.id ?? null)
  }
  editorUiStore.cancelRelationPicking()
  showSuccessToast('Relation deleted')
}

function startCreatePicking(): void {
  isCreateSectionOpen.value = true
  editorUiStore.setSelectedRelationId(null)
  editorUiStore.beginRelationCreation()
}

function openCreateSection(): void {
  isCreateSectionOpen.value = true
  editorUiStore.setSelectedRelationId(null)
}

function startRepickSource(): void {
  if (!selectedRelation.value?.id) return
  editorUiStore.beginRelationRepickSource(selectedRelation.value.id)
}

function startRepickTarget(): void {
  if (!selectedRelation.value?.id) return
  editorUiStore.beginRelationRepickTarget(selectedRelation.value.id)
}

function cancelPicking(): void {
  editorUiStore.cancelRelationPicking()
  if (relations.value.length > 0 && !selectedRelation.value && !canCreateDraft.value) {
    isCreateSectionOpen.value = false
  }
}

function getRegionLabel(regionId?: string): string {
  if (!regionId) return 'Not set'
  return props.regions.find(region => region.value === regionId)?.label ?? regionId
}

watch(relations, (nextRelations) => {
  if (nextRelations.length === 0) {
    isCreateSectionOpen.value = true
  }

  if (nextRelations.length === 0) {
    editorUiStore.clearRelationSelection()
    return
  }

  if (!editorUiStore.relationsEditor.selectedRelationId) {
    editorUiStore.setSelectedRelationId(nextRelations[0]?.id ?? null)
    return
  }

  const selectedStillExists = nextRelations.some(relation => relation.id === editorUiStore.relationsEditor.selectedRelationId)
  if (!selectedStillExists) {
    editorUiStore.setSelectedRelationId(nextRelations[0]?.id ?? null)
  }
}, { immediate: true, deep: true })

watch(selectedRelation, (relation) => {
  selectedDraft.value = relationToDraft(relation)
  if (relation) {
    isCreateSectionOpen.value = false
  }
}, { immediate: true, deep: true })
</script>

<template>
  <div class="h-full flex flex-col bg-elevated">
    <div class="toolbar flex items-center gap-2 p-2 border-b border-default bg-muted/50">
      <UButton
        size="xs"
        :variant="relationsOverlayVisible ? 'solid' : 'ghost'"
        :color="relationsOverlayVisible ? 'primary' : 'neutral'"
        title="Show or hide relation overlay in the editor"
        @click="toggleOverlay"
      >
        <Icon :name="relationsOverlayVisible ? 'i-lucide-eye' : 'i-lucide-eye-off'" class="w-4 h-4" />
      </UButton>

      <UButton
        size="xs"
        :variant="relationsOverlaySettings.showLabels ? 'solid' : 'ghost'"
        :color="relationsOverlaySettings.showLabels ? 'primary' : 'neutral'"
        :disabled="!relationsOverlayVisible"
        title="Show or hide relation labels on the overlay"
        @click="toggleOverlayLabels"
      >
        <Icon name="i-lucide-tag" class="w-4 h-4" />
      </UButton>

      <div class="flex-1" />

      <span class="text-xs text-muted">{{ relations.length }} relation<span v-if="relations.length !== 1">s</span></span>
    </div>

    <div class="flex-1 overflow-y-auto p-3 space-y-4">
      <section class="space-y-2">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-semibold">
              Relations
            </p>
            <p class="text-xs text-muted">
              Existing PAGE relations on this page
            </p>
          </div>
          <UButton
            size="xs"
            variant="soft"
            icon="i-lucide-plus"
            @click="openCreateSection"
          >
            New Relation
          </UButton>
        </div>

        <div v-if="relations.length > 0" class="space-y-2">
          <button
            v-for="relation in relations"
            :key="relation.id ?? `${relation.sourceRegionRef}-${relation.targetRegionRef}`"
            type="button"
            class="w-full rounded-md border px-3 py-2 text-left transition"
            :class="relation.id === selectedRelationId ? 'border-primary bg-primary/10' : 'border-default bg-default hover:bg-accented/50'"
            @click="setSelectedRelation(relation.id ?? null)"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="text-sm font-medium truncate">{{ getRelationDisplayLabel(relation) }}</span>
              <UBadge color="neutral" variant="subtle">
                {{ relation.type ?? 'link' }}
              </UBadge>
            </div>
            <p class="mt-1 text-xs text-muted truncate">
              {{ getRegionLabel(relation.sourceRegionRef) }} -> {{ getRegionLabel(relation.targetRegionRef) }}
            </p>
          </button>
        </div>

        <p v-else class="text-sm text-muted rounded-md border border-dashed border-default p-3">
          No relations on this page yet.
        </p>
      </section>

      <section v-if="showCreateSection" class="space-y-3 rounded-md border border-default p-3">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-semibold">
              Create Relation
            </p>
            <p class="text-xs text-muted">
              Prepare the draft, then pick source and target on the canvas.
            </p>
          </div>
          <div class="flex items-center gap-2">
            <UButton
              v-if="relations.length > 0 && pickerMode === 'idle'"
              size="xs"
              variant="ghost"
              color="neutral"
              icon="i-lucide-arrow-left"
              @click="isCreateSectionOpen = false"
            >
              Back
            </UButton>
            <UButton
              v-if="pickerMode !== 'idle'"
              size="xs"
              variant="ghost"
              color="neutral"
              icon="i-lucide-x"
              @click="cancelPicking"
            >
              Cancel
            </UButton>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-2 md:grid-cols-2">
          <div
            class="rounded-md border px-3 py-2"
            :class="draft.sourceRegionRef ? 'border-sky-300 bg-sky-50/60' : 'border-dashed border-default bg-default'"
          >
            <p class="text-[11px] font-semibold uppercase tracking-wide text-muted">
              Source
            </p>
            <p class="text-sm" :class="draft.sourceRegionRef ? 'text-default' : 'text-muted'">
              {{ draft.sourceRegionRef ? getRegionLabel(draft.sourceRegionRef) : 'Not picked yet' }}
            </p>
          </div>

          <div
            class="rounded-md border px-3 py-2"
            :class="draft.targetRegionRef ? 'border-sky-300 bg-sky-50/60' : 'border-dashed border-default bg-default'"
          >
            <p class="text-[11px] font-semibold uppercase tracking-wide text-muted">
              Target
            </p>
            <p class="text-sm" :class="draft.targetRegionRef ? 'text-default' : 'text-muted'">
              {{ draft.targetRegionRef ? getRegionLabel(draft.targetRegionRef) : 'Not picked yet' }}
            </p>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
          <UFormField label="Relation ID">
            <UInput :model-value="draft.id" placeholder="Auto-generate if blank" @update:model-value="value => updateDraftField('id', value ?? '')" />
          </UFormField>

          <UFormField label="Type">
            <USelectMenu
              :model-value="draft.type"
              :items="draftTypeOptions"
              value-key="value"
              @update:model-value="value => updateDraftField('type', String(value ?? 'link'))"
            />
          </UFormField>
        </div>

        <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
          <UFormField label="Source Region">
            <USelectMenu
              :model-value="draft.sourceRegionRef"
              :items="regions"
              value-key="value"
              searchable
              @update:model-value="value => updateDraftField('sourceRegionRef', String(value ?? ''))"
            />
          </UFormField>

          <UFormField label="Target Region">
            <USelectMenu
              :model-value="draft.targetRegionRef"
              :items="regions"
              value-key="value"
              searchable
              @update:model-value="value => updateDraftField('targetRegionRef', String(value ?? ''))"
            />
          </UFormField>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <UButton
            size="xs"
            variant="soft"
            icon="i-lucide-crosshair"
            @click="startCreatePicking"
          >
            {{ isPickingCreateSource ? 'Pick Source' : isPickingCreateTarget ? 'Pick Target' : 'Pick On Canvas' }}
          </UButton>
          <UButton
            size="xs"
            color="primary"
            :disabled="!canCreateDraft"
            @click="createRelationFromDraft"
          >
            Create Now
          </UButton>
          <UButton
            size="xs"
            variant="ghost"
            color="neutral"
            @click="editorUiStore.resetRelationDraft()"
          >
            Reset Draft
          </UButton>
        </div>

        <div v-if="isPickingCreateSource || isPickingCreateTarget" class="rounded-md border border-sky-200 bg-sky-50/70 px-3 py-2 text-xs text-sky-800">
          <p v-if="isPickingCreateSource">
            Click a region on the canvas to pick the source endpoint.
          </p>
          <p v-else>
            Source picked as <strong>{{ getRegionLabel(draft.sourceRegionRef) }}</strong>. Click another region to pick the target and create the relation.
          </p>
        </div>

        <UFormField label="Custom">
          <UTextarea :model-value="draft.custom" :rows="2" @update:model-value="value => updateDraftField('custom', value ?? '')" />
        </UFormField>

        <UFormField label="Comments">
          <UTextarea :model-value="draft.comments" :rows="2" @update:model-value="value => updateDraftField('comments', value ?? '')" />
        </UFormField>

        <MetadataLabelGroupsForm
          :model-value="draft.labels"
          @update:model-value="value => updateDraftField('labels', value ?? [])"
        />
      </section>

      <section v-if="showEditSection && selectedRelation" class="space-y-3 rounded-md border border-default p-3">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-semibold">
              Edit Selected Relation
            </p>
            <p class="text-xs text-muted">
              {{ getRelationDisplayLabel(selectedRelation) }}
            </p>
          </div>
          <div class="flex items-center gap-2">
            <UButton
              size="xs"
              variant="soft"
              icon="i-lucide-plus"
              @click="openCreateSection"
            >
              New Relation
            </UButton>
            <UButton
              size="xs"
              color="error"
              variant="ghost"
              icon="i-lucide-trash-2"
              @click="deleteSelectedRelation"
            >
              Delete
            </UButton>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
          <UFormField label="Relation ID">
            <UInput :model-value="selectedDraft.id" @update:model-value="value => updateSelectedDraftField('id', value ?? '')" />
          </UFormField>

          <UFormField label="Type">
            <USelectMenu
              :model-value="selectedDraft.type"
              :items="relationTypeOptions"
              value-key="value"
              @update:model-value="value => updateSelectedDraftField('type', String(value ?? 'link'))"
            />
          </UFormField>
        </div>

        <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
          <UFormField label="Source Region">
            <USelectMenu
              :model-value="selectedDraft.sourceRegionRef"
              :items="regions"
              value-key="value"
              searchable
              @update:model-value="value => updateSelectedDraftField('sourceRegionRef', String(value ?? ''))"
            />
          </UFormField>

          <UFormField label="Target Region">
            <USelectMenu
              :model-value="selectedDraft.targetRegionRef"
              :items="regions"
              value-key="value"
              searchable
              @update:model-value="value => updateSelectedDraftField('targetRegionRef', String(value ?? ''))"
            />
          </UFormField>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <UButton
            size="xs"
            variant="soft"
            icon="i-lucide-crosshair"
            @click="startRepickSource"
          >
            Repick Source
          </UButton>
          <UButton
            size="xs"
            variant="soft"
            icon="i-lucide-crosshair"
            @click="startRepickTarget"
          >
            Repick Target
          </UButton>
          <UButton size="xs" color="primary" @click="saveSelectedRelation">
            Save Changes
          </UButton>
        </div>

        <p v-if="isRepickingSource" class="text-xs text-primary">
          Click the new source region on the canvas.
        </p>
        <p v-else-if="isRepickingTarget" class="text-xs text-primary">
          Click the new target region on the canvas.
        </p>

        <UFormField label="Custom">
          <UTextarea :model-value="selectedDraft.custom" :rows="2" @update:model-value="value => updateSelectedDraftField('custom', value ?? '')" />
        </UFormField>

        <UFormField label="Comments">
          <UTextarea :model-value="selectedDraft.comments" :rows="2" @update:model-value="value => updateSelectedDraftField('comments', value ?? '')" />
        </UFormField>

        <MetadataLabelGroupsForm
          :model-value="selectedDraft.labels"
          @update:model-value="value => updateSelectedDraftField('labels', value ?? [])"
        />
      </section>
    </div>
  </div>
</template>
