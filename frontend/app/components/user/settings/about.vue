<script setup lang="ts">
const { appVersion } = useRuntimeConfig().public
</script>

<template>
  <div class="space-y-6">
    <div class="relative isolate flex h-24 items-center overflow-hidden rounded-lg bg-elevated/50 px-5">
      <UiDitherWaves variant="banner" />
      <div class="flex items-center gap-3">
        <UiLogo :size="36" />
        <span class="text-lg font-semibold tracking-tight text-highlighted">LAREX</span>
      </div>
    </div>
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
