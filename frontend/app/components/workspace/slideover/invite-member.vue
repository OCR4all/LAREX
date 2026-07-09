<script setup lang="ts">
import type { UserProfile } from '~/types'

const props = defineProps<{
  workspaceId: string
}>()

const emit = defineEmits<{
  close: [invited: boolean]
}>()

const toast = useToast()
const { refreshWorkspaceMembership } = useDataRefresh()

const formId = useId()
const searchQuery = ref('')
const selectedUser = ref<UserProfile | null>(null)
const selectedRole = ref<'CURATOR' | 'EDITOR'>('EDITOR')
const isSearching = ref(false)
const isSubmitting = ref(false)
const searchResults = ref<UserProfile[]>([])

const roleOptions = [
  { label: 'Editor', value: 'EDITOR', description: 'Can edit page annotations only' },
  { label: 'Curator', value: 'CURATOR', description: 'Can manage projects, tasks, and toolkit' }
]

const debouncedSearch = useDebounceFn(async (query: string) => {
  if (!query || query.length < 2) {
    searchResults.value = []
    return
  }

  isSearching.value = true
  try {
    const results = await $fetch<UserProfile[]>(`/api/users/search?q=${encodeURIComponent(query)}&limit=10`)
    searchResults.value = results
  } catch (error) {
    console.error('Failed to search users:', error)
    searchResults.value = []
  } finally {
    isSearching.value = false
  }
}, 300)

watch(searchQuery, (query) => {
  if (selectedUser.value && query !== selectedUser.value.username) {
    selectedUser.value = null
  }
  debouncedSearch(query)
})

function selectUser(user: UserProfile) {
  selectedUser.value = user
  searchQuery.value = user.username
  searchResults.value = []
}

async function handleSubmit() {
  if (!selectedUser.value) {
    toast.add({
      title: 'Please select a user to invite',
      color: 'warning'
    })
    return
  }

  isSubmitting.value = true
  try {
    await $fetch(`/api/workspaces/${props.workspaceId}/invitations`, {
      method: 'POST',
      body: {
        userId: selectedUser.value.id,
        role: selectedRole.value
      }
    })
    await refreshWorkspaceMembership(props.workspaceId)

    toast.add({
      title: 'Invitation sent',
      description: `${selectedUser.value.username} has been invited as ${selectedRole.value.toLowerCase()}`,
      color: 'success'
    })

    searchQuery.value = ''
    selectedUser.value = null
    selectedRole.value = 'EDITOR'
    emit('close', true)
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to send invitation',
      description: extractApiErrorMessage(error, 'Please try again'),
      color: 'error'
    })
  } finally {
    isSubmitting.value = false
  }
}

function handleClose() {
  searchQuery.value = ''
  selectedUser.value = null
  selectedRole.value = 'EDITOR'
  searchResults.value = []
  emit('close', false)
}
</script>

<template>
  <UiResponsiveSlideover
    :close="{ onClick: handleClose }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Invite Member"
        icon="i-lucide-user-plus"
        description="Find a user and assign their initial workspace role."
      />
    </template>

    <template #body>
      <UForm :id="formId" class="space-y-4" @submit="handleSubmit">
        <UiSlideoverSection
          title="Invitation Details"
          description="Select the recipient and the permissions they should receive."
          icon="i-lucide-user-round-plus"
        >
          <div class="space-y-4">
            <UFormField label="Find user" name="user">
              <div class="relative">
                <UInput
                  v-model="searchQuery"
                  placeholder="Search by username or email..."
                  icon="i-lucide-search"
                  :loading="isSearching"
                  class="w-full"
                />

                <div
                  v-if="searchResults.length > 0 && !selectedUser"
                  class="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-sm border border-default bg-default shadow-lg"
                >
                  <button
                    v-for="user in searchResults"
                    :key="user.id"
                    type="button"
                    class="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-elevated/50"
                    @click="selectUser(user)"
                  >
                    <UAvatar :alt="user.username" size="sm" />
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium">
                        {{ user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : user.username }}
                      </p>
                      <p class="truncate text-xs text-muted">
                        {{ user.email || user.username }}
                      </p>
                    </div>
                  </button>
                </div>

                <div v-if="selectedUser" class="mt-2 flex items-center gap-2 rounded-sm bg-elevated/50 p-2">
                  <UAvatar :alt="selectedUser.username" size="sm" />
                  <div class="min-w-0 flex-1">
                    <p class="text-sm font-medium">
                      {{ selectedUser.username }}
                    </p>
                    <p class="text-xs text-muted">
                      {{ selectedUser.email }}
                    </p>
                  </div>
                  <UButton
                    type="button"
                    icon="i-lucide-x"
                    size="xs"
                    color="neutral"
                    variant="ghost"
                    @click="selectedUser = null; searchQuery = ''"
                  />
                </div>
              </div>
            </UFormField>

            <UFormField label="Role" name="role">
              <USelect
                v-model="selectedRole"
                :items="roleOptions"
                value-key="value"
                class="w-full"
              />
            </UFormField>
          </div>
        </UiSlideoverSection>
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          color="neutral"
          variant="outline"
          @click="handleClose"
        >
          Cancel
        </UButton>
        <UButton
          type="submit"
          :form="formId"
          :disabled="!selectedUser"
          :loading="isSubmitting"
        >
          Send Invite
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
