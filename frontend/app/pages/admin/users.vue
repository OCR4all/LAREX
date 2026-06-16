<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent, TableColumn } from '@nuxt/ui'
import { LazyUiConfirmSlideover } from '#components'
import type {
  AdminGlobalRoles,
  AdminUser,
  AdminUserAuditEvent,
  AdminUserIdentitySource,
  AdminUserOnboardingState,
  AdminUserPage,
  AdminUserStatusFilter,
  ErrorResponseData
} from '@/types/admin-users'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const toast = useToast()
const overlay = useOverlay()
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)
const { user: sessionUser } = useUserSession()

const UAvatar = resolveComponent('UAvatar')
const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')

const currentUserId = computed(() => sessionUser.value?.id || '')

const datatableUi = {
  base: 'table-fixed border-separate border-spacing-0',
  thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
  tbody: '[&>tr]:last:[&>td]:border-b-0',
  th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
  td: 'border-b border-default',
  separator: 'h-0'
}

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

const page = ref(1)
const itemsPerPage = ref(25)
const itemsPerPageOptions = [10, 25, 50, 100].map(value => ({ label: `${value} per page`, value }))
const statusFilter = ref<AdminUserStatusFilter>('ALL')
const searchInput = ref('')
const debouncedSearch = ref('')
const isCreateUserModalOpen = ref(false)
const isCreatingUser = ref(false)
const selectedUserId = ref<string | null>(null)
const isDetailsOpen = ref(false)
const detailUser = ref<AdminUser | null>(null)
const detailAuditEvents = ref<AdminUserAuditEvent[]>([])
const detailGlobalRoles = ref<AdminGlobalRoles | null>(null)
const detailPending = ref(false)
const detailError = ref<string | null>(null)
const activeActionKey = ref<string | null>(null)
const isGlobalRoleModalOpen = ref(false)
const globalRoleAction = ref<'grant' | 'revoke' | null>(null)
const globalRoleReason = ref('')
const isSubmittingGlobalRole = ref(false)

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
const itemsPerPageModel = useItemsPerPageModel(page, itemsPerPage, totalItems)
const visibleActiveCount = computed(() => users.value.filter(user => user.onboardingState === 'ACTIVE').length)
const visiblePendingCount = computed(() => users.value.filter(user => user.onboardingState === 'PENDING_SETUP').length)
const visibleDisabledCount = computed(() => users.value.filter(user => user.onboardingState === 'DISABLED').length)
const showingFrom = computed(() => totalItems.value === 0 ? 0 : (page.value - 1) * itemsPerPage.value + 1)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, totalItems.value))
const activeUserFilters = computed(() => {
  const filters: Array<{ key: string, label: string, clear: () => void }> = []
  if (searchInput.value.trim()) {
    filters.push({
      key: 'search',
      label: `Search: ${searchInput.value}`,
      clear: () => { searchInput.value = '' }
    })
  }
  if (statusFilter.value !== 'ALL') {
    filters.push({
      key: 'status',
      label: statusOptions.find(option => option.value === statusFilter.value)?.label ?? statusFilter.value,
      clear: () => { statusFilter.value = 'ALL' }
    })
  }
  return filters
})

watch(searchInput, useDebounceFn((value: string) => {
  debouncedSearch.value = value.trim()
  page.value = 1
}, 300))

watch(statusFilter, () => {
  page.value = 1
})

watch(totalPages, (newTotalPages) => {
  if (page.value > newTotalPages) {
    page.value = newTotalPages
  }
})

type UserRowAction = {
  key: string
  label: string
  icon: string
  color: 'neutral' | 'primary' | 'error'
  loading?: boolean
  onSelect: () => void
}

