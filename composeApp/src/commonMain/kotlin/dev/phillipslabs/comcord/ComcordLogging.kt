package dev.phillipslabs.comcord

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.api.logger.Level
import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.backend.DefaultBackend
import de.connect2x.lognity.config.systemLogAppender

private const val COMCORD_LOG_PATTERN = "{{hh}}:{{mm}}:{{ss}}.{{SSS}} {{level}} [{{name}}] {{message}}"

private var isComcordLoggingConfigured = false

fun configureComcordLogging(level: Level = DefaultBackend.defaultLevel) {
    if (isComcordLoggingConfigured) return

    Backend.set(DefaultBackend)
    Logger.defaultConfig = {
        this.level = level
        systemLogAppender(COMCORD_LOG_PATTERN)
    }

    isComcordLoggingConfigured = true
    Logger("dev.phillipslabs.comcord.logging").info { "Logging configured at $level" }
}

fun comcordLogger(name: String): Logger = Logger("dev.phillipslabs.comcord.$name")
