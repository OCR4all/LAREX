import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, defineStore, setActivePinia } from 'pinia'
import { computed, nextTick, ref, watch } from 'vue'
import { LabelSet } from '../../../models/editor/labels'
import type { PageData } from '../types'

;(globalThis as any).defineStore = defineStore
;(globalThis as any).ref = ref
;(globalThis as any).computed = computed
;(globalThis as any).watch = watch

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
    store.setProjectTextIndexDefaults({ gtIndex: 0, recognitionIndices: [1, 2] }, 'project-a')
    store.setProjectTextIndexDefaults({ gtIndex: 3, recognitionIndices: [4] }, 'project-b')

    sessionStore.setActiveProject('project-a')
    await nextTick()
    expect(store.pages.map(page => page.id)).toEqual(['a-1'])
    expect(store.labelSet?.id).toBe('label-a')
    expect(store.projectCodecId).toBe('codec-a')
    expect(store.projectCodecCharacters).toEqual(['a'])
    expect(store.projectTextDefaultGtIndex).toBe(0)
    expect(store.projectTextDefaultRecognitionIndices).toEqual([1, 2])

    sessionStore.setActiveProject('project-b')
    await nextTick()
    expect(store.pages.map(page => page.id)).toEqual(['b-1'])
    expect(store.labelSet?.id).toBe('label-b')
    expect(store.projectCodecId).toBe('codec-b')
    expect(store.projectCodecCharacters).toEqual(['b'])
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
})
