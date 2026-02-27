<template>
  <div class="fixed inset-0 z-50 flex h-screen w-full items-center justify-center overflow-hidden bg-background">
    <div class="flex flex-col items-center gap-5">
      <UiLogo :size="60" />
      <UProgress
        :v-model="null"
        animation="swing"
        size="sm"
        class="w-20"
      />
    </div>
  </div>
</template>
