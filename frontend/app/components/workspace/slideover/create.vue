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
    const errorMessage = extractApiErrorMessage(error) || 'An error occurred'
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
  <USlideover
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #header>
      <UiSlideoverHeader title="Create Workspace" icon="i-bxs-layer-plus" />
    </template>

    <template #body>
      <UForm
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <UFormField label="Name" name="name">
          <UInput v-model="state.name" />
        </UFormField>

        <UFormField label="Description" name="description">
          <UInput v-model="state.description" />
        </UFormField>

        <USeparator />

        <div>
          <div class="flex items-center justify-between mb-2">
            <UFormField label="Invite Members" name="invites" class="mb-0" />
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
              class="absolute z-10 w-full mt-1 bg-default border border-default rounded-sm shadow-lg max-h-60 overflow-auto"
            >
              <button
                v-for="user in searchResults"
                :key="user.id"
                type="button"
                class="w-full px-4 py-2 text-left hover:bg-elevated/50 flex items-center gap-3"
                @click="selectUser(user)"
              >
                <UAvatar :alt="user.username" size="sm" />
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium truncate">
                    {{ user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : user.username }}
                  </p>
                  <p class="text-xs text-muted truncate">
                    {{ user.email || user.username }}
                  </p>
                </div>
              </button>
            </div>
          </div>

          <div v-if="invitedUsers.length > 0" class="mt-3 space-y-2">
            <div
              v-for="user in invitedUsers"
              :key="user.userId"
              class="flex items-center gap-2 p-2 bg-elevated/50 rounded-sm"
            >
              <UAvatar :alt="user.username" size="sm" />
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium truncate">
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

        <div class="flex justify-end gap-1 pt-4">
          <UButton
            color="neutral"
            variant="ghost"
            :disabled="isCreating"
            @click="emit('close', false)"
          >
            Cancel
          </UButton>
          <UButton variant="solid" type="submit" :loading="isCreating">
            Create Workspace
          </UButton>
        </div>
      </UForm>
    </template>
  </USlideover>
</template>
