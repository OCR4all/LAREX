import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Region } from '@/models/editor/region'
import type { AnnotationApiMode } from '@/stores/editor/types'
import { useActivePageCommands } from '../use-active-page-commands'

const copyTextToClipboard = vi.fn(async () => true)
const addToast = vi.fn()

function setup(overrides: {
  mode?: AnnotationApiMode
  canEdit?: boolean
  comparison?: boolean
  locked?: boolean
  canManageDatasets?: boolean
  xmlFileId?: string | null
  saveResult?: boolean
  regions?: Region[]
} = {}) {
  const canvases = {
    canvas: {
      projectId: 'project',
      pageId: 'page',
      imageVariantId: 'image-bin',
      xmlFileId: 'xmlFileId' in overrides ? overrides.xmlFileId : 'xml'
    }
  }
  vi.stubGlobal('useEditorStore', () => ({
    canvases,
    projectTextDefaultGtIndex: 0,
    getPage: () => ({
      label: 'Page 1',
      imageVariants: [
        { id: 'image-raw', url: '', fileName: '0001.png', label: '0001.png' },
        { id: 'image-bin', url: '', fileName: '0001.bin.png', label: 'bin', type: 'bin' }
      ]
    })
  }))
  const saveBeforeContinuing = vi.fn(async () => overrides.saveResult ?? true)
  const exportPage = vi.fn(async () => {})
  const runAction = vi.fn(async () => {})
  const openAddToDataset = vi.fn()
  const { commands } = useActivePageCommands({
    activeCanvasId: ref('canvas'),
    activePage: shallowRef({ regions: overrides.regions ?? [] }),
    activeAnnotationMode: ref(overrides.mode ?? 'PROJECT'),
    activeCanvasCanEdit: ref(overrides.canEdit ?? true),
    activeCanvasIsComparison: ref(overrides.comparison ?? false),
    isActivePageLocked: ref(overrides.locked ?? false),
    canManageDatasets: ref(overrides.canManageDatasets ?? true),
    getProjectTitle: () => 'Project',
    saveBeforeContinuing,
    exportPage,
    runAction,
    openAddToDataset
  })
  const command = (id: string) => commands.value.find(item => item.id === id)!
  return { command, commands, exportPage, openAddToDataset, runAction, saveBeforeContinuing }
}

beforeEach(() => {
  vi.stubGlobal('useEditorUiStore', () => ({ textlineSortOrder: 'top-to-bottom' }))
  vi.stubGlobal('useToast', () => ({ add: addToast }))
  vi.stubGlobal('useRouter', () => ({
    resolve: ({ path, query }: { path: string, query: Record<string, string> }) => ({ href: `${path}?${new URLSearchParams(query)}` })
  }))
  vi.stubGlobal('copyTextToClipboard', copyTextToClipboard)
})

afterEach(() => {
  vi.clearAllMocks()
  vi.unstubAllGlobals()
})

describe('useActivePageCommands', () => {
  it('saves first, then exports with the displayed image variant preselected', async () => {
    const { command, exportPage, saveBeforeContinuing } = setup()
    command('page-export').onSelect()
    await vi.waitFor(() => expect(exportPage).toHaveBeenCalledOnce())

    expect(saveBeforeContinuing).toHaveBeenCalledWith(['canvas'], expect.objectContaining({ title: 'Save before exporting?' }))
    expect(exportPage).toHaveBeenCalledWith('project', expect.objectContaining({ id: 'page', name: 'Page 1' }), {
      mode: 'GLOBAL',
      variant: 'bin',
      fallbackImage: false
    })
  })

  it('does not export when saving is cancelled or fails', async () => {
    const { command, exportPage, saveBeforeContinuing } = setup({ saveResult: false })
    command('page-export').onSelect()
    await vi.waitFor(() => expect(saveBeforeContinuing).toHaveBeenCalledOnce())
    await nextTick()
    expect(exportPage).not.toHaveBeenCalled()
  })

  it('exports the saved state without a save prompt for viewers', async () => {
    const { command, exportPage, saveBeforeContinuing } = setup({ canEdit: false })
    command('page-export').onSelect()
    await vi.waitFor(() => expect(exportPage).toHaveBeenCalledOnce())
    expect(saveBeforeContinuing).not.toHaveBeenCalled()
  })

  it('disables project-based commands for frozen dataset copies', () => {
    const { command } = setup({ mode: 'DATASET_COPY' })
    expect(command('page-export').disabled).toBe(true)
    expect(command('page-run-action').disabled).toBe(true)
    expect(command('page-add-to-dataset').disabled).toBe(true)
    expect(command('page-copy-text').disabled).toBe(false)
  })

  it('reflects missing annotations, locks and permissions', () => {
    const { command } = setup({ xmlFileId: null, locked: true, canManageDatasets: false })
    expect(command('page-export').disabled).toBe(true)
    expect(command('page-run-action').disabled).toBe(true)
    expect(command('page-add-to-dataset').disabled).toBe(true)
  })

  it('disables everything on comparison canvases', () => {
    const { commands } = setup({ comparison: true })
    expect(commands.value.every(item => item.disabled)).toBe(true)
  })

  it('targets the whole page when running an Action', () => {
    const { command, runAction } = setup()
    command('page-run-action').onSelect()
    expect(runAction).toHaveBeenCalledWith({
      targetSelection: { type: 'PAGE', pages: [{ pageId: 'page', regionIds: [], textLineIds: [] }] },
      targetSummary: 'Current page'
    })
  })

  it('adds only the active page to a dataset', async () => {
    const { command, openAddToDataset } = setup()
    command('page-add-to-dataset').onSelect()
    await vi.waitFor(() => expect(openAddToDataset).toHaveBeenCalledWith({
      projectId: 'project',
      projectName: 'Project',
      pages: [{ id: 'page', name: 'Page 1' }]
    }))
  })

  it('copies the link with the displayed variant', async () => {
    const { command } = setup()
    command('page-copy-link').onSelect()
    await vi.waitFor(() => expect(copyTextToClipboard).toHaveBeenCalledWith(
      '/editor?projectId=project&pageId=page&variantId=image-bin',
      expect.objectContaining({ successTitle: 'Page link copied' })
    ))
  })

  it('reports pages without text instead of copying nothing', async () => {
    const { command } = setup()
    command('page-copy-text').onSelect()
    await vi.waitFor(() => expect(addToast).toHaveBeenCalledWith(expect.objectContaining({ title: 'No text to copy' })))
    expect(copyTextToClipboard).not.toHaveBeenCalled()
  })
})
