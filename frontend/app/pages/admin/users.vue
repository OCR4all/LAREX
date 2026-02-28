<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import * as z from 'zod'
import type { FormSubmitEvent, TableColumn } from '@nuxt/ui'
import { LazyUiConfirmSlideover } from '#components'
import { globalKey } from '@/utils/fetch-keys'

definePageMeta({ layout: 'admin', middleware: 'admin' })

type AdminUserOnboardingState = 'ACTIVE' | 'PENDING_SETUP' | 'DISABLED' | 'SERVICE_ACCOUNT'
type AdminUserStatusFilter = 'ALL' | 'ACTIVE' | 'PENDING_SETUP' | 'DISABLED'
type AdminUserAuditAction = 'CREATE' | 'ENABLE' | 'DISABLE' | 'RESEND_SETUP_EMAIL'
type AdminUserAuditOutcome = 'SUCCESS' | 'FAILURE'
type AdminUserIdentitySource = 'LOCAL' | 'LDAP' | 'SERVICE_ACCOUNT'

interface AdminUser {
  id: string
  username: string
  email?: string | null
  firstName?: string | null
  lastName?: string | null
  avatar?: string | null
  enabled: boolean
  emailVerified: boolean
  serviceAccount: boolean
  externallyManaged: boolean
  identitySource: AdminUserIdentitySource
  onboardingState: AdminUserOnboardingState
  createdTimestamp?: string | null
}

interface AdminUserPage {
  items: AdminUser[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  creationAllowed: boolean
  setupEmailAllowed: boolean
}

interface AdminUserAuditEvent {
  id: string
  action: AdminUserAuditAction
  outcome: AdminUserAuditOutcome
  actorUserId: string
  actorUsername: string
  created?: string | null
  details?: string | null
}

interface ErrorResponseData {
  code?: string
  message?: string
  details?: string[]
}

const toast = useToast()
const overlay = useOverlay()
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)
const { user: sessionUser } = useUserSession()

const UAvatar = resolveComponent('UAvatar')
const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')

const currentUserId = computed(() => sessionUser.value?.id || '')

const errorCodeMessages: Record<string, string> = {
  ADMIN_USER_DUPLICATE_USERNAME: 'Username already exists.',
  ADMIN_USER_DUPLICATE_EMAIL: 'Email already exists.',
  ADMIN_USER_INVALID_USERNAME: 'This username is not allowed.',
  ADMIN_USER_SETUP_EMAIL_FAILED: 'User was not created because the setup email could not be sent.',
  ADMIN_USER_SELF_DISABLE_FORBIDDEN: 'You cannot disable your own account.',
  ADMIN_USER_SERVICE_ACCOUNT_FORBIDDEN: 'Service accounts cannot be managed here.',
  ADMIN_USER_ALREADY_ENABLED: 'User is already enabled.',
  ADMIN_USER_ALREADY_DISABLED: 'User is already disabled.',
  ADMIN_USER_RESEND_NOT_ALLOWED: 'Setup email can only be resent for users still completing onboarding.',
  ADMIN_USER_PROVISIONING_DISABLED: 'This deployment uses LDAP-managed identities. User provisioning from LAREX is disabled.',
  ADMIN_USER_EXTERNALLY_MANAGED: 'This user is managed externally through LDAP and cannot be changed here.'
}

const createUserSchema = z.object({
  username: z.string().trim().min(1, 'Username is required').max(255, 'Username is too long')
    .refine(value => !value.toLowerCase().startsWith('service-account-'), 'This username is not allowed.'),
  email: z.string().trim().min(1, 'Email is required').max(255, 'Email is too long').email('Email must be valid'),
  firstName: z.string().trim().max(255, 'First name is too long').optional().or(z.literal('')),
  lastName: z.string().trim().max(255, 'Last name is too long').optional().or(z.literal(''))
})

type CreateUserSchema = z.output<typeof createUserSchema>

const statusOptions: { label: string, value: AdminUserStatusFilter }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Pending Setup', value: 'PENDING_SETUP' },
  { label: 'Disabled', value: 'DISABLED' }
]

const pageSizeOptions = [
  { label: '10 per page', value: 10 },
  { label: '25 per page', value: 25 },
  { label: '50 per page', value: 50 },
  { label: '100 per page', value: 100 }
]

