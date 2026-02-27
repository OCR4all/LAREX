import type { Command, CommandContext } from './types'

export class Commander {
  private history: Command[] = []
  private currentIndex: number = -1

  execute<TResult>(command: Command<TResult>, ctx?: CommandContext): TResult {
    this.history = this.history.slice(0, this.currentIndex + 1)

    const result = command.execute(ctx)
    this.history.push(command)
    this.currentIndex++

    return result
  }

  undo(ctx?: CommandContext): boolean {
    if (this.canUndo()) {
      const command = this.history[this.currentIndex]
      if (!command) return false
      command.undo(ctx)
      this.currentIndex--

      return true
    }
    return false
  }

  redo(ctx?: CommandContext): boolean {
    if (this.canRedo()) {
      this.currentIndex++
      const command = this.history[this.currentIndex]
      if (!command) return false
      command.execute(ctx)

      return true
    }
    return false
  }

  canUndo(): boolean {
    return this.currentIndex >= 0
  }

  canRedo(): boolean {
    return this.currentIndex < this.history.length - 1
  }

  getHistory(): string[] {
    return this.history.map((cmd, index) =>
      `${index === this.currentIndex ? '→' : ' '} ${index}: ${cmd.getDescription()}`
    )
  }

  getDetailedHistory() {
    return this.history.map((cmd, index) => ({
      index,
      description: cmd.getDescription(),
      isCurrent: index === this.currentIndex,
      canUndo: index <= this.currentIndex,
      canRedo: index === this.currentIndex + 1
    }))
  }

  jumpToHistory(targetIndex: number, ctx?: CommandContext): boolean {
    if (targetIndex < -1 || targetIndex >= this.history.length) {
      return false
    }

    if (targetIndex < this.currentIndex) {
      while (this.currentIndex > targetIndex) {
        const command = this.history[this.currentIndex]
        if (!command) return false
        command.undo(ctx)
        this.currentIndex--
      }
    } else if (targetIndex > this.currentIndex) {
      while (this.currentIndex < targetIndex) {
        this.currentIndex++
        const command = this.history[this.currentIndex]
        if (!command) return false
        command.execute(ctx)
      }
    }

    return true
  }

  getState() {
    return {
      currentIndex: this.currentIndex,
      totalCount: this.history.length,
      canUndo: this.canUndo(),
      canRedo: this.canRedo()
    }
  }
}

export const commander = new Commander()
