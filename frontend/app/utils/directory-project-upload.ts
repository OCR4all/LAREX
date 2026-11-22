const SUPPORTED_IMAGE_EXTENSIONS = ['.png', '.jpg', '.jpeg', '.gif', '.bmp', '.tiff', '.webp']
export const SUPPORTED_XML_EXTENSIONS = ['.xml', '.hocr', '.html', '.xhtml']

type DirectoryFile = Pick<File, 'name' | 'type'> & Partial<Pick<File, 'webkitRelativePath'>>

export function getDirectoryProjectName(files: DirectoryFile[], fallbackName = ''): string {
  const relativePath = files.find(file => file.webkitRelativePath)?.webkitRelativePath ?? ''
  return relativePath.split('/').filter(Boolean)[0] ?? fallbackName
}

export function isProjectImageOrXml(file: DirectoryFile): boolean {
  const name = file.name.toLowerCase()
  return SUPPORTED_XML_EXTENSIONS.some(extension => name.endsWith(extension))
    || file.type.startsWith('image/')
    || SUPPORTED_IMAGE_EXTENSIONS.some(extension => name.endsWith(extension))
}

export function getProjectNameError(name: string, existingNames: string[]): string | undefined {
  const candidate = name.trim()
  if (!candidate) return 'Project name is required.'
  if (candidate.length > 255) return 'Project name must not exceed 255 characters.'
  if (existingNames.includes(candidate)) return 'This project name is already taken in this workspace.'
}
