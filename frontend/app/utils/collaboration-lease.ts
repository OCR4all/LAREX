import type { CollaborationLeaseState } from '@/types/collaboration'

/** Heartbeats extend a lease without changing its ownership epoch. */
export function isStaleLeaseState(incoming: CollaborationLeaseState, current: CollaborationLeaseState): boolean {
  const incomingEpoch = BigInt(incoming.leaseEpoch)
  const currentEpoch = BigInt(current.leaseEpoch)
  if (incomingEpoch !== currentEpoch) return incomingEpoch < currentEpoch
  return Boolean(incoming.expiresAt && current.expiresAt
    && Date.parse(incoming.expiresAt) < Date.parse(current.expiresAt))
}
