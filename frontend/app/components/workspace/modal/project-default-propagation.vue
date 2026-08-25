<script setup lang="ts">
import type {
  ProjectDefaultPropagationScope,
  ProjectDefaultsPreview
} from '~/types/workspace-project-defaults'

const props = defineProps<{
  preview: ProjectDefaultsPreview
  changedDefaults: Array<{ label: string, before: string, after: string }>
}>()

const emit = defineEmits<{
  close: [scope: ProjectDefaultPropagationScope | null]
}>()

const isOpen = ref(true)
const scope = ref<ProjectDefaultPropagationScope>('FUTURE_ONLY')

const scopeItems = computed(() => [
  {
    label: 'Future projects only',
    value: 'FUTURE_ONLY' as const,
    description: 'Save the workspace defaults. Existing projects keep their current settings.'
  },
  {
    label: 'Fill missing project settings',
    value: 'UNSET_ONLY' as const,
    description: `${props.preview.unsetOnly.affectedProjects} unlocked project${props.preview.unsetOnly.affectedProjects === 1 ? '' : 's'} will receive empty settings. Explicit values are preserved${props.preview.unsetOnly.skippedLockedProjects > 0 ? `; ${props.preview.unsetOnly.skippedLockedProjects} locked project${props.preview.unsetOnly.skippedLockedProjects === 1 ? '' : 's'} skipped` : ''}.`
  },
  {
    label: 'Replace settings in all projects',
    value: 'ALL' as const,
    description: `${props.preview.all.affectedProjects} unlocked project${props.preview.all.affectedProjects === 1 ? '' : 's'} will be updated${props.preview.all.skippedLockedProjects > 0 ? `; ${props.preview.all.skippedLockedProjects} locked project${props.preview.all.skippedLockedProjects === 1 ? '' : 's'} skipped` : ''}. This can replace explicit project choices.`
  }
])

function cancel() {
  emit('close', null)
  isOpen.value = false
}

function confirm() {
  emit('close', scope.value)
  isOpen.value = false
}
</script>

<template>
  <UModal
    v-model:open="isOpen"
    title="Apply project defaults?"
    description="Choose how this workspace change should affect existing projects."
    :ui="{ content: 'max-w-2xl' }"
    @close="cancel"
  >
    <template #body>
      <div class="space-y-5">
        <div>
          <p class="text-sm text-muted">
            Workspace defaults are copied when a project is created. Select whether to update existing projects as part of this save.
          </p>
          <ul class="mt-3 space-y-1 text-sm text-muted">
            <li v-for="item in changedDefaults" :key="item.label" class="flex items-center justify-between gap-4">
              <span>{{ item.label }}</span>
              <span class="text-right text-highlighted">{{ item.before }} → {{ item.after }}</span>
            </li>
          </ul>
        </div>

        <URadioGroup v-model="scope" :items="scopeItems" />

        <UAlert
          v-if="scope === 'ALL'"
          color="warning"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Explicit project settings will be replaced"
          description="Locked projects are protected and will be reported as skipped."
        />
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="outline" @click="cancel">
          Cancel
        </UButton>
        <UButton color="primary" icon="i-lucide-save" @click="confirm">
          Save defaults
        </UButton>
      </div>
    </template>
  </UModal>
</template>
