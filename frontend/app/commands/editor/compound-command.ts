import type { Command, CommandContext } from './types'

export class CompoundCommand implements Command {
  private commands: Command[]
  private description: string
  private executedCount: number = 0

  constructor(commands: Command[], description?: string) {
    this.commands = commands
    this.description = description ?? `Batch operation (${commands.length} commands)`
  }

  execute(ctx?: CommandContext): unknown[] {
    const results: unknown[] = []
    this.executedCount = 0

    for (const command of this.commands) {
      try {
        const result = command.execute(ctx)
        results.push(result)
        this.executedCount++
      } catch (error) {
        this.undoExecuted(ctx)
        throw error
      }
    }

    return results
  }

  undo(ctx?: CommandContext): void {
    for (let i = this.commands.length - 1; i >= 0; i--) {
      const command = this.commands[i]
      if (command) {
        command.undo(ctx)
      }
    }
  }

  private undoExecuted(ctx?: CommandContext): void {
    for (let i = this.executedCount - 1; i >= 0; i--) {
      const command = this.commands[i]
      if (command) {
        try {
          command.undo(ctx)
        } catch {
          // Ignore rollback errors while unwinding a failed compound execution.
        }
      }
    }
  }

  getDescription(): string {
    return this.description
  }

  get count(): number {
    return this.commands.length
  }

  getCommandDescriptions(): string[] {
    return this.commands.map(cmd => cmd.getDescription())
  }
}

export class CompoundCommandBuilder {
  private commands: Command[] = []
  private description: string

  private constructor(description: string) {
    this.description = description
  }

  static create(description: string = 'Batch operation'): CompoundCommandBuilder {
    return new CompoundCommandBuilder(description)
  }

  add(command: Command): CompoundCommandBuilder {
    this.commands.push(command)
    return this
  }

  addAll(commands: Command[]): CompoundCommandBuilder {
    this.commands.push(...commands)
    return this
  }

  addIf(condition: boolean, command: Command): CompoundCommandBuilder {
    if (condition) {
      this.commands.push(command)
    }
    return this
  }

  build(): CompoundCommand {
    return new CompoundCommand([...this.commands], this.description)
  }

  get isEmpty(): boolean {
    return this.commands.length === 0
  }

  get count(): number {
    return this.commands.length
  }
}
