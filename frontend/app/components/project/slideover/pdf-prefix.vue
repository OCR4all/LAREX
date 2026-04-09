<script setup lang="ts">
import { computed, ref } from 'vue'

type PdfFileInfo = {
  fileName: string
  defaultPrefix: string
}

const props = defineProps<{
  files: PdfFileInfo[]
}>()

const emit = defineEmits<{
  close: [result: Record<string, string> | null]
}>()

type PdfPrefixState = {
  useFileName: boolean
  customPrefix: string
}

const stateByFileName = ref<Record<string, PdfPrefixState>>(
  Object.fromEntries(
    props.files.map((f) => [
      f.fileName,
      { useFileName: true, customPrefix: f.defaultPrefix }
    ])
  )
)

const resolvedPrefixes = computed<Record<string, string>>(() => {
  const out: Record<string, string> = {}
  for (const f of props.files) {
    const st = stateByFileName.value[f.fileName]
    const prefix = st?.useFileName ? f.defaultPrefix : (st?.customPrefix ?? '').trim()
    out[f.fileName] = prefix
  }
  return out
})

const hasInvalidPrefix = computed(() => {
  for (const f of props.files) {
    const prefix = resolvedPrefixes.value[f.fileName]
    if (!prefix) return true
  }
  return false
})

function ensureFileState(fileName: string): PdfPrefixState {
  const existing = stateByFileName.value[fileName]
  if (existing) return existing

  const file = props.files.find(entry => entry.fileName === fileName)
  const nextState: PdfPrefixState = {
    useFileName: true,
    customPrefix: file?.defaultPrefix ?? ''
  }
  stateByFileName.value[fileName] = nextState
  return nextState
}
</script>

<template>
  <USlideover
    side="right"
    title="PDF Page Prefix"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #body>
      <div class="space-y-4">
        <div class="text-sm text-muted">
          Choose how pages created from the PDF should be named.
        </div>

        <div class="space-y-3">
          <div
            v-for="f in props.files"
            :key="f.fileName"
            class="rounded-sm border border-default p-3 space-y-2"
          >
            <div class="text-sm font-medium truncate">
              {{ f.fileName }}
            </div>

            <div class="flex items-center justify-between gap-3">
              <div class="text-sm">
                Use file name
              </div>
              <USwitch v-model="ensureFileState(f.fileName).useFileName" />
            </div>

            <div v-if="!ensureFileState(f.fileName).useFileName" class="space-y-1">
              <div class="text-sm text-muted">
                Prefix
              </div>
              <UInput v-model="ensureFileState(f.fileName).customPrefix" placeholder="Enter prefix..." />
            </div>

            <div class="text-xs text-muted">
              Pages will be created as {{ resolvedPrefixes[f.fileName] || '…' }}_001, {{ resolvedPrefixes[f.fileName] || '…' }}_002, …
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton
          color="primary"
          variant="solid"
          :disabled="hasInvalidPrefix"
          @click="emit('close', resolvedPrefixes)"
        >
          Continue
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
