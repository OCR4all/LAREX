<script setup lang="ts">
import type {
  CreateUserPrivateAccessTokenRequest,
  CreateUserPrivateAccessTokenResponse,
  PrivateAccessTokenScope,
  UserPrivateAccessTokenSummary
} from '~/types'

const toast = useToast()
const { getPasswordChangeUrl, getDeleteAccountUrl } = useKeycloakUrls()

const { data: passwordChangeUrl } = await useAsyncData(
  'settings-password-change-url',
  () => getPasswordChangeUrl()
)
const { data: deleteAccountUrl } = await useAsyncData(
  'settings-delete-account-url',
  () => getDeleteAccountUrl()
)

interface WorkspaceOption {
  id: string
  name: string
}

const { data: workspaces } = await useFetch<WorkspaceOption[]>('/api/workspaces', {
  default: () => []
})

const patTokens = ref<UserPrivateAccessTokenSummary[]>([])
const patTokensState = ref<'loading' | 'enabled' | 'disabled' | 'error'>('loading')
const loadingTokens = ref(false)
const creatingToken = ref(false)
const revokingTokenId = ref<string | null>(null)
const createdSecret = ref<string | null>(null)

const patForm = reactive({
  workspaceId: '',
  name: '',
  expiresAt: defaultExpiryInput(),
  xmlRead: true,
  xmlWrite: false
})

watchEffect(() => {
  if (!patForm.workspaceId && workspaces.value.length > 0) {
    patForm.workspaceId = workspaces.value[0].id
  }
})

await fetchPatTokens()

const workspaceItems = computed(() =>
  (workspaces.value ?? []).map(workspace => ({
    label: workspace.name,
    value: workspace.id
  }))
)

function openPasswordChange() {
  if (passwordChangeUrl.value) {
    window.open(passwordChangeUrl.value, '_blank')
  }
}

function openDeleteAccount() {
  if (deleteAccountUrl.value) {
    window.open(deleteAccountUrl.value, '_blank')
  }
}

async function fetchPatTokens() {
  loadingTokens.value = true
  if (patTokensState.value === 'loading') {
    patTokensState.value = 'enabled'
  }

  try {
    patTokens.value = await $fetch<UserPrivateAccessTokenSummary[]>('/api/profile/private-access-tokens')
    patTokensState.value = 'enabled'
  } catch (error: unknown) {
    const statusCode = (error as { statusCode?: number, response?: { status?: number } })?.statusCode
      ?? (error as { response?: { status?: number } })?.response?.status

    if (statusCode === 403) {
      patTokensState.value = 'disabled'
      patTokens.value = []
      return
    }

    patTokensState.value = 'error'
    console.error('Failed to load private access tokens', error)
  } finally {
    loadingTokens.value = false
  }
}

async function createPat() {
  if (!patForm.workspaceId || !patForm.name.trim()) {
    toast.add({
      title: 'Missing fields',
      description: 'Workspace and token name are required.',
      color: 'warning'
    })
    return
  }

  const scopes: PrivateAccessTokenScope[] = []
  if (patForm.xmlRead) scopes.push('xml:read')
  if (patForm.xmlWrite) scopes.push('xml:write')

  if (scopes.length === 0) {
    toast.add({
      title: 'No scope selected',
      description: 'Select at least one scope.',
      color: 'warning'
    })
    return
  }

  creatingToken.value = true
  createdSecret.value = null

  try {
    const payload: CreateUserPrivateAccessTokenRequest = {
      workspaceId: patForm.workspaceId,
      name: patForm.name.trim(),
      expiresAt: toApiDateTime(patForm.expiresAt),
      scopes
    }

    const response = await $fetch<CreateUserPrivateAccessTokenResponse>('/api/profile/private-access-tokens', {
      method: 'POST',
      body: payload
    })

    createdSecret.value = response.secret
    patForm.name = ''
    patForm.expiresAt = defaultExpiryInput()
    patForm.xmlRead = true
    patForm.xmlWrite = false

    await fetchPatTokens()

    toast.add({
      title: 'Private access token created',
      description: 'Copy the token now. It will not be shown again.',
      color: 'success'
    })
  } catch (error: unknown) {
    console.error('Failed to create private access token', error)
    const description = extractApiMessage(error, 'Failed to create private access token')
    toast.add({
      title: 'Creation failed',
      description,
      color: 'error'
    })
  } finally {
    creatingToken.value = false
  }
}