function getRowActions(user: AdminUser): UserRowAction[] {
  const actions: UserRowAction[] = [
    {
      key: `details-${user.id}`,
      label: 'Details',
      icon: 'i-lucide-info',
      color: 'neutral',
      onSelect: () => openUserDetails(user.id)
    }
  ]

  if (canResendSetup(user)) {
    actions.push({
      key: `resend-${user.id}`,
      label: 'Resend Setup',
      icon: 'i-lucide-send',
      color: 'primary',
      loading: isActionPending(user.id, 'resend'),
      onSelect: () => resendSetupEmail(user)
    })
  }

  if (canEnable(user)) {
    actions.push({
      key: `enable-${user.id}`,
      label: 'Enable',
      icon: 'i-lucide-check-circle',
      color: 'primary',
      loading: isActionPending(user.id, 'enable'),
      onSelect: () => enableUser(user)
    })
  }

  if (canDisable(user)) {
    actions.push({
      key: `disable-${user.id}`,
      label: 'Disable',
      icon: 'i-lucide-ban',
      color: 'error',
      loading: isActionPending(user.id, 'disable'),
      onSelect: () => disableUser(user)
    })
  }

  return actions
}

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
      const actions = getRowActions(user).map(action => h(UButton, {
        key: action.key,
        size: 'xs',
        variant: 'ghost',
        color: action.color,
        label: action.label,
        loading: action.loading,
        onClick: action.onSelect
      }))

      return h('div', { class: 'flex flex-wrap justify-end gap-2' }, actions)
    }
  }
])

const contextMenuUser = ref<AdminUser | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuUser.value) return []
  return [getRowActions(contextMenuUser.value).map(action => ({
    label: action.label,
    icon: action.icon,
    color: action.color,
    disabled: action.loading,
    onSelect: action.onSelect
  }))]
})

function handleRowContextMenu(_event: Event, row: { original: AdminUser }) {
  contextMenuUser.value = row.original
}

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

