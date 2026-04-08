import { createHmac, timingSafeEqual } from 'node:crypto'

export interface CollaborationRoomTokenPayload {
  sub: string
  username: string
  displayName: string
  avatar?: string | null
  workspaceId: string
  projectId: string
  pageId: string
  xmlId: string
  roomKey: string
  canEdit: boolean
  canForceTakeover: boolean
  persistedRevision: string
  exp: number
}

function encodeBase64Url(value: string): string {
  return Buffer.from(value, 'utf8').toString('base64url')
}

function decodeBase64Url(value: string): string {
  return Buffer.from(value, 'base64url').toString('utf8')
}

function signValue(value: string, secret: string): string {
  return createHmac('sha256', secret).update(value).digest('base64url')
}

export function signCollaborationRoomToken(
  payload: CollaborationRoomTokenPayload,
  secret: string
): string {
  const encodedPayload = encodeBase64Url(JSON.stringify(payload))
  const signature = signValue(encodedPayload, secret)
  return `${encodedPayload}.${signature}`
}

export function verifyCollaborationRoomToken(
  token: string,
  secret: string
): CollaborationRoomTokenPayload | null {
  const separatorIndex = token.indexOf('.')
  if (separatorIndex === -1) return null

  const encodedPayload = token.slice(0, separatorIndex)
  const receivedSignature = token.slice(separatorIndex + 1)
  if (!encodedPayload || !receivedSignature) return null

  const expectedSignature = signValue(encodedPayload, secret)
  const expectedBuffer = Buffer.from(expectedSignature)
  const receivedBuffer = Buffer.from(receivedSignature)

  if (expectedBuffer.length !== receivedBuffer.length) {
    return null
  }
  if (!timingSafeEqual(expectedBuffer, receivedBuffer)) {
    return null
  }

  try {
    const payload = JSON.parse(decodeBase64Url(encodedPayload)) as CollaborationRoomTokenPayload
    if (!payload?.roomKey || !payload?.sub || !payload?.pageId || !payload?.projectId || !payload?.xmlId) {
      return null
    }
    if (typeof payload.exp !== 'number' || payload.exp <= Date.now()) {
      return null
    }
    return payload
  } catch {
    return null
  }
}