async function revokePat(tokenId: string) {
  revokingTokenId.value = tokenId
  try {
    await $fetch(`/api/profile/private-access-tokens/${tokenId}/revoke`, {
      method: 'POST'
    })
    await fetchPatTokens()
    toast.add({
      title: 'Private access token deleted',
      color: 'success'
    })
  } catch (error: unknown) {
    console.error('Failed to revoke private access token', error)
    toast.add({
      title: 'Delete failed',
      description: extractApiMessage(error, 'Failed to delete private access token'),
      color: 'error'
    })
  } finally {
    revokingTokenId.value = null
  }
}

function workspaceName(workspaceId: string): string {
  return workspaces.value.find(workspace => workspace.id === workspaceId)?.name || workspaceId
}

function formatDate(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

function defaultExpiryInput(): string {
  const date = new Date()
  date.setDate(date.getDate() + 30)
  date.setSeconds(0, 0)
  return toDateTimeLocalValue(date)
}

function toDateTimeLocalValue(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toApiDateTime(value?: string): string | undefined {
  if (!value || !value.trim()) {
    return undefined
  }
  return value.length === 16 ? `${value}:00` : value
}

function extractApiMessage(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null) {
    const candidate = error as { data?: { message?: string, error?: string }, message?: string }
    return candidate.data?.message || candidate.data?.error || candidate.message || fallback
  }
  return fallback
}

async function copyCreatedSecret() {
  if (!createdSecret.value) {
    return
  }

  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(createdSecret.value)
    } else {
      const copied = copyWithExecCommand(createdSecret.value)
      if (!copied) {
        throw new Error('Clipboard not available')
      }
    }

    toast.add({
      title: 'Token copied',
      description: 'Private access token copied to clipboard.',
      color: 'success'
    })
  } catch (error) {
    console.error('Failed to copy private access token', error)
    toast.add({
      title: 'Copy failed',
      description: 'Could not copy token to clipboard. Please copy it manually.',
      color: 'error'
    })
  }
}

function copyWithExecCommand(value: string): boolean {
  if (typeof document === 'undefined') {
    return false
  }

  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'absolute'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()

  let copied = false
  try {
    copied = document.execCommand('copy')
  } finally {
    document.body.removeChild(textarea)
  }

  return copied
}
</script>

