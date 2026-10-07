<script setup lang="ts">
const props = defineProps<{
  name: string
  workspaceName: string
  resourceId: string
  targetWorkspaceId: string
}>()
const emit = defineEmits<{ close: [name: string | null] }>()
const newName = ref(props.name)
const status = ref<'checking' | 'available' | 'taken' | 'invalid' | 'unavailable'>('checking')
let timer: ReturnType<typeof setTimeout> | undefined
let sequence = 0

const nameError = computed(() => {
  if (status.value === 'taken') return 'This name is already taken in the target workspace.'
  if (status.value === 'invalid') return 'Enter a label set name.'
  if (status.value === 'unavailable') return 'Could not check this name. Try again.'
  return undefined
})
const nameDescription = computed(() => {
  if (status.value === 'available') return 'This name is available.'
  if (status.value === 'checking') return 'Checking name availability…'
  return undefined
})

function checkName(value: string) {
  if (timer) clearTimeout(timer)
  const candidate = value.trim()
  const current = ++sequence
  if (!candidate) {
    status.value = 'invalid'
    return
  }
  status.value = 'checking'
  timer = setTimeout(async () => {
    try {
      const result = await $fetch<{ available: boolean }>('/api/resource-transfers/label-set-name-availability', {
        query: { resourceId: props.resourceId, targetWorkspaceId: props.targetWorkspaceId, name: candidate }
      })
      if (current === sequence) status.value = result.available ? 'available' : 'taken'
    } catch {
      if (current === sequence) status.value = 'unavailable'
    }
  }, 350)
}

function continueTransfer() {
  if (status.value === 'available') emit('close', newName.value.trim())
}

watch(newName, checkName, { immediate: true })
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <UiResponsiveSlideover :close="{ onClick: () => emit('close', null) }">
    <template #header>
      <UiSlideoverHeader
        title="Name the label set"
        icon="i-lucide-pencil"
        :description="`Choose the name to use in ${workspaceName}.`"
      />
    </template>
    <template #body>
      <UFormField
        label="Label set name"
        :error="nameError"
        :description="nameDescription"
        required
      >
        <UInput
          v-model="newName"
          maxlength="255"
          autofocus
          class="w-full"
          :loading="status === 'checking'"
          @keydown.enter.prevent="continueTransfer"
        />
      </UFormField>
    </template>
    <template #footer>
      <UButton color="neutral" variant="ghost" @click="emit('close', null)">
        Cancel
      </UButton>
      <UButton :disabled="status !== 'available'" @click="continueTransfer">
        Continue
      </UButton>
    </template>
  </UiResponsiveSlideover>
</template>