const page = ref(1)
const itemsPerPage = ref(25)
const statusFilter = ref<AdminUserStatusFilter>('ALL')
const searchInput = ref('')
const debouncedSearch = ref('')
const isCreateUserModalOpen = ref(false)
const isCreatingUser = ref(false)
const selectedUserId = ref<string | null>(null)
const isDetailsOpen = ref(false)
const detailUser = ref<AdminUser | null>(null)
const detailAuditEvents = ref<AdminUserAuditEvent[]>([])
const detailPending = ref(false)
const detailError = ref<string | null>(null)
const activeActionKey = ref<string | null>(null)

const createUserState = reactive<Partial<CreateUserSchema>>({
  username: '',
  email: '',
  firstName: '',
  lastName: ''
})

const defaultUsersPage = (): AdminUserPage => ({
  items: [],
  page: 0,
  size: itemsPerPage.value,
  totalElements: 0,
  totalPages: 0,
  creationAllowed: true,
  setupEmailAllowed: true
})

const usersQuery = computed(() => {
  const query: Record<string, string | number | boolean> = {
    page: page.value - 1,
    size: itemsPerPage.value,
    status: statusFilter.value,
    includeServiceAccounts: false
  }

  if (debouncedSearch.value) {
    query.search = debouncedSearch.value
  }

  return query
})

const usersKey = computed(() => globalKey(
  'admin',
  'users',
  page.value,
  itemsPerPage.value,
  statusFilter.value,
  debouncedSearch.value || 'none'
))

const { data: usersPage, refresh, pending } = await useFetch<AdminUserPage>('/api/admin/users', {
  key: usersKey,
  query: usersQuery,
  watch: [usersQuery],
  default: defaultUsersPage
})

const users = computed(() => usersPage.value?.items ?? [])
const creationAllowed = computed(() => usersPage.value?.creationAllowed ?? true)
const setupEmailAllowed = computed(() => usersPage.value?.setupEmailAllowed ?? true)
const totalItems = computed(() => usersPage.value?.totalElements ?? 0)
const totalPages = computed(() => Math.max(1, usersPage.value?.totalPages ?? 1))
const visibleActiveCount = computed(() => users.value.filter(user => user.onboardingState === 'ACTIVE').length)
const visiblePendingCount = computed(() => users.value.filter(user => user.onboardingState === 'PENDING_SETUP').length)
const visibleDisabledCount = computed(() => users.value.filter(user => user.onboardingState === 'DISABLED').length)
const showingFrom = computed(() => totalItems.value === 0 ? 0 : (page.value - 1) * itemsPerPage.value + 1)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, totalItems.value))

watch(searchInput, useDebounceFn((value: string) => {
  debouncedSearch.value = value.trim()
  page.value = 1
}, 300))

watch([statusFilter, itemsPerPage], () => {
  page.value = 1
})

watch(totalPages, (newTotalPages) => {
  if (page.value > newTotalPages) {
    page.value = newTotalPages
  }
})

