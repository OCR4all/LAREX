<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Subtask } from '~/types/index'

const props = defineProps<{
  subtask: Subtask
  selectionMode: boolean
  selected: boolean
  pending: boolean
  editing: boolean
  assigneeOptions: { label: string, value: string }[]
}>()

const editingTitle = defineModel<string>('editingTitle', { required: true })
const editingDescription = defineModel<string>('editingDescription', { required: true })
const emit = defineEmits<{
  select: []
  toggle: []
  edit: []
  cancel: []
  save: []
  delete: []
  assign: [userId: string | null]
}>()

const assigneeButton = ref<HTMLButtonElement | null>(null)
const menuOpen = ref(false)
const assigneeName = computed(() => {
  const user = props.subtask.assignedTo
  if (!user) return 'Unassigned'
  return user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : user.username
})
const menuItems = computed(() => props.assigneeOptions.map(option => ({
  label: option.label,
  onSelect: () => emit('assign', option.value || null)
})))

function restoreAssigneeFocus(event: Event) {
  event.preventDefault()
  assigneeButton.value?.focus({ preventScroll: true })
}
</script>

<template>
  <div
    class="group flex items-center gap-2 rounded-sm px-2 py-2 hover:bg-elevated/30"
    :class="{ 'bg-primary/5': selectionMode && selected }"
  >
    <input
      v-if="selectionMode"
      type="checkbox"
      class="size-4 shrink-0 accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      :aria-label="`Select ${subtask.title}`"
      :checked="selected"
      @change="emit('select')"
    >
    <input
      type="checkbox"
      class="size-4 shrink-0 accent-primary disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      :aria-label="`Complete ${subtask.title}`"
      :checked="subtask.completed"
      :disabled="selectionMode || pending"
      @change="emit('toggle')"
    >

    <div v-if="editing" class="flex-1 flex flex-col gap-2">
      <UInput
        v-model="editingTitle"
        size="sm"
        class="flex-1"
        aria-label="Task title"
        autofocus
        @keyup.enter="emit('save')"
        @keyup.escape="emit('cancel')"
      />
      <UTextarea
        v-model="editingDescription"
        size="sm"
        :rows="2"
        aria-label="Task description"
        placeholder="Add a description"
      />
      <div class="flex items-center gap-2">
        <UButton
          icon="i-lucide-check"
          color="success"
          variant="ghost"
          size="xs"
          aria-label="Save task"
          @click="emit('save')"
        />
        <UButton
          icon="i-lucide-x"
          color="neutral"
          variant="ghost"
          size="xs"
          aria-label="Cancel editing"
          @click="emit('cancel')"
        />
      </div>
    </div>

    <div v-else class="flex-1 min-w-0 flex flex-col gap-0.5">
      <div class="flex items-center gap-2 min-w-0">
        <span
          class="text-sm cursor-pointer truncate"
          :class="{ 'line-through text-muted': subtask.completed }"
          @dblclick="emit('edit')"
        >
          {{ subtask.title }}
        </span>
        <NuxtLink
          v-if="subtask.pageId && subtask.pageName"
          :to="`/project/${subtask.projectId}`"
          class="shrink-0"
          @click.stop
        >
          <UBadge
            color="neutral"
            variant="subtle"
            size="xs"
            class="cursor-pointer hover:bg-elevated"
          >
            <UIcon name="i-lucide-file" class="size-3 mr-1" />
            {{ subtask.pageName }}
          </UBadge>
        </NuxtLink>
      </div>
      <p v-if="subtask.description || subtask.taskDescription" class="text-xs text-muted truncate">
        {{ subtask.description || subtask.taskDescription }}
      </p>
    </div>

    <div v-if="!selectionMode && assigneeOptions.length > 1" class="shrink-0">
      <button
        ref="assigneeButton"
        type="button"
        class="inline-flex items-center gap-1 rounded-sm p-1 hover:bg-elevated focus-visible:outline-2 focus-visible:outline-primary"
        :aria-label="`Assign ${subtask.title}: ${assigneeName}`"
        aria-haspopup="menu"
        :aria-expanded="menuOpen"
        @click="menuOpen = !menuOpen"
        @keydown.down.prevent="menuOpen = true"
      >
        <AppAvatar
          v-if="subtask.assignedTo"
          :seed="subtask.assignedTo.id"
          :src="subtask.assignedTo.avatar"
          :alt="assigneeName"
          size="2xs"
        />
        <UIcon v-else name="i-lucide-user" class="size-3 text-muted" />
        <span class="text-xs text-muted max-w-20 truncate hidden sm:inline">
          {{ subtask.assignedTo ? assigneeName : '' }}
        </span>
      </button>
      <!-- Keep the menu tree out of scrolling; mount it only on activation. -->
      <LazyUDropdownMenu
        v-if="menuOpen"
        v-model:open="menuOpen"
        :items="menuItems"
        :content="{ reference: assigneeButton, onCloseAutoFocus: restoreAssigneeFocus }"
      />
    </div>

    <div
      v-if="!editing && !selectionMode"
      class="flex items-center gap-1 transition-opacity [@media(hover:hover)]:opacity-0 group-hover:opacity-100 group-focus-within:opacity-100"
    >
      <button
        type="button"
        class="inline-flex rounded-sm p-1 text-muted hover:bg-elevated hover:text-default focus-visible:outline-2 focus-visible:outline-primary"
        :aria-label="`Edit ${subtask.title}`"
        @click="emit('edit')"
      >
        <UIcon name="i-lucide-pencil" class="size-4" />
      </button>
      <button
        type="button"
        class="inline-flex rounded-sm p-1 text-error hover:bg-error/10 focus-visible:outline-2 focus-visible:outline-error"
        :aria-label="`Delete ${subtask.title}`"
        @click="emit('delete')"
      >
        <UIcon name="i-lucide-trash-2" class="size-4" />
      </button>
    </div>
  </div>
</template>
