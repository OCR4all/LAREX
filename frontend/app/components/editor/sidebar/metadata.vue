<script setup lang="ts">
import { LazyUiConfirmModal } from '#components'
import type { FormSubmitEvent } from '@nuxt/ui'
import type { PcGts } from '@/models/editor/document'
import type { Page } from '@/models/editor/page'
import type { Region, TextRegion, RegionKind } from '@/models/editor/region'
import { isTextRegion, ALL_REGION_KINDS, ALL_TEXT_REGION_SUBTYPES, ALL_GRAPHIC_REGION_SUBTYPES, ALL_CHART_REGION_SUBTYPES, canContainTextLines } from '@/models/editor/region'
import type { TextLine } from '@/models/editor/text'
import type { RenderablePolyline } from '@/types/editor/rendering'
import type { MetadataApplyPayload, RegionKindChangePayload } from '@/types/editor/metadata'
import { getRegionKindDisplayName } from '@/utils/editor/region-colors'
import { LANGUAGE_SIMPLE_TYPE_VALUES, SCRIPT_SIMPLE_TYPE_VALUES } from '@/utils/editor/page-xml-enums'
import {
  documentMetadataSchema,
  pageMetadataSchema,
  textRegionMetadataSchema,
  genericRegionMetadataSchema,
  textLineMetadataSchema,
  baselineMetadataSchema,
  createDocumentMetadataFormState,
  createPageMetadataFormState,
  createTextRegionMetadataFormState,
  createGenericRegionMetadataFormState,
  createTextLineMetadataFormState,
  createBaselineMetadataFormState,
  type DocumentMetadataFormState,
  type PageMetadataFormState,
  type TextRegionMetadataFormState,
  type GenericRegionMetadataFormState,
  type TextLineMetadataFormState,
  type BaselineMetadataFormState,
  type AlternativeImageFormState,
  type LabelsFormState,
  type LabelFormState,
  type UserAttributeFormState,
  type MetadataItemFormState,
  type GridRowFormState
} from '@/utils/editor/metadata-schema'

const overlay = useOverlay()
const confirmModal = overlay.create(LazyUiConfirmModal)

const props = defineProps<{
  document?: PcGts | null
  page?: Page | null
  selectedElement?: Region | TextLine | RenderablePolyline | null
}>()

const emit = defineEmits<{
  'apply': [payload: MetadataApplyPayload]
  'change-region-kind': [payload: RegionKindChangePayload]
}>()

const isEditing = ref(false)
type EditContext = 'document' | 'page' | 'textRegion' | 'genericRegion' | 'textLine' | 'baseline'
const editContext = ref<EditContext | null>(null)

const documentFormState = reactive<DocumentMetadataFormState>({
  creator: '',
  created: '',
  lastChange: '',
  comments: '',
  externalRef: '',
  userDefinedAttributes: [],
  items: []
})

const pageFormState = reactive<PageMetadataFormState>({
  imageFilename: '',
  imageWidth: 0,
  imageHeight: 0,
  imageXResolution: undefined,
  imageYResolution: undefined,
  imageResolutionUnit: undefined,
  custom: '',
  orientation: undefined,
  type: undefined,
  primaryLanguage: undefined,
  secondaryLanguage: undefined,
  primaryScript: undefined,
  secondaryScript: undefined,
  readingDirection: undefined,
  textLineOrder: undefined,
  conf: undefined,
  alternativeImages: [],
  labels: [],
  userDefinedAttributes: [],
  textStyle: {}
})

const textRegionFormState = reactive<TextRegionMetadataFormState>({
  id: '',
  kind: '',
  custom: '',
  comments: '',
  continuation: undefined,
  orientation: undefined,
  type: '',
  leading: undefined,
  readingDirection: undefined,
  textLineOrder: undefined,
  readingOrientation: undefined,
  indented: undefined,
  align: undefined,
  primaryLanguage: undefined,
  secondaryLanguage: undefined,
  primaryScript: undefined,
  secondaryScript: undefined,
  production: undefined,
  alternativeImages: [],
  labels: [],
  userDefinedAttributes: [],
  textStyle: {}
})

const genericRegionFormState = reactive<GenericRegionMetadataFormState>({
  id: '',
  kind: '',
  type: '',
  custom: '',
  comments: '',
  continuation: undefined,
  orientation: undefined,
  numColours: undefined,
  embText: undefined,
  colourDepth: '',
  lineColour: '',
  lineSeparators: undefined,
  rows: undefined,
  columns: undefined,
  colour: '',
  penColour: '',
  borderPresent: undefined,
  textColourRgb: undefined,
  bgColourRgb: undefined,
  tableCellRole: {},
  gridRows: [],
  alternativeImages: [],
  labels: [],
  userDefinedAttributes: [],
  textStyle: {}
})

const textLineFormState = reactive<TextLineMetadataFormState>({
  id: '',
  primaryLanguage: undefined,
  primaryScript: undefined,
  secondaryScript: undefined,
  readingDirection: undefined,
  production: undefined,
  custom: '',
  comments: '',
  index: undefined,
  alternativeImages: [],
  labels: [],
  userDefinedAttributes: [],
  textStyle: {}
})

const baselineFormState = reactive<BaselineMetadataFormState>({
  conf: undefined
})

function isRegionElement(element: unknown): element is Region {
  return !!element && typeof element === 'object' && 'kind' in element && 'coords' in element
}

function isTextLineElement(element: unknown): element is TextLine {
  return !!element && typeof element === 'object' && 'id' in element && 'coords' in element && !('kind' in element) && !('type' in element)
}

function isBaselineElement(element: unknown): element is RenderablePolyline {
  return !!element && typeof element === 'object' && 'type' in element && (element as RenderablePolyline).type === 'baseline'
}

const currentContext = computed<EditContext | null>(() => {
  if (!props.selectedElement && !props.page && !props.document) return null

  if (props.selectedElement) {
    if (isBaselineElement(props.selectedElement)) return 'baseline'
    if (isTextLineElement(props.selectedElement)) return 'textLine'
    if (isRegionElement(props.selectedElement)) {
      return isTextRegion(props.selectedElement) ? 'textRegion' : 'genericRegion'
    }
  }

  if (props.page) return 'page'
  if (props.document) return 'document'

  return null
})

const canEdit = computed(() => currentContext.value !== null)

function formatDate(dateStr?: string): string {
  if (!dateStr) return '—'
  try {
    const date = new Date(dateStr)
    return date.toLocaleString()
  } catch {
    return dateStr
  }
}

function formatConfidence(value?: number): string {
  if (value === undefined || value === null) return '—'
  return `${(value * 100).toFixed(1)}%`
}

function formatNumber(value?: number | string): string {
  if (value === undefined || value === null) return '—'
  return String(value)
}

function formatBoolean(value?: boolean): string {
  if (value === undefined) return '—'
  return value ? 'Yes' : 'No'
}

function startEditing() {
  const ctx = currentContext.value
  if (!ctx) return

  editContext.value = ctx

  switch (ctx) {
    case 'document':
      if (props.document?.metadata) {
        Object.assign(documentFormState, createDocumentMetadataFormState(props.document.metadata))
      }
      break
    case 'page':
      if (props.page) {
        Object.assign(pageFormState, createPageMetadataFormState(props.page))
      }
      break
    case 'textRegion':
      if (props.selectedElement && isRegionElement(props.selectedElement) && isTextRegion(props.selectedElement)) {
        Object.assign(textRegionFormState, createTextRegionMetadataFormState(props.selectedElement))
      }
      break
    case 'genericRegion':
      if (props.selectedElement && isRegionElement(props.selectedElement)) {
        Object.assign(genericRegionFormState, createGenericRegionMetadataFormState(props.selectedElement))
      }
      break
    case 'textLine':
      if (props.selectedElement && isTextLineElement(props.selectedElement)) {
        Object.assign(textLineFormState, createTextLineMetadataFormState(props.selectedElement))
      }
      break
    case 'baseline':
      if (props.selectedElement && isBaselineElement(props.selectedElement)) {
        Object.assign(baselineFormState, createBaselineMetadataFormState({ conf: undefined }))
      }
      break
  }

  isEditing.value = true
}

function cancelEditing() {
  isEditing.value = false
  editContext.value = null
}

function onSubmitDocument(event: FormSubmitEvent<DocumentMetadataFormState>) {
  emit('apply', { target: 'document', data: event.data })
  cancelEditing()
}

function onSubmitPage(event: FormSubmitEvent<PageMetadataFormState>) {
  emit('apply', { target: 'page', data: event.data })
  cancelEditing()
}

