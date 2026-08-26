<template>
  <div class="flex min-h-screen w-full items-center justify-center bg-elevated">
    <div class="flex flex-col items-center gap-6 px-6 text-center">
      <div class="relative">
        <div class="relative rounded-sm border-2 border-dashed border-neutral-300 bg-neutral-200   p-8 shadow-sm">
          <UIcon
            name="i-lucide-file-text"
            class="h-16 w-16 text-neutral-400"
          />
        </div>
        <div class="absolute -right-2 -top-2 rounded-sm bg-navy-500 p-1.5 shadow-md flex items-center justify-center">
          <UIcon
            name="i-lucide-mouse-pointer-click"
            class="h-3.5 w-3.5 text-white"
          />
        </div>
      </div>

      <div class="flex max-w-md flex-col gap-2">
        <h2 class="text-balance text-2xl font-semibold tracking-tight text-elevated">
          Select a page to begin editing
        </h2>
        <p class="text-pretty text-sm leading-relaxed text-elevated/80">
          Choose a page from the sidebar to start creating regions and defining your document structure
        </p>
      </div>

      <div class="flex items-center gap-2 rounded-sm bg-neutral-200 px-3 py-2 text-xs text-neutral-800">
        <span>Tip: Use</span>
        <UKbd value="meta" />
        <span>+</span>
        <UKbd>
          K
        </UKbd>
        <span>to quickly search pages</span>
      </div>
    </div>
  </div>
</template>
