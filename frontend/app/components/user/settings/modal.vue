<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'
import {
  UModal,
  USlideover,
  LazyUserSettingsProfile,
  LazyUserSettingsAppearance,
  LazyUserSettingsInvitations,
  LazyUserSettingsTransfers,
  LazyUserSettingsNotifications,
  LazyUserSettingsSecurity
} from '#components'

const isMobile = useMediaQuery('(max-width: 639px)')

const { isSettingsOpen, settingsSection } = useUserSettings()
const { documentationUrl } = useRuntimeConfig().public

const sections = [
  { id: 'profile', label: 'Profile', icon: 'i-lucide-user', component: LazyUserSettingsProfile },
  { id: 'appearance', label: 'Appearance', icon: 'i-lucide-sun-moon', component: LazyUserSettingsAppearance },
  { id: 'invitations', label: 'Invitations', icon: 'i-lucide-mail', component: LazyUserSettingsInvitations },
  { id: 'transfers', label: 'Transfers', icon: 'i-lucide-arrow-right-left', component: LazyUserSettingsTransfers },
  { id: 'notifications', label: 'Notifications', icon: 'i-lucide-bell', component: LazyUserSettingsNotifications },
  { id: 'security', label: 'Security', icon: 'i-lucide-shield', component: LazyUserSettingsSecurity }
] as const

const mobileLinks = sections.map(section => ({ label: section.label, value: section.id, icon: section.icon }))

const selectedSection = computed(() => sections.find(section => section.id === settingsSection.value) ?? sections[0])
const links = computed<NavigationMenuItem[]>(() => sections.map(section => ({
  label: section.label,
  icon: section.icon,
  active: settingsSection.value === section.id,
  onSelect: () => { settingsSection.value = section.id }
})))
</script>

<template>
  <component
    :is="isMobile ? USlideover : UModal"
    v-bind="isMobile ? { side: 'bottom' as const } : {}"
    v-model:open="isSettingsOpen"
    title="Settings"
    description="Manage your account and personal preferences."
    :ui="{
      content: isMobile
        ? 'h-[90dvh] max-h-[90dvh] rounded-t-lg'
        : 'max-w-5xl h-[min(48rem,calc(100dvh-4rem))]',
      header: 'shrink-0',
      body: 'p-0 sm:p-0 min-h-0 flex overflow-hidden',
      description: 'sr-only'
    }"
  >
    <template #body>
      <div class="flex min-h-0 min-w-0 flex-1 flex-col sm:flex-row">
        <nav aria-label="Settings sections" class="shrink-0 border-b border-default bg-elevated/40 p-3 sm:w-52 sm:border-b-0 sm:border-r sm:p-4">
          <UNavigationMenu :items="links" orientation="vertical" class="hidden sm:block" />
          <USelect
            v-model="settingsSection"
            :items="mobileLinks"
            aria-label="Settings section"
            class="w-full sm:hidden"
          />
          <UButton
            label="Documentation"
            icon="i-lucide-book-open"
            :to="documentationUrl"
            target="_blank"
            color="neutral"
            variant="link"
            class="mt-6 hidden sm:flex"
          />
        </nav>
        <section :key="settingsSection" :aria-label="selectedSection.label" class="min-h-0 min-w-0 flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">
          <Suspense :key="settingsSection">
            <component :is="selectedSection.component" />
            <template #fallback>
              <div role="status" class="flex items-center justify-center gap-2 py-16 text-muted">
                <UIcon name="i-lucide-loader-circle" class="size-5 animate-spin" />
                Loading {{ selectedSection.label.toLowerCase() }}…
              </div>
            </template>
          </Suspense>
        </section>
      </div>
    </template>
  </component>
</template>
