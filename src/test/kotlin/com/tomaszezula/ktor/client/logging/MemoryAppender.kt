package com.tomaszezula.ktor.client.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.util.*

/**
 * In-memory logging.
 * Credit: https://www.baeldung.com/junit-asserting-logs
 */
class MemoryAppender : ListAppender<ILoggingEvent>() {

    fun reset() {
        this.list.clear()
    }

    fun contains(message: String, level: Level): Boolean =
        this.list.stream()
            .anyMatch { event -> event.toString().contains(message) && event.level == level }

    fun countEvents(loggerName: String): Int =
        this.list.count { event -> event.loggerName == loggerName }

    fun findEvents(message: String): List<ILoggingEvent> =
        this.list.filter { event -> event.toString().contains(message) }

    fun findEvents(message: String, level: Level): List<ILoggingEvent> =
        this.list.filter { event -> event.toString().contains(message) && event.level == level }
    
    fun size(): Int = this.list.size
    
    fun getAllEvents(): List<ILoggingEvent> = Collections.unmodifiableList(this.list)
}