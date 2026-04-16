<script setup lang="ts">
import type {
  AdminGlobalRoles,
  AdminUser,
  AdminUserAuditEvent,
  AdminUserIdentitySource,
  AdminUserOnboardingState
} from '@/types/admin-users'
import { copyTextToClipboard } from '@/utils/clipboard'

interface Props {
  open: boolean
  user: AdminUser | null
  auditEvents: AdminUserAuditEvent[]
  globalRoles: AdminGlobalRoles | null
  patAccessUpdating: boolean
  pending: boolean
  error: string | null
}

defineProps<Props>()

const emit = defineEmits<{
  close: []
  refresh: []
  globalRoleAction: [action: 'grant' | 'revoke']
  patAccessAction: [enabled: boolean]
}>()

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

function formatDetailDate(value?: string | null): string {
  if (!value) {
    return '-'
  }

  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function formatActionLabel(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
    .split(/\s+/)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ')
}

function stringifyAuditValue(value: unknown): string {
  if (value == null) {
    return '-'
  }

  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }

  return String(value)
}

function formatAuditDetails(details?: string | null): Array<{ label: string, value: string }> | null {
  if (!details) {
    return null
  }

  try {
    const parsed = JSON.parse(details) as Record<string, unknown>
    return Object.entries(parsed).map(([key, value]) => ({
      label: formatActionLabel(key),
      value: stringifyAuditValue(value)
    }))
  } catch {
    return [{
      label: 'Details',
      value: details
    }]
  }
}

function avatarFallback(user: AdminUser): string {
  const source = displayName(user).trim() || user.username
  return source
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(part => part.charAt(0).toUpperCase())
    .join('')
}

async function copyUserId(userId: string) {
  await copyTextToClipboard(userId, {
    successTitle: 'User ID copied',
    failureTitle: 'Copy failed',
    failureDescription: 'Unable to copy the user ID to the clipboard.'
  })
}

function handleOpenChange(open: boolean) {
  if (!open) {
    emit('close')
  }
}
</script>

