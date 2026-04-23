<script setup lang="ts">
import type { CollaborationRoomMember } from '@/types/collaboration'

const props = withDefaults(defineProps<{
  collaborators: CollaborationRoomMember[]
  maxVisible?: number
  showCount?: boolean
  size?: 'xs' | 'sm' | 'md'
}>(), {
  maxVisible: 3,
  showCount: true,
  size: 'xs'
})

const visibleCollaborators = computed(() => props.collaborators.slice(0, props.maxVisible))
const hiddenCount = computed(() => Math.max(0, props.collaborators.length - visibleCollaborators.value.length))

function avatarSrc(collaborator: CollaborationRoomMember): string | undefined {
  return resolveManagedProfileAvatarSrc(collaborator.user.avatar)
}

function avatarFallback(collaborator: CollaborationRoomMember): string {
  return getAvatarInitials({
    name: collaborator.user.displayName,
    username: collaborator.user.username
  })
}

function tooltipText(collaborator: CollaborationRoomMember): string {
  const selectionId = collaborator.presence?.selectionId
  if (selectionId) {
    return `${collaborator.user.displayName} on ${selectionId}`
  }
  return collaborator.user.displayName
}
</script>

<template>
  <div v-if="collaborators.length > 0" class="flex items-center gap-1.5">
    <div class="flex items-center -space-x-1.5">
      <UAvatar
        v-for="collaborator in visibleCollaborators"
        :key="collaborator.user.id"
        :src="avatarSrc(collaborator)"
        :alt="collaborator.user.displayName"
        :text="avatarFallback(collaborator)"
        :size="size"
        :title="tooltipText(collaborator)"
        class="ring-1 ring-white/20 dark:ring-neutral-950/80"
      />
      <div
        v-if="hiddenCount > 0"
        class="flex items-center justify-center rounded-full bg-neutral-900 text-neutral-200 text-[10px] font-medium ring-1 ring-white/20 dark:ring-neutral-950/80"
        :class="size === 'xs' ? 'h-5 min-w-5 px-1' : size === 'sm' ? 'h-6 min-w-6 px-1.5' : 'h-8 min-w-8 px-2'"
      >
        +{{ hiddenCount }}
      </div>
    </div>

    <span v-if="showCount" class="text-[10px] font-medium text-neutral-500 dark:text-neutral-400">
      {{ collaborators.length }}
    </span>
  </div>
</template>