const columns = computed<TableColumn<AdminUser>[]>(() => [
  {
    accessorKey: 'user',
    header: 'User',
    cell: ({ row }) => h('div', { class: 'flex items-center gap-3 min-w-0' }, [
      h(UAvatar, {
        src: row.original.avatar || undefined,
        alt: row.original.username,
        size: 'sm'
      }),
      h('div', { class: 'min-w-0' }, [
        h('div', { class: 'font-medium truncate' }, displayName(row.original)),
        h('div', { class: 'flex flex-wrap items-center gap-2 text-sm text-muted' }, [
          h('span', { class: 'truncate' }, row.original.email || row.original.username),
          h(UBadge, {
            color: identitySourceColor(row.original.identitySource),
            variant: 'subtle',
            size: 'sm'
          }, () => identitySourceLabel(row.original.identitySource))
        ])
      ])
    ])
  },
  {
    accessorKey: 'username',
    header: 'Username'
  },
  {
    accessorKey: 'onboardingState',
    header: 'Status',
    cell: ({ row }) => h(UBadge, {
      color: statusColor(row.original.onboardingState),
      variant: 'soft'
    }, () => statusLabel(row.original.onboardingState))
  },
  {
    accessorKey: 'createdTimestamp',
    header: 'Created',
    cell: ({ row }) => formatDate(row.original.createdTimestamp)
  },
  {
    id: 'actions',
    header: () => h('div', { class: 'text-right' }, 'Actions'),
    cell: ({ row }) => {
      const user = row.original
      const actions = [
        h(UButton, {
          key: `details-${user.id}`,
          size: 'xs',
          variant: 'ghost',
          color: 'neutral',
          label: 'Details',
          onClick: () => openUserDetails(user.id)
        })
      ]

      if (canResendSetup(user)) {
        actions.push(h(UButton, {
          key: `resend-${user.id}`,
          size: 'xs',
          variant: 'ghost',
          color: 'primary',
          label: 'Resend Setup',
          loading: isActionPending(user.id, 'resend'),
          onClick: () => resendSetupEmail(user)
        }))
      }

      if (canEnable(user)) {
        actions.push(h(UButton, {
          key: `enable-${user.id}`,
          size: 'xs',
          variant: 'ghost',
          color: 'primary',
          label: 'Enable',
          loading: isActionPending(user.id, 'enable'),
          onClick: () => enableUser(user)
        }))
      }

      if (canDisable(user)) {
        actions.push(h(UButton, {
          key: `disable-${user.id}`,
          size: 'xs',
          variant: 'ghost',
          color: 'error',
          label: 'Disable',
          loading: isActionPending(user.id, 'disable'),
          onClick: () => disableUser(user)
        }))
      }

      return h('div', { class: 'flex flex-wrap justify-end gap-2' }, actions)
    }
  }
])

function displayName(user: AdminUser): string {
  const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ').trim()
  return fullName || user.username
}

function statusLabel(state: AdminUserOnboardingState): string {
  switch (state) {
    case 'ACTIVE':
      return 'Active'
    case 'PENDING_SETUP':
      return 'Pending Setup'
    case 'DISABLED':
      return 'Disabled'
    case 'SERVICE_ACCOUNT':
      return 'Service Account'
  }
}

function statusColor(state: AdminUserOnboardingState): 'success' | 'warning' | 'error' | 'neutral' {
  switch (state) {
    case 'ACTIVE':
      return 'success'
    case 'PENDING_SETUP':
      return 'warning'
    case 'DISABLED':
      return 'error'
    case 'SERVICE_ACCOUNT':
      return 'neutral'
  }
}

function identitySourceLabel(source: AdminUserIdentitySource): string {
  switch (source) {
    case 'LOCAL':
      return 'Local'
    case 'LDAP':
      return 'LDAP'
    case 'SERVICE_ACCOUNT':
      return 'Service Account'
  }
}

function identitySourceColor(source: AdminUserIdentitySource): 'success' | 'info' | 'neutral' {
  switch (source) {
    case 'LOCAL':
      return 'success'
    case 'LDAP':
      return 'info'
    case 'SERVICE_ACCOUNT':
      return 'neutral'
  }
}

function formatDate(value?: string | null): string {
  return value ? new Date(value).toLocaleString() : '-'
}

function formatActionLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function formatAuditDetails(details?: string | null): string {
  if (!details) {
    return 'No details recorded.'
  }

  try {
    const parsed = JSON.parse(details) as Record<string, unknown>
    return Object.entries(parsed)
      .map(([key, value]) => `${formatActionLabel(key)}: ${String(value)}`)
      .join('\n')
  } catch {
    return details
  }
}

function getErrorData(error: unknown): ErrorResponseData | undefined {
  if (!error || typeof error !== 'object' || !('data' in error)) {
    return undefined
  }

  const data = (error as { data?: unknown }).data
  if (!data || typeof data !== 'object') {
    return undefined
  }

  return data as ErrorResponseData
}

