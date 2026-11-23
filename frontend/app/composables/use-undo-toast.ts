interface UndoToastOptions {
  title: string
  description?: string
  onUndo: () => void
}

const UNDO_TOAST_DURATION_MS = 8000

export function useUndoToast() {
  const toast = useToast()

  return (options: UndoToastOptions): void => {
    let undone = false

    toast.add({
      title: options.title,
      description: options.description,
      icon: 'i-lucide-trash-2',
      color: 'neutral',
      duration: UNDO_TOAST_DURATION_MS,
      actions: [{
        label: 'Undo',
        icon: 'i-lucide-undo-2',
        variant: 'outline',
        onClick: () => {
          if (undone) return
          undone = true
          options.onUndo()
        }
      }]
    })
  }
}
