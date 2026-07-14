/**
 * Structured logging service with production-aware log levels.
 * Prevents debug logs from appearing in production builds.
 */

export type LogLevel = 'debug' | 'info' | 'warn' | 'error'

export interface LogEntry {
  level: LogLevel
  category: string
  message: string
  data?: unknown
  timestamp: Date
}

export interface LoggerOptions {
  /** Minimum log level to output. Defaults to 'debug' in dev, 'warn' in production */
  minLevel?: LogLevel
  /** Enable console output. Defaults to true in dev */
  enableConsole?: boolean
  /** Optional callback for log entries (e.g., for remote logging) */
  onLog?: (entry: LogEntry) => void
}

const LOG_LEVEL_PRIORITY: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3
}

/**
 * Check if we're in production mode.
 * Uses multiple detection methods for reliability.
 */
function isProduction(): boolean {
  if (typeof process !== 'undefined' && process.env?.NODE_ENV === 'production') {
    return true
  }
  if (typeof useRuntimeConfig !== 'undefined') {
    try {
      const config = useRuntimeConfig()
      return config.public?.env === 'production' || import.meta.env?.PROD === true
    } catch {
      // Runtime config is unavailable when this utility runs outside a Nuxt context.
    }
  }
  if (typeof import.meta !== 'undefined' && import.meta.env?.PROD) {
    return true
  }
  return false
}

class LoggerService {
  private options: Required<LoggerOptions>
  private static instance: LoggerService | null = null

  constructor(options: LoggerOptions = {}) {
    const isProd = isProduction()

    this.options = {
      minLevel: options.minLevel ?? (isProd ? 'warn' : 'debug'),
      enableConsole: options.enableConsole ?? !isProd,
      onLog: options.onLog ?? (() => {})
    }
  }

  /**
   * Get or create the singleton logger instance
   */
  static getInstance(options?: LoggerOptions): LoggerService {
    if (!LoggerService.instance) {
      LoggerService.instance = new LoggerService(options)
    }
    return LoggerService.instance
  }

  /**
   * Update logger options at runtime
   */
  configure(options: Partial<LoggerOptions>): void {
    if (options.minLevel !== undefined) {
      this.options.minLevel = options.minLevel
    }
    if (options.enableConsole !== undefined) {
      this.options.enableConsole = options.enableConsole
    }
    if (options.onLog !== undefined) {
      this.options.onLog = options.onLog
    }
  }

  /**
   * Check if a log level should be output
   */
  private shouldLog(level: LogLevel): boolean {
    return LOG_LEVEL_PRIORITY[level] >= LOG_LEVEL_PRIORITY[this.options.minLevel]
  }

  /**
   * Format and output a log entry
   */
  private log(level: LogLevel, category: string, message: string, data?: unknown): void {
    if (!this.shouldLog(level)) {
      return
    }

    const entry: LogEntry = {
      level,
      category,
      message,
      data,
      timestamp: new Date()
    }

    this.options.onLog(entry)

    if (this.options.enableConsole) {
      const prefix = `[${category}]`
      const consoleMethod = level === 'debug' ? 'log' : level

      if (data !== undefined) {
        console[consoleMethod](prefix, message, data)
      } else {
        console[consoleMethod](prefix, message)
      }
    }
  }

  /**
   * Debug level log - development only
   */
  debug(category: string, message: string, data?: unknown): void {
    this.log('debug', category, message, data)
  }

  /**
   * Info level log - general information
   */
  info(category: string, message: string, data?: unknown): void {
    this.log('info', category, message, data)
  }

  /**
   * Warning level log - something unexpected but not fatal
   */
  warn(category: string, message: string, data?: unknown): void {
    this.log('warn', category, message, data)
  }

  /**
   * Error level log - something went wrong
   */
  error(category: string, message: string, data?: unknown): void {
    this.log('error', category, message, data)
  }

  /**
   * Create a scoped logger for a specific category
   */
  scope(category: string): ScopedLogger {
    return new ScopedLogger(this, category)
  }
}

/**
 * Scoped logger that automatically includes category prefix
 */
class ScopedLogger {
  constructor(
    private logger: LoggerService,
    private category: string
  ) {}

  debug(message: string, data?: unknown): void {
    this.logger.debug(this.category, message, data)
  }

  info(message: string, data?: unknown): void {
    this.logger.info(this.category, message, data)
  }

  warn(message: string, data?: unknown): void {
    this.logger.warn(this.category, message, data)
  }

  error(message: string, data?: unknown): void {
    this.logger.error(this.category, message, data)
  }
}

export const logger = LoggerService.getInstance()

export function createLogger(options?: LoggerOptions): LoggerService {
  return new LoggerService(options)
}

export function createScopedLogger(category: string): ScopedLogger {
  return logger.scope(category)
}

export { LoggerService, ScopedLogger }
