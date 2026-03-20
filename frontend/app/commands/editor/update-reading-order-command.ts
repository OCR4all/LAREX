import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { ReadingOrder } from '@/models/editor'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'

export interface UpdateReadingOrderCommandData {
  readingOrder: ReadingOrder
}

function cloneReadingOrder(readingOrder: ReadingOrder | undefined): ReadingOrder | undefined {
  if (!readingOrder) return undefined
  return JSON.parse(JSON.stringify(readingOrder)) as ReadingOrder
}

export class UpdateReadingOrderCommand implements Command<void> {
  private readonly readingOrder: ReadingOrder
  private previousReadingOrder?: ReadingOrder

  constructor(data: UpdateReadingOrderCommandData) {
    this.readingOrder = cloneReadingOrder(data.readingOrder)!
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts?.page) return

    this.previousReadingOrder = cloneReadingOrder(pcGts.page.readingOrder)
    pcGts.page.readingOrder = cloneReadingOrder(this.readingOrder)
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    useEditorUiStore().bumpReadingOrderVersion()
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts?.page) return

    pcGts.page.readingOrder = cloneReadingOrder(this.previousReadingOrder)
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    useEditorUiStore().bumpReadingOrderVersion()
  }

  getDescription(): string {
    return 'Update reading order'
  }
}