<template>
  <USlideover
    :open="open"
    title="User Details"
    :close="{ onClick: () => emit('close') }"
    @update:open="handleOpenChange"
  >
    <template #body>
      <div class="space-y-8">
        <div v-if="pending && !user" class="text-sm text-muted">
          Loading user details...
        </div>

        <div v-else-if="error" class="rounded-2xl border border-error/20 bg-error/5 px-4 py-3 text-sm text-error">
          {{ error }}
        </div>

        <template v-else-if="user">
          <section class="flex items-start gap-4">
            <UAvatar
              :src="user.avatar || undefined"
              :alt="user.username"
              :fallback="avatarFallback(user)"
              size="lg"
              class="shrink-0"
            />

            <div class="min-w-0 flex-1">
              <div class="text-base font-semibold text-highlighted break-words">
                {{ displayName(user) }}
              </div>

              <div class="mt-1 text-sm text-muted break-all">
                {{ user.email || user.username }}
              </div>

              <div class="mt-3 flex flex-wrap items-center gap-2">
                <UBadge
                  :color="statusColor(user.onboardingState)"
                  variant="soft"
                >
                  {{ statusLabel(user.onboardingState) }}
                </UBadge>

                <div
                  class="inline-flex items-center gap-2 text-sm font-medium"
                  :class="user.emailVerified ? 'text-success' : 'text-warning'"
                >
                  <UIcon
                    :name="user.emailVerified ? 'i-lucide-check-circle' : 'i-lucide-alert-circle'"
                    class="size-5 shrink-0"
                  />
                  <span>{{ user.emailVerified ? 'Verified' : 'Unverified' }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="space-y-4">
            <h3 class="text-xs font-medium uppercase tracking-wide text-muted">
              Account Information
            </h3>

            <div class="space-y-3">
              <div class="flex flex-col gap-3 rounded-lg border border-default px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div class="flex items-center gap-3 text-muted">
                  <UIcon name="i-lucide-user" class="size-4 shrink-0" />
                  <span class="text-sm">Username</span>
                </div>
                <div class="text-sm font-medium text-highlighted break-all sm:text-right">
                  {{ user.username }}
                </div>
              </div>

              <div class="flex flex-col gap-3 rounded-lg border border-default px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div class="flex items-center gap-3 text-muted">
                  <UIcon name="i-lucide-hash" class="size-4 shrink-0" />
                  <span class="text-sm">User ID</span>
                </div>
                <div class="flex items-center gap-2 sm:max-w-[55%] sm:justify-end">
                  <div class="min-w-0 text-sm font-medium text-highlighted truncate">
                    {{ user.id }}
                  </div>
                  <UButton
                    color="neutral"
                    variant="ghost"
                    icon="i-lucide-copy"
                    size="sm"
                    aria-label="Copy user ID"
                    @click="copyUserId(user.id)"
                  />
                </div>
              </div>

              <div class="flex flex-col gap-3 rounded-lg border border-default px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div class="flex items-center gap-3 text-muted">
                  <UIcon name="i-lucide-globe" class="size-4 shrink-0" />
                  <span class="text-sm">Identity Source</span>
                </div>
                <div class="text-sm font-medium text-highlighted sm:text-right">
                  <div>{{ identitySourceLabel(user.identitySource) }}</div>
                  <div v-if="user.externallyManaged" class="mt-1 text-sm font-normal text-muted">
                    Managed externally
                  </div>
                </div>
              </div>

              <div class="flex flex-col gap-3 rounded-lg border border-default px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div class="flex items-center gap-3 text-muted">
                  <UIcon name="i-lucide-calendar-days" class="size-4 shrink-0" />
                  <span class="text-sm">Created</span>
                </div>
                <div class="text-sm font-medium text-highlighted sm:text-right">
                  {{ formatDetailDate(user.createdTimestamp) }}
                </div>
              </div>
            </div>
          </section>

          <USeparator />

          <section class="space-y-4">
            <h3 class="text-xs font-medium uppercase tracking-wide text-muted">
              Global Permissions
            </h3>

            <div class="space-y-3">
              <div class="flex items-center justify-between gap-4 rounded-lg border border-default bg-elevated/30 px-4 py-3">
                <div class="flex min-w-0 items-center gap-3">
                  <UIcon name="i-lucide-shield" class="size-4 shrink-0 text-muted" />
                  <div class="min-w-0">
                    <div class="text-sm font-medium text-highlighted">
                      Global Admin
                    </div>
                    <div class="text-sm text-muted">
                      Full administrative access
                    </div>
                  </div>
                </div>

                <UBadge color="neutral" variant="subtle">
                  {{ globalRoles?.globalAdmin ? 'Yes' : 'No' }}
                </UBadge>
              </div>

              <div class="flex items-center justify-between gap-4 rounded-lg border border-default bg-elevated/30 px-4 py-3">
                <div class="flex min-w-0 items-center gap-3">
                  <UIcon name="i-lucide-key" class="size-4 shrink-0 text-muted" />
                  <div class="min-w-0">
                    <div class="text-sm font-medium text-highlighted">
                      Global Curator
                    </div>
                    <div class="text-sm text-muted">
                      Can curate resources across workspaces
                    </div>
                  </div>
                </div>

                <UBadge color="neutral" variant="subtle">
                  {{ globalRoles?.globalCurator ? 'Yes' : 'No' }}
                </UBadge>
              </div>

              <div class="flex items-center justify-between gap-4 rounded-lg border border-default bg-elevated/30 px-4 py-3">
                <div class="flex min-w-0 items-center gap-3">
                  <UIcon name="i-lucide-key-round" class="size-4 shrink-0 text-muted" />
                  <div class="min-w-0">
                    <div class="text-sm font-medium text-highlighted">
                      PAT Access
                    </div>
                    <div class="text-sm text-muted">
                      Can create and revoke private access tokens in account settings
                    </div>
                  </div>
                </div>

                <UBadge :color="user.privateAccessTokensEnabled ? 'success' : 'neutral'" variant="subtle">
                  {{ user.privateAccessTokensEnabled ? 'Enabled' : 'Disabled' }}
                </UBadge>
              </div>
            </div>

            <p class="text-sm text-muted">
              Changes take effect after token refresh or re-login.
            </p>

            <div v-if="!user.serviceAccount" class="flex flex-wrap gap-3">
              <UButton
                :color="globalRoles?.globalCurator ? 'error' : 'neutral'"
                variant="outline"
                :icon="globalRoles?.globalCurator ? 'i-lucide-user-minus' : 'i-lucide-user-plus'"
                @click="emit('globalRoleAction', globalRoles?.globalCurator ? 'revoke' : 'grant')"
              >
                {{ globalRoles?.globalCurator ? 'Revoke Curator' : 'Grant Curator' }}
              </UButton>

              <UButton
                :color="user.privateAccessTokensEnabled ? 'error' : 'primary'"
                variant="outline"
                :icon="user.privateAccessTokensEnabled ? 'i-lucide-toggle-left' : 'i-lucide-toggle-right'"
                :loading="patAccessUpdating"
                @click="emit('patAccessAction', !user.privateAccessTokensEnabled)"
              >
                {{ user.privateAccessTokensEnabled ? 'Disable PAT Access' : 'Enable PAT Access' }}
              </UButton>
            </div>

            <p v-else class="text-sm text-muted">
              Service accounts cannot be changed.
            </p>
          </section>

          <div
            v-if="user.identitySource === 'LDAP'"
            class="rounded-2xl border border-info/25 bg-info/10 px-4 py-3 text-sm text-info"
          >
            Account lifecycle changes must be handled in your directory or identity provider.
          </div>

          <USeparator />

          <section class="space-y-4">
            <div class="mb-3 flex items-center justify-between">
              <h3 class="text-xs font-medium uppercase tracking-wide text-muted">
                Audit Events
              </h3>
              <UButton
                size="sm"
                variant="ghost"
                color="neutral"
                icon="i-lucide-refresh-cw"
                :loading="pending"
                @click="emit('refresh')"
              >
                Refresh
              </UButton>
            </div>

            <div
              v-if="auditEvents.length === 0"
              class="rounded-lg border border-dashed border-default px-6 py-10 text-center text-sm text-muted"
            >
              No audit events recorded for this user.
            </div>

            <div v-else class="space-y-3">
              <div
                v-for="event in auditEvents"
                :key="event.id"
                class="rounded-lg border border-default bg-default px-4 py-3"
              >
                <div class="flex flex-col gap-2">
                  <div class="flex flex-wrap items-center gap-2.5">
                    <UBadge :color="event.outcome === 'SUCCESS' ? 'success' : 'error'" variant="soft" class="rounded-full">
                      {{ formatActionLabel(event.outcome) }}
                    </UBadge>
                    <span class="font-semibold text-highlighted">{{ formatActionLabel(event.action) }}</span>
                    <span class="text-sm text-muted">by {{ event.actorUsername }}</span>
                  </div>
                  <div class="text-sm text-muted">
                    {{ formatDetailDate(event.created) }}
                  </div>

                  <div
                    v-if="formatAuditDetails(event.details)?.length"
                    class="overflow-hidden rounded-md border border-default"
                  >
                    <table class="min-w-full divide-y divide-default text-sm">
                      <tbody class="divide-y divide-default">
                        <tr
                          v-for="detail in formatAuditDetails(event.details)"
                          :key="`${event.id}-${detail.label}`"
                          class="align-top"
                        >
                          <th class="w-40 bg-elevated/30 px-3 py-2 text-left font-medium text-muted">
                            {{ detail.label }}
                          </th>
                          <td class="px-3 py-2 text-highlighted break-words">
                            {{ detail.value }}
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </template>
      </div>
    </template>
  </USlideover>
</template>
