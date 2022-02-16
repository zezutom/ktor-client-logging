package com.tomaszezula.ktor.client.logging.config

import org.slf4j.event.Level

data class ResponseConfig(val level: Level, val loggingEnabled: Boolean)