<template>
  <UPageCard
    data-tour="settings-security-password"
    title="Password"
    description="Change your password securely through Keycloak's self-service portal."
    variant="subtle"
  >
    <UButton
      label="Change Password"
      icon="i-lucide-lock"
      variant="subtle"
      color="neutral"
      :disabled="!passwordChangeUrl"
      @click="openPasswordChange"
    />
  </UPageCard>

  <UPageCard
    title="Private Access Tokens (PAT)"
    description="Create workspace-scoped private access tokens for API automation. Tokens are shown once and can be deleted at any time."
    variant="subtle"
  >
    <div class="space-y-4">
      <div v-if="loadingTokens" class="flex items-center gap-2 text-sm text-muted">
        <UIcon name="i-lucide-loader-2" class="size-4 animate-spin" />
        Loading tokens...
      </div>

      <UAlert
        v-else-if="patTokensState === 'disabled'"
        icon="i-lucide-lock"
        color="warning"
        variant="subtle"
        title="Private access tokens are disabled"
        description="Private access token access has not been enabled for this account."
      />

      <UAlert
        v-else-if="patTokensState === 'error'"
        icon="i-lucide-alert-triangle"
        color="error"
        variant="subtle"
        title="Failed to load private access tokens"
        description="Try again in a moment."
      />

      <template v-else>
        <div v-if="createdSecret" class="space-y-2">
          <UAlert
            icon="i-lucide-key-round"
            color="success"
            variant="subtle"
            title="Copy this token now"
            :description="createdSecret"
          />
          <div class="flex justify-end">
            <UButton
              label="Copy to Clipboard"
              icon="i-lucide-copy"
              color="neutral"
              variant="soft"
              size="sm"
              @click="copyCreatedSecret"
            />
          </div>
        </div>

        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <UFormField label="Workspace" required>
            <USelect
              v-model="patForm.workspaceId"
              :items="workspaceItems"
              :disabled="creatingToken || workspaceItems.length === 0"
            />
          </UFormField>

          <UFormField label="Token Name" required>
            <UInput
              v-model="patForm.name"
              placeholder="e.g. slurm-cluster-job"
              :disabled="creatingToken"
            />
          </UFormField>

          <UFormField label="Expires At">
            <UInput
              v-model="patForm.expiresAt"
              type="datetime-local"
              :disabled="creatingToken"
            />
          </UFormField>

          <UFormField label="Scopes">
            <div class="flex flex-col gap-2 py-1">
              <UCheckbox v-model="patForm.xmlRead" label="xml:read" :disabled="creatingToken" />
              <UCheckbox v-model="patForm.xmlWrite" label="xml:write" :disabled="creatingToken" />
            </div>
          </UFormField>
        </div>

        <div class="flex justify-end">
          <UButton
            label="Create Private Access Token"
            icon="i-lucide-plus"
            :loading="creatingToken"
            :disabled="creatingToken || workspaceItems.length === 0"
            @click="createPat"
          />
        </div>

        <div class="space-y-3">
          <p class="text-sm font-medium text-default">
            Existing tokens
          </p>

          <UAlert
            v-if="patTokens.length === 0"
            icon="i-lucide-info"
            color="neutral"
            variant="subtle"
            description="No private access tokens created yet."
          />

          <div
            v-for="token in patTokens"
            :key="token.id"
            class="rounded-md border border-default p-3"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="space-y-1">
                <p class="font-medium text-sm">
                  {{ token.name }}
                </p>
                <p class="text-xs text-muted">
                  Workspace: {{ workspaceName(token.workspaceId) }}
                </p>
                <p class="text-xs text-muted">
                  Prefix: {{ token.secretPrefix }}
                </p>
                <p class="text-xs text-muted">
                  Scopes: {{ token.scopes.join(', ') }}
                </p>
                <p class="text-xs text-muted">
                  Expires: {{ formatDate(token.expiresAt) }}
                </p>
                <p class="text-xs text-muted">
                  Last used: {{ formatDate(token.lastUsedAt) }}
                </p>
                <UBadge :color="token.active ? 'success' : 'neutral'" variant="subtle" size="sm">
                  {{ token.active ? 'Active' : 'Inactive' }}
                </UBadge>
              </div>

              <UButton
                label="Delete"
                icon="i-lucide-trash-2"
                color="error"
                variant="soft"
                size="sm"
                :loading="revokingTokenId === token.id"
                :disabled="revokingTokenId !== null"
                @click="revokePat(token.id)"
              />
            </div>
          </div>
        </div>
      </template>
    </div>
  </UPageCard>

  <UPageCard
    data-tour="settings-security-delete"
    title="Account"
    description="Delete your account through Keycloak's self-service portal. This action is not reversible. All information related to this account will be deleted permanently."
    class="bg-linear-to-tl from-error/5 from-5% to-default"
  >
    <UButton
      label="Delete account"
      color="error"
      variant="subtle"
      icon="i-lucide-trash"
      :disabled="!deleteAccountUrl"
      @click="openDeleteAccount"
    />
  </UPageCard>
</template>
