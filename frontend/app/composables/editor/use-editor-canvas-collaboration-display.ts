import type { Ref } from 'vue'
import type { CollaborationPresence, CollaborationRoomMember, CollaborationUserIdentity } from '@/types/collaboration'
import { getCollaborationColor } from '@/types/collaboration'
import { resolveManagedProfileAvatarSrc } from '@/utils/avatar'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'

export interface CollaborationDisplayParticipant {
  key: string
  user: CollaborationUserIdentity
  presence: CollaborationPresence | null
  role: 'editing' | 'viewing'
  isCurrentUser: boolean
}

type EditorCanvasCollaborationDisplayOptions = {
  canvasId: Readonly<Ref<string>>
  hexToRgba: (hex: string, opacity: number) => string
}

export function useEditorCanvasCollaborationDisplay(options: EditorCanvasCollaborationDisplayOptions) {
  const collaboration = useEditorCollaboration()
  const collaborationRoom = computed(() => collaboration.getRoomForCanvas(options.canvasId.value))

  const collaborationParticipants = computed<CollaborationDisplayParticipant[]>(() => {
    const room = collaborationRoom.value
    if (!room) return []

    const dedupedMembers = new Map<string, CollaborationRoomMember>()
    for (const member of room.presence.members) {
      dedupedMembers.set(member.user.id, latestMember(dedupedMembers.get(member.user.id), member))
    }

    if (!dedupedMembers.has(room.identity.user.id)) {
      dedupedMembers.set(room.identity.user.id, {
        peerId: `self:${room.identity.user.id}`,
        user: room.identity.user,
        presence: null,
        joinedAt: new Date().toISOString(),
        lastSeenAt: new Date().toISOString()
      })
    }

    const editorId = room.lease.editor?.user.id ?? null

    return [...dedupedMembers.values()]
      .map<CollaborationDisplayParticipant>(member => ({
        key: member.user.id,
        user: member.user,
        presence: member.presence,
        role: member.user.id === editorId ? 'editing' : 'viewing',
        isCurrentUser: member.user.id === room.identity.user.id
      }))
      .sort((left, right) => {
        if (left.role !== right.role) return left.role === 'editing' ? -1 : 1
        if (left.isCurrentUser !== right.isCurrentUser) return left.isCurrentUser ? -1 : 1
        return left.user.displayName.localeCompare(right.user.displayName)
      })
  })

  const collaborationVisibleParticipants = computed(() => collaborationParticipants.value.slice(0, 3))
  const editingParticipants = computed(() => collaborationParticipants.value.filter(participant => participant.role === 'editing'))
  const viewingParticipants = computed(() => collaborationParticipants.value.filter(participant => participant.role === 'viewing'))
  const collaborationSummaryLabel = computed(() => {
    const count = collaborationParticipants.value.length
    return `${count} collaborator${count === 1 ? '' : 's'}`
  })
  const showCollaboratorsPopover = computed(() => collaborationParticipants.value.length > 1)

  function avatarSrc(user: CollaborationUserIdentity): string | undefined {
    return resolveManagedProfileAvatarSrc(user.avatar)
  }

  function collaborationAvatarStyle(userId: string): Record<string, string> {
    const color = getCollaborationColor(userId)
    return {
      backgroundColor: options.hexToRgba(color, 0.18),
      color,
      borderColor: options.hexToRgba(color, 0.4)
    }
  }

  function collaboratorActivityLabel(participant: CollaborationDisplayParticipant): string {
    const modeLabel = participant.presence?.uiMode === 'text' ? ' in text view' : ''
    if (participant.role === 'editing') {
      return participant.presence?.active ? `Editing${modeLabel}` : 'Idle'
    }

    return `Viewing${modeLabel}`
  }

  function collaboratorStatus(participant: CollaborationDisplayParticipant): { label: string, color: 'primary' | 'neutral' } | null {
    if (participant.role !== 'editing') return null

    return participant.presence?.active
      ? { label: 'Live', color: 'primary' }
      : { label: 'Idle', color: 'neutral' }
  }

  return {
    collaborationRoom,
    collaborationParticipants,
    collaborationVisibleParticipants,
    editingParticipants,
    viewingParticipants,
    collaborationSummaryLabel,
    showCollaboratorsPopover,
    avatarSrc,
    collaborationAvatarStyle,
    collaboratorActivityLabel,
    collaboratorStatus
  }
}

function latestMember(current: CollaborationRoomMember | undefined, next: CollaborationRoomMember): CollaborationRoomMember {
  if (!current) return next
  return new Date(next.lastSeenAt).getTime() >= new Date(current.lastSeenAt).getTime() ? next : current
}
