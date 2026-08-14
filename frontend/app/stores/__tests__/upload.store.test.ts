import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { UploadUiFile } from '../upload.store'

async function createStore() {
  const vue = await import('vue')
  const pinia = await import('pinia')
  ;(globalThis as any).ref = vue.ref
  ;(globalThis as any).shallowRef = vue.shallowRef
  ;(globalThis as any).triggerRef = vue.triggerRef
  ;(globalThis as any).computed = vue.computed
  pinia.setActivePinia(pinia.createPinia())
  const { useUploadStore } = await import('../upload.store')
  return useUploadStore()
}

function createFiles(count: number): UploadUiFile[] {
  return Array.from({ length: count }, (_, index) => ({
    id: `file-${index}`,
    fileName: `page-${index}.png`,
    fileSize: 1024,
    mimeType: 'image/png',
    baseName: `page-${index}`,
    variant: 'png',
    status: 'pending',
    progress: 0,
    chunksReceived: 0,
    totalChunks: 1
  }))
}

describe('upload.store large upload state', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('retains only bounded metadata while preserving the full file count', async () => {
    const store = await createStore()
    const files = createFiles(1500)
    Object.assign(files[0]!, { file: new Blob(['large local file']) })

    store.registerUpload('session-1', 'project-1', 'Project', 'workspace-1', files)

    const upload = store.activeUploads.get('session-1')
    expect(upload?.totalFiles).toBe(1500)
    expect(upload?.files).toHaveLength(20)
    expect(upload?.files.every(file => !('file' in file))).toBe(true)
  })

  it('counts updates for files outside the retained detail window', async () => {
    const store = await createStore()
    store.registerUpload('session-1', 'project-1', 'Project', 'workspace-1', createFiles(1500))

    store.updateFileProgress('session-1', 'file-1499', {
      status: 'uploaded',
      chunksReceived: 1,
      totalChunks: 1
    })

    expect(store.activeUploads.get('session-1')?.uploadedFiles).toBe(1)
    expect(store.uploadsArray[0]?.uploadedFiles).toBe(1)
  })
})
