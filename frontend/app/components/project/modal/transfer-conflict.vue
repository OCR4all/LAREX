<script setup lang="ts">
const props = defineProps<{
  projectId: string
  projectName: string
  targetWorkspaceId: string
  targetWorkspaceName: string
  transferType: 'MOVE' | 'COPY'
}>()

const emit = defineEmits<{
  close: [result: string | null]
}>()

type NameStatus = 'taken' | 'available' | 'checking' | 'invalid' | 'unavailable'

const newProjectName = ref(props.projectName)
const nameStatus = ref<NameStatus>('taken')
let debounceTimer: ReturnType<typeof setTimeout> | undefined
let checkSequence = 0

const nameError = computed(() => {
  switch (nameStatus.value) {
    case 'taken':
      return 'This project name is already taken in the target workspace.'
    case 'invalid':
      return 'Enter a project name.'
    case 'unavailable':
      return 'Could not check this name. Try again.'
    default:
      return undefined
  }
})

const nameDescription = computed(() => {
  if (nameStatus.value === 'available') return 'This project name is available.'
  if (nameStatus.value === 'checking') return 'Checking name availability…'
  return undefined
})

const canRename = computed(() => nameStatus.value === 'available')

function cancel() {
  emit('close', null)
}

function scheduleAvailabilityCheck(value: string) {
  if (debounceTimer) clearTimeout(debounceTimer)

  const candidate = value.trim()
  const originalName = props.projectName.trim()
  checkSequence++

  if (!candidate) {
    nameStatus.value = 'invalid'
    return
  }

  if (candidate === originalName) {
    nameStatus.value = 'taken'
    return
  }

  nameStatus.value = 'checking'
  const sequence = checkSequence
  debounceTimer = setTimeout(async () => {
    try {
      const result = await $fetch<{ available: boolean }>('/api/project-transfers/name-availability', {
        query: {
          projectId: props.projectId,
          targetWorkspaceId: props.targetWorkspaceId,
          projectName: candidate
        }
      })

      if (sequence !== checkSequence || candidate !== newProjectName.value.trim()) return
      nameStatus.value = result.available ? 'available' : 'taken'
    } catch {
      if (sequence !== checkSequence || candidate !== newProjectName.value.trim()) return
      nameStatus.value = 'unavailable'
    }
  }, 350)
}

function rename() {
  const trimmedName = newProjectName.value.trim()
  if (!trimmedName) {
    nameStatus.value = 'invalid'
    return
  }
  if (!canRename.value) return

  emit('close', trimmedName)
}

watch(newProjectName, scheduleAvailabilityCheck)

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<template>
  <UiResponsiveSlideover :close="{ onClick: cancel }">
    <template #header>
      <UiSlideoverHeader
        title="Choose a new project name"
        icon="i-lucide-pencil"
        description="The project name is already in use in the target workspace."
      />
    </template>

    <template #body>
      <div class="space-y-5">
        <UAlert
          icon="i-lucide-alert-triangle"
          color="warning"
          variant="subtle"
          :title="`Rename the ${transferType === 'COPY' ? 'copied' : 'moved'} project before continuing`"
          :description="`A project with this name already exists in ${targetWorkspaceName}.`"
        />

        <UFormField
          label="New project name"
          :error="nameError"
          :description="nameDescription"
          required
        >
          <UInput
            v-model="newProjectName"
            maxlength="255"
            autofocus
            class="w-full"
            :color="nameStatus === 'available' ? 'success' : nameError ? 'error' : 'neutral'"
            :loading="nameStatus === 'checking'"
            @keydown.enter.prevent="rename"
          >
            <template #trailing>
              <UIcon
                v-if="nameStatus === 'available'"
                name="i-lucide-check"
                class="size-4 text-success"
              />
              <UIcon
                v-else-if="nameError"
                name="i-lucide-circle-alert"
                class="size-4 text-error"
              />
            </template>
          </UInput>
        </UFormField>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="cancel">
          Cancel
        </UButton>
        <UButton
          icon="i-lucide-pencil"
          :disabled="!canRename"
          @click="rename"
        >
          Rename
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
