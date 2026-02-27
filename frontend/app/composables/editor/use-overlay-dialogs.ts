import { LazyUiConfirmSlideover } from '#components'

type ConfirmColor = 'primary' | 'error' | 'warning' | 'success' | 'neutral'

export function useOverlayDialogs() {
  const overlay = useOverlay()
  const confirmSlideover = overlay.create(LazyUiConfirmSlideover)

  const confirm = async (options: {
    title: string
    message: string
    confirmLabel?: string
    cancelLabel?: string
    confirmColor?: ConfirmColor
    confirmIcon?: string
  }): Promise<boolean> => {
    const instance = confirmSlideover.open({
      title: options.title,
      message: options.message,
      confirmLabel: options.confirmLabel ?? 'Confirm',
      cancelLabel: options.cancelLabel ?? 'Cancel',
      confirmColor: options.confirmColor ?? 'primary',
      confirmIcon: options.confirmIcon,
      showCancel: true
    })
    return await instance.result
  }

  const alert = (options: {
    title: string
    message: string
    buttonLabel?: string
    color?: ConfirmColor
    icon?: string
  }): void => {
    confirmSlideover.open({
      title: options.title,
      message: options.message,
      confirmLabel: options.buttonLabel ?? 'OK',
      confirmColor: options.color ?? 'neutral',
      confirmIcon: options.icon,
      showCancel: false
    })
  }

  return { confirm, alert }
}
