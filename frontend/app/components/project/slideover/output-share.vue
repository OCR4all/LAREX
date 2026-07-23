<script setup lang="ts">
import type { ActionOutput, ActionOutputShareRequest, ActionOutputShareResponse } from '@/types/action-output'

const props = defineProps<{ projectId: string, output: ActionOutput }>()
const emit = defineEmits<{ close: [boolean] }>()
const toast = useToast()
const { selectedWorkspace } = await useWorkspaceBootstrap()
const state = ref<ActionOutput>({ ...props.output })
const expiresAtInput = ref(toLocal(props.output.shareExpiresAt) || defaultExpiry())
const saving = ref(false)
const revoking = ref(false)
const changed = ref(false)
const recentShare = ref<ActionOutputShareResponse | null>(null)
const browserDownloadUrl = computed(() => recentShare.value ? buildReleaseShareBrowserDownloadUrl(recentShare.value.downloadUrl) : null)
const curlSnippet = computed(() => recentShare.value ? buildProjectReleaseShareCurlSnippet(recentShare.value.downloadUrl, recentShare.value.secret) : '')

function formatLocal(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
function toLocal(value?: string | null) {
  return value ? formatLocal(new Date(value)) : ''
}
function defaultExpiry() {
  const expiry = new Date(Date.now() + 7 * 86400000)
  if (props.output.expiresAt && expiry > new Date(props.output.expiresAt)) return toLocal(props.output.expiresAt)
  return formatLocal(expiry)
}
function payload(): ActionOutputShareRequest {
  return { expiresAt: expiresAtInput.value.length === 16 ? `${expiresAtInput.value}:00` : expiresAtInput.value }
}
function formatDate(value?: string | null) {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}
async function refresh() {
  if (!selectedWorkspace.value) return
  const outputs = await $fetch<ActionOutput[]>(`/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/outputs`)
  const current = outputs.find(output => output.id === props.output.id)
  if (current) state.value = current
}
async function createOrRotate() {
  if (!selectedWorkspace.value) return
  saving.value = true
  try {
    recentShare.value = await $fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/outputs/${props.output.id}/share`, { method: 'POST', body: payload() })
    changed.value = true
    await refresh()
    toast.add({ title: props.output.shareEnabled ? 'Share rotated' : 'Share created', description: 'The bearer secret is shown only once. Copy it now.', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Share failed', description: extractApiErrorMessage(error, 'Failed to create output share'), color: 'error' })
  } finally { saving.value = false }
}
async function updateExpiry() {
  if (!selectedWorkspace.value) return
  saving.value = true
  try {
    state.value = await $fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/outputs/${props.output.id}/share`, { method: 'PATCH', body: payload() })
    changed.value = true
    toast.add({ title: 'Expiry updated', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Update failed', description: extractApiErrorMessage(error, 'Failed to update share expiry'), color: 'error' })
  } finally { saving.value = false }
}
async function revoke() {
  if (!selectedWorkspace.value) return
  revoking.value = true
  try {
    state.value = await $fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/outputs/${props.output.id}/share`, { method: 'DELETE' })
    recentShare.value = null
    changed.value = true
    toast.add({ title: 'Share revoked', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Revoke failed', description: extractApiErrorMessage(error, 'Failed to revoke output share'), color: 'error' })
  } finally { revoking.value = false }
}
async function copySecret() {
  if (recentShare.value) await copyTextToClipboard(recentShare.value.secret, { successTitle: 'Secret copied' })
}
async function copyBrowserUrl() {
  if (browserDownloadUrl.value) await copyTextToClipboard(browserDownloadUrl.value, { successTitle: 'Browser URL copied' })
}
async function copyCurl() {
  await copyTextToClipboard(curlSnippet.value, { successTitle: 'cURL command copied' })
}
</script>

<template>
  <UiResponsiveSlideover :close="{ onClick: () => emit('close', changed) }">
    <template #header>
      <UiSlideoverHeader title="Output Share" icon="i-lucide-key-round" />
    </template>
    <template #body>
      <div class="space-y-6">
        <div class="flex items-center gap-2">
          <UBadge :color="state.shareEnabled ? 'success' : 'neutral'" variant="soft">
            {{ state.shareEnabled ? 'Enabled' : 'Disabled' }}
          </UBadge><span class="text-sm text-muted">{{ state.processorName }}</span>
        </div>
        <div class="grid gap-3 sm:grid-cols-2">
          <div class="rounded-lg border border-default p-3">
            <div class="text-xs uppercase text-muted">
              Secret prefix
            </div><div class="mt-1 font-mono text-sm">
              {{ state.shareSecretPrefix || '—' }}
            </div>
          </div>
          <div class="rounded-lg border border-default p-3">
            <div class="text-xs uppercase text-muted">
              Downloads
            </div><div class="mt-1 text-sm">
              {{ state.shareDownloadCount }}
            </div>
          </div>
          <div class="rounded-lg border border-default p-3">
            <div class="text-xs uppercase text-muted">
              Share expires
            </div><div class="mt-1 text-sm">
              {{ formatDate(state.shareExpiresAt) }}
            </div>
          </div>
          <div class="rounded-lg border border-default p-3">
            <div class="text-xs uppercase text-muted">
              Output expires
            </div><div class="mt-1 text-sm">
              {{ formatDate(state.expiresAt) }}
            </div>
          </div>
        </div>
        <UFormField label="Share expiry" hint="It cannot extend beyond the output's automatic deletion date.">
          <UInput v-model="expiresAtInput" type="datetime-local" :max="toLocal(state.expiresAt) || undefined" />
        </UFormField>
        <div class="flex flex-wrap gap-2">
          <UButton icon="i-lucide-key-round" :loading="saving" @click="createOrRotate">
            {{ state.shareEnabled ? 'Rotate Secret' : 'Create Share' }}
          </UButton>
          <UButton
            v-if="state.shareEnabled"
            color="neutral"
            variant="outline"
            :loading="saving"
            @click="updateExpiry"
          >
            Update Expiry
          </UButton>
          <UButton
            v-if="state.shareEnabled"
            color="error"
            variant="ghost"
            :loading="revoking"
            @click="revoke"
          >
            Revoke
          </UButton>
        </div>
        <section v-if="recentShare" class="space-y-3 rounded-lg border border-success/40 bg-success/10 p-4">
          <UAlert
            color="success"
            variant="soft"
            title="Copy this secret now"
            description="The raw bearer secret will not be shown again."
          />
          <UFormField label="Secret">
            <div class="flex gap-2">
              <UInput :model-value="recentShare.secret" readonly class="flex-1" /><UButton icon="i-lucide-copy" color="neutral" @click="copySecret" />
            </div>
          </UFormField>
          <UFormField v-if="browserDownloadUrl" label="Browser download URL">
            <div class="flex gap-2">
              <UInput :model-value="browserDownloadUrl" readonly class="flex-1" /><UButton icon="i-lucide-copy" color="neutral" @click="copyBrowserUrl" />
            </div>
          </UFormField>
          <UFormField label="cURL">
            <div class="flex gap-2">
              <UInput :model-value="curlSnippet" readonly class="flex-1" /><UButton icon="i-lucide-copy" color="neutral" @click="copyCurl" />
            </div>
          </UFormField>
        </section>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-center">
        <UButton color="neutral" variant="ghost" @click="emit('close', changed)">
          Close
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
