import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { LabelSet } from '../../../models/editor/labels'
import type { PageData } from '../types'

async function createStores() {
  const { useEditorSessionStore } = await import('../editor.session.store')
  const { useEditorDocumentStore } = await import('../editor.document.store')
  return {
    sessionStore: useEditorSessionStore(),
    store: useEditorDocumentStore()
  }
}

function createPage(projectId: string, pageId: string, label = pageId): PageData {
  return {
    id: pageId,
    projectId,
    projectName: projectId,
    label,
    thumbnail: undefined,
    imageVariants: [
      {
        id: `${pageId}-img`,
        url: `/images/${pageId}.jpg`,
        label: 'default'
      }
    ],
    xmlFiles: [],
    tags: [],
    resolvedTags: null,
    locked: false,
    lockedReason: null
  }
}

describe('editor.document.store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('preserves the configured default label when loading an API label set', async () => {
    const { store } = await createStores()

    store.setLabelSetFromApi({
      id: 'labels',
      meta: { name: 'Labels', defaultLabelId: 'image' },
      labels: [
        {
          id: 'paragraph',
          scope: 'region',
          name: 'Paragraph',
          color: '#123456',
          hasText: true,
          isContainer: false,
          mapping: { pageXml: { regionType: 'TextRegion', textType: 'paragraph', customKey: 'structure' } }
        },
        {
          id: 'image',
          scope: 'region',
          name: 'Image',
          color: '#654321',
          hasText: false,
          isContainer: false,
          mapping: { pageXml: { regionType: 'ImageRegion', customKey: 'structure' } }
        }
      ],
      created: '',
      updated: ''
    })

    expect(store.labelSet?.defaultLabelId).toBe('image')
    expect(store.labelSet?.getDefaultLabel()?.id).toBe('image')
  })

  it('keeps pages and metadata isolated per project and swaps active context', async () => {
    const { sessionStore, store } = await createStores()

    sessionStore.addOpenedProject('project-a')
    sessionStore.addOpenedProject('project-b')

    store.setProjectPages('project-a', [createPage('project-a', 'a-1')], { replaceProject: true })
    store.setProjectPages('project-b', [createPage('project-b', 'b-1')], { replaceProject: true })
    store.setLabelSet(new LabelSet('label-a', 'Label A', []), 'project-a')
    store.setLabelSet(new LabelSet('label-b', 'Label B', []), 'project-b')
    store.setProjectCodec('codec-a', ['a'], 'project-a')
    store.setProjectCodec('codec-b', ['b'], 'project-b')
    store.setProjectDictionary({ id: 'dictionary-a', forms: [], canEdit: true, locked: false }, 'project-a')
    store.setProjectDictionary({ id: 'dictionary-b', forms: [], canEdit: false, locked: true }, 'project-b')
    store.setProjectVirtualKeyboard('keyboard-a', 'project-a')
    store.setProjectVirtualKeyboard('keyboard-b', 'project-b')
    store.setProjectTextIndexDefaults({ gtIndex: 0, recognitionIndices: [1, 2] }, 'project-a')
    store.setProjectTextIndexDefaults({ gtIndex: 3, recognitionIndices: [4] }, 'project-b')

    sessionStore.setActiveProject('project-a')
    await nextTick()
    expect(store.pages.map(page => page.id)).toEqual(['a-1'])
    expect(store.labelSet?.id).toBe('label-a')
    expect(store.projectCodecId).toBe('codec-a')
    expect(store.projectCodecCharacters).toEqual(['a'])
    expect(store.projectDictionaryId).toBe('dictionary-a')
    expect(store.projectDictionaryCanEdit).toBe(true)
    expect(store.projectVirtualKeyboardId).toBe('keyboard-a')
    expect(store.projectTextDefaultGtIndex).toBe(0)
    expect(store.projectTextDefaultRecognitionIndices).toEqual([1, 2])

    sessionStore.setActiveProject('project-b')
    await nextTick()
    expect(store.pages.map(page => page.id)).toEqual(['b-1'])
    expect(store.labelSet?.id).toBe('label-b')
    expect(store.projectCodecId).toBe('codec-b')
    expect(store.projectCodecCharacters).toEqual(['b'])
    expect(store.projectDictionaryId).toBe('dictionary-b')
    expect(store.projectDictionaryCanEdit).toBe(false)
    expect(store.projectDictionaryLocked).toBe(true)
    expect(store.projectVirtualKeyboardId).toBe('keyboard-b')
    expect(store.projectTextDefaultGtIndex).toBe(3)
    expect(store.projectTextDefaultRecognitionIndices).toEqual([4])

    expect(store.getPage('a-1', 'project-a')?.id).toBe('a-1')
    expect(store.getPage('a-1', 'project-b')).toBeUndefined()
  })

  it('supports per-project page append/remove and selected variant isolation', async () => {
    const { sessionStore, store } = await createStores()

    sessionStore.addOpenedProject('project-a')
    sessionStore.addOpenedProject('project-b')

    store.setProjectPages('project-a', [createPage('project-a', 'a-1')], { replaceProject: true })
    store.appendProjectPages('project-a', [createPage('project-a', 'a-2')])
    store.setProjectPages('project-b', [createPage('project-b', 'b-1')], { replaceProject: true })

    store.setSelectedVariantOverride('a-1', 'variant-a1', 'project-a')
    store.setSelectedVariantOverride('b-1', 'variant-b1', 'project-b')

    sessionStore.setActiveProject('project-a')
    await nextTick()
    expect(store.getProjectPages('project-a').map(page => page.id)).toEqual(['a-1', 'a-2'])
    expect(store.selectedVariantIdByPageId).toEqual({ 'a-1': 'variant-a1' })

    sessionStore.setActiveProject('project-b')
    await nextTick()
    expect(store.getProjectPages('project-b').map(page => page.id)).toEqual(['b-1'])
    expect(store.selectedVariantIdByPageId).toEqual({ 'b-1': 'variant-b1' })

    store.removeProject('project-a')
    expect(store.getProjectPages('project-a')).toEqual([])
    expect(store.getProjectPages('project-b').map(page => page.id)).toEqual(['b-1'])
  })

  it('preserves page summary state when enriching its editor data', async () => {
    const { store } = await createStores()
    const page = {
      ...createPage('project-a', 'a-1'),
      workflowState: 'DONE' as const,
      imageCount: 2,
      xmlFileCount: 1
    }
    store.setProjectPages('project-a', [page], { replaceProject: true })

    store.enrichPage('a-1', {
      ...createPage('project-a', 'a-1'),
      imageVariants: [{ id: 'full-image', url: '/images/full.jpg', label: 'full' }]
    }, 'project-a')

    expect(store.getPage('a-1', 'project-a')).toMatchObject({
      workflowState: 'DONE',
      imageCount: 2,
      xmlFileCount: 1,
      imageVariants: [{ id: 'full-image' }]
    })
  })

  it('preserves incoming project page order when initializing an editor session', async () => {
    const { sessionStore, store } = await createStores()

    store.setPagesWithSession([
      createPage('project-1234', 'page-0020', '0020'),
      createPage('project-1234', 'page-0013', '0013'),
      createPage('project-1234', 'page-0100', '0100')
    ], 'project-1234', 'workspace-1')

    expect(store.getProjectPages('project-1234').map(page => page.label)).toEqual(['0020', '0013', '0100'])
    expect(sessionStore.getOpenedPageIds('project-1234')).toEqual(['page-0020'])
    expect(sessionStore.getActivePageId('project-1234')).toBe('page-0020')
  })

  it('returns null active metadata when active project has no metadata configured', async () => {
    const { sessionStore, store } = await createStores()

    sessionStore.addOpenedProject('project-a')
    sessionStore.addOpenedProject('project-b')
    store.setLabelSet(new LabelSet('label-a', 'Label A', []), 'project-a')
    store.setProjectCodec('codec-a', ['x'], 'project-a')

    sessionStore.setActiveProject('project-b')
    await nextTick()
    expect(store.labelSet).toBeNull()
    expect(store.projectCodecId).toBeNull()
    expect(store.projectCodecCharacters).toEqual([])
  })

  it('stores effective toolkit selections and defaults separately per project', async () => {
    const { sessionStore, store } = await createStores()

    sessionStore.addOpenedProject('project-a')
    sessionStore.addOpenedProject('project-b')

    store.setProjectToolkitSettings({
      codecId: null,
      dictionaryId: null,
      virtualKeyboardId: null,
      allowCodecOverride: true,
      allowDictionaryOverride: true,
      allowVirtualKeyboardOverride: true
    }, 'project-a')
    store.setProjectToolkitSettings({
      codecId: 'codec-default-b',
      dictionaryId: 'dictionary-default-b',
      virtualKeyboardId: 'keyboard-default-b',
      allowCodecOverride: false,
      allowDictionaryOverride: false,
      allowVirtualKeyboardOverride: false
    }, 'project-b')

    store.setProjectCodec('codec-temp-a', ['a'], 'project-a')
    store.setProjectDictionary({ id: 'dictionary-temp-a', forms: [], locked: false }, 'project-a')
    store.setProjectVirtualKeyboard('keyboard-temp-a', 'project-a')
    store.setProjectCodec('codec-default-b', ['b'], 'project-b')
    store.setProjectDictionary({ id: 'dictionary-default-b', forms: [], locked: false }, 'project-b')
    store.setProjectVirtualKeyboard('keyboard-default-b', 'project-b')

    sessionStore.setActiveProject('project-a')
    await nextTick()
    expect(store.projectToolkitSettings.allowCodecOverride).toBe(true)
    expect(store.projectCodecId).toBe('codec-temp-a')
    expect(store.projectDictionaryId).toBe('dictionary-temp-a')
    expect(store.projectVirtualKeyboardId).toBe('keyboard-temp-a')

    sessionStore.setActiveProject('project-b')
    await nextTick()
    expect(store.projectToolkitSettings.allowCodecOverride).toBe(false)
    expect(store.projectToolkitSettings.allowDictionaryOverride).toBe(false)
    expect(store.projectToolkitSettings.allowVirtualKeyboardOverride).toBe(false)
    expect(store.projectCodecId).toBe('codec-default-b')
    expect(store.projectDictionaryId).toBe('dictionary-default-b')
    expect(store.projectVirtualKeyboardId).toBe('keyboard-default-b')
  })
})
