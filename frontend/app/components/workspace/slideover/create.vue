<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'

interface UserProfile {
  id: string
  username: string
  email?: string
  firstName?: string
  lastName?: string
}

interface InvitedUser {
  userId: string
  username: string
  role: 'CURATOR' | 'EDITOR'
}

const emit = defineEmits<{ close: [boolean] }>()

const workspaceStore = useWorkspaceStore()

const schema = z.object({
  name: z.preprocess(
    value => typeof value === 'string' ? value : '',
    z.string().trim().min(1, { error: 'Workspace name is required' }).max(100, { error: 'Name is too long' })
  ),
  description: z.string().optional()
})

type Schema = z.output<typeof schema>

const state = reactive<Partial<Schema>>({
  name: undefined,
  description: undefined
})

const invitedUsers = ref<InvitedUser[]>([])
const searchQuery = ref('')
const selectedUser = ref<UserProfile | null>(null)
const searchResults = ref<UserProfile[]>([])
const isSearching = ref(false)
const isCreating = ref(false)
const formId = useId()

const roleOptions = [
  { label: 'Editor', value: 'EDITOR' as const },
  { label: 'Curator', value: 'CURATOR' as const }
]

const debouncedSearch = useDebounceFn(async (query: string) => {
  if (!query || query.length < 2) {
    searchResults.value = []
    return
  }

  isSearching.value = true
  try {
    const results = await $fetch<UserProfile[]>(`/api/users/search?q=${encodeURIComponent(query)}&limit=10`)
    searchResults.value = results.filter(u => !invitedUsers.value.some(invited => invited.userId === u.id))
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
  invitedUsers.value.push({
    userId: user.id,
    username: user.username,
    role: 'EDITOR'
  })
}

function removeUser(userId: string) {
  invitedUsers.value = invitedUsers.value.filter(u => u.userId !== userId)
  if (selectedUser.value?.id === userId) {
    selectedUser.value = null
    searchQuery.value = ''
  }
}

function updateUserRole(userId: string, role: 'CURATOR' | 'EDITOR') {
  const user = invitedUsers.value.find(u => u.userId === userId)
  if (user) {
    user.role = role
  }
}

const toast = useToast()
async function onSubmit(event: FormSubmitEvent<Schema>) {
  isCreating.value = true

  try {
    const body = {
      ...event.data,
      initialInvites: invitedUsers.value.map(u => ({
        userId: u.userId,
        role: u.role
      }))
    }

    const data = await $fetch<{ id: string }>('/api/workspaces', {
      method: 'POST',
      body
    })

    await refreshNuxtData(globalKey('workspaces', 'list'))
    await workspaceStore.refreshWorkspaces()
    if (data.id) {
      workspaceStore.selectWorkspace(data.id)
    }

    if (invitedUsers.value.length > 0) {
      toast.add({
        title: 'Success',
        description: `Workspace created with ${invitedUsers.value.length} member${invitedUsers.value.length > 1 ? 's' : ''}.`,
        color: 'success'
      })
    } else {
      toast.add({ title: 'Success', description: 'Workspace has been created.', color: 'success' })
    }
    emit('close', true)
  } catch (error: unknown) {
    const errorMessage = extractApiErrorMessage(error, 'An error occurred')
    toast.add({
      title: 'Error',
      description: errorMessage,
      color: 'error'
    })
  } finally {
    isCreating.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Create Workspace"
        icon="i-bxs-layer-plus"
        description="Set up a workspace and optionally invite its first members."
      />
    </template>

    <template #body>
      <UForm
        :id="formId"
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <UiSlideoverSection
          title="Workspace Details"
          description="The workspace name and description shown to its members."
          icon="i-lucide-panels-top-left"
        >
          <div class="space-y-4">
            <UFormField label="Name" name="name">
              <UInput v-model="state.name" />
            </UFormField>

            <UFormField label="Description" name="description">
              <UInput v-model="state.description" />
            </UFormField>
          </div>
        </UiSlideoverSection>

        <UiSlideoverSection
          title="Initial Members"
          description="Invite editors or curators while creating the workspace."
          icon="i-lucide-users-round"
          class="overflow-visible"
        >
          <div class="space-y-3">
            <div class="flex items-center justify-end">
              <span class="text-xs text-muted">{{ invitedUsers.length }} invited</span>
            </div>

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
            </div>

            <div v-if="invitedUsers.length > 0" class="space-y-2">
              <div
                v-for="user in invitedUsers"
                :key="user.userId"
                class="flex items-center gap-2 rounded-sm bg-elevated/50 p-2"
              >
                <UAvatar :alt="user.username" size="sm" />
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium">
                    {{ user.username }}
                  </p>
                </div>
                <USelect
                  v-model="user.role"
                  :items="roleOptions"
                  value-key="value"
                  class="w-32"
                  size="sm"
                  @update:model-value="(val: 'CURATOR' | 'EDITOR') => updateUserRole(user.userId, val)"
                />
                <UButton
                  icon="i-lucide-x"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  @click="removeUser(user.userId)"
                />
              </div>
            </div>
          </div>
        </UiSlideoverSection>
      </UForm>
    </template>

    <template #footer>
      <UButton
        color="neutral"
        variant="ghost"
        :disabled="isCreating"
        @click="emit('close', false)"
      >
        Cancel
      </UButton>
      <UButton
        :form="formId"
        variant="solid"
        type="submit"
        :loading="isCreating"
      >
        Create Workspace
      </UButton>
    </template>
  </UiResponsiveSlideover>
</template>
