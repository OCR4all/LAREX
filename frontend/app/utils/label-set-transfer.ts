import { isLabelSetNameConflictError } from '@/utils/api-error'

export type LabelSetNameStatus = 'checking' | 'available' | 'taken' | 'unavailable' | null

export function defaultLabelSetTransferName(name: string, transferType: 'MOVE' | 'COPY'): string {
  return transferType === 'COPY' ? `${name} (Copy)` : name
}

export async function requestLabelSetTransfer<T>(options: {
  name: string
  nameStatus: LabelSetNameStatus
  request: (targetName?: string) => Promise<T>
  chooseName: (suggestedName: string) => Promise<string | null>
}): Promise<T | null> {
  let targetName: string | undefined

  if (options.nameStatus === 'taken') {
    const chosenName = await options.chooseName(options.name)
    if (!chosenName) return null
    targetName = chosenName
  }

  while (true) {
    try {
      return await options.request(targetName)
    } catch (error) {
      // The server remains authoritative if the availability result went stale.
      if (!isLabelSetNameConflictError(error)) throw error
      const chosenName = await options.chooseName(targetName || options.name)
      if (!chosenName) return null
      targetName = chosenName
    }
  }
}
