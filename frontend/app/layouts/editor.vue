<script setup lang="ts">
useDashboard()
</script>

<template>
  <div class="h-screen w-screen">
    <slot />

    <AppNotificationsSlideover />
  </div>
</template>
