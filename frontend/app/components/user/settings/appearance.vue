<script setup lang="ts">
const colorMode = useColorMode()
const themes = [
  { value: 'light', label: 'Light', icon: 'i-lucide-sun', description: 'A bright, clear workspace.' },
  { value: 'dark', label: 'Dark', icon: 'i-lucide-moon', description: 'A softer view in low light.' },
  { value: 'system', label: 'System', icon: 'i-lucide-monitor', description: 'Follow your device settings.' }
]
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-lg font-semibold text-highlighted">
        Appearance
      </h2>
      <p class="mt-1 text-sm text-muted">
        Choose how LAREX looks on this device.
      </p>
    </div>
    <div role="group" aria-label="Color theme" class="grid gap-4 md:grid-cols-3">
      <button
        v-for="theme in themes"
        :key="theme.value"
        type="button"
        :aria-pressed="colorMode.preference === theme.value"
        class="overflow-hidden rounded-lg border text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        :class="colorMode.preference === theme.value ? 'border-primary ring-1 ring-primary bg-primary/5' : 'border-default hover:border-accented bg-default'"
        @click="colorMode.preference = theme.value"
      >
        <div class="bg-elevated/50 p-4" aria-hidden="true">
          <div class="theme-preview" :class="`theme-preview-${theme.value}`">
            <div
              v-for="mode in theme.value === 'system' ? ['light', 'dark'] : [theme.value]"
              :key="mode"
              class="preview-surface"
              :class="`preview-surface-${mode}`"
            >
              <div class="preview-toolbar">
                <span /><span /><span />
              </div>
              <div class="preview-layout">
                <div class="preview-sidebar">
                  <span /><span /><span />
                </div>
                <div class="preview-main">
                  <span /><span /><span />
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="space-y-2 p-4">
          <div class="flex items-center gap-2">
            <UIcon :name="theme.icon" class="size-4 text-muted" />
            <span class="flex-1 font-medium text-highlighted">{{ theme.label }}</span>
            <UIcon v-if="colorMode.preference === theme.value" name="i-lucide-circle-check" class="size-5 text-primary" />
            <span v-else class="size-5 rounded-full border border-default" />
          </div>
          <p class="text-xs text-muted">
            {{ theme.description }}
          </p>
        </div>
      </button>
    </div>
    <p class="flex items-center gap-2 border-t border-default pt-4 text-sm text-muted">
      <UIcon name="i-lucide-check" class="size-4 shrink-0" />
      Your appearance preference is saved automatically.
    </p>
  </div>
</template>

<style scoped>
.theme-preview {
  position: relative;
  overflow: hidden;
  height: 8rem;
  border: 1px solid #e4e2da;
  border-radius: 0.25rem;
}
.theme-preview-dark { border-color: #454041; }
.preview-surface {
  --preview-bg: #fff;
  --preview-toolbar: #f0eee6;
  --preview-block: #e4e2da;
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: var(--preview-bg);
}
.preview-surface-dark {
  --preview-bg: #1b1718;
  --preview-toolbar: #292526;
  --preview-block: #454041;
}
/* Clip a complete dark preview so every element shares the same split. */
.theme-preview-system .preview-surface-dark { clip-path: inset(0 0 0 50%); }
.preview-toolbar { display: flex; gap: 0.2rem; padding: 0.5rem; background: var(--preview-toolbar); }
.preview-toolbar span { width: 0.3rem; height: 0.3rem; border-radius: 50%; background: #9d9993; }
.preview-layout { display: flex; flex: 1; min-height: 0; }
.preview-sidebar { width: 26%; padding: 0.6rem 0.4rem; background: var(--preview-toolbar); }
.preview-sidebar span { display: block; height: 0.35rem; margin-bottom: 0.5rem; background: #9d9993; opacity: 0.4; }
.preview-main { display: grid; flex: 1; gap: 0.4rem; padding: 0.6rem; align-content: start; }
.preview-main span { height: 1.25rem; border-radius: 0.2rem; background: var(--preview-block); }
</style>
