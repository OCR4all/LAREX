<script setup lang="ts">
import type { AdminErrorEventDetail } from '@/types/admin-errors'

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
  <UiResponsiveSlideover
    :open="open"
    :close="{ onClick: () => emit('close') }"
    @update:open="handleOpenChange"
  >
    <template #header>
      <UiSlideoverHeader
        title="Error Details"
        icon="i-lucide-bug"
        description="Inspect the failure context, diagnostic data, and stack trace."
      />
    </template>

    <template #body>
      <div class="space-y-6">
        <div v-if="pending && !errorEvent" class="space-y-4">
          <USkeleton class="h-52 w-full rounded-lg" />
          <USkeleton class="h-32 w-full rounded-lg" />
        </div>

        <UAlert
          v-else-if="errorMessage"
          color="error"
          variant="soft"
          icon="i-lucide-circle-alert"
          title="Could not load error details"
          :description="errorMessage"
        />

        <template v-else-if="errorEvent">
          <UiSlideoverSection
            title="Failure Summary"
            description="Core identifiers and request context for this event."
            icon="i-lucide-triangle-alert"
          >
            <div class="grid gap-3 sm:grid-cols-2">
              <div class="rounded-lg bg-elevated/50 p-4">
                <div class="text-xs font-medium uppercase tracking-wide text-muted">
                  Error ID
                </div>
                <div class="mt-2 flex items-center gap-2">
                  <code class="min-w-0 flex-1 break-all text-sm">{{ errorEvent.id }}</code>
                  <UButton
                    icon="i-lucide-copy"
                    size="xs"
                    variant="ghost"
                    color="neutral"
                    aria-label="Copy error ID"
                    @click="copyErrorId"
                  />
                </div>
              </div>

              <div class="rounded-lg bg-elevated/50 p-4">
                <div class="text-xs font-medium uppercase tracking-wide text-muted">
                  Created
                </div>
                <div class="mt-2 text-sm text-highlighted">
                  {{ formatDate(errorEvent.created) }}
                </div>
              </div>

              <div class="rounded-lg bg-elevated/50 p-4">
                <div class="text-xs font-medium uppercase tracking-wide text-muted">
                  Status
                </div>
                <div class="mt-2 flex flex-wrap items-center gap-2 text-sm text-highlighted">
                  <UBadge :color="errorEvent.severity === 'ERROR' ? 'error' : 'warning'" variant="soft">
                    {{ errorEvent.status }}
                  </UBadge>
                  <span class="break-words">{{ errorEvent.error }}</span>
                </div>
              </div>

              <div class="rounded-lg bg-elevated/50 p-4">
                <div class="text-xs font-medium uppercase tracking-wide text-muted">
                  Context
                </div>
                <dl class="mt-2 space-y-1 text-sm text-highlighted">
                  <div>
                    <dt class="inline text-muted">
                      User:
                    </dt>
                    <dd class="inline">
                      {{ errorEvent.username || errorEvent.userId || '-' }}
                    </dd>
                  </div>
                  <div>
                    <dt class="inline text-muted">
                      Workspace:
                    </dt>
                    <dd class="inline">
                      {{ errorEvent.workspaceId || '-' }}
                    </dd>
                  </div>
                  <div>
                    <dt class="inline text-muted">
                      Method:
                    </dt>
                    <dd class="inline">
                      {{ errorEvent.method }}
                    </dd>
                  </div>
                </dl>
              </div>
            </div>
          </UiSlideoverSection>

          <UiSlideoverSection
            title="Request Details"
            description="The reported message, route, and exception metadata."
            icon="i-lucide-file-warning"
          >
            <div class="space-y-3">
              <div class="rounded-lg bg-elevated/50 p-4 text-sm text-highlighted">
                <div class="mb-2 text-xs font-medium uppercase tracking-wide text-muted">
                  Message
                </div>
                {{ errorEvent.message }}
              </div>

              <div class="rounded-lg bg-elevated/50 p-4 text-sm">
                <div class="mb-2 text-xs font-medium uppercase tracking-wide text-muted">
                  Path
                </div>
                <code class="break-all">{{ errorEvent.path }}</code>
              </div>

              <div v-if="errorEvent.code || errorEvent.exceptionClass" class="grid gap-3 sm:grid-cols-2">
                <div v-if="errorEvent.code" class="rounded-lg bg-elevated/50 p-4 text-sm">
                  <div class="text-xs font-medium uppercase tracking-wide text-muted">
                    Code
                  </div>
                  <code class="mt-2 block break-all">{{ errorEvent.code }}</code>
                </div>

                <div v-if="errorEvent.exceptionClass" class="rounded-lg bg-elevated/50 p-4 text-sm">
                  <div class="text-xs font-medium uppercase tracking-wide text-muted">
                    Exception
                  </div>
                  <code class="mt-2 block break-all">{{ errorEvent.exceptionClass }}</code>
                </div>
              </div>
            </div>
          </UiSlideoverSection>

          <UiSlideoverSection
            v-if="errorEvent.detailsJson || errorEvent.stackTrace"
            title="Diagnostics"
            description="Raw diagnostic information captured with the event."
            icon="i-lucide-square-terminal"
          >
            <div class="space-y-4">
              <div v-if="errorEvent.detailsJson" class="space-y-2">
                <h4 class="text-xs font-medium uppercase tracking-wide text-muted">
                  Details
                </h4>
                <pre class="max-h-72 overflow-auto rounded-lg bg-elevated/50 p-4 text-xs text-muted">{{ errorEvent.detailsJson }}</pre>
              </div>

              <div v-if="errorEvent.stackTrace" class="space-y-2">
                <h4 class="text-xs font-medium uppercase tracking-wide text-muted">
                  Stack Trace
                </h4>
                <pre class="max-h-96 overflow-auto rounded-lg bg-elevated/50 p-4 text-xs text-muted">{{ errorEvent.stackTrace }}</pre>
              </div>
            </div>
          </UiSlideoverSection>
        </template>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
