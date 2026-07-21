<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'

defineProps<{
  collapsed?: boolean
}>()

const colorMode = useColorMode()
const { user } = useUserSession()
const { documentationUrl } = useRuntimeConfig().public

const displayName = computed(() => {
  return user.value?.name || user.value?.login || 'User'
})

const avatarSrc = computed(() => {
  return resolveManagedProfileAvatarSrc(user.value?.avatar)
})

const hasAdminRole = computed(() => {
  return user.value?.roles?.includes('GLOBAL_ADMIN') || false
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
    slot: 'user-label'
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
      checked: colorMode.preference === 'light',
      onSelect(e: Event) {
        e.preventDefault()
        colorMode.preference = 'light'
      }
    }, {
      label: 'Dark',
      icon: 'i-lucide-moon',
      type: 'checkbox',
      checked: colorMode.preference === 'dark',
      onUpdateChecked(checked: boolean) {
        if (checked) {
          colorMode.preference = 'dark'
        }
      },
      onSelect(e: Event) {
        e.preventDefault()
      }
    }, {
      label: 'System',
      icon: 'i-lucide-monitor',
      type: 'checkbox',
      checked: colorMode.preference === 'system',
      onUpdateChecked(checked: boolean) {
        if (checked) {
          colorMode.preference = 'system'
        }
      },
      onSelect(e: Event) {
        e.preventDefault()
      }
    }]
  }], [{
    label: 'Documentation',
    icon: 'i-lucide-book-open',
    to: documentationUrl,
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
    >
      <template #leading>
        <AppAvatar
          :seed="user?.id || user?.login || 'user'"
          :src="avatarSrc"
          :alt="displayName"
          size="sm"
        />
      </template>
    </UButton>
    <template #user-label>
      <div class="flex min-w-0 items-center gap-2">
        <AppAvatar
          :seed="user?.id || user?.login || 'user'"
          :src="avatarSrc"
          :alt="displayName"
          size="sm"
        />
        <span class="truncate">{{ displayName }}</span>
      </div>
    </template>
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
