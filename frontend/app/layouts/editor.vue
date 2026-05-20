<script setup lang="ts">
useDashboard()
</script>

<template>
  <div class="h-screen w-screen">
    <slot />

    <AppStatusOverlay />
    <AppNotificationsSlideover />
  </div>
</template>
