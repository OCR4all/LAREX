import type { Ref } from 'vue'
import { useEditorPageLink } from '@/composables/editor/use-editor-page-link'
import type { Region } from '@/models/editor/region'
import type { ReadingOrder } from '@/models/editor'
import type { AnnotationApiMode } from '@/stores/editor/types'
import type { ActionTargetSelection } from '@/types/action'
import type { ExportPageRef, ImageVariantSelection } from '@/types/project-page'
import { collectPageText } from '@/utils/editor/page-text'

export type SaveBeforePrompt = {
  title: string
  message: (pageNames: string) => string
  confirmLabel: string
  failureDescription: string
}

export type ActivePageCommand = {
  id: string
  label: string
  icon: string
  suffix: string
  disabled: boolean
  onSelect: () => void
}

type ActivePageCommandsOptions = {
  activeCanvasId: Readonly<Ref<string | null | undefined>>
  activePage: Readonly<Ref<{ regions?: Region[], readingOrder?: ReadingOrder } | null | undefined>>
  activeAnnotationMode: Readonly<Ref<AnnotationApiMode | null>>
  activeCanvasCanEdit: Readonly<Ref<boolean>>
  activeCanvasIsComparison: Readonly<Ref<boolean>>
  isActivePageLocked: Readonly<Ref<boolean>>
  canManageDatasets: Readonly<Ref<boolean>>
  getProjectTitle: (projectId: string) => string
  saveBeforeContinuing: (canvasIds: string[], prompt: SaveBeforePrompt) => Promise<boolean>
  exportPage: (projectId: string, page: ExportPageRef, initialImageVariantSelection?: ImageVariantSelection) => Promise<void>
  runAction: (payload: { targetSelection: ActionTargetSelection, targetSummary: string }) => Promise<void>
  openAddToDataset: (props: {
    projectId: string
    projectName: string
    pages: Array<{ id: string, name: string }>
  }) => void
}

