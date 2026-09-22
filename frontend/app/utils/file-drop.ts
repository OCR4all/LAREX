type FileSystemEntryLike = {
  isFile: boolean
  isDirectory: boolean
  name: string
  file?: (onSuccess: (file: File) => void, onError?: () => void) => void
  createReader?: () => {
    readEntries: (onSuccess: (entries: FileSystemEntryLike[]) => void, onError?: () => void) => void
  }
}

type DirectoryDataTransferItem = DataTransferItem & {
  webkitGetAsEntry?: () => FileSystemEntryLike | null
}

function readFileEntry(entry: FileSystemEntryLike): Promise<File | null> {
  return new Promise((resolve) => {
    if (entry.file) {
      entry.file(resolve, () => resolve(null))
    } else {
      resolve(null)
    }
  })
}

async function readDirectoryEntry(entry: FileSystemEntryLike): Promise<File[]> {
  if (entry.isFile) {
    const file = await readFileEntry(entry)
    return file ? [file] : []
  }

  if (!entry.isDirectory || !entry.createReader) return []

  const reader = entry.createReader()
  const entries: FileSystemEntryLike[] = []
  while (true) {
    const batch = await new Promise<FileSystemEntryLike[]>((resolve) => {
      reader.readEntries(resolve, () => resolve([]))
    })
    if (batch.length === 0) break
    entries.push(...batch)
  }

  return (await Promise.all(entries.map(readDirectoryEntry))).flat()
}

export async function getDroppedDirectoryFiles(event: DragEvent): Promise<{ name: string, files: File[] } | null> {
  const items = Array.from(event.dataTransfer?.items ?? [])
  if (items.length !== 1) return null

  const entry = (items[0] as DirectoryDataTransferItem).webkitGetAsEntry?.()
  if (!entry?.isDirectory) return null

  return { name: entry.name, files: await readDirectoryEntry(entry) }
}
