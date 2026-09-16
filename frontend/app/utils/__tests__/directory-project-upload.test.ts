import { describe, expect, it } from 'vitest'
import { getDirectoryProjectName, getProjectNameError, isProjectImageOrXml } from '../directory-project-upload'

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
})
