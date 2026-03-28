<script setup lang="ts">
import type { AdminErrorEventDetail } from '@/types/admin-errors'
import { copyTextToClipboard } from '@/utils/clipboard'

interface Props {
  open: boolean
  errorEvent: AdminErrorEventDetail | null
  pending: boolean
  errorMessage: string | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
}>()

function formatDate(value?: string | null): string {
  if (!value) {
    return '-'
  }

  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(value))
}

async function copyErrorId() {
  if (!props.errorEvent?.id) {
    return
  }

  await copyTextToClipboard(props.errorEvent.id, {
    successTitle: 'Error ID copied',
    failureTitle: 'Copy failed',
    failureDescription: 'Unable to copy the error ID to the clipboard.'
  })
}

function handleOpenChange(open: boolean) {
  if (!open) {
    emit('close')
  }
}
</script>

<template>
  <USlideover
    :open="open"
    title="Error Details"
    :close="{ onClick: () => emit('close') }"
    @update:open="handleOpenChange"
  >
    <template #body>
      <div class="space-y-6">
        <div v-if="pending && !errorEvent" class="text-sm text-muted">
          Loading error details...
        </div>

        <div v-else-if="errorMessage" class="rounded-2xl border border-error/20 bg-error/5 px-4 py-3 text-sm text-error">
          {{ errorMessage }}
        </div>

        <template v-else-if="errorEvent">
          <div class="grid gap-4 md:grid-cols-2">
            <div class="rounded-xl border border-default bg-elevated/20 p-4">
              <div class="text-xs uppercase tracking-wide text-muted">
                Error ID
              </div>
              <div class="mt-2 flex items-center gap-2">
                <code class="min-w-0 flex-1 break-all text-sm">{{ errorEvent.id }}</code>
                <UButton
                  icon="i-lucide-copy"
                  size="xs"
                  variant="outline"
                  color="neutral"
                  @click="copyErrorId"
                />
              </div>
            </div>

            <div class="rounded-xl border border-default bg-elevated/20 p-4">
              <div class="text-xs uppercase tracking-wide text-muted">
                Created
              </div>
              <div class="mt-2 text-sm text-highlighted">
                {{ formatDate(errorEvent.created) }}
              </div>
            </div>

            <div class="rounded-xl border border-default bg-elevated/20 p-4">
              <div class="text-xs uppercase tracking-wide text-muted">
                Status
              </div>
              <div class="mt-2 flex items-center gap-2 text-sm text-highlighted">
                <UBadge :color="errorEvent.severity === 'ERROR' ? 'error' : 'warning'" variant="soft">
                  {{ errorEvent.status }}
                </UBadge>
                <span>{{ errorEvent.error }}</span>
              </div>
            </div>

            <div class="rounded-xl border border-default bg-elevated/20 p-4">
              <div class="text-xs uppercase tracking-wide text-muted">
                Context
              </div>
              <div class="mt-2 space-y-1 text-sm text-highlighted">
                <div>User: {{ errorEvent.username || errorEvent.userId || '-' }}</div>
                <div>Workspace: {{ errorEvent.workspaceId || '-' }}</div>
                <div>Method: {{ errorEvent.method }}</div>
              </div>
            </div>
          </div>

          <section class="space-y-2">
            <h3 class="text-sm font-semibold uppercase tracking-wide text-muted">
              Message
            </h3>
            <div class="rounded-xl border border-default bg-default p-4 text-sm text-highlighted">
              {{ errorEvent.message }}
            </div>
          </section>

          <section class="space-y-2">
            <h3 class="text-sm font-semibold uppercase tracking-wide text-muted">
              Path
            </h3>
            <div class="rounded-xl border border-default bg-default p-4 text-sm">
              <code class="break-all">{{ errorEvent.path }}</code>
            </div>
          </section>

          <section v-if="errorEvent.code || errorEvent.exceptionClass" class="grid gap-4 md:grid-cols-2">
            <div v-if="errorEvent.code" class="rounded-xl border border-default bg-default p-4 text-sm">
              <div class="text-xs uppercase tracking-wide text-muted">
                Code
              </div>
              <code class="mt-2 block break-all">{{ errorEvent.code }}</code>
            </div>

            <div v-if="errorEvent.exceptionClass" class="rounded-xl border border-default bg-default p-4 text-sm">
              <div class="text-xs uppercase tracking-wide text-muted">
                Exception
              </div>
              <code class="mt-2 block break-all">{{ errorEvent.exceptionClass }}</code>
            </div>
          </section>

          <section v-if="errorEvent.detailsJson" class="space-y-2">
            <h3 class="text-sm font-semibold uppercase tracking-wide text-muted">
              Details
            </h3>
            <pre class="overflow-x-auto rounded-xl border border-default bg-default p-4 text-xs text-muted">{{ errorEvent.detailsJson }}</pre>
          </section>

          <section v-if="errorEvent.stackTrace" class="space-y-2">
            <h3 class="text-sm font-semibold uppercase tracking-wide text-muted">
              Stack Trace
            </h3>
            <pre class="overflow-x-auto rounded-xl border border-default bg-default p-4 text-xs text-muted">{{ errorEvent.stackTrace }}</pre>
          </section>
        </template>
      </div>
    </template>
  </USlideover>
</template>
