<script setup lang="ts">
const { getPasswordChangeUrl, getDeleteAccountUrl } = useKeycloakUrls()

const { data: security } = await useFetch<{ passwordChangeAvailable: boolean }>('/api/profile/security', {
  key: 'settings-account-security'
})
const passwordChangeAvailable = computed(() => security.value?.passwordChangeAvailable !== false)

const { data: passwordChangeUrl } = await useAsyncData(
  'settings-password-change-url',
  () => getPasswordChangeUrl()
)
const { data: deleteAccountUrl } = await useAsyncData(
  'settings-delete-account-url',
  () => getDeleteAccountUrl()
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
</script>

<template>
  <div class="space-y-6">
    <UPageCard
      v-if="passwordChangeAvailable"
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
      v-else
      data-tour="settings-security-password"
      title="Password"
      description="You sign in through your organization's single sign-on, so your password is managed there."
      variant="subtle"
    />

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
  </div>
</template>
