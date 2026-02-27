<script setup lang="ts">
import type { NotificationType } from '~/types'

const toast = useToast()
const {
  preferences,
  types,
  isLoading,
  isSaving,
  desktopPermission,
  isDesktopSupported,
  fetchPreferences,
  toggleEmail,
  toggleDesktop,
  toggleInApp,
  requestDesktopPermission,
  getPreference
} = useNotificationPreferences()

onMounted(async () => {
  await fetchPreferences()
})

async function onEmailToggle(type: NotificationType, value: boolean) {
  const success = await toggleEmail(type, value)
  if (success) {
    toast.add({
      title: 'Preferences updated',
      description: `Email notifications ${value ? 'enabled' : 'disabled'} for ${getTypeLabel(type)}`,
      color: 'success'
    })
  } else {
    toast.add({
      title: 'Failed to update',
      description: 'Could not update email notification preference',
      color: 'error'
    })
  }
}

async function onDesktopToggle(type: NotificationType, value: boolean) {
  const success = await toggleDesktop(type, value)
  if (success) {
    toast.add({
      title: 'Preferences updated',
      description: `Desktop notifications ${value ? 'enabled' : 'disabled'} for ${getTypeLabel(type)}`,
      color: 'success'
    })
  } else {
    if (desktopPermission.value === 'denied') {
      toast.add({
        title: 'Permission denied',
        description: 'Please enable notifications in your browser settings',
        color: 'warning'
      })
    } else {
      toast.add({
        title: 'Failed to update',
        description: 'Could not update desktop notification preference',
        color: 'error'
      })
    }
  }
}

async function onInAppToggle(type: NotificationType, value: boolean) {
  const success = await toggleInApp(type, value)
  if (success) {
    toast.add({
      title: 'Preferences updated',
      description: `In-app notifications ${value ? 'enabled' : 'disabled'} for ${getTypeLabel(type)}`,
      color: 'success'
    })
  } else {
    toast.add({
      title: 'Failed to update',
      description: 'Could not update in-app notification preference',
      color: 'error'
    })
  }
}

async function onRequestPermission() {
  const permission = await requestDesktopPermission()
  if (permission === 'granted') {
    toast.add({
      title: 'Permission granted',
      description: 'You can now receive desktop notifications',
      color: 'success'
    })
  } else if (permission === 'denied') {
    toast.add({
      title: 'Permission denied',
      description: 'You can enable notifications in your browser settings',
      color: 'warning'
    })
  }
}

function getTypeLabel(type: NotificationType): string {
  const typeInfo = types.value.find(t => t.type === type)
  return typeInfo?.label || type.replace(/_/g, ' ').toLowerCase()
}

function getEmailValue(type: NotificationType): boolean {
  return getPreference(type)?.emailEnabled ?? false
}

function getDesktopValue(type: NotificationType): boolean {
  return getPreference(type)?.desktopEnabled ?? false
}

function getInAppValue(type: NotificationType): boolean {
  return getPreference(type)?.inAppEnabled ?? true
}

const permissionLabel = computed(() => {
  switch (desktopPermission.value) {
    case 'granted':
      return 'Enabled'
    case 'denied':
      return 'Blocked'
    case 'unsupported':
      return 'Not supported'
    default:
      return 'Not enabled'
  }
})