function onSubmitTextRegion(event: FormSubmitEvent<TextRegionMetadataFormState>) {
  if (!props.selectedElement || !isRegionElement(props.selectedElement) || !isTextRegion(props.selectedElement)) return

  emit('apply', {
    target: 'textRegion',
    elementId: props.selectedElement.id,
    data: event.data
  })
  cancelEditing()
}

function onSubmitGenericRegion(event: FormSubmitEvent<GenericRegionMetadataFormState>) {
  if (!props.selectedElement || !isRegionElement(props.selectedElement)) return

  emit('apply', {
    target: 'genericRegion',
    elementId: props.selectedElement.id,
    data: event.data
  })
  cancelEditing()
}

function onSubmitTextLine(event: FormSubmitEvent<TextLineMetadataFormState>) {
  if (!props.selectedElement || !isTextLineElement(props.selectedElement)) return

  emit('apply', {
    target: 'textLine',
    elementId: props.selectedElement.id,
    data: event.data
  })
  cancelEditing()
}

function onSubmitBaseline(event: FormSubmitEvent<BaselineMetadataFormState>) {
  if (!props.selectedElement || !isBaselineElement(props.selectedElement)) return
  emit('apply', {
    target: 'baseline',
    elementId: props.selectedElement.id,
    data: event.data
  })
  cancelEditing()
}

function switchToContext(ctx: EditContext) {
  editContext.value = ctx

  switch (ctx) {
    case 'document':
      if (props.document?.metadata) {
        Object.assign(documentFormState, createDocumentMetadataFormState(props.document.metadata))
      }
      break
    case 'page':
      if (props.page) {
        Object.assign(pageFormState, createPageMetadataFormState(props.page))
      }
      break
  }
}

const readingDirectionOptions = [
  { label: 'Left to Right', value: 'left-to-right' },
  { label: 'Right to Left', value: 'right-to-left' },
  { label: 'Top to Bottom', value: 'top-to-bottom' },
  { label: 'Bottom to Top', value: 'bottom-to-top' }
]

const textLineOrderOptions = [
  { label: 'Top to Bottom', value: 'top-to-bottom' },
  { label: 'Bottom to Top', value: 'bottom-to-top' },
  { label: 'Left to Right', value: 'left-to-right' },
  { label: 'Right to Left', value: 'right-to-left' }
]

const productionOptions = [
  { label: 'Printed', value: 'printed' },
  { label: 'Handwritten Cursive', value: 'handwritten-cursive' },
  { label: 'Handwritten Printscript', value: 'handwritten-printscript' },
  { label: 'Medieval Manuscript', value: 'medieval-manuscript' },
  { label: 'Typewritten', value: 'typewritten' }
]

const pageTypeOptions = [
  { label: 'Front Cover', value: 'front-cover' },
  { label: 'Back Cover', value: 'back-cover' },
  { label: 'Title', value: 'title' },
  { label: 'Table of Contents', value: 'table-of-contents' },
  { label: 'Index', value: 'index' },
  { label: 'Content', value: 'content' },
  { label: 'Blank', value: 'blank' },
  { label: 'Other', value: 'other' }
]

const alignOptions = [
  { label: 'Left', value: 'left' },
  { label: 'Centre', value: 'centre' },
  { label: 'Right', value: 'right' },
  { label: 'Justify', value: 'justify' }
]

const resolutionUnitOptions = [
  { label: 'Pixels per Inch (PPI)', value: 'PPI' },
  { label: 'Pixels per Centimeter (PPCM)', value: 'PPCM' },
  { label: 'Other', value: 'other' }
]
const userAttributeTypeOptions = [
  { label: 'String', value: 'xsd:string' },
  { label: 'Integer', value: 'xsd:integer' },
  { label: 'Boolean', value: 'xsd:boolean' },
  { label: 'Float', value: 'xsd:float' }
]
const metadataItemTypeOptions = [
  { label: 'Author', value: 'author' },
  { label: 'Image Properties', value: 'imageProperties' },
  { label: 'Processing Step', value: 'processingStep' },
  { label: 'Other', value: 'other' }
]
const languageOptions = [
  ...LANGUAGE_SIMPLE_TYPE_VALUES
]
const scriptOptions = [
  ...SCRIPT_SIMPLE_TYPE_VALUES
]

const regionKindOptions: { label: string, value: string }[] = ALL_REGION_KINDS.map(kind => ({
  label: getRegionKindDisplayName(kind),
  value: kind
}))

const textRegionSubtypeOptions: { label: string, value: string }[] = ALL_TEXT_REGION_SUBTYPES.map(subtype => ({
  label: formatSubtypeLabel(subtype),
  value: subtype
}))

const graphicRegionSubtypeOptions: { label: string, value: string }[] = ALL_GRAPHIC_REGION_SUBTYPES.map(subtype => ({
  label: formatSubtypeLabel(subtype),
  value: subtype
}))

const chartRegionSubtypeOptions: { label: string, value: string }[] = ALL_CHART_REGION_SUBTYPES.map(subtype => ({
  label: formatSubtypeLabel(subtype),
  value: subtype
}))

