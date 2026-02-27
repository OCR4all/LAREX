import { driver, type Config, type Driver } from 'driver.js'
import 'driver.js/dist/driver.css'
import '@/assets/css/driver-theme.css'

export default defineNuxtPlugin(() => {
  const createDriver = (config?: Config): Driver => {
    return driver({
      showProgress: true,
      progressText: '{{current}} of {{total}}',
      animate: true,
      smoothScroll: true,
      allowClose: true,
      overlayOpacity: 0.5,
      overlayColor: '#000',
      stagePadding: 12,
      stageRadius: 8,
      popoverClass: 'larex-tour-popover',
      nextBtnText: 'Next',
      prevBtnText: 'Back',
      doneBtnText: 'Done',
      ...config
    })
  }

  return {
    provide: {
      createDriver
    }
  }
})

declare module '#app' {
  interface NuxtApp {
    $createDriver: (config?: Config) => Driver
  }
}
