<script setup lang="ts">
import { globalKey } from '@/utils/fetch-keys'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const toast = useToast()
const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')
const UAvatar = resolveComponent('UAvatar')

interface AdminUser {
  id: string
  username: string
  email?: string
  firstName?: string
  lastName?: string
  avatar?: string
  enabled: boolean
  emailVerified: boolean
  createdTimestamp?: string
}

const { data: users, refresh, pending } = await useFetch<AdminUser[]>('/api/admin/users', {
  key: globalKey('admin', 'users', 'all'),
  default: () => []
})

const isCreateUserModalOpen = ref(false)
const isCreatingUser = ref(false)
const createUserForm = reactive({
  username: '',
  email: '',
  firstName: '',
  lastName: ''
})

const canCreateUser = computed(() => {
  return createUserForm.username.trim().length > 0 && createUserForm.email.trim().length > 0
})

type AdminUserRow = AdminUser & {
  name: string
  description: string
}

const userRows = computed<AdminUserRow[]>(() => {
  return (users.value ?? []).map((user) => {
    const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ').trim()
    return {
      ...user,
      name: fullName || user.username,
      description: user.email || ''
    }
  })
})

const { sort, globalFilter, filteredAndSortedData, activeFilters, resetAllFilters } = useTableFilters(userRows, { column: 'createdTimestamp', direction: 'desc' })

const columns = [
  {
    accessorKey: 'avatar',
    header: '',
    cell: ({ row }) => h(UAvatar, { src: row.original.avatar, alt: row.original.username, size: 'sm' })
  },
  {
    accessorKey: 'username',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Username'),
      h(UButton, { icon: sort.value.column === 'username' ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down') : 'i-lucide-arrow-up-down', size: 'xs', variant: 'ghost', color: sort.value.column === 'username' ? 'primary' : 'neutral', onClick: () => { sort.value = sort.value.column === 'username' ? { column: 'username', direction: sort.value.direction === 'asc' ? 'desc' : 'asc' } : { column: 'username', direction: 'asc' } } })
    ])
  },
  {
    accessorKey: 'email',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Email'),
      h(UButton, { icon: sort.value.column === 'email' ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down') : 'i-lucide-arrow-up-down', size: 'xs', variant: 'ghost', color: sort.value.column === 'email' ? 'primary' : 'neutral', onClick: () => { sort.value = sort.value.column === 'email' ? { column: 'email', direction: sort.value.direction === 'asc' ? 'desc' : 'asc' } : { column: 'email', direction: 'asc' } } })
    ])
  },
  {
    accessorKey: 'fullName',
    header: 'Name',
    cell: ({ row }) => {
      const firstName = row.original.firstName || ''
      const lastName = row.original.lastName || ''
      const fullName = [firstName, lastName].filter(Boolean).join(' ')
      return fullName || '-'
    }
  },
  {
    accessorKey: 'enabled',
    header: 'Status',
    cell: ({ row }) => h(UBadge, { color: row.original.enabled ? 'success' : 'error', variant: 'soft' }, () => row.original.enabled ? 'Active' : 'Disabled')
  },
  {
    accessorKey: 'emailVerified',
    header: 'Email Verified',
    cell: ({ row }) => h(UBadge, { color: row.original.emailVerified ? 'success' : 'warning', variant: 'soft' }, () => row.original.emailVerified ? 'Verified' : 'Pending')
  },
  {
    accessorKey: 'createdTimestamp',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Created'),
      h(UButton, { icon: sort.value.column === 'createdTimestamp' ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down') : 'i-lucide-arrow-up-down', size: 'xs', variant: 'ghost', color: sort.value.column === 'createdTimestamp' ? 'primary' : 'neutral', onClick: () => { sort.value = sort.value.column === 'createdTimestamp' ? { column: 'createdTimestamp', direction: sort.value.direction === 'asc' ? 'desc' : 'asc' } : { column: 'createdTimestamp', direction: 'asc' } } })
    ]),
    cell: ({ row }) => row.original.createdTimestamp ? new Date(row.original.createdTimestamp).toLocaleDateString() : '-'
  }
]

const activeCount = computed(() => users.value.filter(u => u.enabled).length)
const verifiedCount = computed(() => users.value.filter(u => u.emailVerified).length)

