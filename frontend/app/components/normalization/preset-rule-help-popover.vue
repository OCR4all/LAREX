<script setup lang="ts">

const props = defineProps<{
  ruleKey: NormalizationPresetRuleKey
}>()

const help = computed(() => NORMALIZATION_PRESET_RULE_HELP[props.ruleKey])
</script>

<template>
  <UPopover mode="hover">
    <UButton
      icon="i-lucide-circle-help"
      color="neutral"
      variant="ghost"
      size="xs"
      class="-mx-1"
      :ui="{ base: 'rounded-full' }"
      aria-label="Show rule details"
    />

    <template #content>
      <div class="max-w-sm space-y-2 p-3">
        <p class="text-sm font-medium">
          {{ help.title }}
        </p>
        <p class="text-xs text-muted">
          {{ help.description }}
        </p>
        <ul v-if="help.details.length > 0" class="space-y-1 text-xs text-muted list-disc pl-4">
          <li
            v-for="detail in help.details"
            :key="detail"
          >
            {{ detail }}
          </li>
        </ul>
      </div>
    </template>
  </UPopover>
</template>
