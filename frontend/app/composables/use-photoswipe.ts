import PhotoSwipeLightbox from 'photoswipe/lightbox'
import type PhotoSwipe from 'photoswipe'
import 'photoswipe/style.css'

interface PhotoSwipeSlide {
  src: string
  width: number
  height: number
  alt?: string
}

export function usePhotoSwipe() {
  let lightbox: PhotoSwipeLightbox | null = null
  let pswp: PhotoSwipe | null = null
  const isOpen = ref(false)

  function init(dataSource: PhotoSwipeSlide[], options?: Record<string, unknown>) {
    if (lightbox) {
      lightbox.destroy()
      lightbox = null
    }

    lightbox = new PhotoSwipeLightbox({
      dataSource,
      pswpModule: () => import('photoswipe'),
      ...options
    })

    lightbox.on('openingAnimationStart', () => { isOpen.value = true })
    lightbox.on('close', () => { isOpen.value = false; pswp = null })
    lightbox.on('afterInit', () => { pswp = lightbox?.pswp ?? null })
    lightbox.init()
  }

  function open(index = 0) { lightbox?.loadAndOpen(index) }
  function close() { pswp?.close() }
  function destroy() { lightbox?.destroy(); lightbox = null; pswp = null }

  return { isOpen: readonly(isOpen), init, open, close, destroy }
}
