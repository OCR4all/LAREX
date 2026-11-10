<script setup lang="ts">
withDefaults(defineProps<{
  disabled?: boolean
  loading?: boolean
  loadingLabel?: string
}>(), {
  disabled: false,
  loading: false,
  loadingLabel: 'Starting Action…'
})

const arrowPixels = [[2, 2], [7, 7], [12, 12], [7, 17], [2, 22]]
const arrowOpacities = [1, 0.75, 0.5, 0.3, 0.15]
</script>

<template>
  <UButton
    type="button"
    class="action-start-button relative max-w-full shadow-none inset-shadow-none hover:bg-primary active:bg-primary"
    :disabled="disabled || loading"
    :loading="loading"
    :aria-busy="loading"
  >
    <template #leading>
      <span class="size-5 shrink-0" aria-hidden="true">
        <span class="action-start-button__accent">
          <span class="action-start-button__grain" />
          <UIcon
            v-if="loading"
            name="i-lucide-loader-circle"
            class="size-3.5 motion-safe:animate-spin"
          />
          <template v-else>
            <svg class="action-start-button__arrow" viewBox="0 0 18 28" fill="currentColor">
              <rect
                v-for="([x, y], index) in arrowPixels"
                :key="index"
                :x="x"
                :y="y"
                width="4"
                height="4"
              />
            </svg>
            <span class="action-start-button__trail">
              <svg
                v-for="(opacity, index) in arrowOpacities"
                :key="index"
                viewBox="0 0 18 28"
                fill="currentColor"
                :style="{ opacity }"
              >
                <rect
                  v-for="([x, y], pixel) in arrowPixels"
                  :key="pixel"
                  :x="x"
                  :y="y"
                  width="4"
                  height="4"
                />
              </svg>
            </span>
          </template>
        </span>
      </span>
    </template>
    <span class="action-start-button__label min-w-0 [overflow-wrap:anywhere]">
      <template v-if="loading">{{ loadingLabel }}</template>
      <slot v-else>Start Action</slot>
    </span>
  </UButton>
</template>

<style scoped>
.action-start-button__accent {
  position: absolute;
  z-index: 1;
  inset-block: 6px;
  inset-inline-start: 10px;
  display: grid;
  width: 20px;
  flex-shrink: 0;
  place-items: center;
  overflow: hidden;
  border-radius: 3px;
  background: var(--color-brand-blue);
  color: white;
}

.action-start-button__grain {
  position: absolute;
  inset: 0;
  background: conic-gradient(from 90deg, currentColor 25%, transparent 0) 0 0 / 4px 4px;
  mask-image: linear-gradient(120deg, transparent 15%, black);
  opacity: 0.08;
}

.action-start-button__arrow {
  width: 9px;
  height: 14px;
}

.action-start-button__trail {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding-inline: 10px;
  opacity: 0;
}

.action-start-button__trail svg {
  width: 9px;
  height: 14px;
  flex-shrink: 0;
}

@media (prefers-reduced-motion: no-preference) {
  .action-start-button__accent {
    transition: width 350ms cubic-bezier(0.2, 0, 0, 1),
      inset-inline-start 350ms cubic-bezier(0.2, 0, 0, 1),
      inset-block 350ms cubic-bezier(0.2, 0, 0, 1);
  }

  .action-start-button__arrow,
  .action-start-button__label {
    transition: opacity 100ms ease-out;
  }

  .action-start-button__trail {
    transition: opacity 200ms ease-out;
  }

  .action-start-button:not(:disabled):focus-visible .action-start-button__accent {
    inset-block: 4px;
    inset-inline-start: 4px;
    width: calc(100% - 8px);
  }

  .action-start-button:not(:disabled):focus-visible .action-start-button__arrow,
  .action-start-button:not(:disabled):focus-visible .action-start-button__label {
    opacity: 0;
  }

  .action-start-button:not(:disabled):focus-visible .action-start-button__trail {
    opacity: 1;
  }
}

@media (hover: hover) and (prefers-reduced-motion: no-preference) {
  .action-start-button:not(:disabled):hover .action-start-button__accent {
    inset-block: 4px;
    inset-inline-start: 4px;
    width: calc(100% - 8px);
  }

  .action-start-button:not(:disabled):hover .action-start-button__arrow,
  .action-start-button:not(:disabled):hover .action-start-button__label {
    opacity: 0;
  }

  .action-start-button:not(:disabled):hover .action-start-button__trail {
    opacity: 1;
  }
}
</style>
