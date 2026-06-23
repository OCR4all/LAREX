<template>
  <UAlert
    color="warning"
    variant="soft"
    icon="i-lucide-triangle-alert"
    class="my-6"
  >
    <template #description>
      <div class="space-y-2">
        <slot />
      </div>
    </template>
  </UAlert>
</template>