function getErrorMessage(error: unknown, fallback: string): string {
  const data = getErrorData(error)
  const mappedMessage = data?.code ? errorCodeMessages[data.code] : undefined
  if (mappedMessage) {
    return mappedMessage
  }
  if (data?.message) {
    return data.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}

function normalizeOptional(value?: string) {
  const normalized = value?.trim()
  return normalized ? normalized : undefined
}

function resetCreateUserForm() {
  createUserState.username = ''
  createUserState.email = ''
  createUserState.firstName = ''
  createUserState.lastName = ''
}

function openCreateUserModal() {
  if (!creationAllowed.value) {
    return
  }
  resetCreateUserForm()
  isCreateUserModalOpen.value = true
}

function closeCreateUserModal() {
  isCreateUserModalOpen.value = false
}

function canDisable(user: AdminUser): boolean {
  return creationAllowed.value
    && user.enabled
    && !user.serviceAccount
    && !user.externallyManaged
    && user.id !== currentUserId.value
}

function canEnable(user: AdminUser): boolean {
  return creationAllowed.value
    && !user.enabled
    && !user.serviceAccount
    && !user.externallyManaged
}

function canResendSetup(user: AdminUser): boolean {
  return setupEmailAllowed.value
    && user.onboardingState === 'PENDING_SETUP'
    && !user.serviceAccount
    && !user.externallyManaged
}

function actionKey(userId: string, action: 'disable' | 'enable' | 'resend'): string {
  return `${action}:${userId}`
}

function isActionPending(userId: string, action: 'disable' | 'enable' | 'resend'): boolean {
  return activeActionKey.value === actionKey(userId, action)
}

async function refreshUsersAndDetails() {
  await refresh()
  if (isDetailsOpen.value && selectedUserId.value) {
    await loadUserDetails(selectedUserId.value)
  }
}

async function loadUserDetails(userId: string) {
  detailPending.value = true
  detailError.value = null

  try {
    const [user, events] = await Promise.all([
      $fetch<AdminUser>(`/api/admin/users/${userId}`),
      $fetch<AdminUserAuditEvent[]>(`/api/admin/users/${userId}/audit-events`, {
        query: { limit: 50 }
      })
    ])

    detailUser.value = user
    detailAuditEvents.value = events
  } catch (error: unknown) {
    detailError.value = getErrorMessage(error, 'Failed to load user details.')
  } finally {
    detailPending.value = false
  }
}

async function openUserDetails(userId: string) {
  selectedUserId.value = userId
  isDetailsOpen.value = true
  await loadUserDetails(userId)
}

function closeUserDetails() {
  isDetailsOpen.value = false
  selectedUserId.value = null
  detailUser.value = null
  detailAuditEvents.value = []
  detailError.value = null
}

async function onCreateUserSubmit(event: FormSubmitEvent<CreateUserSchema>) {
  isCreatingUser.value = true

  try {
    await $fetch('/api/admin/users', {
      method: 'POST',
      body: {
        username: event.data.username.trim(),
        email: event.data.email.trim(),
        firstName: normalizeOptional(event.data.firstName),
        lastName: normalizeOptional(event.data.lastName)
      }
    })

    toast.add({
      title: 'User created, setup email sent',
      color: 'success'
    })

    resetCreateUserForm()
    closeCreateUserModal()
    page.value = 1
    await refreshUsersAndDetails()
  } catch (error: unknown) {
    toast.add({
      title: 'User creation failed',
      description: getErrorMessage(error, 'Failed to create user.'),
      color: 'error'
    })
  } finally {
    isCreatingUser.value = false
  }
}

async function disableUser(user: AdminUser) {
  const instance = confirmSlideover.open({
    title: 'Disable User?',
    message: `Disable ${displayName(user)}? They will not be able to sign in until re-enabled.`,
    confirmLabel: 'Disable User',
    confirmColor: 'error'
  })
  const confirmed = await instance.result
  if (!confirmed) {
    return
  }

  activeActionKey.value = actionKey(user.id, 'disable')
  try {
    await $fetch(`/api/admin/users/${user.id}/disable`, { method: 'POST' })
    toast.add({ title: 'User disabled', color: 'success' })
    await refreshUsersAndDetails()
  } catch (error: unknown) {
    toast.add({
      title: 'Disable failed',
      description: getErrorMessage(error, 'Failed to disable user.'),
      color: 'error'
    })
  } finally {
    activeActionKey.value = null
  }
}

async function enableUser(user: AdminUser) {
  activeActionKey.value = actionKey(user.id, 'enable')
  try {
    await $fetch(`/api/admin/users/${user.id}/enable`, { method: 'POST' })
    toast.add({ title: 'User enabled', color: 'success' })
    await refreshUsersAndDetails()
  } catch (error: unknown) {
    toast.add({
      title: 'Enable failed',
      description: getErrorMessage(error, 'Failed to enable user.'),
      color: 'error'
    })
  } finally {
    activeActionKey.value = null
  }
}

async function resendSetupEmail(user: AdminUser) {
  activeActionKey.value = actionKey(user.id, 'resend')
  try {
    await $fetch(`/api/admin/users/${user.id}/resend-setup`, { method: 'POST' })
    toast.add({ title: 'Setup email sent', color: 'success' })
    await refreshUsersAndDetails()
  } catch (error: unknown) {
    toast.add({
      title: 'Resend failed',
      description: getErrorMessage(error, 'Failed to resend setup email.'),
      color: 'error'
    })
  } finally {
    activeActionKey.value = null
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
            v-if="creationAllowed"
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
            @click="refreshUsersAndDetails"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold">
              {{ totalItems }}
            </h3>
            <p class="text-sm text-muted">
              Matched Users
            </p>
          </div>
        </UCard>
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold text-warning">
              {{ visiblePendingCount }}
            </h3>
            <p class="text-sm text-muted">
              Pending Setup on Page
            </p>
          </div>
        </UCard>
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold text-error">
              {{ visibleDisabledCount }}
            </h3>
            <p class="text-sm text-muted">
              Disabled on Page
            </p>
          </div>
        </UCard>
      </div>

      <UCard>
        <template #header>
          <div class="flex flex-col gap-4">
            <div
              v-if="!creationAllowed"
              class="rounded-lg border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-warning"
            >
              User creation is disabled because this deployment uses LDAP-managed identities.
            </div>

            <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div class="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
                <UInput
                  v-model="searchInput"
                  icon="i-lucide-search"
                  placeholder="Search by username, email, or name..."
                  class="w-full sm:max-w-md"
                >
                  <template v-if="searchInput" #trailing>
                    <UButton
                      color="neutral"
                      variant="link"
                      icon="i-lucide-x"
                      :padded="false"
                      @click="searchInput = ''"
                    />
                  </template>
                </UInput>

                <USelect
                  v-model="statusFilter"
                  :items="statusOptions"
                  value-key="value"
                  class="w-full sm:w-56"
                />
              </div>

              <div class="text-sm text-muted">
                Service accounts are hidden by default.
              </div>
            </div>
          </div>
        </template>

        <UTable :data="users" :columns="columns" :loading="pending" />

        <template #footer>
          <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div class="text-sm text-muted">
              Showing {{ showingFrom }} to {{ showingTo }} of {{ totalItems }} users
            </div>

            <div class="flex items-center gap-4">
              <div class="hidden text-sm text-muted md:block">
                Active on page: {{ visibleActiveCount }}
              </div>

              <USelect
                v-model="itemsPerPage"
                :items="pageSizeOptions"
                value-key="value"
                class="w-32"
                size="sm"
              />

              <UPagination
                v-model:page="page"
                :total="totalItems"
                :items-per-page="itemsPerPage"
                show-edges
                :sibling-count="1"
              />
            </div>
          </div>
        </template>
      </UCard>
    </template>
  </UDashboardPanel>

  <UModal
    v-model:open="isCreateUserModalOpen"
    title="Create User"
    :close="{ onClick: closeCreateUserModal }"
  >
    <template #body>
      <UForm
        :schema="createUserSchema"
        :state="createUserState"
        class="space-y-4"
        @submit="onCreateUserSubmit"
      >
        <UFormField label="Username" name="username" required>
          <UInput
            v-model="createUserState.username"
            placeholder="username"
            autocomplete="off"
          />
        </UFormField>

        <UFormField label="Email" name="email" required>
          <UInput
            v-model="createUserState.email"
            type="email"
            placeholder="user@example.org"
            autocomplete="off"
          />
        </UFormField>

        <UFormField label="First name" name="firstName">
          <UInput
            v-model="createUserState.firstName"
            placeholder="Optional"
            autocomplete="off"
          />
        </UFormField>

        <UFormField label="Last name" name="lastName">
          <UInput
            v-model="createUserState.lastName"
            placeholder="Optional"
            autocomplete="off"
          />
        </UFormField>

        <div class="flex justify-end gap-2 pt-2">
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
            type="submit"
            :loading="isCreatingUser"
          >
            Create User
          </UButton>
        </div>
      </UForm>
    </template>
  </UModal>

  <USlideover
    v-model:open="isDetailsOpen"
    title="User Details"
    :close="{ onClick: closeUserDetails }"
  >
    <template #body>
      <div class="space-y-6">
        <div v-if="detailPending" class="text-sm text-muted">
          Loading user details...
        </div>

        <div v-else-if="detailError" class="text-sm text-error">
          {{ detailError }}
        </div>

        <template v-else-if="detailUser">
          <div class="flex items-center gap-3">
            <UAvatar :src="detailUser.avatar || undefined" :alt="detailUser.username" size="lg" />
            <div>
              <div class="text-lg font-semibold">
                {{ displayName(detailUser) }}
              </div>
              <div class="text-sm text-muted">
                {{ detailUser.email || detailUser.username }}
              </div>
            </div>
          </div>

          <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
            <UCard>
              <div class="text-xs uppercase tracking-wide text-muted mb-1">
                Username
              </div>
              <div class="font-medium break-all">
                {{ detailUser.username }}
              </div>
            </UCard>

            <UCard>
              <div class="text-xs uppercase tracking-wide text-muted mb-1">
                User ID
              </div>
              <div class="font-medium break-all">
                {{ detailUser.id }}
              </div>
            </UCard>

            <UCard>
              <div class="text-xs uppercase tracking-wide text-muted mb-1">
                Status
              </div>
              <div class="flex items-center gap-2">
                <UBadge :color="statusColor(detailUser.onboardingState)" variant="soft">
                  {{ statusLabel(detailUser.onboardingState) }}
                </UBadge>
                <UBadge :color="detailUser.emailVerified ? 'success' : 'warning'" variant="subtle">
                  {{ detailUser.emailVerified ? 'Email Verified' : 'Email Unverified' }}
                </UBadge>
              </div>
            </UCard>

            <UCard>
              <div class="text-xs uppercase tracking-wide text-muted mb-1">
                Identity Source
              </div>
              <div class="flex items-center gap-2">
                <UBadge :color="identitySourceColor(detailUser.identitySource)" variant="soft">
                  {{ identitySourceLabel(detailUser.identitySource) }}
                </UBadge>
                <span
                  v-if="detailUser.externallyManaged"
                  class="text-sm text-muted"
                >
                  Managed externally
                </span>
              </div>
            </UCard>

            <UCard>
              <div class="text-xs uppercase tracking-wide text-muted mb-1">
                Created
              </div>
              <div class="font-medium">
                {{ formatDate(detailUser.createdTimestamp) }}
              </div>
            </UCard>
          </div>

          <div
            v-if="detailUser.identitySource === 'LDAP'"
            class="rounded-lg border border-info/30 bg-info/10 px-4 py-3 text-sm text-info"
          >
            Account lifecycle changes must be handled in your directory or identity provider.
          </div>

          <div>
            <div class="mb-3 flex items-center justify-between">
              <h3 class="text-sm font-semibold uppercase tracking-wide text-muted">
                Audit Events
              </h3>
              <UButton
                size="xs"
                variant="ghost"
                color="neutral"
                icon="i-lucide-refresh-cw"
                :loading="detailPending"
                @click="selectedUserId && loadUserDetails(selectedUserId)"
              >
                Refresh
              </UButton>
            </div>

            <div v-if="detailAuditEvents.length === 0" class="text-sm text-muted">
              No audit events recorded for this user.
            </div>

            <div v-else class="space-y-3">
              <UCard v-for="event in detailAuditEvents" :key="event.id">
                <div class="flex flex-col gap-2">
                  <div class="flex flex-wrap items-center gap-2">
                    <UBadge :color="event.outcome === 'SUCCESS' ? 'success' : 'error'" variant="soft">
                      {{ formatActionLabel(event.outcome) }}
                    </UBadge>
                    <span class="font-medium">{{ formatActionLabel(event.action) }}</span>
                    <span class="text-sm text-muted">by {{ event.actorUsername }}</span>
                    <span class="text-sm text-muted">{{ formatDate(event.created) }}</span>
                  </div>
                  <pre class="whitespace-pre-wrap break-words text-sm text-muted font-sans">{{ formatAuditDetails(event.details) }}</pre>
                </div>
              </UCard>
            </div>
          </div>
        </template>
      </div>
    </template>
  </USlideover>
</template>
