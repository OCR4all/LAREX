<script setup lang="ts">
import type { AdminAvatarSettings, AvatarStyle } from '~/types/avatar'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const toast = useToast()
const { refresh: refreshGlobalAvatarSettings } = useAvatarSettings()
const selectedStyle = ref<AvatarStyle>('GRADIENT')
const isSaving = ref(false)

const options: Array<{
  value: AvatarStyle
  label: string
  description: string
}> = [
  {
    value: 'GRADIENT',
    label: 'Gradient',
    description: 'Soft deterministic mesh gradients.'
  },
  {
    value: 'IDENTICON',
    label: 'Identicon',
    description: 'Geometric GitHub-style patterns.'
  },
  {
    value: 'FLOW_FIELD',
    label: 'Flow Field',
    description: 'Plotter-inspired generative lines.'
  },
  {
    value: 'INITIALS',
    label: 'Initials',
    description: 'Classic name-based placeholders.'
  }
]

const {
  data: settings,
  pending,
  error,
  refresh
} = await useFetch<AdminAvatarSettings>('/api/admin/settings/avatar', {
  key: 'admin-avatar-settings'
})

watch(settings, (value) => {
  if (value) selectedStyle.value = value.defaultStyle
}, { immediate: true })

const hasChanges = computed(() => !!settings.value && selectedStyle.value !== settings.value.defaultStyle)

async function saveSettings() {
  isSaving.value = true
  try {
    settings.value = await $fetch<AdminAvatarSettings>('/api/admin/settings/avatar', {
      method: 'PUT',
      body: { defaultStyle: selectedStyle.value }
    })
    await refreshGlobalAvatarSettings()
    toast.add({
      title: 'Avatar style saved',
      description: `${options.find(option => option.value === selectedStyle.value)?.label} is now the default generated avatar style.`,
      color: 'success'
    })
  } catch (requestError: unknown) {
    showApiErrorToast({
      title: 'Failed to save avatar settings',
      error: requestError,
      fallback: 'The default avatar style could not be updated.'
    })
  } finally {
    isSaving.value = false
  }
}

function formatUpdatedAt(value: string | null | undefined) {
  if (!value) return 'Initial Gradient default'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}
</script>

<template>
  <UDashboardPanel id="admin-avatar-settings">
    <template #header>
      <UDashboardNavbar title="Avatar Settings">
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            :loading="pending"
            aria-label="Refresh avatar settings"
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
          title="Could not load avatar settings"
          :description="error.message"
        />

        <UCard>
          <template #header>
            <div>
              <h2 class="font-semibold text-highlighted">
                Default generated avatar
              </h2>
              <p class="mt-1 text-sm text-muted">
                This style is used whenever a user, project, or workspace has no custom image.
              </p>
            </div>
          </template>

          <div class="grid gap-4 sm:grid-cols-2">
            <button
              v-for="option in options"
              :key="option.value"
              type="button"
              class="flex items-center gap-4 rounded-lg border p-4 text-left transition-colors"
              :class="selectedStyle === option.value
                ? 'border-primary bg-primary/5 ring-1 ring-primary'
                : 'border-default hover:bg-elevated/50'"
              :aria-pressed="selectedStyle === option.value"
              @click="selectedStyle = option.value"
            >
              <AppAvatar
                seed="avatar-settings-preview"
                alt="Ada Lovelace"
                size="3xl"
                :avatar-style="option.value"
              />
              <span class="min-w-0">
                <span class="block font-medium text-highlighted">{{ option.label }}</span>
                <span class="mt-1 block text-sm text-muted">{{ option.description }}</span>
              </span>
              <UIcon
                v-if="selectedStyle === option.value"
                name="i-lucide-circle-check"
                class="ml-auto size-5 shrink-0 text-primary"
              />
            </button>
          </div>

          <div class="mt-6">
            <UButton
              icon="i-lucide-save"
              :loading="isSaving"
              :disabled="pending || !hasChanges"
              @click="saveSettings"
            >
              Save default style
            </UButton>
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

        <UAlert
          color="neutral"
          variant="subtle"
          icon="i-lucide-image"
          title="Custom images take precedence"
          description="Uploaded user profile images remain visible regardless of the selected generated style. Removing an image restores this default."
        />
      </div>
    </template>
  </UDashboardPanel>
</template>
