function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object'
}

const STORAGE_QUOTA_FALLBACK_MESSAGE = 'Uploads are blocked because this workspace has exceeded its storage quota. Remove data or ask an admin to increase the quota.'

function normalizeTransportMessage(message: string): string {
  const trimmed = message.trim()
  if (!trimmed.startsWith('[')) {
    return trimmed
  }

  const separatorIndex = trimmed.lastIndexOf(':')
  if (separatorIndex === -1 || separatorIndex === trimmed.length - 1) {
    return trimmed
  }

  return trimmed.slice(separatorIndex + 1).trim()
}

function isStorageQuotaTransportMessage(message: string): boolean {
  const normalized = normalizeTransportMessage(message).toLowerCase()
  return normalized === '507 insufficient storage'
    || normalized === 'insufficient storage'
    || normalized.includes('storage quota exceeded')
}

export function extractApiMessageFromPayload(payload: unknown, fallback: string): string {
  if (!isRecord(payload)) {
    return fallback
  }

  if (typeof payload.message === 'string' && payload.message.trim()) {
    return payload.message
  }

  if (typeof payload.error === 'string' && payload.error.trim()) {
    return payload.error
  }

  return fallback
}

export function extractApiErrorMessage(error: unknown, fallback: string): string {
  if (isRecord(error) && isRecord(error.data)) {
    if (isStorageQuotaError(error)) {
      return extractApiMessageFromPayload(error.data, STORAGE_QUOTA_FALLBACK_MESSAGE)
    }

    const dataMessage = extractApiMessageFromPayload(error.data, '')
    if (dataMessage) return dataMessage
  }

  if (isStorageQuotaError(error)) {
    return STORAGE_QUOTA_FALLBACK_MESSAGE
  }

  if (error instanceof Error && error.message.trim()) {
    const normalizedMessage = normalizeTransportMessage(error.message)
    if (isStorageQuotaTransportMessage(normalizedMessage)) {
      return STORAGE_QUOTA_FALLBACK_MESSAGE
    }
    return normalizedMessage
  }

  return fallback
}

export function isStorageQuotaError(error: unknown): boolean {
  if (!isRecord(error)) return false

  const statusCode = typeof error.statusCode === 'number' ? error.statusCode : null
  if (statusCode === 507) return true

  if (isRecord(error.data) && error.data.code === 'STORAGE_QUOTA_EXCEEDED') {
    return true
  }

  return false
}