function formatSubtypeLabel(subtype: string): string {
  return subtype
    .split('-')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

function addAlternativeImageRow(target: AlternativeImageFormState[]) {
  target.push({
    filename: '',
    comments: '',
    confidence: undefined
  })
}

function addLabelGroup(target: LabelsFormState[]) {
  target.push({
    externalModel: '',
    externalId: '',
    prefix: '',
    comments: '',
    labels: []
  })
}

function addLabelRow(target: LabelFormState[]) {
  target.push({
    value: '',
    type: '',
    comments: ''
  })
}

function addUserAttributeRow(target: UserAttributeFormState[]) {
  target.push({
    name: '',
    description: '',
    type: undefined,
    value: ''
  })
}

function addMetadataItemRow(target: MetadataItemFormState[]) {
  target.push({
    type: undefined,
    name: '',
    value: '',
    date: '',
    labels: []
  })
}

function addGridRow(target: GridRowFormState[]) {
  target.push({
    index: undefined,
    points: ''
  })
}

function removeAt<T>(target: T[], index: number) {
  target.splice(index, 1)
}

async function handleRegionKindChange(newKind: RegionKind) {
  if (!props.selectedElement || !isRegionElement(props.selectedElement)) return

  const currentKind = props.selectedElement.kind
  if (newKind === currentKind) return

  const textLinesToRemove = isTextRegion(props.selectedElement) && !canContainTextLines(newKind)
    ? (props.selectedElement as TextRegion).textLines?.length ?? 0
    : 0

  if (textLinesToRemove > 0) {
    const instance = confirmModal.open({
      title: 'Remove Text Lines?',
      description: `Converting to ${getRegionKindDisplayName(newKind)} will remove ${textLinesToRemove} text line${textLinesToRemove === 1 ? '' : 's'}. Continue?`,
      confirmLabel: 'Continue',
      confirmColor: 'warning'
    })
    const confirmed = await instance.result
    if (!confirmed) return
  }

  emit('change-region-kind', {
    regionId: props.selectedElement.id,
    newKind
  })
  cancelEditing()
}

function onRegionKindSelectChange(val: unknown) {
  if (typeof val === 'string' && ALL_REGION_KINDS.includes(val as RegionKind)) {
    handleRegionKindChange(val as RegionKind)
  }
}

watch(() => props.selectedElement, () => {
  if (isEditing.value) {
    cancelEditing()
  }
})
</script>

<template>
  <div class="h-full flex flex-col bg-background">
    <div class="flex-1 overflow-y-auto">
      <div class="p-4 space-y-4">
        <template v-if="isEditing">
          <UForm
            v-if="editContext === 'document' && document?.metadata"
            :schema="documentMetadataSchema"
            :state="documentFormState"
            class="space-y-4"
            @submit="onSubmitDocument"
          >
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:book-open" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium">Document Metadata</span>
            </div>

            <UFormField label="Creator" name="creator" required>
              <UInput v-model="documentFormState.creator" placeholder="Creator name" />
            </UFormField>

            <UFormField label="Created" name="created">
              <UInput :model-value="formatDate(documentFormState.created)" disabled class="bg-muted" />
              <template #hint>
                <span class="text-xs text-muted-foreground">Read-only</span>
              </template>
            </UFormField>

            <UFormField label="Last Change" name="lastChange">
              <UInput :model-value="formatDate(documentFormState.lastChange)" disabled class="bg-muted" />
              <template #hint>
                <span class="text-xs text-muted-foreground">Auto-updated on save</span>
              </template>
            </UFormField>

            <UFormField label="Comments" name="comments">
              <UTextarea v-model="documentFormState.comments" placeholder="Comments..." :rows="3" />
            </UFormField>

            <UFormField label="External Reference" name="externalRef">
              <UInput v-model="documentFormState.externalRef" placeholder="URL or reference" />
            </UFormField>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">User-Defined Attributes</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addUserAttributeRow(documentFormState.userDefinedAttributes)"
                >
                  Add
                </UButton>
              </div>
              <div
                v-for="(attr, attrIndex) in documentFormState.userDefinedAttributes"
                :key="`doc-attr-${attrIndex}`"
                class="space-y-2 rounded-md border border-border p-2"
              >
                <UFormField label="Name">
                  <UInput v-model="attr.name" placeholder="Attribute name" />
                </UFormField>
                <UFormField label="Description">
                  <UInput v-model="attr.description" placeholder="Description" />
                </UFormField>
                <UFormField label="Type">
                  <USelect v-model="attr.type" :items="userAttributeTypeOptions" placeholder="Select type" />
                </UFormField>
                <UFormField label="Value">
                  <UInput v-model="attr.value" placeholder="Value" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(documentFormState.userDefinedAttributes, attrIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Metadata Items</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addMetadataItemRow(documentFormState.items)"
                >
                  Add
                </UButton>
              </div>
              <div
                v-for="(item, itemIndex) in documentFormState.items"
                :key="`doc-item-${itemIndex}`"
                class="space-y-2 rounded-md border border-border p-2"
              >
                <UFormField label="Type">
                  <USelect v-model="item.type" :items="metadataItemTypeOptions" placeholder="Select type" />
                </UFormField>
                <UFormField label="Name">
                  <UInput v-model="item.name" placeholder="Name" />
                </UFormField>
                <UFormField label="Value">
                  <UTextarea v-model="item.value" placeholder="Value" :rows="2" />
                </UFormField>
                <UFormField label="Date">
                  <UInput v-model="item.date" placeholder="ISO date/time" />
                </UFormField>

                <div class="space-y-2 rounded-md border border-border p-2">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-medium">Label Groups</span>
                    <UButton
                      type="button"
                      size="xs"
                      variant="ghost"
                      @click="addLabelGroup(item.labels)"
                    >
                      Add Group
                    </UButton>
                  </div>
                  <div
                    v-for="(group, groupIndex) in item.labels"
                    :key="`doc-item-${itemIndex}-group-${groupIndex}`"
                    class="space-y-2 rounded-md border border-border p-2"
                  >
                    <UFormField label="External Model">
                      <UInput v-model="group.externalModel" placeholder="External model" />
                    </UFormField>
                    <UFormField label="External ID">
                      <UInput v-model="group.externalId" placeholder="External ID" />
                    </UFormField>
                    <UFormField label="Prefix">
                      <UInput v-model="group.prefix" placeholder="Prefix" />
                    </UFormField>
                    <UFormField label="Comments">
                      <UTextarea v-model="group.comments" placeholder="Comments..." :rows="2" />
                    </UFormField>
                    <div class="space-y-2">
                      <div class="flex items-center justify-between">
                        <span class="text-xs font-medium">Labels</span>
                        <UButton
                          type="button"
                          size="xs"
                          variant="ghost"
                          @click="addLabelRow(group.labels)"
                        >
                          Add Label
                        </UButton>
                      </div>
                      <div
                        v-for="(label, labelIndex) in group.labels"
                        :key="`doc-item-${itemIndex}-group-${groupIndex}-label-${labelIndex}`"
                        class="space-y-2 rounded-md border border-border p-2"
                      >
                        <UFormField label="Value">
                          <UInput v-model="label.value" placeholder="Value" />
                        </UFormField>
                        <UFormField label="Type">
                          <UInput v-model="label.type" placeholder="Type" />
                        </UFormField>
                        <UFormField label="Comments">
                          <UInput v-model="label.comments" placeholder="Comments" />
                        </UFormField>
                        <UButton
                          type="button"
                          size="xs"
                          color="error"
                          variant="ghost"
                          @click="removeAt(group.labels, labelIndex)"
                        >
                          Remove Label
                        </UButton>
                      </div>
                    </div>
                    <UButton
                      type="button"
                      size="xs"
                      color="error"
                      variant="ghost"
                      @click="removeAt(item.labels, groupIndex)"
                    >
                      Remove Group
                    </UButton>
                  </div>
                </div>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(documentFormState.items, itemIndex)"
                >
                  Remove Item
                </UButton>
              </div>
            </div>

            <div class="flex gap-2 pt-4 border-t border-border">
              <UButton type="submit" class="flex-1">
                <Icon name="lucide:save" class="w-4 h-4 mr-1" />Save
              </UButton>
              <UButton type="button" variant="outline" @click="cancelEditing">
                Cancel
              </UButton>
            </div>

            <UButton
              v-if="page"
              type="button"
              variant="ghost"
              size="sm"
              class="w-full"
              @click="switchToContext('page')"
            >
              <Icon name="lucide:file-text" class="w-4 h-4 mr-1" />Edit Page Metadata Instead
            </UButton>
          </UForm>

          <UForm
            v-else-if="editContext === 'page' && page"
            :schema="pageMetadataSchema"
            :state="pageFormState"
            class="space-y-4"
            @submit="onSubmitPage"
          >
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:file-image" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium">Page Metadata</span>
            </div>

            <UFormField label="Image Filename" name="imageFilename">
              <UInput :model-value="pageFormState.imageFilename" disabled class="bg-muted" />
            </UFormField>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Width" name="imageWidth">
                <UInput :model-value="pageFormState.imageWidth + 'px'" disabled class="bg-muted" />
              </UFormField>
              <UFormField label="Height" name="imageHeight">
                <UInput :model-value="pageFormState.imageHeight + 'px'" disabled class="bg-muted" />
              </UFormField>
            </div>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="X Resolution" name="imageXResolution">
                <UInput v-model.number="pageFormState.imageXResolution" type="number" placeholder="72" />
              </UFormField>
              <UFormField label="Y Resolution" name="imageYResolution">
                <UInput v-model.number="pageFormState.imageYResolution" type="number" placeholder="72" />
              </UFormField>
            </div>

            <UFormField label="Resolution Unit" name="imageResolutionUnit">
              <USelect v-model="pageFormState.imageResolutionUnit" :items="resolutionUnitOptions" placeholder="Select unit" />
            </UFormField>

            <UFormField label="Page Type" name="type">
              <USelect v-model="pageFormState.type" :items="pageTypeOptions" placeholder="Select type" />
            </UFormField>

            <UFormField label="Orientation" name="orientation">
              <UInput
                v-model.number="pageFormState.orientation"
                type="number"
                step="0.1"
                placeholder="0"
              />
              <template #hint>
                <span class="text-xs text-muted-foreground">Degrees (-180 to 180)</span>
              </template>
            </UFormField>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Primary Language" name="primaryLanguage">
                <USelectMenu
                  v-model="pageFormState.primaryLanguage"
                  :items="languageOptions"
                  placeholder="Select language"
                  :search-input="{ placeholder: 'Search language...' }"
                  virtualize
                  clear
                />
              </UFormField>
              <UFormField label="Secondary Language" name="secondaryLanguage">
                <USelectMenu
                  v-model="pageFormState.secondaryLanguage"
                  :items="languageOptions"
                  placeholder="Select language"
                  :search-input="{ placeholder: 'Search language...' }"
                  virtualize
                  clear
                />
              </UFormField>
            </div>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Primary Script" name="primaryScript">
                <USelectMenu
                  v-model="pageFormState.primaryScript"
                  :items="scriptOptions"
                  placeholder="Select script"
                  :search-input="{ placeholder: 'Search script...' }"
                  virtualize
                  clear
                />
              </UFormField>
              <UFormField label="Secondary Script" name="secondaryScript">
                <USelectMenu
                  v-model="pageFormState.secondaryScript"
                  :items="scriptOptions"
                  placeholder="Select script"
                  :search-input="{ placeholder: 'Search script...' }"
                  virtualize
                  clear
                />
              </UFormField>
            </div>

            <UFormField label="Reading Direction" name="readingDirection">
              <USelect v-model="pageFormState.readingDirection" :items="readingDirectionOptions" placeholder="Select direction" />
            </UFormField>

            <UFormField label="Text Line Order" name="textLineOrder">
              <USelect v-model="pageFormState.textLineOrder" :items="textLineOrderOptions" placeholder="Select order" />
            </UFormField>

            <UFormField label="Confidence" name="conf">
              <UInput
                v-model.number="pageFormState.conf"
                type="number"
                step="0.01"
                min="0"
                max="1"
                placeholder="0.0 - 1.0"
              />
            </UFormField>

            <UFormField label="Custom" name="custom">
              <UInput v-model="pageFormState.custom" placeholder="Custom data" />
            </UFormField>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Alternative Images</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addAlternativeImageRow(pageFormState.alternativeImages)"
                >
                  Add
                </UButton>
              </div>
              <div v-for="(img, imgIndex) in pageFormState.alternativeImages" :key="`page-alt-${imgIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Filename">
                  <UInput v-model="img.filename" placeholder="Image filename" />
                </UFormField>
                <UFormField label="Confidence">
                  <UInput
                    v-model.number="img.confidence"
                    type="number"
                    min="0"
                    max="1"
                    step="0.01"
                    placeholder="0.0 - 1.0"
                  />
                </UFormField>
                <UFormField label="Comments">
                  <UTextarea v-model="img.comments" placeholder="Comments..." :rows="2" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(pageFormState.alternativeImages, imgIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Labels</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addLabelGroup(pageFormState.labels)"
                >
                  Add Group
                </UButton>
              </div>
              <div v-for="(group, groupIndex) in pageFormState.labels" :key="`page-label-group-${groupIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="External Model">
                  <UInput v-model="group.externalModel" placeholder="External model" />
                </UFormField>
                <UFormField label="External ID">
                  <UInput v-model="group.externalId" placeholder="External ID" />
                </UFormField>
                <UFormField label="Prefix">
                  <UInput v-model="group.prefix" placeholder="Prefix" />
                </UFormField>
                <UFormField label="Comments">
                  <UTextarea v-model="group.comments" placeholder="Comments..." :rows="2" />
                </UFormField>
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-medium">Labels</span>
                    <UButton
                      type="button"
                      size="xs"
                      variant="ghost"
                      @click="addLabelRow(group.labels)"
                    >
                      Add Label
                    </UButton>
                  </div>
                  <div v-for="(label, labelIndex) in group.labels" :key="`page-label-group-${groupIndex}-label-${labelIndex}`" class="space-y-2 rounded-md border border-border p-2">
                    <UFormField label="Value">
                      <UInput v-model="label.value" placeholder="Value" />
                    </UFormField>
                    <UFormField label="Type">
                      <UInput v-model="label.type" placeholder="Type" />
                    </UFormField>
                    <UFormField label="Comments">
                      <UInput v-model="label.comments" placeholder="Comments" />
                    </UFormField>
                    <UButton
                      type="button"
                      size="xs"
                      color="error"
                      variant="ghost"
                      @click="removeAt(group.labels, labelIndex)"
                    >
                      Remove Label
                    </UButton>
                  </div>
                </div>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(pageFormState.labels, groupIndex)"
                >
                  Remove Group
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">User-Defined Attributes</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addUserAttributeRow(pageFormState.userDefinedAttributes)"
                >
                  Add
                </UButton>
              </div>
              <div v-for="(attr, attrIndex) in pageFormState.userDefinedAttributes" :key="`page-attr-${attrIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Name">
                  <UInput v-model="attr.name" placeholder="Attribute name" />
                </UFormField>
                <UFormField label="Description">
                  <UInput v-model="attr.description" placeholder="Description" />
                </UFormField>
                <UFormField label="Type">
                  <USelect v-model="attr.type" :items="userAttributeTypeOptions" placeholder="Select type" />
                </UFormField>
                <UFormField label="Value">
                  <UInput v-model="attr.value" placeholder="Value" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(pageFormState.userDefinedAttributes, attrIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <span class="text-sm font-medium">Text Style</span>
              <UFormField label="Font Family">
                <UInput v-model="pageFormState.textStyle.fontFamily" placeholder="Font family" />
              </UFormField>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Font Size">
                  <UInput v-model.number="pageFormState.textStyle.fontSize" type="number" step="0.1" />
                </UFormField>
                <UFormField label="X Height">
                  <UInput v-model.number="pageFormState.textStyle.xHeight" type="number" step="0.1" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Kerning">
                  <UInput v-model.number="pageFormState.textStyle.kerning" type="number" step="0.1" />
                </UFormField>
                <UFormField label="Underline Style">
                  <UInput v-model="pageFormState.textStyle.underlineStyle" placeholder="Underline style" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Text Colour">
                  <UInput v-model="pageFormState.textStyle.textColour" placeholder="Named colour" />
                </UFormField>
                <UFormField label="Background Colour">
                  <UInput v-model="pageFormState.textStyle.bgColour" placeholder="Named colour" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Text Colour RGB">
                  <UInput v-model.number="pageFormState.textStyle.textColourRgb" type="number" />
                </UFormField>
                <UFormField label="Background Colour RGB">
                  <UInput v-model.number="pageFormState.textStyle.bgColourRgb" type="number" />
                </UFormField>
              </div>
              <div class="space-y-1">
                <UCheckbox v-model="pageFormState.textStyle.serif" label="Serif" />
                <UCheckbox v-model="pageFormState.textStyle.monospace" label="Monospace" />
                <UCheckbox v-model="pageFormState.textStyle.bold" label="Bold" />
                <UCheckbox v-model="pageFormState.textStyle.italic" label="Italic" />
                <UCheckbox v-model="pageFormState.textStyle.underlined" label="Underlined" />
                <UCheckbox v-model="pageFormState.textStyle.subscript" label="Subscript" />
                <UCheckbox v-model="pageFormState.textStyle.superscript" label="Superscript" />
                <UCheckbox v-model="pageFormState.textStyle.strikethrough" label="Strikethrough" />
                <UCheckbox v-model="pageFormState.textStyle.smallCaps" label="Small Caps" />
                <UCheckbox v-model="pageFormState.textStyle.letterSpaced" label="Letter Spaced" />
                <UCheckbox v-model="pageFormState.textStyle.reverseVideo" label="Reverse Video" />
              </div>
            </div>

            <div class="flex gap-2 pt-4 border-t border-border">
              <UButton type="submit" class="flex-1">
                <Icon name="lucide:save" class="w-4 h-4 mr-1" />Save
              </UButton>
              <UButton type="button" variant="outline" @click="cancelEditing">
                Cancel
              </UButton>
            </div>

            <UButton
              v-if="document?.metadata"
              type="button"
              variant="ghost"
              size="sm"
              class="w-full"
              @click="switchToContext('document')"
            >
              <Icon name="lucide:book-open" class="w-4 h-4 mr-1" />Edit Document Metadata Instead
            </UButton>
          </UForm>

          <UForm
            v-else-if="editContext === 'textRegion'"
            :schema="textRegionMetadataSchema"
            :state="textRegionFormState"
            class="space-y-4"
            @submit="onSubmitTextRegion"
          >
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:box" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium">Text Region</span>
            </div>

            <UFormField label="ID" name="id">
              <UInput :model-value="textRegionFormState.id" disabled class="bg-muted font-mono text-xs" />
            </UFormField>

            <UFormField label="Region Kind" name="kind">
              <USelect
                :model-value="textRegionFormState.kind"
                :items="regionKindOptions"
                placeholder="Select region kind"
                @update:model-value="onRegionKindSelectChange"
              />
              <template #hint>
                <span class="text-xs text-muted-foreground">Changing kind may remove child elements</span>
              </template>
            </UFormField>

            <UFormField label="Text Type" name="type">
              <USelect v-model="textRegionFormState.type" :items="textRegionSubtypeOptions" placeholder="Select text type" />
            </UFormField>

            <UFormField label="Orientation" name="orientation">
              <UInput
                v-model.number="textRegionFormState.orientation"
                type="number"
                step="0.1"
                placeholder="0"
              />
            </UFormField>

            <UFormField label="Reading Orientation" name="readingOrientation">
              <UInput
                v-model.number="textRegionFormState.readingOrientation"
                type="number"
                step="0.1"
                placeholder="0"
              />
            </UFormField>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Primary Language" name="primaryLanguage">
                <USelectMenu
                  v-model="textRegionFormState.primaryLanguage"
                  :items="languageOptions"
                  placeholder="Select language"
                  :search-input="{ placeholder: 'Search language...' }"
                  virtualize
                  clear
                />
              </UFormField>
              <UFormField label="Secondary Language" name="secondaryLanguage">
                <USelectMenu
                  v-model="textRegionFormState.secondaryLanguage"
                  :items="languageOptions"
                  placeholder="Select language"
                  :search-input="{ placeholder: 'Search language...' }"
                  virtualize
                  clear
                />
              </UFormField>
            </div>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Primary Script" name="primaryScript">
                <USelectMenu
                  v-model="textRegionFormState.primaryScript"
                  :items="scriptOptions"
                  placeholder="Select script"
                  :search-input="{ placeholder: 'Search script...' }"
                  virtualize
                  clear
                />
              </UFormField>
              <UFormField label="Secondary Script" name="secondaryScript">
                <USelectMenu
                  v-model="textRegionFormState.secondaryScript"
                  :items="scriptOptions"
                  placeholder="Select script"
                  :search-input="{ placeholder: 'Search script...' }"
                  virtualize
                  clear
                />
              </UFormField>
            </div>

            <UFormField label="Reading Direction" name="readingDirection">
              <USelect v-model="textRegionFormState.readingDirection" :items="readingDirectionOptions" placeholder="Select direction" />
            </UFormField>

            <UFormField label="Text Line Order" name="textLineOrder">
              <USelect v-model="textRegionFormState.textLineOrder" :items="textLineOrderOptions" placeholder="Select order" />
            </UFormField>

            <UFormField label="Production" name="production">
              <USelect v-model="textRegionFormState.production" :items="productionOptions" placeholder="Select production" />
            </UFormField>

            <UFormField label="Align" name="align">
              <USelect v-model="textRegionFormState.align" :items="alignOptions" placeholder="Select alignment" />
            </UFormField>

            <UFormField label="Leading" name="leading">
              <UInput v-model.number="textRegionFormState.leading" type="number" placeholder="Line spacing in points" />
            </UFormField>

            <UFormField label="Indented" name="indented">
              <UCheckbox v-model="textRegionFormState.indented" label="Text is indented" />
            </UFormField>

            <UFormField label="Continuation" name="continuation">
              <UCheckbox v-model="textRegionFormState.continuation" label="Continuation from previous region" />
            </UFormField>

            <UFormField label="Comments" name="comments">
              <UTextarea v-model="textRegionFormState.comments" placeholder="Comments..." :rows="2" />
            </UFormField>

            <UFormField label="Custom" name="custom">
              <UInput v-model="textRegionFormState.custom" placeholder="Custom data" />
            </UFormField>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Alternative Images</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addAlternativeImageRow(textRegionFormState.alternativeImages)"
                >
                  Add
                </UButton>
              </div>
              <div v-for="(img, imgIndex) in textRegionFormState.alternativeImages" :key="`text-region-alt-${imgIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Filename">
                  <UInput v-model="img.filename" placeholder="Image filename" />
                </UFormField>
                <UFormField label="Confidence">
                  <UInput
                    v-model.number="img.confidence"
                    type="number"
                    min="0"
                    max="1"
                    step="0.01"
                  />
                </UFormField>
                <UFormField label="Comments">
                  <UTextarea v-model="img.comments" placeholder="Comments..." :rows="2" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(textRegionFormState.alternativeImages, imgIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Labels</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addLabelGroup(textRegionFormState.labels)"
                >
                  Add Group
                </UButton>
              </div>
              <div v-for="(group, groupIndex) in textRegionFormState.labels" :key="`text-region-label-group-${groupIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="External Model">
                  <UInput v-model="group.externalModel" placeholder="External model" />
                </UFormField>
                <UFormField label="External ID">
                  <UInput v-model="group.externalId" placeholder="External ID" />
                </UFormField>
                <UFormField label="Prefix">
                  <UInput v-model="group.prefix" placeholder="Prefix" />
                </UFormField>
                <UFormField label="Comments">
                  <UTextarea v-model="group.comments" placeholder="Comments..." :rows="2" />
                </UFormField>
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-medium">Labels</span>
                    <UButton
                      type="button"
                      size="xs"
                      variant="ghost"
                      @click="addLabelRow(group.labels)"
                    >
                      Add Label
                    </UButton>
                  </div>
                  <div v-for="(label, labelIndex) in group.labels" :key="`text-region-label-group-${groupIndex}-label-${labelIndex}`" class="space-y-2 rounded-md border border-border p-2">
                    <UFormField label="Value">
                      <UInput v-model="label.value" placeholder="Value" />
                    </UFormField>
                    <UFormField label="Type">
                      <UInput v-model="label.type" placeholder="Type" />
                    </UFormField>
                    <UFormField label="Comments">
                      <UInput v-model="label.comments" placeholder="Comments" />
                    </UFormField>
                    <UButton
                      type="button"
                      size="xs"
                      color="error"
                      variant="ghost"
                      @click="removeAt(group.labels, labelIndex)"
                    >
                      Remove Label
                    </UButton>
                  </div>
                </div>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(textRegionFormState.labels, groupIndex)"
                >
                  Remove Group
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">User-Defined Attributes</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addUserAttributeRow(textRegionFormState.userDefinedAttributes)"
                >
                  Add
                </UButton>
              </div>
              <div v-for="(attr, attrIndex) in textRegionFormState.userDefinedAttributes" :key="`text-region-attr-${attrIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Name">
                  <UInput v-model="attr.name" placeholder="Attribute name" />
                </UFormField>
                <UFormField label="Description">
                  <UInput v-model="attr.description" placeholder="Description" />
                </UFormField>
                <UFormField label="Type">
                  <USelect v-model="attr.type" :items="userAttributeTypeOptions" placeholder="Select type" />
                </UFormField>
                <UFormField label="Value">
                  <UInput v-model="attr.value" placeholder="Value" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(textRegionFormState.userDefinedAttributes, attrIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <span class="text-sm font-medium">Text Style</span>
              <UFormField label="Font Family">
                <UInput v-model="textRegionFormState.textStyle.fontFamily" placeholder="Font family" />
              </UFormField>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Font Size">
                  <UInput v-model.number="textRegionFormState.textStyle.fontSize" type="number" step="0.1" />
                </UFormField>
                <UFormField label="X Height">
                  <UInput v-model.number="textRegionFormState.textStyle.xHeight" type="number" step="0.1" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Kerning">
                  <UInput v-model.number="textRegionFormState.textStyle.kerning" type="number" step="0.1" />
                </UFormField>
                <UFormField label="Underline Style">
                  <UInput v-model="textRegionFormState.textStyle.underlineStyle" placeholder="Underline style" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Text Colour">
                  <UInput v-model="textRegionFormState.textStyle.textColour" placeholder="Named colour" />
                </UFormField>
                <UFormField label="Background Colour">
                  <UInput v-model="textRegionFormState.textStyle.bgColour" placeholder="Named colour" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Text Colour RGB">
                  <UInput v-model.number="textRegionFormState.textStyle.textColourRgb" type="number" />
                </UFormField>
                <UFormField label="Background Colour RGB">
                  <UInput v-model.number="textRegionFormState.textStyle.bgColourRgb" type="number" />
                </UFormField>
              </div>
              <div class="space-y-1">
                <UCheckbox v-model="textRegionFormState.textStyle.serif" label="Serif" />
                <UCheckbox v-model="textRegionFormState.textStyle.monospace" label="Monospace" />
                <UCheckbox v-model="textRegionFormState.textStyle.bold" label="Bold" />
                <UCheckbox v-model="textRegionFormState.textStyle.italic" label="Italic" />
                <UCheckbox v-model="textRegionFormState.textStyle.underlined" label="Underlined" />
                <UCheckbox v-model="textRegionFormState.textStyle.subscript" label="Subscript" />
                <UCheckbox v-model="textRegionFormState.textStyle.superscript" label="Superscript" />
                <UCheckbox v-model="textRegionFormState.textStyle.strikethrough" label="Strikethrough" />
                <UCheckbox v-model="textRegionFormState.textStyle.smallCaps" label="Small Caps" />
                <UCheckbox v-model="textRegionFormState.textStyle.letterSpaced" label="Letter Spaced" />
                <UCheckbox v-model="textRegionFormState.textStyle.reverseVideo" label="Reverse Video" />
              </div>
            </div>

            <div class="flex gap-2 pt-4 border-t border-border">
              <UButton type="submit" class="flex-1">
                <Icon name="lucide:save" class="w-4 h-4 mr-1" />Save
              </UButton>
              <UButton type="button" variant="outline" @click="cancelEditing">
                Cancel
              </UButton>
            </div>
          </UForm>

          <UForm
            v-else-if="editContext === 'genericRegion'"
            :schema="genericRegionMetadataSchema"
            :state="genericRegionFormState"
            class="space-y-4"
            @submit="onSubmitGenericRegion"
          >
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:square" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium">{{ genericRegionFormState.kind.replace(/Region$/, '') }} Region</span>
            </div>

            <UFormField label="ID" name="id">
              <UInput :model-value="genericRegionFormState.id" disabled class="bg-muted font-mono text-xs" />
            </UFormField>

            <UFormField label="Region Kind" name="kind">
              <USelect
                :model-value="genericRegionFormState.kind"
                :items="regionKindOptions"
                placeholder="Select region kind"
                @update:model-value="onRegionKindSelectChange"
              />
              <template #hint>
                <span class="text-xs text-muted-foreground">Changing to TextRegion enables text lines</span>
              </template>
            </UFormField>

            <UFormField v-if="genericRegionFormState.kind === 'GraphicRegion'" label="Graphic Type" name="type">
              <USelect v-model="genericRegionFormState.type" :items="graphicRegionSubtypeOptions" placeholder="Select graphic type" />
            </UFormField>

            <UFormField v-if="genericRegionFormState.kind === 'ChartRegion'" label="Chart Type" name="type">
              <USelect v-model="genericRegionFormState.type" :items="chartRegionSubtypeOptions" placeholder="Select chart type" />
            </UFormField>
            <UFormField label="Continuation" name="continuation">
              <UCheckbox v-model="genericRegionFormState.continuation" label="Continuation from previous region" />
            </UFormField>

            <UFormField label="Comments" name="comments">
              <UTextarea v-model="genericRegionFormState.comments" placeholder="Comments..." :rows="2" />
            </UFormField>

            <UFormField label="Custom" name="custom">
              <UInput v-model="genericRegionFormState.custom" placeholder="Custom data" />
            </UFormField>

            <UFormField label="Orientation" name="orientation">
              <UInput
                v-model.number="genericRegionFormState.orientation"
                type="number"
                step="0.1"
                placeholder="0"
              />
            </UFormField>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Num Colours" name="numColours">
                <UInput v-model.number="genericRegionFormState.numColours" type="number" min="0" />
              </UFormField>
              <UFormField label="Embedded Text" name="embText">
                <UCheckbox v-model="genericRegionFormState.embText" label="Embedded text present" />
              </UFormField>
            </div>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Rows" name="rows">
                <UInput v-model.number="genericRegionFormState.rows" type="number" min="0" />
              </UFormField>
              <UFormField label="Columns" name="columns">
                <UInput v-model.number="genericRegionFormState.columns" type="number" min="0" />
              </UFormField>
            </div>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Colour Depth" name="colourDepth">
                <UInput v-model="genericRegionFormState.colourDepth" placeholder="Colour depth" />
              </UFormField>
              <UFormField label="Line Colour" name="lineColour">
                <UInput v-model="genericRegionFormState.lineColour" placeholder="Line colour" />
              </UFormField>
            </div>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Colour" name="colour">
                <UInput v-model="genericRegionFormState.colour" placeholder="Colour" />
              </UFormField>
              <UFormField label="Pen Colour" name="penColour">
                <UInput v-model="genericRegionFormState.penColour" placeholder="Pen colour" />
              </UFormField>
            </div>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Text Colour RGB" name="textColourRgb">
                <UInput v-model.number="genericRegionFormState.textColourRgb" type="number" />
              </UFormField>
              <UFormField label="Background Colour RGB" name="bgColourRgb">
                <UInput v-model.number="genericRegionFormState.bgColourRgb" type="number" />
              </UFormField>
            </div>

            <div class="space-y-1">
              <UCheckbox v-model="genericRegionFormState.lineSeparators" label="Line separators" />
              <UCheckbox v-model="genericRegionFormState.borderPresent" label="Border present" />
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <span class="text-sm font-medium">Table Cell Role</span>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Row Index">
                  <UInput v-model.number="genericRegionFormState.tableCellRole.rowIndex" type="number" min="0" />
                </UFormField>
                <UFormField label="Column Index">
                  <UInput v-model.number="genericRegionFormState.tableCellRole.columnIndex" type="number" min="0" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Row Span">
                  <UInput v-model.number="genericRegionFormState.tableCellRole.rowSpan" type="number" min="0" />
                </UFormField>
                <UFormField label="Column Span">
                  <UInput v-model.number="genericRegionFormState.tableCellRole.colSpan" type="number" min="0" />
                </UFormField>
              </div>
              <UCheckbox v-model="genericRegionFormState.tableCellRole.header" label="Header cell" />
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Table Grid Rows</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addGridRow(genericRegionFormState.gridRows)"
                >
                  Add Row
                </UButton>
              </div>
              <div v-for="(row, rowIndex) in genericRegionFormState.gridRows" :key="`region-grid-row-${rowIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Row Index">
                  <UInput v-model.number="row.index" type="number" min="0" />
                </UFormField>
                <UFormField label="Points">
                  <UTextarea v-model="row.points" placeholder="x,y x,y x,y ..." :rows="2" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(genericRegionFormState.gridRows, rowIndex)"
                >
                  Remove Row
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Alternative Images</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addAlternativeImageRow(genericRegionFormState.alternativeImages)"
                >
                  Add
                </UButton>
              </div>
              <div v-for="(img, imgIndex) in genericRegionFormState.alternativeImages" :key="`region-alt-${imgIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Filename">
                  <UInput v-model="img.filename" placeholder="Image filename" />
                </UFormField>
                <UFormField label="Confidence">
                  <UInput
                    v-model.number="img.confidence"
                    type="number"
                    min="0"
                    max="1"
                    step="0.01"
                  />
                </UFormField>
                <UFormField label="Comments">
                  <UTextarea v-model="img.comments" placeholder="Comments..." :rows="2" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(genericRegionFormState.alternativeImages, imgIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Labels</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addLabelGroup(genericRegionFormState.labels)"
                >
                  Add Group
                </UButton>
              </div>
              <div v-for="(group, groupIndex) in genericRegionFormState.labels" :key="`region-label-group-${groupIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="External Model">
                  <UInput v-model="group.externalModel" placeholder="External model" />
                </UFormField>
                <UFormField label="External ID">
                  <UInput v-model="group.externalId" placeholder="External ID" />
                </UFormField>
                <UFormField label="Prefix">
                  <UInput v-model="group.prefix" placeholder="Prefix" />
                </UFormField>
                <UFormField label="Comments">
                  <UTextarea v-model="group.comments" placeholder="Comments..." :rows="2" />
                </UFormField>
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-medium">Labels</span>
                    <UButton
                      type="button"
                      size="xs"
                      variant="ghost"
                      @click="addLabelRow(group.labels)"
                    >
                      Add Label
                    </UButton>
                  </div>
                  <div v-for="(label, labelIndex) in group.labels" :key="`region-label-group-${groupIndex}-label-${labelIndex}`" class="space-y-2 rounded-md border border-border p-2">
                    <UFormField label="Value">
                      <UInput v-model="label.value" placeholder="Value" />
                    </UFormField>
                    <UFormField label="Type">
                      <UInput v-model="label.type" placeholder="Type" />
                    </UFormField>
                    <UFormField label="Comments">
                      <UInput v-model="label.comments" placeholder="Comments" />
                    </UFormField>
                    <UButton
                      type="button"
                      size="xs"
                      color="error"
                      variant="ghost"
                      @click="removeAt(group.labels, labelIndex)"
                    >
                      Remove Label
                    </UButton>
                  </div>
                </div>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(genericRegionFormState.labels, groupIndex)"
                >
                  Remove Group
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">User-Defined Attributes</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addUserAttributeRow(genericRegionFormState.userDefinedAttributes)"
                >
                  Add
                </UButton>
              </div>
              <div v-for="(attr, attrIndex) in genericRegionFormState.userDefinedAttributes" :key="`region-attr-${attrIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Name">
                  <UInput v-model="attr.name" placeholder="Attribute name" />
                </UFormField>
                <UFormField label="Description">
                  <UInput v-model="attr.description" placeholder="Description" />
                </UFormField>
                <UFormField label="Type">
                  <USelect v-model="attr.type" :items="userAttributeTypeOptions" placeholder="Select type" />
                </UFormField>
                <UFormField label="Value">
                  <UInput v-model="attr.value" placeholder="Value" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(genericRegionFormState.userDefinedAttributes, attrIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <span class="text-sm font-medium">Text Style</span>
              <UFormField label="Font Family">
                <UInput v-model="genericRegionFormState.textStyle.fontFamily" placeholder="Font family" />
              </UFormField>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Font Size">
                  <UInput v-model.number="genericRegionFormState.textStyle.fontSize" type="number" step="0.1" />
                </UFormField>
                <UFormField label="X Height">
                  <UInput v-model.number="genericRegionFormState.textStyle.xHeight" type="number" step="0.1" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Kerning">
                  <UInput v-model.number="genericRegionFormState.textStyle.kerning" type="number" step="0.1" />
                </UFormField>
                <UFormField label="Underline Style">
                  <UInput v-model="genericRegionFormState.textStyle.underlineStyle" placeholder="Underline style" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Text Colour">
                  <UInput v-model="genericRegionFormState.textStyle.textColour" placeholder="Named colour" />
                </UFormField>
                <UFormField label="Background Colour">
                  <UInput v-model="genericRegionFormState.textStyle.bgColour" placeholder="Named colour" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Text Colour RGB">
                  <UInput v-model.number="genericRegionFormState.textStyle.textColourRgb" type="number" />
                </UFormField>
                <UFormField label="Background Colour RGB">
                  <UInput v-model.number="genericRegionFormState.textStyle.bgColourRgb" type="number" />
                </UFormField>
              </div>
              <div class="space-y-1">
                <UCheckbox v-model="genericRegionFormState.textStyle.serif" label="Serif" />
                <UCheckbox v-model="genericRegionFormState.textStyle.monospace" label="Monospace" />
                <UCheckbox v-model="genericRegionFormState.textStyle.bold" label="Bold" />
                <UCheckbox v-model="genericRegionFormState.textStyle.italic" label="Italic" />
                <UCheckbox v-model="genericRegionFormState.textStyle.underlined" label="Underlined" />
                <UCheckbox v-model="genericRegionFormState.textStyle.subscript" label="Subscript" />
                <UCheckbox v-model="genericRegionFormState.textStyle.superscript" label="Superscript" />
                <UCheckbox v-model="genericRegionFormState.textStyle.strikethrough" label="Strikethrough" />
                <UCheckbox v-model="genericRegionFormState.textStyle.smallCaps" label="Small Caps" />
                <UCheckbox v-model="genericRegionFormState.textStyle.letterSpaced" label="Letter Spaced" />
                <UCheckbox v-model="genericRegionFormState.textStyle.reverseVideo" label="Reverse Video" />
              </div>
            </div>

            <div class="flex gap-2 pt-4 border-t border-border">
              <UButton type="submit" class="flex-1">
                <Icon name="lucide:save" class="w-4 h-4 mr-1" />Save
              </UButton>
              <UButton type="button" variant="outline" @click="cancelEditing">
                Cancel
              </UButton>
            </div>
          </UForm>

          <UForm
            v-else-if="editContext === 'textLine'"
            :schema="textLineMetadataSchema"
            :state="textLineFormState"
            class="space-y-4"
            @submit="onSubmitTextLine"
          >
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:type" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium">Text Line</span>
            </div>

            <UFormField label="ID" name="id">
              <UInput :model-value="textLineFormState.id" disabled class="bg-muted font-mono text-xs" />
            </UFormField>

            <UFormField label="Index" name="index">
              <UInput
                v-model.number="textLineFormState.index"
                type="number"
                min="0"
                placeholder="Position in region"
              />
            </UFormField>

            <UFormField label="Primary Language" name="primaryLanguage">
              <USelectMenu
                v-model="textLineFormState.primaryLanguage"
                :items="languageOptions"
                placeholder="Select language"
                :search-input="{ placeholder: 'Search language...' }"
                virtualize
                clear
              />
            </UFormField>

            <div class="grid grid-cols-2 gap-2">
              <UFormField label="Primary Script" name="primaryScript">
                <USelectMenu
                  v-model="textLineFormState.primaryScript"
                  :items="scriptOptions"
                  placeholder="Select script"
                  :search-input="{ placeholder: 'Search script...' }"
                  virtualize
                  clear
                />
              </UFormField>
              <UFormField label="Secondary Script" name="secondaryScript">
                <USelectMenu
                  v-model="textLineFormState.secondaryScript"
                  :items="scriptOptions"
                  placeholder="Select script"
                  :search-input="{ placeholder: 'Search script...' }"
                  virtualize
                  clear
                />
              </UFormField>
            </div>

            <UFormField label="Reading Direction" name="readingDirection">
              <USelect v-model="textLineFormState.readingDirection" :items="readingDirectionOptions" placeholder="Select direction" />
            </UFormField>

            <UFormField label="Production" name="production">
              <USelect v-model="textLineFormState.production" :items="productionOptions" placeholder="Select production" />
            </UFormField>

            <UFormField label="Comments" name="comments">
              <UTextarea v-model="textLineFormState.comments" placeholder="Comments..." :rows="2" />
            </UFormField>

            <UFormField label="Custom" name="custom">
              <UInput v-model="textLineFormState.custom" placeholder="Custom data" />
            </UFormField>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Alternative Images</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addAlternativeImageRow(textLineFormState.alternativeImages)"
                >
                  Add
                </UButton>
              </div>
              <div v-for="(img, imgIndex) in textLineFormState.alternativeImages" :key="`text-line-alt-${imgIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Filename">
                  <UInput v-model="img.filename" placeholder="Image filename" />
                </UFormField>
                <UFormField label="Confidence">
                  <UInput
                    v-model.number="img.confidence"
                    type="number"
                    min="0"
                    max="1"
                    step="0.01"
                  />
                </UFormField>
                <UFormField label="Comments">
                  <UTextarea v-model="img.comments" placeholder="Comments..." :rows="2" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(textLineFormState.alternativeImages, imgIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">Labels</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addLabelGroup(textLineFormState.labels)"
                >
                  Add Group
                </UButton>
              </div>
              <div v-for="(group, groupIndex) in textLineFormState.labels" :key="`text-line-label-group-${groupIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="External Model">
                  <UInput v-model="group.externalModel" placeholder="External model" />
                </UFormField>
                <UFormField label="External ID">
                  <UInput v-model="group.externalId" placeholder="External ID" />
                </UFormField>
                <UFormField label="Prefix">
                  <UInput v-model="group.prefix" placeholder="Prefix" />
                </UFormField>
                <UFormField label="Comments">
                  <UTextarea v-model="group.comments" placeholder="Comments..." :rows="2" />
                </UFormField>
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-medium">Labels</span>
                    <UButton
                      type="button"
                      size="xs"
                      variant="ghost"
                      @click="addLabelRow(group.labels)"
                    >
                      Add Label
                    </UButton>
                  </div>
                  <div v-for="(label, labelIndex) in group.labels" :key="`text-line-label-group-${groupIndex}-label-${labelIndex}`" class="space-y-2 rounded-md border border-border p-2">
                    <UFormField label="Value">
                      <UInput v-model="label.value" placeholder="Value" />
                    </UFormField>
                    <UFormField label="Type">
                      <UInput v-model="label.type" placeholder="Type" />
                    </UFormField>
                    <UFormField label="Comments">
                      <UInput v-model="label.comments" placeholder="Comments" />
                    </UFormField>
                    <UButton
                      type="button"
                      size="xs"
                      color="error"
                      variant="ghost"
                      @click="removeAt(group.labels, labelIndex)"
                    >
                      Remove Label
                    </UButton>
                  </div>
                </div>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(textLineFormState.labels, groupIndex)"
                >
                  Remove Group
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">User-Defined Attributes</span>
                <UButton
                  type="button"
                  size="xs"
                  variant="soft"
                  @click="addUserAttributeRow(textLineFormState.userDefinedAttributes)"
                >
                  Add
                </UButton>
              </div>
              <div v-for="(attr, attrIndex) in textLineFormState.userDefinedAttributes" :key="`text-line-attr-${attrIndex}`" class="space-y-2 rounded-md border border-border p-2">
                <UFormField label="Name">
                  <UInput v-model="attr.name" placeholder="Attribute name" />
                </UFormField>
                <UFormField label="Description">
                  <UInput v-model="attr.description" placeholder="Description" />
                </UFormField>
                <UFormField label="Type">
                  <USelect v-model="attr.type" :items="userAttributeTypeOptions" placeholder="Select type" />
                </UFormField>
                <UFormField label="Value">
                  <UInput v-model="attr.value" placeholder="Value" />
                </UFormField>
                <UButton
                  type="button"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="removeAt(textLineFormState.userDefinedAttributes, attrIndex)"
                >
                  Remove
                </UButton>
              </div>
            </div>

            <div class="space-y-2 rounded-md border border-border p-3">
              <span class="text-sm font-medium">Text Style</span>
              <UFormField label="Font Family">
                <UInput v-model="textLineFormState.textStyle.fontFamily" placeholder="Font family" />
              </UFormField>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Font Size">
                  <UInput v-model.number="textLineFormState.textStyle.fontSize" type="number" step="0.1" />
                </UFormField>
                <UFormField label="X Height">
                  <UInput v-model.number="textLineFormState.textStyle.xHeight" type="number" step="0.1" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Kerning">
                  <UInput v-model.number="textLineFormState.textStyle.kerning" type="number" step="0.1" />
                </UFormField>
                <UFormField label="Underline Style">
                  <UInput v-model="textLineFormState.textStyle.underlineStyle" placeholder="Underline style" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Text Colour">
                  <UInput v-model="textLineFormState.textStyle.textColour" placeholder="Named colour" />
                </UFormField>
                <UFormField label="Background Colour">
                  <UInput v-model="textLineFormState.textStyle.bgColour" placeholder="Named colour" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField label="Text Colour RGB">
                  <UInput v-model.number="textLineFormState.textStyle.textColourRgb" type="number" />
                </UFormField>
                <UFormField label="Background Colour RGB">
                  <UInput v-model.number="textLineFormState.textStyle.bgColourRgb" type="number" />
                </UFormField>
              </div>
              <div class="space-y-1">
                <UCheckbox v-model="textLineFormState.textStyle.serif" label="Serif" />
                <UCheckbox v-model="textLineFormState.textStyle.monospace" label="Monospace" />
                <UCheckbox v-model="textLineFormState.textStyle.bold" label="Bold" />
                <UCheckbox v-model="textLineFormState.textStyle.italic" label="Italic" />
                <UCheckbox v-model="textLineFormState.textStyle.underlined" label="Underlined" />
                <UCheckbox v-model="textLineFormState.textStyle.subscript" label="Subscript" />
                <UCheckbox v-model="textLineFormState.textStyle.superscript" label="Superscript" />
                <UCheckbox v-model="textLineFormState.textStyle.strikethrough" label="Strikethrough" />
                <UCheckbox v-model="textLineFormState.textStyle.smallCaps" label="Small Caps" />
                <UCheckbox v-model="textLineFormState.textStyle.letterSpaced" label="Letter Spaced" />
                <UCheckbox v-model="textLineFormState.textStyle.reverseVideo" label="Reverse Video" />
              </div>
            </div>

            <div class="flex gap-2 pt-4 border-t border-border">
              <UButton type="submit" class="flex-1">
                <Icon name="lucide:save" class="w-4 h-4 mr-1" />Save
              </UButton>
              <UButton type="button" variant="outline" @click="cancelEditing">
                Cancel
              </UButton>
            </div>
          </UForm>

          <UForm
            v-else-if="editContext === 'baseline'"
            :schema="baselineMetadataSchema"
            :state="baselineFormState"
            class="space-y-4"
            @submit="onSubmitBaseline"
          >
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:minus" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium">Baseline</span>
            </div>

            <p class="text-sm text-muted-foreground">
              Baselines have minimal editable metadata. Only confidence can be set.
            </p>

            <UFormField label="Confidence" name="conf">
              <UInput
                v-model.number="baselineFormState.conf"
                type="number"
                step="0.01"
                min="0"
                max="1"
                placeholder="0.0 - 1.0"
              />
            </UFormField>

            <div class="flex gap-2 pt-4 border-t border-border">
              <UButton type="submit" class="flex-1">
                <Icon name="lucide:save" class="w-4 h-4 mr-1" />Save
              </UButton>
              <UButton type="button" variant="outline" @click="cancelEditing">
                Cancel
              </UButton>
            </div>
          </UForm>
        </template>

        <template v-else>
          <template v-if="selectedElement && isBaselineElement(selectedElement)">
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:minus" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium">Baseline</span>
            </div>
            <div class="space-y-1.5 text-sm">
              <div class="flex justify-between">
                <span class="text-muted-foreground">ID</span><span class="font-mono text-xs">{{ selectedElement.id }}</span>
              </div>
            </div>
          </template>

          <template v-else-if="selectedElement && isTextLineElement(selectedElement)">
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:type" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium truncate">Text Line</span>
            </div>
            <div class="space-y-1.5 text-sm">
              <div class="flex justify-between">
                <span class="text-muted-foreground">ID</span><span class="font-mono text-xs truncate ml-2">{{ selectedElement.id }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Index</span><span>{{ formatNumber(selectedElement.index) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Primary Language</span><span>{{ selectedElement.primaryLanguage || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Primary Script</span><span>{{ selectedElement.primaryScript || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Secondary Script</span><span>{{ selectedElement.secondaryScript || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Reading Direction</span><span>{{ selectedElement.readingDirection || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Production</span><span>{{ selectedElement.production || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Comments</span><span>{{ selectedElement.comments || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Custom</span><span class="truncate ml-2">{{ selectedElement.custom || '—' }}</span>
              </div>
            </div>
          </template>

          <template v-else-if="selectedElement && isRegionElement(selectedElement)">
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon :name="isTextRegion(selectedElement) ? 'lucide:box' : 'lucide:square'" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium truncate">{{ selectedElement.kind.replace(/Region$/, '') }} Region</span>
            </div>
            <div class="space-y-1.5 text-sm">
              <div class="flex justify-between">
                <span class="text-muted-foreground">ID</span><span class="font-mono text-xs truncate ml-2">{{ selectedElement.id }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Kind</span><span>{{ selectedElement.kind }}</span>
              </div>

              <template v-if="isTextRegion(selectedElement)">
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Type</span><span>{{ selectedElement.type || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Orientation</span><span>{{ formatNumber(selectedElement.orientation) }}°</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Reading Orientation</span><span>{{ formatNumber(selectedElement.readingOrientation) }}°</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Primary Language</span><span>{{ selectedElement.primaryLanguage || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Secondary Language</span><span>{{ selectedElement.secondaryLanguage || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Primary Script</span><span>{{ selectedElement.primaryScript || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Secondary Script</span><span>{{ selectedElement.secondaryScript || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Reading Direction</span><span>{{ selectedElement.readingDirection || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Text Line Order</span><span>{{ selectedElement.textLineOrder || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Production</span><span>{{ selectedElement.production || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Align</span><span>{{ selectedElement.align || '—' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Leading</span><span>{{ formatNumber(selectedElement.leading) }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">Indented</span><span>{{ formatBoolean(selectedElement.indented) }}</span>
                </div>
              </template>

              <div class="flex justify-between">
                <span class="text-muted-foreground">Continuation</span><span>{{ formatBoolean(selectedElement.continuation) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Comments</span><span>{{ selectedElement.comments || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Custom</span><span class="truncate ml-2">{{ selectedElement.custom || '—' }}</span>
              </div>
            </div>
          </template>

          <template v-else-if="page">
            <div class="flex items-center gap-2 pb-2 border-b border-border">
              <Icon name="lucide:file-image" class="w-4 h-4 text-muted-foreground" />
              <span class="text-sm font-medium">Page</span>
            </div>
            <div class="space-y-1.5 text-sm">
              <div class="flex justify-between">
                <span class="text-muted-foreground">Image</span><span class="truncate ml-2">{{ page.imageFilename }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Dimensions</span><span>{{ page.imageWidth }} × {{ page.imageHeight }}px</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">X Resolution</span><span>{{ formatNumber(page.imageXResolution) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Y Resolution</span><span>{{ formatNumber(page.imageYResolution) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Resolution Unit</span><span>{{ page.imageResolutionUnit || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Type</span><span>{{ page.type || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Orientation</span><span>{{ formatNumber(page.orientation) }}°</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Primary Language</span><span>{{ page.primaryLanguage || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Secondary Language</span><span>{{ page.secondaryLanguage || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Primary Script</span><span>{{ page.primaryScript || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Secondary Script</span><span>{{ page.secondaryScript || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Reading Direction</span><span>{{ page.readingDirection || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Text Line Order</span><span>{{ page.textLineOrder || '—' }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Confidence</span><span>{{ formatConfidence(page.conf) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">Custom</span><span class="truncate ml-2">{{ page.custom || '—' }}</span>
              </div>
            </div>

            <template v-if="document?.metadata">
              <div class="mt-4 pt-4 border-t border-border">
                <div class="flex items-center gap-2 pb-2 border-b border-border">
                  <Icon name="lucide:book-open" class="w-4 h-4 text-muted-foreground" />
                  <span class="text-sm font-medium">Document Metadata</span>
                </div>
                <div class="space-y-1.5 text-sm mt-2">
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">Creator</span><span>{{ document.metadata.creator || '—' }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">Created</span><span>{{ formatDate(document.metadata.created) }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">Last Change</span><span>{{ formatDate(document.metadata.lastChange) }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">Comments</span><span>{{ document.metadata.comments || '—' }}</span>
                  </div>
                  <div class="flex justify-between">
                    <span class="text-muted-foreground">External Ref</span><span class="truncate ml-2">{{ document.metadata.externalRef || '—' }}</span>
                  </div>
                </div>
              </div>
            </template>
          </template>

          <UEmpty
            v-else
            icon="i-lucide-file-question"
            title="No metadata available"
            description="Load a document to view metadata."
          />
        </template>
      </div>
    </div>

    <div class="p-3 border-t border-border bg-background">
      <UButton
        v-if="!isEditing"
        class="w-full"
        size="sm"
        :disabled="!canEdit"
        @click="startEditing"
      >
        <Icon name="lucide:pencil" class="w-3.5 h-3.5 mr-1.5" />
        Edit Metadata
      </UButton>
      <UButton
        v-else
        class="w-full"
        size="sm"
        variant="outline"
        @click="cancelEditing"
      >
        <Icon name="lucide:x" class="w-3.5 h-3.5 mr-1.5" />
        Cancel Editing
      </UButton>
    </div>
  </div>
</template>
