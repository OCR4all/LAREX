function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object'
}

const STORAGE_QUOTA_FALLBACK_MESSAGE = 'Uploads are blocked because this workspace has exceeded its storage quota. Remove data or ask an admin to increase the quota.'

export interface ApiErrorDetails {
  timestamp?: string
  status?: number
  error?: string
  message: string
  path?: string
  details?: string[]
  code?: string
  errorId?: string
  workspaceId?: string
}

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
  return extractApiErrorDetails(error, fallback).message
}

export function extractApiErrorDetails(error: unknown, fallback: string): ApiErrorDetails {
  if (isRecord(error) && isRecord(error.data)) {
    if (isStorageQuotaError(error)) {
      return toApiErrorDetails(error.data, STORAGE_QUOTA_FALLBACK_MESSAGE, toNumber(error.statusCode))
    }

    const dataMessage = extractApiMessageFromPayload(error.data, '')
    if (dataMessage) {
      return toApiErrorDetails(error.data, dataMessage, toNumber(error.statusCode))
    }
  }

  if (isStorageQuotaError(error)) {
    return { message: STORAGE_QUOTA_FALLBACK_MESSAGE, status: 507, code: 'STORAGE_QUOTA_EXCEEDED' }
  }

  if (error instanceof Error && error.message.trim()) {
    const normalizedMessage = normalizeTransportMessage(error.message)
    if (isStorageQuotaTransportMessage(normalizedMessage)) {
      return { message: STORAGE_QUOTA_FALLBACK_MESSAGE, status: 507, code: 'STORAGE_QUOTA_EXCEEDED' }
    }
    return { message: normalizedMessage }
  }

  return { message: fallback }
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

export function buildApiErrorClipboardPayload(error: ApiErrorDetails, pageUrl?: string): string {
  const lines = [
    ['Error ID', error.errorId],
    ['Timestamp', error.timestamp],
    ['HTTP Status', error.status != null ? String(error.status) : undefined],
    ['Code', error.code],
    ['Message', error.message],
    ['API Path', error.path],
    ['Workspace ID', error.workspaceId],
    ['Page URL', pageUrl]
  ]
    .filter(([, value]) => Boolean(value))
    .map(([label, value]) => `${label}: ${value}`)

  if (error.details && error.details.length > 0) {
    lines.push(`Details: ${error.details.join(' | ')}`)
  }

  return lines.join('\n')
}

function toApiErrorDetails(payload: Record<string, unknown>, fallback: string, fallbackStatus?: number | null): ApiErrorDetails {
  return {
    timestamp: typeof payload.timestamp === 'string' ? payload.timestamp : undefined,
    status: toNumber(payload.status) ?? fallbackStatus ?? undefined,
    error: typeof payload.error === 'string' ? payload.error : undefined,
    message: extractApiMessageFromPayload(payload, fallback),
    path: typeof payload.path === 'string' ? payload.path : undefined,
    details: Array.isArray(payload.details) ? payload.details.filter((item): item is string => typeof item === 'string') : undefined,
    code: typeof payload.code === 'string' ? payload.code : undefined,
    errorId: typeof payload.errorId === 'string' ? payload.errorId : undefined,
    workspaceId: typeof payload.workspaceId === 'string' ? payload.workspaceId : undefined
  }
}

function toNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string') {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }
  return undefined
}
