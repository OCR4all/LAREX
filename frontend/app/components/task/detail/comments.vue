<script setup lang="ts">
import type { TaskComment } from '~/types/index'
import { formatDistanceToNow, parseISO } from 'date-fns'

const props = defineProps<{
  taskId: string
  comments: TaskComment[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const toast = useToast()
const { user } = useUserSession()
const currentUserId = computed(() => user.value?.id || '')

const newComment = ref('')
const isSubmitting = ref(false)
const editingCommentId = ref<string | null>(null)
const editContent = ref('')

async function submitComment() {
  if (!newComment.value.trim()) return

  isSubmitting.value = true
  try {
    await $fetch(`/api/tasks/${props.taskId}/comments`, {
      method: 'POST',
      body: { content: newComment.value.trim() }
    })
    newComment.value = ''
    emit('refresh')
    toast.add({ title: 'Comment added', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to add comment', description: err?.data?.message, color: 'error' })
  } finally {
    isSubmitting.value = false
  }
}

function startEdit(comment: TaskComment) {
  editingCommentId.value = comment.id
  editContent.value = comment.content
}

function cancelEdit() {
  editingCommentId.value = null
  editContent.value = ''
}

async function saveEdit(commentId: string) {
  if (!editContent.value.trim()) return

  isSubmitting.value = true
  try {
    await $fetch(`/api/tasks/${props.taskId}/comments/${commentId}`, {
      method: 'PUT',
      body: { content: editContent.value.trim() }
    })
    editingCommentId.value = null
    editContent.value = ''
    emit('refresh')
    toast.add({ title: 'Comment updated', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to update comment', description: err?.data?.message, color: 'error' })
  } finally {
    isSubmitting.value = false
  }
}

async function deleteComment(commentId: string) {
  try {
    await $fetch(`/api/tasks/${props.taskId}/comments/${commentId}`, {
      method: 'DELETE'
    })
    emit('refresh')
    toast.add({ title: 'Comment deleted', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to delete comment', description: err?.data?.message, color: 'error' })
  }
}

function formatTime(dateString: string) {
  return formatDistanceToNow(parseISO(dateString), { addSuffix: true })
}

function getDisplayName(comment: TaskComment) {
  if (comment.user?.firstName && comment.user?.lastName) {
    return `${comment.user.firstName} ${comment.user.lastName}`
  }
  return comment.user?.username || 'Unknown'
}
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-2">
      <UTextarea
        v-model="newComment"
        placeholder="Write a comment... (use @username to mention)"
        :rows="3"
        :disabled="isSubmitting"
      />
      <div class="flex justify-end">
        <UButton
          :loading="isSubmitting"
          :disabled="!newComment.trim() || isSubmitting"
          @click="submitComment"
        >
          Add Comment
        </UButton>
      </div>
    </div>

    <USeparator v-if="comments.length > 0" />

    <div v-if="comments.length > 0" class="space-y-4">
      <div
        v-for="comment in comments"
        :key="comment.id"
        class="flex gap-3"
      >
        <AppAvatar
          :seed="comment.user?.id || comment.userId"
          :src="comment.user?.avatar"
          :alt="getDisplayName(comment)"
          size="sm"
          class="shrink-0"
        />
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium">{{ getDisplayName(comment) }}</span>
            <span class="text-xs text-muted">{{ formatTime(comment.created) }}</span>
            <span v-if="comment.created !== comment.updated" class="text-xs text-muted">(edited)</span>
          </div>

          <div v-if="editingCommentId === comment.id" class="mt-2 space-y-2">
            <UTextarea
              v-model="editContent"
              :rows="3"
              :disabled="isSubmitting"
            />
            <div class="flex gap-2">
              <UButton
                size="sm"
                :loading="isSubmitting"
                :disabled="!editContent.trim() || isSubmitting"
                @click="saveEdit(comment.id)"
              >
                Save
              </UButton>
              <UButton
                size="sm"
                color="neutral"
                variant="outline"
                :disabled="isSubmitting"
                @click="cancelEdit"
              >
                Cancel
              </UButton>
            </div>
          </div>

          <div v-else>
            <p class="mt-1 text-sm whitespace-pre-wrap">
              {{ comment.content }}
            </p>

            <div v-if="comment.userId === currentUserId" class="mt-2 flex gap-2">
              <UButton
                size="xs"
                color="neutral"
                variant="ghost"
                icon="i-lucide-pencil"
                @click="startEdit(comment)"
              >
                Edit
              </UButton>
              <UButton
                size="xs"
                color="error"
                variant="ghost"
                icon="i-lucide-trash-2"
                @click="deleteComment(comment.id)"
              >
                Delete
              </UButton>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="text-center py-8 text-muted">
      <UIcon name="i-lucide-message-square" class="size-8 mb-2 mx-auto opacity-50" />
      <p class="text-sm">
        No comments yet. Be the first to comment!
      </p>
    </div>
  </div>
</template>