function resetCreateUserForm() {
  createUserForm.username = ''
  createUserForm.email = ''
  createUserForm.firstName = ''
  createUserForm.lastName = ''
}

function openCreateUserModal() {
  resetCreateUserForm()
  isCreateUserModalOpen.value = true
}

function closeCreateUserModal() {
  isCreateUserModalOpen.value = false
}

async function createUser() {
  if (!canCreateUser.value) {
    toast.add({
      title: 'Missing required fields',
      description: 'Username and email are required.',
      color: 'warning'
    })
    return
  }

  isCreatingUser.value = true
  try {
    await $fetch('/api/admin/users', {
      method: 'POST',
      body: {
        username: createUserForm.username.trim(),
        email: createUserForm.email.trim(),
        firstName: createUserForm.firstName.trim() || undefined,
        lastName: createUserForm.lastName.trim() || undefined
      }
    })

    toast.add({
      title: 'User created, setup email sent',
      color: 'success'
    })

    closeCreateUserModal()
    await refresh()
  } catch (error: unknown) {
    const maybeError = error as { data?: { message?: string }, message?: string }
    const message = maybeError.data?.message || maybeError.message || 'Failed to create user.'
    toast.add({
      title: 'User creation failed',
      description: message,
      color: 'error'
    })
  } finally {
    isCreatingUser.value = false
  }
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="User Management" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            color="primary"
            variant="solid"
            icon="i-lucide-user-plus"
            label="Create User"
            @click="openCreateUserModal"
          />
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            label="Refresh"
            :loading="pending"
            @click="refresh()"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold">
              {{ users.length }}
            </h3>
            <p class="text-sm text-muted">
              Total Users
            </p>
          </div>
        </UCard>
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold text-success">
              {{ activeCount }}
            </h3>
            <p class="text-sm text-muted">
              Active
            </p>
          </div>
        </UCard>
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold text-primary">
              {{ verifiedCount }}
            </h3>
            <p class="text-sm text-muted">
              Email Verified
            </p>
          </div>
        </UCard>
      </div>

      <div class="mb-6 space-y-4">
        <div class="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
          <div class="flex-1 max-w-md">
            <UInput v-model="globalFilter" placeholder="Search users..." icon="i-lucide-search">
              <template v-if="globalFilter" #trailing>
                <UButton
                  color="neutral"
                  variant="link"
                  icon="i-lucide-x"
                  :padded="false"
                  @click="globalFilter = ''"
                />
              </template>
            </UInput>
          </div>
          <UButton
            v-if="activeFilters.length > 0"
            color="neutral"
            variant="outline"
            size="sm"
            @click="resetAllFilters"
          >
            Clear Filters
          </UButton>
        </div>
      </div>

      <UTable :data="filteredAndSortedData" :columns="columns" :loading="pending" />
    </template>
  </UDashboardPanel>

  <UModal
    v-model:open="isCreateUserModalOpen"
    title="Create User"
    :close="{ onClick: closeCreateUserModal }"
  >
    <template #body>
      <div class="space-y-4">
        <UFormField label="Username" required>
          <UInput
            v-model="createUserForm.username"
            placeholder="username"
            autocomplete="off"
          />
        </UFormField>

        <UFormField label="Email" required>
          <UInput
            v-model="createUserForm.email"
            type="email"
            placeholder="user@example.org"
            autocomplete="off"
          />
        </UFormField>

        <UFormField label="First name">
          <UInput
            v-model="createUserForm.firstName"
            placeholder="Optional"
            autocomplete="off"
          />
        </UFormField>

        <UFormField label="Last name">
          <UInput
            v-model="createUserForm.lastName"
            placeholder="Optional"
            autocomplete="off"
          />
        </UFormField>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2 w-full">
        <UButton
          color="neutral"
          variant="outline"
          :disabled="isCreatingUser"
          @click="closeCreateUserModal"
        >
          Cancel
        </UButton>
        <UButton
          color="primary"
          :loading="isCreatingUser"
          :disabled="!canCreateUser || isCreatingUser"
          @click="createUser"
        >
          Create User
        </UButton>
      </div>
    </template>
  </UModal>
</template>
