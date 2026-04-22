<script setup lang="ts">
const {
  isHealthy,
  isChecking,
  lastCheckTime,
  consecutiveFailures,
  retryConnection
} = useBackendHealth()

const { loggedIn } = useUserSession()
const isInitialized = useState<boolean>('app.isInitialized', () => false)

const retry = async () => {
  const success = await retryConnection()
  if (success) {
    console.log('Connection restored!')
  }
}

const formatTime = (date: Date) => {
  return new Intl.DateTimeFormat('en', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(date)
}
</script>

<template>
  <Transition
    enter-active-class="transition-all duration-300 ease-out"
    enter-from-class="transform -translate-y-full opacity-0"
    enter-to-class="transform translate-y-0 opacity-100"
    leave-active-class="transition-all duration-200 ease-in"
    leave-from-class="transform translate-y-0 opacity-100"
    leave-to-class="transform -translate-y-full opacity-0"
  >
    <div
      v-if="!isHealthy && loggedIn && isInitialized"
      class="fixed top-0 left-0 right-0 z-900 bg-red-500 text-white px-4 py-2 text-sm"
    >
      <div class="container mx-auto flex items-center justify-between">
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-wifi-off" class="text-white" />
          <span>
            Connection to backend lost
            <span v-if="consecutiveFailures > 1">
              ({{ consecutiveFailures }} failed attempts)
            </span>
          </span>
        </div>

        <div class="flex items-center gap-2">
          <span v-if="lastCheckTime" class="text-red-200 text-xs">
            Last check: {{ formatTime(lastCheckTime) }}
          </span>

          <UButton
            :loading="isChecking"
            size="xs"
            color="neutral"
            variant="outline"
            @click="retry"
          >
            Retry
          </UButton>
        </div>
      </div>
    </div>
  </Transition>
</template>
