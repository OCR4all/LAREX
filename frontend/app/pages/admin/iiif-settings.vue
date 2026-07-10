<script setup lang="ts">
definePageMeta({ layout: 'admin', middleware: 'admin' })

interface IiifSettings {
  deploymentDefaultDownloadMinIntervalMs: number
  overrideDownloadMinIntervalMs: number | null
  effectiveDownloadMinIntervalMs: number
  updatedAt: string | null
  updatedByUserId: string | null
}

const toast = useToast()
const intervalInput = ref(100)
const isSaving = ref(false)
const isResetting = ref(false)

const {
  data: settings,
  pending,
  error,
  refresh
} = await useFetch<IiifSettings>('/api/admin/settings/iiif', {
  key: 'admin-iiif-settings'
})

watch(settings, (value) => {
  if (value) {
    intervalInput.value = value.overrideDownloadMinIntervalMs ?? value.effectiveDownloadMinIntervalMs
  }
}, { immediate: true })

const validationError = computed(() => {
  if (!Number.isInteger(intervalInput.value)) {
    return 'Enter a whole number of milliseconds.'
  }
  if (intervalInput.value < 0 || intervalInput.value > 60_000) {
    return 'The interval must be between 0 and 60,000 milliseconds.'
  }
  return undefined
})

const hasOverride = computed(() => settings.value?.overrideDownloadMinIntervalMs != null)
const hasChanges = computed(() =>
  !!settings.value
  && !validationError.value
  && intervalInput.value !== settings.value?.overrideDownloadMinIntervalMs
)

async function saveOverride() {
  if (validationError.value) return

  isSaving.value = true
  try {
    settings.value = await $fetch<IiifSettings>('/api/admin/settings/iiif', {
      method: 'PUT',
      body: { downloadMinIntervalMs: intervalInput.value }
    })
    toast.add({
      title: 'IIIF settings saved',
      description: `Downloads now use a ${settings.value.effectiveDownloadMinIntervalMs} ms minimum interval per host.`,
      color: 'success'
    })
  } catch (requestError: unknown) {
    showApiErrorToast({
      title: 'Failed to save IIIF settings',
      error: requestError,
      fallback: 'The IIIF settings could not be updated.'
    })
  } finally {
    isSaving.value = false
  }
}

async function resetOverride() {
  isResetting.value = true
  try {
    settings.value = await $fetch<IiifSettings>('/api/admin/settings/iiif', {
      method: 'PUT',
      body: { downloadMinIntervalMs: null }
    })
    intervalInput.value = settings.value.effectiveDownloadMinIntervalMs
    toast.add({
      title: 'IIIF settings reset',
      description: 'Download pacing now uses the deployment default.',
      color: 'success'
    })
  } catch (requestError: unknown) {
    showApiErrorToast({
      title: 'Failed to reset IIIF settings',
      error: requestError,
      fallback: 'The IIIF settings could not be reset.'
    })
  } finally {
    isResetting.value = false
  }
}

function formatUpdatedAt(value: string | null | undefined) {
  if (!value) return 'Not overridden'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}
</script>

<template>
  <UDashboardPanel id="admin-iiif-settings">
    <template #header>
      <UDashboardNavbar title="IIIF Settings">
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            :loading="pending"
            aria-label="Refresh IIIF settings"
            @click="refresh()"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="mx-auto flex w-full max-w-4xl flex-col gap-6 p-6">
        <UAlert
          v-if="error"
          color="error"
          variant="subtle"
          icon="i-lucide-circle-alert"
          title="Could not load IIIF settings"
          :description="error.message"
        />

        <div class="grid gap-4 sm:grid-cols-3">
          <UCard>
            <p class="text-sm text-muted">
              Deployment default
            </p>
            <p class="mt-2 text-2xl font-semibold text-highlighted">
              {{ settings?.deploymentDefaultDownloadMinIntervalMs ?? '—' }} ms
            </p>
          </UCard>
          <UCard>
            <p class="text-sm text-muted">
              Effective interval
            </p>
            <p class="mt-2 text-2xl font-semibold text-highlighted">
              {{ settings?.effectiveDownloadMinIntervalMs ?? '—' }} ms
            </p>
          </UCard>
          <UCard>
            <p class="text-sm text-muted">
              Configuration source
            </p>
            <div class="mt-3">
              <UBadge :color="hasOverride ? 'warning' : 'neutral'" variant="subtle">
                {{ hasOverride ? 'Admin override' : 'Deployment default' }}
              </UBadge>
            </div>
          </UCard>
        </div>

        <UCard>
          <template #header>
            <div class="flex items-start gap-3">
              <div class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                <UIcon name="i-lucide-gauge" class="size-5 text-primary" />
              </div>
              <div>
                <h2 class="font-semibold text-highlighted">
                  Download pacing
                </h2>
                <p class="mt-1 text-sm text-muted">
                  The minimum delay between image download starts to the same IIIF host.
                </p>
              </div>
            </div>
          </template>

          <div class="space-y-5">
            <UAlert
              color="neutral"
              variant="subtle"
              icon="i-lucide-info"
              title="Adaptive cooldown remains active"
              description="A value of 0 disables fixed pacing, but HTTP 429 and Retry-After responses still pause preview and download traffic for that host."
            />

            <UFormField
              label="Minimum download interval"
              description="Applies per remote IIIF host. Allowed range: 0–60,000 ms."
              :error="validationError"
            >
              <UInput
                v-model.number="intervalInput"
                type="number"
                min="0"
                max="60000"
                step="1"
                class="max-w-xs"
              >
                <template #trailing>
                  <span class="text-xs text-muted">ms</span>
                </template>
              </UInput>
            </UFormField>

            <div class="flex flex-wrap gap-3">
              <UButton
                icon="i-lucide-save"
                :loading="isSaving"
                :disabled="pending || !!validationError || !hasChanges"
                @click="saveOverride"
              >
                Save override
              </UButton>
              <UButton
                icon="i-lucide-rotate-ccw"
                color="neutral"
                variant="outline"
                :loading="isResetting"
                :disabled="pending || !hasOverride"
                @click="resetOverride"
              >
                Reset to deployment default
              </UButton>
            </div>
          </div>

          <template #footer>
            <p class="text-xs text-muted">
              Last changed: {{ formatUpdatedAt(settings?.updatedAt) }}
              <template v-if="settings?.updatedByUserId">
                by {{ settings.updatedByUserId }}
              </template>
            </p>
          </template>
        </UCard>

        <UCard>
          <template #header>
            <h2 class="font-semibold text-highlighted">
              Preview behavior
            </h2>
          </template>
          <p class="text-sm leading-6 text-muted">
            IIIF previews use a dedicated worker queue and do not probe every remote image.
            Storage preflight uses a 10 MB estimate per importable canvas. Preview metadata
            bypasses fixed download pacing but honors an active remote-server cooldown.
          </p>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>
</template>
