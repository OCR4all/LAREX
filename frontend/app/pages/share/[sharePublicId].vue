<script setup lang="ts">
definePageMeta({
  layout: false
})

useSeoMeta({
  title: 'Shared Release Download',
  robots: 'noindex, nofollow'
})

const route = useRoute()
const secret = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)
const backgroundDownloads = useBackgroundDownloads()

const sharePublicId = computed(() => String(route.params.sharePublicId || ''))

const sessionEndpoint = computed(() =>
  `/api/public/share/${encodeURIComponent(sharePublicId.value)}/session`
)

async function startDownload() {
  errorMessage.value = ''
  if (!secret.value.trim()) {
    errorMessage.value = 'Enter the share secret.'
    return
  }

  isSubmitting.value = true
  try {
    const result = await $fetch<{ downloadUrl: string }>(sessionEndpoint.value, {
      method: 'POST',
      body: {
        secret: secret.value
      }
    })

    await backgroundDownloads.runBackgroundJob({
      title: 'Downloading shared release',
      subtitle: sharePublicId.value,
      statusLabel: 'Preparing',
      completedLabel: 'Downloaded',
      icon: 'i-lucide-download',
      task: async (job) => {
        const response = await fetch(result.downloadUrl)
        if (!response.ok) {
          throw new Error(`Download failed (${response.status})`)
        }
        await backgroundDownloads.downloadBlobResponse(response, 'release.zip', job)
      }
    })
  } catch (error: unknown) {
    errorMessage.value = extractApiErrorMessage(error, 'Share link or secret is invalid.')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-muted/20 px-4 py-10">
    <div class="mx-auto w-full max-w-md space-y-4">
      <UCard>
        <template #header>
          <div class="space-y-1">
            <h1 class="text-lg font-semibold">
              Download Shared Release
            </h1>
            <p class="text-sm text-muted">
              Enter the shared secret to download the archive.
            </p>
          </div>
        </template>

        <UForm class="space-y-4" @submit="startDownload">
          <UFormField label="Share secret">
            <UInput
              v-model="secret"
              type="password"
              placeholder="Enter secret"
              autocomplete="off"
              required
            />
          </UFormField>

          <UAlert
            v-if="errorMessage"
            color="error"
            variant="soft"
            icon="i-lucide-alert-triangle"
            :title="errorMessage"
          />

          <UButton
            type="submit"
            color="primary"
            icon="i-lucide-download"
            :loading="isSubmitting"
            block
          >
            Download Archive
          </UButton>
        </UForm>
      </UCard>

      <p class="text-center text-xs text-muted">
        Keep the secret private. Anyone with this link and secret can download the release while it remains active.
      </p>

      <div class="flex justify-center">
        <AppStatusPopoverTrigger force-popover />
      </div>
    </div>

    <AppStatusFloatingMobile />
    <AppStatusOverlayPanel />
  </main>
</template>