export function useActivePageCommands(options: ActivePageCommandsOptions) {
  const editorStore = useEditorStore()
  const editorUiStore = useEditorUiStore()
  const toast = useToast()
  const { getPageLink } = useEditorPageLink()

  const target = computed(() => {
    const canvasId = options.activeCanvasId.value
    const canvas = canvasId ? editorStore.canvases[canvasId] : undefined
    if (!canvasId || !canvas?.projectId || !canvas.pageId || options.activeCanvasIsComparison.value) return null
    const page = editorStore.getPage(canvas.pageId, canvas.projectId)
    return {
      canvasId,
      projectId: canvas.projectId,
      pageId: canvas.pageId,
      name: page?.label ?? canvas.pageId,
      imageVariantId: canvas.imageVariantId ?? null,
      imageVariants: page?.imageVariants ?? [],
      hasAnnotation: Boolean(canvas.xmlFileId)
    }
  })
  const usesProjectAnnotation = computed(() => target.value !== null && options.activeAnnotationMode.value !== 'DATASET_COPY')

  async function saveIfEditable(canvasId: string, prompt: SaveBeforePrompt): Promise<boolean> {
    if (!options.activeCanvasCanEdit.value) return true
    return options.saveBeforeContinuing([canvasId], prompt)
  }

  async function exportActivePage() {
    const current = target.value
    if (!current || !usesProjectAnnotation.value || !current.hasAnnotation) return
    if (!await saveIfEditable(current.canvasId, {
      title: 'Save before exporting?',
      message: names => `Exports use the saved annotation. Save changes for ${names} before continuing?`,
      confirmLabel: 'Save and Continue',
      failureDescription: 'Could not save the annotation. Please try again before exporting.'
    })) return

    const displayedVariant = current.imageVariants.find(variant => variant.id === current.imageVariantId)?.type
    await options.exportPage(
      current.projectId,
      {
        id: current.pageId,
        name: current.name,
        imageVariants: current.imageVariants.map(variant => ({
          id: variant.id,
          fileName: variant.fileName ?? variant.label,
          variant: variant.type,
          imageType: variant.imageType
        }))
      },
      displayedVariant ? { mode: 'GLOBAL', variant: displayedVariant, fallbackImage: false } : undefined
    )
  }

  async function runActionOnActivePage() {
    const current = target.value
    if (!current || !usesProjectAnnotation.value) return
    await options.runAction({
      targetSelection: {
        type: 'PAGE',
        pages: [{ pageId: current.pageId, regionIds: [], textLineIds: [] }]
      },
      targetSummary: 'Current page'
    })
  }

  async function addActivePageToDataset() {
    const current = target.value
    if (!current || !usesProjectAnnotation.value || !options.canManageDatasets.value) return
    if (!await saveIfEditable(current.canvasId, {
      title: 'Save before adding to a dataset?',
      message: names => `Datasets use the saved annotation. Save changes for ${names} before continuing?`,
      confirmLabel: 'Save and Continue',
      failureDescription: 'Could not save the annotation. Please try again before adding the page to a dataset.'
    })) return

    options.openAddToDataset({
      projectId: current.projectId,
      projectName: options.getProjectTitle(current.projectId),
      pages: [{ id: current.pageId, name: current.name }]
    })
  }

  async function copyActivePageText() {
    const page = options.activePage.value
    const text = page
      ? collectPageText(
          page,
          editorStore.projectTextDefaultGtIndex ?? 0,
          editorUiStore.textlineSortOrder
        )
      : ''
    if (!text) {
      toast.add({ title: 'No text to copy', description: 'This page has no transcribed text lines.', color: 'warning' })
      return
    }
    await copyTextToClipboard(text, { successTitle: 'Page text copied', failureTitle: 'Copy failed' })
  }

  async function copyActivePageLink() {
    const current = target.value
    if (!current) return
    await copyTextToClipboard(
      getPageLink({ projectId: current.projectId, pageId: current.pageId, variantId: current.imageVariantId }),
      { successTitle: 'Page link copied', failureTitle: 'Copy failed' }
    )
  }

  async function copyActivePageId() {
    const current = target.value
    if (!current) return
    await copyTextToClipboard(current.pageId, { successTitle: 'Page ID copied', failureTitle: 'Copy failed' })
  }

  const processCommands = computed<ActivePageCommand[]>(() => [
    {
      id: 'page-run-action',
      label: 'Run Action',
      icon: 'i-lucide-wand-sparkles',
      suffix: 'Run a processor on the active page',
      disabled: !usesProjectAnnotation.value || options.isActivePageLocked.value,
      onSelect: () => { void runActionOnActivePage() }
    },
    {
      id: 'page-add-to-dataset',
      label: 'Add to Dataset',
      icon: 'i-lucide-database',
      suffix: 'Add the active page to a dataset',
      disabled: !usesProjectAnnotation.value || !options.canManageDatasets.value,
      onSelect: () => { void addActivePageToDataset() }
    }
  ])

  const exportCommand = computed<ActivePageCommand>(() => ({
    id: 'page-export',
    label: 'Export Page',
    icon: 'i-lucide-file-output',
    suffix: 'Convert the active page to PDF, DOCX, ALTO and more',
    disabled: !usesProjectAnnotation.value || !target.value?.hasAnnotation,
    onSelect: () => { void exportActivePage() }
  }))

  const copyCommands = computed<ActivePageCommand[]>(() => [
    {
      id: 'page-copy-text',
      label: 'Copy Page Text',
      icon: 'i-lucide-clipboard-type',
      suffix: 'Copy the text of the active page in reading order',
      disabled: !target.value || !options.activePage.value,
      onSelect: () => { void copyActivePageText() }
    },
    {
      id: 'page-copy-link',
      label: 'Copy Page Link',
      icon: 'i-lucide-link',
      suffix: 'Copy a link that opens the active page in the editor',
      disabled: !target.value,
      onSelect: () => { void copyActivePageLink() }
    },
    {
      id: 'page-copy-id',
      label: 'Copy Page ID',
      icon: 'i-lucide-copy',
      suffix: 'Copy the ID of the active page',
      disabled: !target.value,
      onSelect: () => { void copyActivePageId() }
    }
  ])

  const commands = computed(() => [exportCommand.value, ...processCommands.value, ...copyCommands.value])

  return { commands, copyCommands, exportCommand, processCommands }
}
