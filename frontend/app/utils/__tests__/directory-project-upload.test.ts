import { describe, expect, it } from 'vitest'
import { getDirectoryProjectName, getProjectNameError, isProjectImageOrXml } from '../directory-project-upload'
import { getDroppedDirectoryFiles } from '../file-drop'

describe('directory project upload', () => {
  it('derives the root directory and keeps only supported upload files', () => {
    const files = [
      { name: 'page.jpg', type: 'image/jpeg', webkitRelativePath: 'My Project/images/page.jpg' },
      { name: 'page.xml', type: 'text/xml', webkitRelativePath: 'My Project/page/page.xml' },
      { name: 'notes.txt', type: 'text/plain', webkitRelativePath: 'My Project/notes.txt' }
    ]

    expect(getDirectoryProjectName(files)).toBe('My Project')
    expect(files.filter(isProjectImageOrXml).map(file => file.name)).toEqual(['page.jpg', 'page.xml'])
    expect(getProjectNameError('My Project', ['Existing'])).toBeUndefined()
    expect(getProjectNameError('Existing', ['Existing'])).toContain('already taken')
  })

  it('reads files from a dropped directory entry', async () => {
    const file = new File(['<PcGts />'], 'page.xml', { type: 'text/xml' })
    let read = false
    const directoryEntry = {
      isFile: false,
      isDirectory: true,
      name: 'Dropped Project',
      createReader: () => ({
        readEntries: (resolve: (entries: unknown[]) => void) => {
          resolve(read
            ? []
            : [{
                isFile: true,
                isDirectory: false,
                name: file.name,
                file: (onSuccess: (file: File) => void) => onSuccess(file)
              }])
          read = true
        }
      })
    }
    const event = {
      dataTransfer: {
        items: [{ kind: 'file', webkitGetAsEntry: () => directoryEntry }]
      }
    } as unknown as DragEvent

    await expect(getDroppedDirectoryFiles(event)).resolves.toEqual({
      name: 'Dropped Project',
      files: [file]
    })
  })
})