const permissionColor = computed(() => {
  switch (desktopPermission.value) {
    case 'granted':
      return 'success'
    case 'denied':
      return 'error'
    case 'unsupported':
      return 'neutral'
    default:
      return 'warning'
  }
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <UPageCard
        data-tour="settings-notifications-permission"
        title="Desktop Notifications"
        description="Receive browser notifications even when LAREX is in the background"
        variant="subtle"
        class="mb-4"
      />

      <UPageCard variant="subtle">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-bell" class="size-5 text-muted" />
            <div>
              <p class="font-medium">
                Browser Permission
              </p>
              <p class="text-sm text-muted">
                {{ isDesktopSupported ? 'Allow LAREX to show desktop notifications' : 'Your browser does not support desktop notifications' }}
              </p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <UBadge :color="permissionColor" variant="solid">
              {{ permissionLabel }}
            </UBadge>
            <UButton
              v-if="isDesktopSupported && desktopPermission === 'default'"
              size="sm"
              variant="soft"
              @click="onRequestPermission"
            >
              Enable
            </UButton>
          </div>
        </div>
      </UPageCard>
    </div>

    <div>
      <UPageCard
        title="Notification Preferences"
        description="Choose how you want to be notified for each type of event"
        variant="subtle"
        class="mb-4"
      />

      <UPageCard v-if="isLoading" variant="subtle">
        <div class="flex items-center justify-center py-8">
          <UIcon name="i-lucide-loader-2" class="size-6 animate-spin text-muted" />
        </div>
      </UPageCard>

      <UPageCard v-else variant="subtle" data-tour="settings-notifications-matrix">
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="border-b border-default">
                <th class="py-3 text-left text-sm font-medium text-muted">
                  Notification Type
                </th>
                <th class="px-4 py-3 text-center text-sm font-medium text-muted">
                  <div class="flex items-center justify-center gap-1">
                    <UIcon name="i-lucide-mail" class="size-4" />
                    <span>Email</span>
                  </div>
                </th>
                <th class="px-4 py-3 text-center text-sm font-medium text-muted">
                  <div class="flex items-center justify-center gap-1">
                    <UIcon name="i-lucide-monitor" class="size-4" />
                    <span>Desktop</span>
                  </div>
                </th>
                <th class="px-4 py-3 text-center text-sm font-medium text-muted">
                  <div class="flex items-center justify-center gap-1">
                    <UIcon name="i-lucide-bell" class="size-4" />
                    <span>In-App</span>
                  </div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="typeInfo in types"
                :key="typeInfo.type"
                class="border-b border-default last:border-0"
              >
                <td class="py-4">
                  <div>
                    <p class="font-medium">
                      {{ typeInfo.label }}
                    </p>
                    <p class="text-sm text-muted">
                      {{ typeInfo.description }}
                    </p>
                  </div>
                </td>
                <td class="px-4 py-4 text-center">
                  <USwitch
                    :model-value="getEmailValue(typeInfo.type)"
                    :disabled="isSaving"
                    @update:model-value="onEmailToggle(typeInfo.type, $event)"
                  />
                </td>
                <td class="px-4 py-4 text-center">
                  <UTooltip
                    v-if="desktopPermission !== 'granted'"
                    :text="desktopPermission === 'denied' ? 'Notifications blocked in browser' : 'Enable browser permission first'"
                  >
                    <USwitch
                      :model-value="getDesktopValue(typeInfo.type)"
                      :disabled="isSaving || desktopPermission !== 'granted'"
                      @update:model-value="onDesktopToggle(typeInfo.type, $event)"
                    />
                  </UTooltip>
                  <USwitch
                    v-else
                    :model-value="getDesktopValue(typeInfo.type)"
                    :disabled="isSaving"
                    @update:model-value="onDesktopToggle(typeInfo.type, $event)"
                  />
                </td>
                <td class="px-4 py-4 text-center">
                  <USwitch
                    :model-value="getInAppValue(typeInfo.type)"
                    :disabled="isSaving"
                    @update:model-value="onInAppToggle(typeInfo.type, $event)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="types.length === 0" class="py-8 text-center text-muted">
          <UIcon name="i-lucide-bell-off" class="mx-auto mb-2 size-8" />
          <p>No notification types available</p>
        </div>
      </UPageCard>
    </div>

    <UPageCard variant="subtle" class="bg-info/5">
      <div class="flex gap-3">
        <UIcon name="i-lucide-info" class="size-5 shrink-0 text-info" />
        <div class="text-sm text-muted">
          <p class="font-medium text-default">
            About notification channels
          </p>
          <ul class="mt-1 list-inside list-disc space-y-1">
            <li><strong>Email:</strong> Receive notifications to your registered email address</li>
            <li><strong>Desktop:</strong> Get browser push notifications even when LAREX is in the background</li>
            <li><strong>In-App:</strong> See notifications in the notification panel within LAREX</li>
          </ul>
        </div>
      </div>
    </UPageCard>
  </div>
</template>
