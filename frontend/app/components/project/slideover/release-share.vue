<script setup lang="ts">
import type {
  ProjectPackageRelease,
  ProjectPackageReleaseShareRequest,
  ProjectPackageReleaseShareResponse
} from '@/types/project-package-release'

const props = defineProps<{
  projectId: string
  release: ProjectPackageRelease
}>()

const emit = defineEmits<{ close: [boolean] }>()

const toast = useToast()
const { selectedWorkspace } = await useWorkspaceBootstrap()

const releaseState = ref<ProjectPackageRelease>({ ...props.release })
const expiresAtInput = ref(toDateTimeLocalInput(props.release.shareExpiresAt) || defaultExpiryInput())
const saving = ref(false)
const revoking = ref(false)
const changed = ref(false)
const recentShare = ref<ProjectPackageReleaseShareResponse | null>(null)

watch(() => props.release, (value) => {
  releaseState.value = { ...value }
  expiresAtInput.value = toDateTimeLocalInput(value.shareExpiresAt) || defaultExpiryInput()
}, { immediate: true })

const curlSnippet = computed(() =>
  recentShare.value ? buildReleaseShareCurlSnippet('project', recentShare.value.downloadUrl, recentShare.value.secret) : ''
)

const wgetSnippet = computed(() =>
  recentShare.value ? buildReleaseShareWgetSnippet('project', recentShare.value.downloadUrl, recentShare.value.secret) : ''
)

const browserDownloadUrl = computed(() =>
  recentShare.value ? buildReleaseShareBrowserDownloadUrl(recentShare.value.downloadUrl) : null
)

async function refreshReleaseState() {
  if (!selectedWorkspace.value) return

  const releases = await $fetch<ProjectPackageRelease[]>(`/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/releases`)
  const updated = releases.find(release => release.id === props.release.id)
  if (updated) {
    releaseState.value = updated
    expiresAtInput.value = toDateTimeLocalInput(updated.shareExpiresAt) || expiresAtInput.value
  }
}

function createPayload(): ProjectPackageReleaseShareRequest {
  return {
    expiresAt: normalizeDateTimeLocal(expiresAtInput.value)
  }
}

async function createOrRotateShare() {
  if (!selectedWorkspace.value) return

  saving.value = true
  try {
    recentShare.value = await $fetch<ProjectPackageReleaseShareResponse>(
      `/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/releases/${props.release.id}/share`,
      {
        method: 'POST',
        body: createPayload()
      }
    )
    changed.value = true
    toast.add({
      title: releaseState.value.shareEnabled ? 'Share rotated' : 'Share created',
      description: 'The secret is shown once. Copy it now for browser or CLI download.',
      color: 'success'
    })
    await refreshReleaseState()
  } catch (error: unknown) {
    toast.add({
      title: releaseState.value.shareEnabled ? 'Rotate failed' : 'Share creation failed',
      description: extractApiErrorMessage(error, 'Failed to create a release share'),
      color: 'error'
    })
  } finally {
    saving.value = false
  }
}

async function updateShareExpiry() {
  if (!selectedWorkspace.value || !releaseState.value.shareEnabled) return

  saving.value = true
  try {
    releaseState.value = await $fetch<ProjectPackageRelease>(
      `/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/releases/${props.release.id}/share`,
      {
        method: 'PATCH',
        body: createPayload()
      }
    )
    changed.value = true
    toast.add({ title: 'Expiry updated', color: 'success' })
  } catch (error: unknown) {
    toast.add({
      title: 'Expiry update failed',
      description: extractApiErrorMessage(error, 'Failed to update the share expiry'),
      color: 'error'
    })
  } finally {
    saving.value = false
  }
}

async function revokeShare() {
  if (!selectedWorkspace.value || !releaseState.value.shareEnabled) return

  revoking.value = true
  try {
    releaseState.value = await $fetch<ProjectPackageRelease>(
      `/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/releases/${props.release.id}/share`,
      {
        method: 'DELETE'
      }
    )
    recentShare.value = null
    changed.value = true
    toast.add({ title: 'Share revoked', color: 'success' })
  } catch (error: unknown) {
    toast.add({
      title: 'Revoke failed',
      description: extractApiErrorMessage(error, 'Failed to revoke the release share'),
      color: 'error'
    })
  } finally {
    revoking.value = false
  }
}

async function copyCurlSnippet() {
  if (!recentShare.value) return
  await copyTextToClipboard(curlSnippet.value, {
    successTitle: 'cURL command copied',
    failureDescription: 'Unable to copy the cURL command to the clipboard.'
  })
}

async function copyWgetSnippet() {
  if (!recentShare.value) return
  await copyTextToClipboard(wgetSnippet.value, {
    successTitle: 'wget command copied',
    failureDescription: 'Unable to copy the wget command to the clipboard.'
  })
}

async function copySecret() {
  if (!recentShare.value) return
  await copyTextToClipboard(recentShare.value.secret, {
    successTitle: 'Secret copied',
    failureDescription: 'Unable to copy the share secret to the clipboard.'
  })
}

async function copyBrowserDownloadUrl() {
  if (!browserDownloadUrl.value) return
  await copyTextToClipboard(browserDownloadUrl.value, {
    successTitle: 'Browser URL copied',
    failureDescription: 'Unable to copy the browser download URL to the clipboard.'
  })
}

