<script setup lang="ts">
// Preserve bookmarks and authentication callbacks without a dedicated settings page.
definePageMeta({
  middleware: to => navigateTo({
    path: '/',
    query: { ...to.query, settings: String(to.params.section || 'profile') }
  }, { replace: true })
})
</script>

<template>
  <div />
</template>