function clearFilters() {
  searchInput.value = ''
  debouncedSearch.value = ''
  statusFilter.value = 'ALL'
  page.value = 1
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
    const [user, events, globalRoles] = await Promise.all([
      $fetch<AdminUser>(`/api/admin/users/${userId}`),
      $fetch<AdminUserAuditEvent[]>(`/api/admin/users/${userId}/audit-events`, {
        query: { limit: 50 }
      }),
      $fetch<AdminGlobalRoles>(`/api/admin/users/${userId}/global-roles`)
    ])

    detailUser.value = user
    detailAuditEvents.value = events
    detailGlobalRoles.value = globalRoles
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
  detailGlobalRoles.value = null
  detailError.value = null
  closeGlobalRoleModal()
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
    showApiErrorToast({
      title: 'User creation failed',
      error,
      fallback: getErrorMessage(error, 'Failed to create user.')
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
    showApiErrorToast({
      title: 'Disable failed',
      error,
      fallback: getErrorMessage(error, 'Failed to disable user.')
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
    showApiErrorToast({
      title: 'Enable failed',
      error,
      fallback: getErrorMessage(error, 'Failed to enable user.')
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
    showApiErrorToast({
      title: 'Resend failed',
      error,
      fallback: getErrorMessage(error, 'Failed to resend setup email.')
    })
  } finally {
    activeActionKey.value = null
  }
}

function openGlobalRoleModal(action: 'grant' | 'revoke') {
  if (!detailUser.value || detailUser.value.serviceAccount) {
    return
  }
  globalRoleAction.value = action
  globalRoleReason.value = ''
  isGlobalRoleModalOpen.value = true
}

function closeGlobalRoleModal() {
  isGlobalRoleModalOpen.value = false
  globalRoleAction.value = null
  globalRoleReason.value = ''
}

async function submitGlobalRoleAction() {
  if (!detailUser.value || !globalRoleAction.value) {
    return
  }

  const reason = globalRoleReason.value.trim()
  if (!reason) {
    return
  }

  const isGrant = globalRoleAction.value === 'grant'
  const endpoint = isGrant ? 'grant' : 'revoke'

  isSubmittingGlobalRole.value = true
  try {
    const updatedRoles = await $fetch<AdminGlobalRoles>(`/api/admin/users/${detailUser.value.id}/global-curator/${endpoint}`, {
      method: 'POST',
      body: { reason }
    })

    detailGlobalRoles.value = updatedRoles
    toast.add({
      title: isGrant ? 'Global curator granted' : 'Global curator revoked',
      color: 'success'
    })

    closeGlobalRoleModal()
    if (selectedUserId.value) {
      await loadUserDetails(selectedUserId.value)
    }
    await refresh()
  } catch (error: unknown) {
    showApiErrorToast({
      title: isGrant ? 'Grant failed' : 'Revoke failed',
      error,
      fallback: getErrorMessage(error, 'Failed to update global curator role.')
    })
  } finally {
    isSubmittingGlobalRole.value = false
  }
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="User Management" :ui="{ right: 'gap-3' }">
        <template #right>
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            label="Refresh"
            :loading="pending"
            @click="refreshUsersAndDetails"
          />
          <UButton
            v-if="creationAllowed"
            color="primary"
            variant="solid"
            icon="i-lucide-user-plus"
            label="Create User"
            @click="openCreateUserModal"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="searchInput"
            icon="i-lucide-search"
            placeholder="Search by username, email, or name..."
            class="w-full sm:w-80"
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
            class="w-full sm:w-48"
          />
        </template>

        <template #right>
          <div class="flex items-center gap-2">
            <UBadge color="neutral" variant="subtle">
              Service accounts hidden by default
            </UBadge>
            <AppTableColumnsDropdown table-id="admin-users" :columns="columns" />
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div
        v-if="!creationAllowed"
        class="mb-4 rounded-lg border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-warning"
      >
        User creation is disabled because this deployment uses LDAP-managed identities.
      </div>

      <div class="mb-6 grid grid-cols-1 gap-3 md:grid-cols-3">
        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Matched Users
          </p>
          <div class="mt-2 text-xl font-semibold text-highlighted">
            {{ totalItems }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Pending Setup on Page
          </p>
          <div class="mt-2 text-xl font-semibold text-warning">
            {{ visiblePendingCount }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Disabled on Page
          </p>
          <div class="mt-2 text-xl font-semibold text-error">
            {{ visibleDisabledCount }}
          </div>
        </div>
      </div>

      <div>
        <AppTableActiveFilters
          :filters="activeUserFilters"
          @clear-all="clearFilters"
        />

        <UContextMenu :items="contextMenuItems as any">
          <AppTable
            table-id="admin-users"
            :data="users"
            :columns="columns"
            :loading="pending"
            :ui="datatableUi"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <div class="mt-4 flex flex-col gap-4 border-t border-default pt-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="text-sm text-muted">
            Showing {{ showingFrom }} to {{ showingTo }} of {{ totalItems }} users
          </div>

          <div class="flex items-center gap-4">
            <div class="hidden text-sm text-muted md:block">
              Active on page: {{ visibleActiveCount }}
            </div>

            <USelect
              v-model="itemsPerPageModel"
              :items="itemsPerPageOptions"
              value-key="value"
              class="w-32"
              size="sm"
            />

            <UPagination
              v-model:page="page"
              :total="totalItems"
              :items-per-page="itemsPerPage"
              :disabled="totalPages <= 1"
              show-edges
              :sibling-count="1"
            />
          </div>
        </div>
      </div>
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

  <AdminSlideoverUserDetails
    :open="isDetailsOpen"
    :user="detailUser"
    :audit-events="detailAuditEvents"
    :global-roles="detailGlobalRoles"
    :pending="detailPending"
    :error="detailError"
    @close="closeUserDetails"
    @refresh="selectedUserId && loadUserDetails(selectedUserId)"
    @global-role-action="openGlobalRoleModal"
  />

  <UModal
    v-model:open="isGlobalRoleModalOpen"
    :title="globalRoleAction === 'grant' ? 'Grant Global Curator' : globalRoleAction === 'revoke' ? 'Revoke Global Curator' : 'Update Global Curator'"
    :close="{ onClick: closeGlobalRoleModal }"
  >
    <template #body>
      <div class="space-y-4">
        <p class="text-sm text-muted">
          {{ globalRoleAction === 'grant'
            ? 'Grant GLOBAL_CURATOR to this user. A reason is required for audit logging.'
            : 'Revoke GLOBAL_CURATOR from this user. A reason is required for audit logging.' }}
        </p>

        <UFormField label="Reason" required>
          <UTextarea
            v-model="globalRoleReason"
            :rows="4"
            placeholder="Enter reason"
          />
        </UFormField>

        <div class="flex justify-end gap-2 pt-1">
          <UButton
            color="neutral"
            variant="outline"
            :disabled="isSubmittingGlobalRole"
            @click="closeGlobalRoleModal"
          >
            Cancel
          </UButton>
          <UButton
            :color="globalRoleAction === 'grant' ? 'primary' : 'error'"
            :loading="isSubmittingGlobalRole"
            :disabled="!globalRoleReason.trim()"
            @click="submitGlobalRoleAction"
          >
            {{ globalRoleAction === 'grant' ? 'Grant' : 'Revoke' }}
          </UButton>
        </div>
      </div>
    </template>
  </UModal>
</template>
