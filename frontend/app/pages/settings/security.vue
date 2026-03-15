<script setup lang="ts">
const { getPasswordChangeUrl, getDeleteAccountUrl } = useKeycloakUrls()

const { data: passwordChangeUrl } = await useAsyncData(
  'settings-password-change-url',
  () => getPasswordChangeUrl()
)
const { data: deleteAccountUrl } = await useAsyncData(
  'settings-delete-account-url',
  () => getDeleteAccountUrl()
)

const openPasswordChange = () => {
  if (passwordChangeUrl.value) {
    window.open(passwordChangeUrl.value, '_blank')
  }
}

const openDeleteAccount = () => {
  if (deleteAccountUrl.value) {
    window.open(deleteAccountUrl.value, '_blank')
  }
}
</script>

<template>
  <UPageCard
    data-tour="settings-security-password"
    title="Password"
    description="Change your password securely through Keycloak's self-service portal."
    variant="subtle"
  >
    <UButton
      label="Change Password"
      icon="i-lucide-lock"
      variant="subtle"
      colour="neutral"
      :disabled="!passwordChangeUrl"
      @click="openPasswordChange"
    />
  </UPageCard>

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
</template>
