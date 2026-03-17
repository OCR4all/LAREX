export function normalizeSingleLineText(value: string): string {
  return value.replace(/[ \t]*\r?\n+[ \t]*/g, ' ')
}

function insertSanitizedTextAtSelection(target: HTMLTextAreaElement, text: string): void {
  const sanitized = normalizeSingleLineText(text)
  const start = target.selectionStart ?? 0
  const end = target.selectionEnd ?? 0
  target.value = target.value.slice(0, start) + sanitized + target.value.slice(end)
  const nextPos = start + sanitized.length
  target.selectionStart = nextPos
  target.selectionEnd = nextPos
  target.dispatchEvent(new Event('input', { bubbles: true }))
}

export function handleSingleLineTextareaKeydownEnter(event: KeyboardEvent, editable: boolean) {
  if (!editable) return
  if (event.altKey || event.ctrlKey || event.metaKey) return
  event.preventDefault()
}

export function handleSingleLineTextareaBeforeInput(event: InputEvent, editable: boolean) {
  if (!editable) return
  if (event.inputType === 'insertLineBreak' || event.inputType === 'insertParagraph') {
    event.preventDefault()
  }
}

export function handleSingleLineTextareaPaste(event: ClipboardEvent, editable: boolean) {
  if (!editable) return
  const target = event.target
  if (!(target instanceof HTMLTextAreaElement)) return
  const text = event.clipboardData?.getData('text/plain')
  if (typeof text !== 'string' || !/[\r\n]/.test(text)) return
  event.preventDefault()
  insertSanitizedTextAtSelection(target, text)
}

export function handleSingleLineTextareaDrop(event: DragEvent, editable: boolean) {
  if (!editable) return
  const target = event.target
  if (!(target instanceof HTMLTextAreaElement)) return
  const text = event.dataTransfer?.getData('text/plain')
  if (typeof text !== 'string' || text.length === 0 || !/[\r\n]/.test(text)) return
  event.preventDefault()
  insertSanitizedTextAtSelection(target, text)
}
