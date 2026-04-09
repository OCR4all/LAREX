<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { getAvatarInitials, resolveManagedProfileAvatarSrc } from '@/utils/avatar'

defineProps<{
  collapsed?: boolean
}>()

const colorMode = useColorMode()
const { user } = useUserSession()
const { isNotificationsSlideoverOpen } = useDashboard()
const { unreadCount, ensureInitialData } = useNotifications()

await ensureInitialData()

const displayName = computed(() => {
  return user.value?.name || user.value?.login || 'User'
})

const avatarSrc = computed(() => {
  return resolveManagedProfileAvatarSrc(user.value?.avatar)
})

const avatarFallback = computed(() => {
  return getAvatarInitials({
    name: user.value?.name,
    login: user.value?.login,
    email: user.value?.email
  })
})

const hasAdminRole = computed(() => {
  return user.value?.roles?.includes('GLOBAL_ADMIN') || false
})

const hasCuratorRole = computed(() => {
  return user.value?.roles?.includes('GLOBAL_CURATOR') || false
})

const displayRole = computed(() => {
  if (hasAdminRole.value) {
    return 'Global Administrator'
  }
  if (hasCuratorRole.value) {
    return 'Curator'
  }
  return 'User'
})

const items = computed<DropdownMenuItem[][]>(() => {
  const settingsItems: DropdownMenuItem[] = [{
    label: 'Settings',
    icon: 'i-lucide-settings',
    to: '/settings'
  }]

  const menuItems: DropdownMenuItem[][] = [[{
    type: 'label',
    label: displayName.value,
    avatar: {
      src: avatarSrc.value,
      alt: displayName.value,
      text: avatarFallback.value
    },
    slot: 'user-label'
  }], [{
    label: 'Notifications',
    icon: 'i-lucide-bell',
    onSelect() { isNotificationsSlideoverOpen.value = true }
  }], settingsItems]

  if (hasAdminRole.value) {
    settingsItems.push({
      label: 'Admin Panel',
      icon: 'i-lucide-shield-check',
      to: '/admin'
    })
  }

  return menuItems.concat([[{
    label: 'Appearance',
    icon: 'i-lucide-sun-moon',
    children: [{
      label: 'Light',
      icon: 'i-lucide-sun',
      type: 'checkbox',
      checked: colorMode.value === 'light',
      onSelect(e: Event) {
        e.preventDefault()
        colorMode.preference = 'light'
      }
    }, {
      label: 'Dark',
      icon: 'i-lucide-moon',
      type: 'checkbox',
      checked: colorMode.value === 'dark',
      onUpdateChecked(checked: boolean) {
        if (checked) {
          colorMode.preference = 'dark'
        }
      },
      onSelect(e: Event) {
        e.preventDefault()
      }
    }]
  }], [{
    label: 'Documentation',
    icon: 'i-lucide-book-open',
    to: 'https://github.com/maxnth',
    target: '_blank'
  }, {
    label: 'GitHub repository',
    icon: 'i-simple-icons-github',
    to: 'https://github.com/maxnth',
    target: '_blank'
  }, {
    label: 'Log out',
    icon: 'i-lucide-log-out',
    async onSelect() {
      await $fetch('/api/auth/logout', { method: 'POST' })
      window.location.href = buildAuthUrl()
    }
  }]])
})
</script>

<template>
  <UDropdownMenu
    :items="items"
    :content="{ align: 'center', collisionPadding: 12 }"
    :ui="{ content: collapsed ? 'w-48' : 'w-(--reka-dropdown-menu-trigger-width)' }"
  >
    <UButton
      v-bind="{
        avatar: {
          src: avatarSrc,
          alt: displayName,
          text: avatarFallback,
          chip: unreadCount > 0 ? { text: String(unreadCount), color: 'error', size: 'xl' } : false
        },
        label: collapsed ? undefined : displayName,
        trailingIcon: collapsed ? undefined : 'i-lucide-chevrons-up-down'
      }"
      color="neutral"
      variant="ghost"
      block
      :square="collapsed"
      class="data-[state=open]:bg-elevated"
      :ui="{
        trailingIcon: 'text-dimmed'
      }"
    />
    <template #chip-leading="{ item }">
      <span
        :style="{
          '--chip-light': `var(--color-${(item as any).chip}-500)`,
          '--chip-dark': `var(--color-${(item as any).chip}-400)`
        }"
        class="ms-0.5 size-2 rounded-sm bg-(--chip-light) dark:bg-(--chip-dark)"
      />
    </template>
  </UDropdownMenu>
</template>
