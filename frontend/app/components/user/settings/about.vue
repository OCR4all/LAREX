<script setup lang="ts">
const { appVersion } = useRuntimeConfig().public
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-lg font-semibold text-highlighted">
        About
      </h2>
      <p class="mt-1 text-sm text-muted">
        Include this version when reporting a bug.
      </p>
    </div>

    <dl class="flex items-center gap-4 text-sm">
      <dt class="text-muted">
        Version
      </dt>
      <dd class="break-all font-mono text-highlighted">
        {{ appVersion }}
      </dd>
    </dl>
  </div>
</template>