function formatDateTime(value?: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

function defaultExpiryInput() {
  const date = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  date.setSeconds(0, 0)
  return formatDateTimeLocal(date)
}

function toDateTimeLocalInput(value?: string | null) {
  if (!value) return ''
  return formatDateTimeLocal(new Date(value))
}

function formatDateTimeLocal(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  const hours = String(value.getHours()).padStart(2, '0')
  const minutes = String(value.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function normalizeDateTimeLocal(value: string) {
  return value.length === 16 ? `${value}:00` : value
}
</script>

<template>
  <UiResponsiveSlideover
    :close="{ onClick: () => emit('close', changed) }"
  >
    <template #header>
      <UiSlideoverHeader title="Release Share" icon="i-lucide-key-round" />
    </template>

    <template #body>
      <div class="space-y-6">
        <section class="space-y-3">
          <div class="flex items-center gap-2">
            <UBadge :color="releaseState.shareEnabled ? 'success' : 'neutral'" variant="soft">
              {{ releaseState.shareEnabled ? 'Enabled' : 'Disabled' }}
            </UBadge>
            <span class="text-sm text-muted">{{ releaseState.versionTag }}</span>
          </div>

          <div class="grid gap-3 sm:grid-cols-2">
            <div class="rounded-lg border border-default p-3">
              <div class="text-xs uppercase tracking-wide text-muted">
                Secret prefix
              </div>
              <div class="mt-1 font-mono text-sm text-highlighted">
                {{ releaseState.shareSecretPrefix || '—' }}
              </div>
            </div>
            <div class="rounded-lg border border-default p-3">
              <div class="text-xs uppercase tracking-wide text-muted">
                Downloads
              </div>
              <div class="mt-1 text-sm text-highlighted">
                {{ releaseState.shareDownloadCount ?? 0 }}
              </div>
            </div>
            <div class="rounded-lg border border-default p-3">
              <div class="text-xs uppercase tracking-wide text-muted">
                Expires
              </div>
              <div class="mt-1 text-sm text-highlighted">
                {{ formatDateTime(releaseState.shareExpiresAt) }}
              </div>
            </div>
            <div class="rounded-lg border border-default p-3">
              <div class="text-xs uppercase tracking-wide text-muted">
                Last used
              </div>
              <div class="mt-1 text-sm text-highlighted">
                {{ formatDateTime(releaseState.shareLastUsedAt) }}
              </div>
            </div>
          </div>
        </section>

        <section class="space-y-3">
          <UFormField
            label="Share expiry"
            hint="This release share is a bearer secret. Anyone with the secret can download the frozen release until it expires or is revoked."
          >
            <UInput v-model="expiresAtInput" type="datetime-local" />
          </UFormField>

          <div class="flex flex-wrap gap-2">
            <UButton
              color="primary"
              icon="i-lucide-key-round"
              :loading="saving"
              @click="createOrRotateShare"
            >
              {{ releaseState.shareEnabled ? 'Rotate Secret' : 'Create Share' }}
            </UButton>

            <UButton
              v-if="releaseState.shareEnabled"
              color="neutral"
              variant="outline"
              icon="i-lucide-calendar-clock"
              :loading="saving"
              @click="updateShareExpiry"
            >
              Update Expiry
            </UButton>

            <UButton
              v-if="releaseState.shareEnabled"
              color="error"
              variant="ghost"
              icon="i-lucide-ban"
              :loading="revoking"
              @click="revokeShare"
            >
              Revoke
            </UButton>
          </div>
        </section>

        <section
          v-if="recentShare"
          class="space-y-4 rounded-lg border border-success/40 bg-success/10 p-4"
        >
          <UAlert
            color="success"
            variant="soft"
            icon="i-lucide-key-round"
            title="Copy this secret now"
            description="The raw secret is shown only once after creation or rotation."
          />

          <UFormField label="Download URL">
            <UInput :model-value="recentShare.downloadUrl" readonly />
          </UFormField>

          <UFormField v-if="browserDownloadUrl" label="Browser download URL">
            <UInput :model-value="browserDownloadUrl" readonly />
          </UFormField>

          <UFormField label="Secret">
            <UInput :model-value="recentShare.secret" readonly />
          </UFormField>

          <div class="flex flex-wrap gap-2">
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-copy"
              @click="copySecret"
            >
              Copy Secret
            </UButton>
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-terminal"
              @click="copyCurlSnippet"
            >
              Copy cURL
            </UButton>
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-terminal-square"
              @click="copyWgetSnippet"
            >
              Copy wget
            </UButton>
            <UButton
              v-if="browserDownloadUrl"
              color="neutral"
              variant="outline"
              icon="i-lucide-globe"
              @click="copyBrowserDownloadUrl"
            >
              Copy Browser URL
            </UButton>
            <UButton
              v-if="browserDownloadUrl"
              color="neutral"
              variant="outline"
              icon="i-lucide-external-link"
              :to="browserDownloadUrl"
              target="_blank"
            >
              Open Browser Page
            </UButton>
          </div>

          <UFormField label="cURL">
            <UTextarea :model-value="curlSnippet" :rows="3" readonly />
          </UFormField>

          <UFormField label="wget">
            <UTextarea :model-value="wgetSnippet" :rows="3" readonly />
          </UFormField>
        </section>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
